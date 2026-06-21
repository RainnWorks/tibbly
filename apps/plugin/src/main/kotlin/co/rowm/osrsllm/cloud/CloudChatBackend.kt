package co.rowm.osrsllm.cloud

import co.rowm.osrsllm.chat.Chat
import co.rowm.osrsllm.chat.ChatBackend
import co.rowm.osrsllm.chat.ChatMessage
import co.rowm.osrsllm.chat.ToolCallListener
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.slf4j.LoggerFactory

/**
 * Adapter that exposes [CloudChatRunner] as the chat panel's [ChatBackend].
 *
 * The chat panel works at the granularity of "one Chat → one turn → one
 * Result". The runner works at the granularity of "one Chat → many deltas
 * → one done frame". This adapter aggregates deltas into a single text
 * result so the panel renders the assistant reply in one bubble (matching
 * the local subprocess UX).
 *
 * The panel's [ToolCallListener] is wired up by [CloudChatRunner]'s
 * tool-dispatch path: when the backend asks the plugin to run a tool, we
 * fire onToolCallStarted, then onToolCallCompleted when the dispatcher
 * returns.
 *
 * RAI-22 NOTE: a single send goes through [CloudChatRunner.send], which
 * funnels all egress through [EgressGate.egress]. There is no direct
 * `webSocket.send` here.
 */
class CloudChatBackend(
    private val runner: CloudChatRunner,
    private val routerSupplier: () -> ContextRouter,
    private val snapshotSupplier: () -> String?,
) : ChatBackend {

    private val log = LoggerFactory.getLogger(CloudChatBackend::class.java)

    override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result {
        val lastUser = chat.messages.lastOrNull { it.role == ChatMessage.Role.USER }
            ?: return ChatBackend.Result(
                success = false,
                text = "(no user message)",
                exitCode = -1,
            )

        val router = runCatching { routerSupplier() }.getOrNull()
        val allowedTools = router?.routeWire(lastUser.text) ?: listOf("core")
        // Backend's ChatSnapshot Zod schema is `z.record(z.string(), z.unknown())` —
        // an object map, not a raw string. Wrap the HarnessContext-built
        // markdown preamble as `{preamble: "..."}` so it serialises cleanly.
        val snapshot = runCatching { snapshotSupplier() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { preamble ->
                buildJsonObject { put("preamble", JsonPrimitive(preamble)) }
            }

        log.info("Cloud send: chatId={} chars={} families={}",
            chat.id, lastUser.text.length, allowedTools.size)

        val result = runner.send(
            chatId = chat.id,
            userText = lastUser.text,
            allowedTools = allowedTools,
            snapshot = snapshot,
        )
        return ChatBackend.Result(
            success = result.ok,
            text = if (result.ok) result.text else (result.errorMessage ?: result.text),
            toolCalls = emptyList(),
            exitCode = if (result.ok) 0 else -1,
        )
    }

    override fun cancel() {
        // CloudChatRunner.cancel(chatId) needs a chatId; the panel only calls
        // cancel() without one. Best effort: the runner remembers the in-flight
        // turn and finishes that one when cancel-by-chat is added (RAI-24).
        log.info("Cloud cancel requested (chat panel does not pass chatId)")
    }
}
