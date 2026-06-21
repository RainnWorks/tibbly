package co.rowm.osrsllm.chat

/**
 * Strategy interface that lets [ChatPanel] dispatch a turn to either the
 * cloud backend ([co.rowm.osrsllm.cloud.CloudChatRunner]) or the BYO direct
 * provider runner ([co.rowm.osrsllm.cloud.DirectChatRunner]).
 *
 * Historically there was a third option — a local `claude -p` subprocess
 * runner — but it was a developer convenience and shipped subprocess
 * spawning in production source. The RuneLite Plugin Hub policy (PR #11453
 * precedent) disallows any subprocess invocation, so that path has been
 * removed outright. Today the selector chooses between cloud, BYO, or no
 * backend at all (tools-only mode, where the chat surface is intentionally
 * inert).
 *
 * The `Result` shape stays a plain data class so the panel's render code
 * does not need to know which concrete runner produced it.
 */
interface ChatBackend {

    data class Result(
        val success: Boolean,
        val text: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val exitCode: Int = 0,
    )

    fun send(chat: Chat, listener: ToolCallListener? = null): Result
    fun cancel()
}

/**
 * Picks the active backend per call to [send] / [cancel].
 *
 * [backendSupplier] returns the cloud-runner adapter (when cloud chat is on
 * AND the WSS link is up) OR the BYO direct runner adapter (when the player
 * has picked a BYO chat mode), OR null. Null means "no chat backend is
 * configured" — the panel renders the result as a configuration error so the
 * player knows what to flip on.
 *
 * Reading the supplier on EVERY call (rather than caching) means a config
 * flip plus plugin restart immediately starts using the new path without
 * dragging the dispatcher into the plugin lifecycle.
 */
class ChatBackendSelector(
    private val backendSupplier: () -> ChatBackend?,
) : ChatBackend {

    override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result {
        val backend = runCatching { backendSupplier() }.getOrNull()
            ?: return ChatBackend.Result(
                success = false,
                text = "No chat backend is configured. Pick a chat mode in the OSRS LLM Helper config (Cloud or one of the Direct: providers).",
                exitCode = -1,
            )
        return backend.send(chat, listener)
    }

    override fun cancel() {
        val backend = runCatching { backendSupplier() }.getOrNull()
        backend?.cancel()
    }
}
