package co.rowm.osrsllm.cloud

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Placeholder [CloudChatRunner.ToolDispatcher] used until the production
 * MCP→cloud tool bridge lands (a follow-up to RAI-22).
 *
 * The cloud-side LLM may still emit `tool_call_request` frames for tools
 * the plugin recognises by name in [ToolRegistry]. This stub answers with
 * a structured `not_yet_wired` payload so the chat loop progresses (rather
 * than the backend timing out waiting for a tool result), and logs the
 * call so the integration is observable end-to-end.
 *
 * Replacing this with a real dispatcher is a localised change: swap the
 * binding in `OsrsLlmHelperPlugin.startUp()` for a dispatcher that
 * delegates to the same tool implementations that `local/McpServerService`
 * already wires up for the developer-mode subprocess path.
 */
class StubToolDispatcher : CloudChatRunner.ToolDispatcher {

    private val log = LoggerFactory.getLogger(StubToolDispatcher::class.java)

    override suspend fun dispatch(toolName: String, input: JsonElement?): JsonElement {
        val knownFamily = ToolRegistry.familyOf(toolName)
        log.warn(
            "tool_call_request for '{}' (family={}) — stub dispatcher returning not_yet_wired",
            toolName, knownFamily?.wireName(),
        )
        return buildJsonObject {
            put("status", "not_yet_wired")
            put("tool", toolName)
            if (knownFamily != null) put("family", knownFamily.wireName())
            put(
                "message",
                "Plugin received the request but the cloud→local tool bridge is not yet active. " +
                    "Track this on the RAI-22 follow-up ticket.",
            )
        }
    }
}
