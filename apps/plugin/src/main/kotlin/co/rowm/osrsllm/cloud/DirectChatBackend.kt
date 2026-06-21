package co.rowm.osrsllm.cloud

import co.rowm.osrsllm.chat.Chat
import co.rowm.osrsllm.chat.ChatBackend
import co.rowm.osrsllm.chat.ChatMessage
import co.rowm.osrsllm.chat.ToolCallListener
import org.slf4j.LoggerFactory

/**
 * Adapter that exposes [DirectChatRunner] as the chat panel's [ChatBackend].
 * Sibling to [CloudChatBackend].
 *
 * Lifecycle nuance vs the cloud adapter:
 *
 *  - The BYO runner has no persistent connection, so there is no
 *    "connected / authenticated" gate to consult here. We always
 *    forward to the runner; it short-circuits in its own preconditions.
 *  - The chat panel does not (yet) render tool calls in BYO mode — the
 *    first cut of BYO is a single-turn assistant reply with no tool
 *    surface. Tool wiring lands in a follow-up PR (the runner already
 *    builds [ChatRequest] in a way that can carry a future
 *    `allowedTools` field; the providers will then map it to their
 *    function-calling shape).
 */
public class DirectChatBackend(
    private val runner: DirectChatRunner,
    private val systemPromptSupplier: () -> String?,
) : ChatBackend {

    private val log = LoggerFactory.getLogger(DirectChatBackend::class.java)

    public override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result {
        val lastUser = chat.messages.lastOrNull { it.role == ChatMessage.Role.USER }
            ?: return ChatBackend.Result(
                success = false,
                text = "(no user message)",
                exitCode = -1,
            )

        val systemPrompt = runCatching { systemPromptSupplier() }.getOrNull()
        log.info("BYO send: chatId={} chars={}", chat.id, lastUser.text.length)

        val result = runner.send(systemPrompt = systemPrompt, userMessage = lastUser.text)
        return ChatBackend.Result(
            success = result.ok,
            text = if (result.ok) result.text else (result.errorMessage ?: result.text),
            toolCalls = emptyList(),
            exitCode = if (result.ok) 0 else -1,
        )
    }

    public override fun cancel() {
        // No persistent connection — nothing to cancel between turns.
        // Mid-turn cancel would require pulling apart the HttpURLConnection,
        // which we leave to a follow-up.
        log.debug("BYO cancel requested (no-op; runner is single-turn)")
    }
}
