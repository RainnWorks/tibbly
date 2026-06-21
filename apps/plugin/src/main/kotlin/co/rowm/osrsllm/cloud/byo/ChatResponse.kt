package co.rowm.osrsllm.cloud.byo

/**
 * Normalized chat-turn response. Each provider decodes its native shape
 * into this so the runner can surface the same text + token usage to the
 * chat panel regardless of where the request went.
 *
 * Token usage is optional because not every provider reports it on every
 * response — when missing the runner just shows the message text without
 * the token line.
 */
public data class ChatResponse(
    /** The assistant's reply text. May be empty if the model returned no text part. */
    val text: String,
    /** Tokens billed for the request (input). Null if the provider didn't report it. */
    val inputTokens: Long? = null,
    /** Tokens billed for the response (output). Null if the provider didn't report it. */
    val outputTokens: Long? = null,
    /** Provider's stop reason (e.g. "end_turn", "stop", "length"). Diagnostic only. */
    val stopReason: String? = null,
)
