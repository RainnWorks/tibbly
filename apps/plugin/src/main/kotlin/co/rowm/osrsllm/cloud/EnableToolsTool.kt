package co.rowm.osrsllm.cloud

import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The meta-tool `enable_tools(family)` — RAI-25's mid-chat escape hatch.
 *
 * The router's first-turn surface is intentionally narrow (CORE + matched
 * families). When the LLM realises it needs a family the router didn't pick
 * — e.g. the user said "I want to fight Vorkath" but the keyword router
 * only enabled COMBAT, and the LLM now needs `add_object_highlights` — it
 * calls `enable_tools(family = "highlights")` and the backend re-inlines
 * that family's tool definitions on the next turn.
 *
 * # Wire shape
 *
 *   input  : `{ "family": "highlights" }` (single) or
 *            `{ "families": ["highlights","nav"] }` (batch)
 *   output : `{ "enabled": ["highlights"], "tools": ["add_object_highlights", ...] }`
 *            or `{ "error": "unknown family: …", "knownFamilies": [...] }`
 *
 * The backend is responsible for actually inlining the tool descriptions in
 * the NEXT turn's prompt; this class's job is to:
 *   (a) validate the requested family,
 *   (b) return the canonical list of tool names that will be re-enabled,
 *       so the LLM can see what it just unlocked, and
 *   (c) emit an AuditLog entry so the player can see the widening on the panel.
 *
 * The tool definition itself ships from the backend (not from MCP) because
 * `local/McpServerService.kt` is the developer-only path. The Description
 * constant below is what RAI-2 inlines into the OpenRouter tool catalog.
 */
@Singleton
class EnableToolsTool @Inject constructor(
    private val auditLog: AuditLog,
) {

    private val log = LoggerFactory.getLogger(EnableToolsTool::class.java)

    /**
     * Anthropic-style tool description that the backend ships in every prompt.
     * Lives here so the registry + meta-tool stay in lockstep and the family
     * names in the prompt match [ToolFamily.wireName].
     */
    val description: String = buildString {
        appendLine("Widen this chat's tool surface to include a new family of OSRS tools.")
        appendLine("Use this when you decide you need a family the initial router didn't pick.")
        appendLine("Calling this is cheap — the families exist precisely so you can pull them in")
        appendLine("when the conversation evolves. Families:")
        for (family in ToolFamily.values()) {
            val tools = ToolRegistry.toolsIn(family)
            if (tools.isEmpty()) continue
            appendLine("  - ${family.wireName()}: ${tools.joinToString(", ")}")
        }
        appendLine("Pass either `family` (single string) or `families` (array of strings).")
    }

    /**
     * Result returned to the LLM after an enable_tools call.
     *
     * Backend serialises this to the LLM as a tool_result content block.
     */
    sealed class Result {
        data class Ok(val enabled: Set<ToolFamily>, val toolNames: List<String>) : Result()
        data class UnknownFamily(val requested: List<String>) : Result()
        data object NoFamilyGiven : Result()
    }

    /**
     * Validate the request and return either the canonical family+tools list
     * (success path) or a structured error (caller turns it into a tool_result
     * the LLM can read and retry from).
     *
     * @param familyNames the wire-form family names the LLM asked to enable.
     *   Caller is responsible for parsing both `family` (single) and
     *   `families` (array) into this single list.
     */
    fun handle(familyNames: List<String>): Result {
        if (familyNames.isEmpty()) {
            log.info("enable_tools called with no families")
            return Result.NoFamilyGiven
        }

        val matched = linkedSetOf<ToolFamily>()
        val unknown = mutableListOf<String>()
        for (raw in familyNames) {
            val fam = ToolFamily.fromWire(raw.trim())
            if (fam == null) unknown += raw else matched += fam
        }

        // If NOTHING parsed, return UnknownFamily so the LLM can self-correct.
        if (matched.isEmpty()) {
            log.info("enable_tools: all families unknown: {}", unknown)
            return Result.UnknownFamily(unknown)
        }

        // Mixed match: succeed with the matched set; the response notes the
        // unknown names so the LLM can fix its next call.
        val tools = matched.flatMap { ToolRegistry.toolsIn(it) }.distinct()
        log.info(
            "enable_tools enabled={} tools={} unknown={}",
            matched.map { it.wireName() }, tools.size, unknown,
        )
        // Audit: visible to the player on the panel as evidence of widening.
        auditLog.record(payloadKind = "EnableTools(${matched.joinToString(",") { it.wireName() }})", sizeBytes = 0)
        return Result.Ok(enabled = matched, toolNames = tools)
    }

    /**
     * Convenience: parse a JSON-string argument bag (the shape MCP / Anthropic
     * tool_use sends) into the family list this tool expects.
     *
     * Accepts either:
     *  - `{"family":"highlights"}` → ["highlights"]
     *  - `{"families":["highlights","nav"]}` → ["highlights","nav"]
     *  - both → concatenated
     */
    fun parseFamilies(arguments: Map<String, Any?>?): List<String> {
        if (arguments == null) return emptyList()
        val single = (arguments["family"] as? String)?.takeIf { it.isNotBlank() }
        val many = (arguments["families"] as? List<*>)
            ?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
            ?: emptyList()
        return buildList {
            if (single != null) add(single)
            addAll(many)
        }
    }
}
