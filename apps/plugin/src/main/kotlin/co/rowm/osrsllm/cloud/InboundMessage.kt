package co.rowm.osrsllm.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sealed family of frames the backend can send to the plugin.
 *
 * Mirrors `ServerToClient` in `packages/shared-types/src/protocol.ts`. The
 * `type` discriminator and field names track that file 1:1, so a frame the
 * backend emits with `encodeServerMessage(msg)` deserialises straight into
 * one of these subtypes via [InboundCodec].
 *
 * Strict mode ([InboundCodec.json]) — unknown fields throw, forcing the
 * backend + plugin to agree on the wire schema in lockstep with the Zod
 * source-of-truth.
 */
@Serializable
sealed class InboundMessage {

    /** Backend accepted the auth handshake. */
    @Serializable
    @SerialName("auth_ok")
    data class AuthOk(
        val userId: String,
        val tier: String,
        val balanceTokens: Long,
    ) : InboundMessage()

    /** Backend rejected the auth handshake. The socket is about to close. */
    @Serializable
    @SerialName("auth_error")
    data class AuthError(
        val reason: String,
    ) : InboundMessage()

    /**
     * Backend asks the plugin to execute a tool. Plugin replies with a
     * [OutboundPayload.ToolResult] keyed by [toolCallId].
     */
    @Serializable
    @SerialName("tool_call_request")
    data class ToolCallRequest(
        val toolCallId: String,
        val name: String,
        val input: JsonElement? = null,
    ) : InboundMessage()

    /** Streamed text delta for the assistant's reply. */
    @Serializable
    @SerialName("assistant_message_delta")
    data class AssistantDelta(
        val chatId: String,
        val delta: String,
    ) : InboundMessage()

    /**
     * Turn finished. `balanceTokens` is the remaining token budget for this
     * user — RAI-24 hooks this into the chat-panel token HUD.
     */
    @Serializable
    @SerialName("assistant_message_done")
    data class AssistantDone(
        val chatId: String,
        val promptTokens: Long,
        val completionTokens: Long,
        val costMicroUsd: Long,
        val balanceTokens: Long,
    ) : InboundMessage()

    /** Structured error from the backend (validation, rate limit, model failure). */
    @Serializable
    @SerialName("error")
    data class ServerError(
        val code: String,
        val message: String,
    ) : InboundMessage()

    /** Heartbeat echo. */
    @Serializable
    @SerialName("pong")
    object Pong : InboundMessage()
}
