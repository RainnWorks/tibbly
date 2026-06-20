package co.rowm.osrsllm.overlay

import co.rowm.osrsllm.chat.Chat
import co.rowm.osrsllm.chat.ChatMessage
import co.rowm.osrsllm.chat.ChatStore
import co.rowm.osrsllm.chat.ClaudeRunner
import co.rowm.osrsllm.chat.ToolCall
import co.rowm.osrsllm.chat.ToolCallListener
import net.runelite.api.ChatMessageType
import net.runelite.api.Client
import net.runelite.api.MenuAction
import net.runelite.api.events.ChatMessage as RsChatMessage
import net.runelite.api.events.CommandExecuted
import net.runelite.api.events.MenuOpened
import net.runelite.api.gameval.InterfaceID
import net.runelite.api.gameval.VarClientID
import net.runelite.client.util.ColorUtil
import net.runelite.client.chat.ChatCommandManager
import net.runelite.client.chat.ChatMessageBuilder
import net.runelite.client.chat.ChatMessageManager
import net.runelite.client.chat.QueuedMessage
import net.runelite.client.events.ChatInput
import net.runelite.client.events.ChatboxInput
import net.runelite.client.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.function.BiConsumer
import java.util.function.BiPredicate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slash commands → claude → results streamed into the AssistantOverlay.
 *
 * Commands:
 *   - `!ai <q>` / `::ai <q>`         continue the active chat with a new question
 *   - `!ai-new <q>` / `::ai-new <q>` reset the active chat first, then ask
 *
 * `!ai` registers with the 3-arg ChatCommandManager variant so its BiPredicate
 * consumes the chatbox input BEFORE it would be sent publicly. `::ai` is a RuneLite
 * developer command and arrives via CommandExecuted (we @Subscribe).
 *
 * Output: the overlay (AssistantOverlayState) renders the response with primitives;
 * a single one-line `[AI] working…` confirmation gets posted to the game chat so the
 * player knows the command landed even before the overlay opens.
 */
@Singleton
class OverlayChatController @Inject constructor(
    private val state: AssistantOverlayStateHolder,
    private val runner: ClaudeRunner,
    private val chatCommandManager: ChatCommandManager,
    private val chatStore: ChatStore,
    private val chatMessageManager: ChatMessageManager,
    private val itemManager: net.runelite.client.game.ItemManager,
    private val iconRegistry: ChatItemIconRegistry,
    private val client: Client,
) {

    private val log = LoggerFactory.getLogger(OverlayChatController::class.java)
    @Volatile private var registered: Boolean = false
    @Volatile private var activeChatId: String? = null

    fun register() {
        if (registered) return
        val unusedExec = BiConsumer<RsChatMessage, String> { _, _ -> }
        val intercept = BiPredicate<ChatInput, String> { _, message ->
            handleInputCommand(message)
            true
        }
        val interceptNew = BiPredicate<ChatInput, String> { _, message ->
            handleInputCommand(message, forceNew = true)
            true
        }
        chatCommandManager.registerCommandAsync("!ai", unusedExec, intercept)
        chatCommandManager.registerCommandAsync("!ai-new", unusedExec, interceptNew)
        registered = true
        log.info("Registered slash commands: !ai / !ai-new / ::ai / ::ai-new")
    }

    fun unregister() {
        if (!registered) return
        runCatching { chatCommandManager.unregisterCommand("!ai") }
        runCatching { chatCommandManager.unregisterCommand("!ai-new") }
        registered = false
    }

    @Subscribe
    fun onCommandExecuted(event: CommandExecuted) {
        val cmd = event.command?.lowercase() ?: return
        val args = event.arguments?.joinToString(" ").orEmpty().trim()
        when (cmd) {
            "ai" -> dispatch(args, forceNew = false)
            "ai-new" -> dispatch(args, forceNew = true)
        }
    }

    /**
     * Right-click on the AI (Trade) tab shows a list of recent chats. Clicking one
     * makes it the active conversation and renders its last response in the overlay.
     */
    @Subscribe
    fun onMenuOpened(event: MenuOpened) {
        val isTradeTab = event.menuEntries.any { entry ->
            val w = entry.widget ?: return@any false
            w.id in TRADE_TAB_WIDGET_IDS
        }
        if (!isTradeTab) return

        val recent = chatStore.list().take(MAX_RECENT_CHATS)
        if (recent.isEmpty()) return

        // Insert in reverse so the most-recent ends up at the top of the chunk.
        for (chat in recent.reversed()) {
            val title = chat.title.take(48)
            client.menu.createMenuEntry(1)
                .setOption("Open")
                .setTarget(ColorUtil.wrapWithColorTag(title, AI_COLOR))
                .setType(MenuAction.RUNELITE)
                .onClick { openChat(chat.id) }
        }
        // Separator-ish "New chat" entry to start fresh from the right-click menu.
        client.menu.createMenuEntry(1)
            .setOption("New chat")
            .setTarget(ColorUtil.wrapWithColorTag("AI", AI_COLOR))
            .setType(MenuAction.RUNELITE)
            .onClick { resetActiveChat(); dismiss() }
    }

    /**
     * Make [chatId] the active conversation and render its last response. The user
     * can then continue with follow-ups (typed in the AI tab or via slash commands).
     */
    fun openChat(chatId: String) {
        val chat = chatStore.read(chatId) ?: return
        log.info("Opening chat {} ('{}') in overlay", chatId, chat.title)
        activeChatId = chatId
        val firstUser = chat.messages.firstOrNull { it.role == ChatMessage.Role.USER }?.text
            ?: chat.title
        val lastAssistant = chat.messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }
        if (lastAssistant == null) {
            state.set(AssistantOverlayState.Thinking(firstUser, emptyList(), System.currentTimeMillis()))
            return
        }
        state.set(
            AssistantOverlayState.Response(
                query = firstUser,
                body = MarkdownToPrimitives.parse(lastAssistant.text),
                toolCalls = lastAssistant.toolCalls,
                finishedAtMs = System.currentTimeMillis(),
                errored = false,
            ),
        )
    }

    /**
     * When the player has the AI tab (= Trade tab, VarClientID.CHAT_VIEW == 6)
     * selected, intercept any plain public-chat message and treat it as a follow-up
     * to the active conversation. Pressing Enter feels natural; no prefix needed.
     *
     * Bails out if:
     *  - The event is already consumed (ChatCommandManager already handled it).
     *  - The chat type isn't public (chatType != 0) — friends/clan/etc stay normal.
     *  - The message starts with `!`/`/`/`::` — explicit slash commands or other
     *    channel prefixes always go through their normal path.
     *  - The AI tab isn't currently the active view.
     */
    @Subscribe
    fun onChatboxInput(event: ChatboxInput) {
        val activeTab = runCatching { client.getVarcIntValue(VarClientID.CHAT_VIEW) }.getOrDefault(0)
        val rawMessage = event.value?.trim().orEmpty()
        if (event.isConsumed) return
        if (event.chatType != 0) {
            log.debug("Skip AI intercept: chatType={} (not public)", event.chatType)
            return
        }
        if (rawMessage.isBlank()) return
        if (rawMessage.startsWith("!") || rawMessage.startsWith("/") || rawMessage.startsWith("::")) return
        if (activeTab != CHAT_VIEW_TRADE) {
            log.debug("Skip AI intercept: CHAT_VIEW={} (need {})", activeTab, CHAT_VIEW_TRADE)
            return
        }
        log.info("Intercepting public chat as AI follow-up (CHAT_VIEW={}, msg='{}')",
            activeTab, rawMessage.take(80))
        event.consume()
        dispatch(rawMessage, forceNew = false)
    }

    private fun handleInputCommand(rawMessage: String, forceNew: Boolean = false) {
        val stripped = rawMessage
            .removePrefix("!ai-new")
            .removePrefix("!ai")
            .trim()
        dispatch(stripped, forceNew)
    }

    private fun dispatch(query: String, forceNew: Boolean) {
        if (query.isBlank()) {
            // Empty query toggles the overlay so the player can re-open the last
            // response without retyping. Hide if it's currently showing.
            val current = state.get()
            if (current is AssistantOverlayState.Hidden) state.reopenLast()
            else state.set(AssistantOverlayState.Hidden)
            return
        }
        if (forceNew) {
            log.info("Starting fresh AI chat (forceNew)")
            activeChatId = null
        }
        runQuery(query)
    }

    fun runQuery(query: String) {
        postChatBreadcrumb("working…")
        val startedAt = System.currentTimeMillis()
        state.set(AssistantOverlayState.Thinking(query, emptyList(), startedAt))

        val chat: Chat = activeChatId
            ?.let { id -> chatStore.read(id) }
            ?: chatStore.create(title = query.take(60)).also { activeChatId = it.id }
        activeChatId = chat.id

        val withUser = chatStore.appendMessage(chat.id, ChatMessage(ChatMessage.Role.USER, query))

        Thread({
            val collected = mutableListOf<ToolCall>()
            val toolIndex = HashMap<String, Int>()
            val listener = object : ToolCallListener {
                override fun onToolCallStarted(id: String, name: String, input: String) {
                    synchronized(collected) {
                        toolIndex[id] = collected.size
                        collected += ToolCall(name = name, input = input, result = "(running…)")
                        publishThinking(query, startedAt, collected)
                    }
                }
                override fun onToolCallCompleted(id: String, result: String) {
                    synchronized(collected) {
                        val idx = toolIndex[id] ?: return
                        if (idx in collected.indices) {
                            collected[idx] = collected[idx].copy(result = result)
                            publishThinking(query, startedAt, collected)
                        }
                    }
                }
            }

            val result = try { runner.send(withUser, listener) }
                catch (t: Throwable) {
                    log.warn("AI run failed", t)
                    ClaudeRunner.RunResult(success = false, text = "Run failed: ${t.message}")
                }

            val body = if (result.text.isBlank()) "(no response)" else result.text
            state.set(
                AssistantOverlayState.Response(
                    query = query,
                    body = MarkdownToPrimitives.parse(body),
                    toolCalls = result.toolCalls.ifEmpty { collected.toList() },
                    finishedAtMs = System.currentTimeMillis(),
                    errored = !result.success,
                ),
            )
            // Also stream the response into the AI chat channel so the player can
            // scroll back through it later, copy/paste, etc.
            postResponseToAiChannel(body, error = !result.success)
            chatStore.appendMessage(
                chat.id,
                ChatMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    text = body,
                    toolCalls = result.toolCalls.ifEmpty { collected.toList() },
                ),
            )
        }, "osrsllm-overlay-send").apply { isDaemon = true }.start()
    }

    private fun publishThinking(query: String, startedAt: Long, calls: MutableList<ToolCall>) {
        state.set(AssistantOverlayState.Thinking(query, calls.toList(), startedAt))
    }

    fun dismiss() { state.set(AssistantOverlayState.Hidden) }
    fun resetActiveChat() { activeChatId = null }

    /**
     * Single-line acknowledgment in the AI chat tab so the player sees "I heard you"
     * even before the overlay finishes opening.
     */
    private fun postChatBreadcrumb(line: String) = queueAiLine(line, BODY_DIM)

    /**
     * Post each non-empty line of the response into the AI chat tab. We use
     * [ChatMessageType.TRADE] because the chatbox's Trade tab (which we've relabeled
     * to "AI" via [AiChannelService]) filters to that type — so AI messages appear
     * there but stay out of Public/Game/Private tabs.
     */
    private fun postResponseToAiChannel(body: String, error: Boolean) {
        if (error) {
            queueAiLine("error: ${body.take(160)}", ERROR_COLOR)
            return
        }
        val itemIds = ITEM_REF_PATTERN.findAll(body).map { it.groupValues[1].toInt() }.toSet()
        val iconMap = if (itemIds.isNotEmpty()) iconRegistry.ensure(itemIds) else emptyMap()
        for (line in formatForChat(body, iconMap)) queueAiLine(line, Color.WHITE)
    }

    private fun queueAiLine(line: String, body: Color) {
        val built = ChatMessageBuilder()
            .append(AI_COLOR, "[AI] ")
            .append(body, line)
            .build()
        chatMessageManager.queue(
            QueuedMessage.builder()
                .type(ChatMessageType.TRADE)
                .runeLiteFormattedMessage(built)
                .build(),
        )
    }

    /**
     * Massage claude's markdown into clean chat lines.
     *  - `[item:N|qty]` → `<img=registry_idx>Item name × qty`
     *  - **bold** / *italic* / `code` markers stripped (chat can't render them well)
     *  - heading markers stripped, `-`/`*` bullets → `• ` prefix
     *  - Lines longer than ~80 visible chars soft-wrap at the nearest space
     */
    private fun formatForChat(text: String, iconMap: Map<Int, Int>): List<String> {
        val sanitized = text
            .replace(ITEM_REF_PATTERN) { m ->
                val id = m.groupValues[1].toInt()
                val qty = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                val name = runCatching { itemManager.getItemComposition(id).name }
                    .getOrDefault("item $id")
                val tail = if (qty > 1) "$name × $qty" else name
                iconMap[id]?.let { idx -> "<img=$idx>$tail" } ?: tail
            }
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""(?<!\*)\*([^*\n]+?)\*(?!\*)"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""^#+\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""^[-*]\s+""", RegexOption.MULTILINE), "• ")

        val out = mutableListOf<String>()
        for (rawLine in sanitized.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            var remaining = trimmed
            while (visibleLength(remaining) > MAX_CHAT_LINE) {
                val cut = remaining.substring(0, MAX_CHAT_LINE).lastIndexOf(' ')
                    .takeIf { it > 30 }
                    ?: MAX_CHAT_LINE
                out += remaining.substring(0, cut)
                remaining = remaining.substring(cut).trimStart()
            }
            if (remaining.isNotEmpty()) out += remaining
        }
        return out
    }

    private fun visibleLength(s: String): Int =
        s.length - IMG_OR_COLOR_TAG.findAll(s).sumOf { it.value.length }

    companion object {
        private val AI_COLOR = Color(140, 214, 168)
        private val BODY_DIM = Color(170, 170, 170)
        private val ERROR_COLOR = Color(232, 100, 100)
        private const val MAX_CHAT_LINE = 80
        private val ITEM_REF_PATTERN = Regex("""\[item:(\d+)(?:\|(\d+))?]""")
        private val IMG_OR_COLOR_TAG = Regex("""<(?:img=\d+|col=[0-9a-fA-F]+|/col)>""")
        /** VarClientID.CHAT_VIEW value when the Trade tab (= our AI tab) is selected. */
        private const val CHAT_VIEW_TRADE = 6
        /** Widget IDs that count as "the Trade tab" for right-click menu detection. */
        private val TRADE_TAB_WIDGET_IDS = setOf(
            InterfaceID.Chatbox.CHAT_TRADE,
            InterfaceID.Chatbox.CHAT_TRADE_GRAPHIC,
            InterfaceID.Chatbox.CHAT_TRADE_TEXT,
            InterfaceID.Chatbox.CHAT_TRADE_FILTER,
        )
        /** Max recent chats listed in the right-click menu (keeps the menu manageable). */
        private const val MAX_RECENT_CHATS = 8
    }
}
