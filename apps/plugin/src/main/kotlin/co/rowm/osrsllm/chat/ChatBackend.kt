package co.rowm.osrsllm.chat

/**
 * Strategy interface that lets [ChatPanel] dispatch a turn to either the
 * legacy local `claude -p` subprocess ([ClaudeRunner]) or the new cloud
 * backend ([co.rowm.osrsllm.cloud.CloudChatRunner]).
 *
 * Why an interface and not a polymorphic ClaudeRunner: ClaudeRunner is the
 * developer-only path (subprocess + MCP) and is kept untouched. The cloud
 * path is a separate runtime that shares only the chat panel's UX. The
 * `Result` shape mirrors [ClaudeRunner.RunResult] so the panel's render
 * code keeps working unchanged.
 *
 * Selection happens in [ChatBackendSelector] from a `cloudChatEnabled`
 * supplier — the legacy path stays the DEFAULT.
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
 * Adapts [ClaudeRunner] to the [ChatBackend] interface. Identity in / out —
 * the panel cannot tell the difference. Useful so the dispatcher can hold
 * `ChatBackend` references without knowing which concrete runner is active.
 */
class LocalClaudeBackend(private val delegate: ClaudeRunner) : ChatBackend {
    override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result {
        val r = delegate.send(chat, listener)
        return ChatBackend.Result(
            success = r.success,
            text = r.text,
            toolCalls = r.toolCalls,
            exitCode = r.exitCode,
        )
    }

    override fun cancel() = delegate.cancel()
}

/**
 * Picks the active backend per call to [send] / [cancel].
 *
 * `cloudBackendSupplier()` returns the cloud-runner adapter when the player
 * has flipped `cloudChatEnabled = true` and the WSS link is up; otherwise
 * the supplier returns null and we fall back to [localBackend].
 *
 * Reading the supplier on EVERY call (rather than caching) means a config
 * flip + plugin restart immediately starts using the new path without
 * dragging the dispatcher into the plugin lifecycle.
 */
class ChatBackendSelector(
    private val localBackend: ChatBackend,
    private val cloudBackendSupplier: () -> ChatBackend?,
) : ChatBackend {

    override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result {
        val cloud = runCatching { cloudBackendSupplier() }.getOrNull()
        return (cloud ?: localBackend).send(chat, listener)
    }

    override fun cancel() {
        val cloud = runCatching { cloudBackendSupplier() }.getOrNull()
        cloud?.cancel()
        localBackend.cancel()
    }
}
