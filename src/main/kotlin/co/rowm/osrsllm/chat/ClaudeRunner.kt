package co.rowm.osrsllm.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Shells out to `claude -p --output-format stream-json --verbose` and parses tool_use /
 * tool_result events so the chat UI can display them alongside the final text.
 *
 * `claude -p` is single-turn, so we replay the user/assistant text history each turn.
 * Tool metadata is for UI display only and is not replayed.
 */
/**
 * Listener for live events from `claude -p --output-format stream-json`. The reader
 * thread parses each JSON line as it arrives and dispatches here so the chat UI can
 * show tool activity in real time — otherwise the user stares at a "thinking…" bubble
 * for a long turn while claude runs many tool calls silently in the background.
 *
 * All callbacks fire on the reader thread; implementations must marshal to EDT.
 */
interface ToolCallListener {
    fun onToolCallStarted(id: String, name: String, input: String) {}
    fun onToolCallCompleted(id: String, result: String) {}
}

class ClaudeRunner(
    private val mcpUrlSupplier: () -> String? = { null },
    private val allowedToolsSupplier: () -> List<String> = { emptyList() },
    private val contextSupplier: () -> String? = { null },
) {

    private val log = LoggerFactory.getLogger(ClaudeRunner::class.java)

    @Volatile private var currentProcess: Process? = null

    fun cancel() {
        currentProcess?.let { p ->
            log.info("Cancelling claude subprocess pid={}", runCatching { p.pid() }.getOrNull())
            runCatching { p.descendants().forEach { it.destroyForcibly() } }
            p.destroyForcibly()
        }
    }

    private val systemPrompt = """
        You are an in-game OSRS (Old School RuneScape) assistant. The player is mid-play
        and wants concise, actionable answers. Markdown OK. Item names + quantities exact.

        # State preamble (read FIRST)
        A "Current state" block follows. It already contains: account type, player vitals,
        location + region, active prayers, combat target HP%, spellbook, attack style,
        slayer task, buffs, FULL STATS (one-line abbreviations), FULL WORN EQUIPMENT,
        INVENTORY CONTENTS (item + qty), bank staleness + size, quest counts, GE offer
        count, active bank tag, open interfaces, and last few events.
        DO NOT call get_stats / get_equipment / get_inventory / get_player_state / get_buffs
        / get_slayer_task / get_attack_style / get_spellbook — they're already in the
        preamble. Only call them if the preamble is missing or you need experience-points
        precision.

        # Speed rules (the player is waiting)
        1. PARALLEL: You can emit MULTIPLE tool_use blocks in one assistant turn —
           they run in parallel. ALWAYS batch independent tools (wiki_search +
           get_bank + get_quest_guide go together, not sequentially).
        2. BANK BATCHING: get_bank takes a `queries` ARRAY — put EVERY substring you'll
           need in ONE call. Splitting wastes a roundtrip per call. No penalty for 50+
           queries in one call.
        3. WIKI: prefer wiki_search snippets. If you need page content, jump straight to
           the right `section` index — don't fetch the full page first.
        4. QUESTS: use get_quest_guide(name, section?), not wiki_page("X/Quick guide").

        # Tools (grouped by purpose)
        Live state: get_combat_info (target HP%), get_xp_rates, get_session_loot,
          get_hitsplat_history, get_diary_progress, get_combat_achievements, get_poh,
          get_quests(state), get_event_log(query, sinceSeconds).
          (player/stats/equipment/inventory/buffs/slayer/spellbook/style/prayers are
          ALREADY in the preamble — don't re-fetch.)
        World: get_nearby_npcs, get_nearby_objects, get_ground_items.
        Bank: get_bank(queries=[...]) BATCH multi-search, list_bank_tabs, get_bank_tab,
          create_bank_tab, remove_bank_tab, open_bank_tab,
          save_equipment_loadout (lay out worn-slots-left + inventory-right, with
            optional metadata + decoration items),
          create_grouped_bank_tab (rows-of-items groups, blank-row separators —
            for "items grouped by quest step / by tier / by category").
        Wiki/market: wiki_search(query) snippets first; wiki_page(title, section?, maxChars?)
          jump to section directly; get_quest_guide(name, section?) for quest help;
          ge_price(query) live GE prices.
        Items: find_item(query) for OSRS item-id lookup (in-memory index, instant) —
          use this for any "what's the id of X" before any other tool. Returns
          {id, name, price}.
        Navigation: find_transport(destination?, includeUnavailable?), find_location(query),
          find_nearest_pois(type).
        On-screen UI: list_open_interfaces, read_interface(groupId, maxNodes?).
          Use these when the preamble says an interface is open — to read NPC dialog,
          shop prices, dialog options, quest journal contents, etc.

        # Routing
        - quest help → get_quest_guide(name) — NOT wiki_page
        - "how do I get to X" → find_transport. Use ONLY what's in results[].
          Each hit: `readiness` ('ready' / 'bank-only' / 'locked') + `use` (item(s) to
          use, with id/name/location) + `missing` (only for locked). Tell the player
          which item to use (e.g. "use your Amulet of glory(4) from your inventory") —
          don't supplement from general knowledge. If results empty, retry with
          includeUnavailable=true to suggest what to unlock.
        - "what's near me" → get_nearby_objects / npcs / ground_items
        - "what just happened" / drops / damage / level → get_event_log(query)
        - "how do I beat X" → wiki_search, then wiki_page(title, section="Strategy"/"Mechanics")
        - "what's X worth" → ge_price (ironmen can't buy from GE but can sell)
        - loadout creation → save_equipment_loadout (after planning items via get_bank queries=[…])

        # Account type (silently shapes every recommendation)
        Ironman/UIM/HCIM/GIM: no GE buying, no trading, no scavenging drops. Self-
          sufficient only. ge_price is reference only. Never suggest "buy it off the GE".
        UIM: no bank — minimise banking, use Looting bag / Rune pouch / deposit boxes.
        HCIM / HCGIM: one life — if HP is low, survival/escape FIRST.

        # Tool envelope
        status="ok" → use data. status="not-ready" → check notReady code + message,
        don't fabricate; tell the player what to do (e.g. open bank) and fall back to
        wiki / general knowledge.

        # Inline icons (overlay rendering)
        When you mention an OSRS item the renderer should show with its sprite, emit
        a `[item:<itemId>]` token in your response. The overlay turns each token into
        an inline icon + auto-pulled item name, so you don't need to write the name
        yourself. Use `[item:<itemId>|<qty>]` for stacks.
        Examples:
          "Use your [item:1712]"   → renders inline icon + "Amulet of glory(4)"
          "Withdraw [item:2434|4]" → icon + "Prayer potion(4) × 4"
        Get ids via find_item. Don't drown text in icons — only the items that
        actually help the player visualise. Plain markdown otherwise (paragraphs,
        `**bold**`, `*italic*`, `` `code` ``, `- bullets`, `| tables |`).
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

    data class RunResult(
        val success: Boolean,
        val text: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val exitCode: Int = 0,
    )

    /**
     * Pulled dynamically from McpServerService at send time so the allow list stays in
     * sync with whatever tools have been registered. No more hardcoded mirror list.
     */
    private fun allowedTools(): String {
        val tools = runCatching { allowedToolsSupplier() }.getOrDefault(emptyList())
        return tools.joinToString(",")
    }

    fun send(chat: Chat, listener: ToolCallListener? = null, timeoutSeconds: Long = 300): RunResult {
        val prompt = buildPrompt(chat)
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/zsh"

        val mcpUrl = runCatching { mcpUrlSupplier() }.getOrNull()
        val mcpArgs = if (mcpUrl != null) {
            // Inline MCP config + strict mode means claude sees only our osrs server,
            // ignoring whatever else the user has registered globally.
            val cfg = """{"mcpServers":{"osrs":{"type":"http","url":"$mcpUrl"}}}"""
            "--strict-mcp-config --mcp-config '$cfg' "
        } else ""

        // --tools "" disables every built-in tool. Combined with --strict-mcp-config
        // and our explicit --allowedTools whitelist, claude can ONLY use the OSRS MCP
        // tools — no Bash, Read, Edit, Grep, Web*, Task, or any future built-in.
        val cmd = "claude -p --output-format stream-json --verbose " +
            mcpArgs +
            "--tools \"\" " +
            "--allowedTools \"${allowedTools()}\""

        log.info("Send: chat={} messages={} promptChars={} timeoutS={}",
            chat.id, chat.messages.size, prompt.length, timeoutSeconds)
        log.debug("Command: {} -l -c '{}'", shell, cmd)
        log.debug("Full prompt:\n{}", prompt)

        val pb = ProcessBuilder(shell, "-l", "-c", cmd)
        pb.redirectErrorStream(true)
        return try {
            val start = System.currentTimeMillis()
            val proc = pb.start()
            currentProcess = proc

            proc.outputStream.bufferedWriter().use { it.write(prompt) }

            // Read on a separate thread so waitFor(timeout) can actually kill the
            // process if it hangs. Otherwise readText() blocks indefinitely waiting
            // for EOF and the timeout never gets a chance to fire.
            val outputBuilder = StringBuilder()
            val readerErr = arrayOfNulls<Throwable>(1)
            val readerThread = Thread({
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(outputBuilder) { outputBuilder.appendLine(line) }
                        if (listener != null) {
                            runCatching { dispatchLiveEvent(line, listener) }
                                .onFailure { log.debug("Live event dispatch failed: {}", it.message) }
                        }
                    }
                } catch (t: Throwable) {
                    readerErr[0] = t
                }
            }, "osrsllm-claude-reader").apply { isDaemon = true; start() }

            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val elapsed = System.currentTimeMillis() - start

            if (!finished) {
                log.warn("claude -p timed out after {}s, killing pid={}", timeoutSeconds,
                    runCatching { proc.pid() }.getOrNull())
                runCatching { proc.descendants().forEach { it.destroyForcibly() } }
                proc.destroyForcibly()
                readerThread.join(2000)
                val partial = synchronized(outputBuilder) { outputBuilder.toString() }
                return RunResult(false,
                    "Timed out after ${timeoutSeconds}s. Try a tighter question — claude may be stuck on a huge tool result.",
                    exitCode = -1)
            }

            readerThread.join(3000)
            val output = synchronized(outputBuilder) { outputBuilder.toString() }
            log.debug("Raw claude output ({} chars, {}ms):\n{}", output.length, elapsed, output.take(4000))
            readerErr[0]?.let { log.warn("Reader thread error: {}", it.message) }

            val exit = proc.exitValue()
            if (exit != 0) {
                log.warn("claude -p exited {} after {}ms", exit, elapsed)
                RunResult(false, output.trim().ifEmpty { "claude exited $exit" }, exitCode = exit)
            } else {
                val parsed = parseStreamJson(output)
                log.info("Send done: success={} textChars={} toolCalls={} elapsedMs={}",
                    parsed.success, parsed.text.length, parsed.toolCalls.size, elapsed)
                parsed.toolCalls.forEach { tc ->
                    log.info("  tool: {} input={} resultChars={}",
                        tc.name, tc.input.take(120), tc.result.length)
                }
                parsed
            }
        } catch (t: Throwable) {
            log.error("Failed to run claude", t)
            RunResult(false, "Failed to run claude: ${t.message}", exitCode = -1)
        } finally {
            currentProcess = null
        }
    }

    /**
     * Parse a single stream-json line and emit live events to [listener]. Same event
     * shapes as [parseStreamJson] — tool_use blocks fire onToolCallStarted, tool_result
     * blocks fire onToolCallCompleted. Result events (final text) are handled in the
     * post-process parse, so we don't surface them here.
     */
    private fun dispatchLiveEvent(line: String, listener: ToolCallListener) {
        val t = line.trim()
        if (!t.startsWith("{")) return
        val event = json.parseToJsonElement(t).jsonObject
        when (event["type"]?.jsonPrimitive?.content) {
            "assistant" -> {
                val content = event["message"]?.jsonObject?.get("content")?.jsonArray ?: return
                for (block in content) {
                    val obj = block.jsonObject
                    if (obj["type"]?.jsonPrimitive?.content != "tool_use") continue
                    val id = obj["id"]?.jsonPrimitive?.content ?: continue
                    val name = obj["name"]?.jsonPrimitive?.content ?: continue
                    val input = obj["input"]?.toString() ?: "{}"
                    listener.onToolCallStarted(id, name, prettyJson(input))
                }
            }
            "user" -> {
                val content = event["message"]?.jsonObject?.get("content")?.jsonArray ?: return
                for (block in content) {
                    val obj = block.jsonObject
                    if (obj["type"]?.jsonPrimitive?.content != "tool_result") continue
                    val id = obj["tool_use_id"]?.jsonPrimitive?.content ?: continue
                    listener.onToolCallCompleted(id, stringifyContent(obj["content"]))
                }
            }
        }
    }

    private fun parseStreamJson(output: String): RunResult {
        val toolUses = LinkedHashMap<String, Pair<String, String>>() // id -> (name, inputJson)
        val toolResults = HashMap<String, String>()                  // id -> resultText
        val orderedIds = mutableListOf<String>()
        var finalText: String? = null
        var sawAnyEvent = false
        var errorText: String? = null

        for (line in output.lineSequence()) {
            val t = line.trim()
            if (!t.startsWith("{")) continue
            val event = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: continue
            sawAnyEvent = true
            when (event["type"]?.jsonPrimitive?.content) {
                "assistant" -> extractToolUses(event, toolUses, orderedIds)
                "user" -> extractToolResults(event, toolResults)
                "result" -> {
                    finalText = event["result"]?.jsonPrimitive?.content
                    val subtype = event["subtype"]?.jsonPrimitive?.content
                    if (subtype != null && subtype != "success") {
                        errorText = event["error"]?.jsonPrimitive?.content ?: "subtype=$subtype"
                    }
                }
            }
        }

        if (!sawAnyEvent) {
            // claude didn't emit JSON (maybe an error before the JSON stream started)
            return RunResult(false, output.trim().ifEmpty { "(empty response)" }, exitCode = 0)
        }

        val toolCalls = orderedIds.mapNotNull { id ->
            val (name, input) = toolUses[id] ?: return@mapNotNull null
            ToolCall(
                name = name,
                input = prettyJson(input),
                result = toolResults[id] ?: "(no result)",
            )
        }

        val text = finalText?.trim().orEmpty().ifEmpty {
            errorText ?: "(no text response)"
        }
        return RunResult(success = errorText == null, text = text, toolCalls = toolCalls)
    }

    private fun extractToolUses(
        event: JsonObject,
        toolUses: LinkedHashMap<String, Pair<String, String>>,
        orderedIds: MutableList<String>,
    ) {
        val content = event["message"]?.jsonObject?.get("content")?.jsonArray ?: return
        for (block in content) {
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "tool_use") continue
            val id = obj["id"]?.jsonPrimitive?.content ?: continue
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val input = obj["input"]?.toString() ?: "{}"
            toolUses[id] = name to input
            orderedIds.add(id)
        }
    }

    private fun extractToolResults(event: JsonObject, toolResults: HashMap<String, String>) {
        val content = event["message"]?.jsonObject?.get("content")?.jsonArray ?: return
        for (block in content) {
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "tool_result") continue
            val id = obj["tool_use_id"]?.jsonPrimitive?.content ?: continue
            toolResults[id] = stringifyContent(obj["content"])
        }
    }

    private fun stringifyContent(c: kotlinx.serialization.json.JsonElement?): String = when (c) {
        null -> ""
        is JsonPrimitive -> c.content
        is JsonArray -> c.joinToString("\n") { item ->
            item.jsonObject["text"]?.jsonPrimitive?.content ?: item.toString()
        }
        else -> c.toString()
    }

    private fun prettyJson(raw: String): String =
        runCatching {
            val element = json.parseToJsonElement(raw)
            prettyJson.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                element,
            )
        }.getOrDefault(raw)

    private fun buildPrompt(chat: Chat): String = buildString {
        appendLine(systemPrompt)
        appendLine()
        val ctx = runCatching { contextSupplier() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (ctx != null) {
            appendLine(ctx)
            appendLine()
        }
        val msgs = chat.messages
        if (msgs.size <= 1) {
            val first = msgs.firstOrNull { it.role == ChatMessage.Role.USER }
            if (first != null) append(first.text)
        } else {
            appendLine("Conversation so far:")
            appendLine()
            for (m in msgs) {
                val role = if (m.role == ChatMessage.Role.USER) "User" else "Assistant"
                appendLine("$role: ${m.text}")
                appendLine()
            }
            appendLine("Respond to the user's most recent message. Don't repeat their question.")
        }
    }
}
