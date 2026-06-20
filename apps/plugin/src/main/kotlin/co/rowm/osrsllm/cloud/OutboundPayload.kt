package co.rowm.osrsllm.cloud

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed class — every wire-shape the plugin can send to the backend.
 *
 * REVIEWER NOTE — RAI-38:
 * The set of subtypes below is the COMPLETE outbound schema. Adding a new
 * piece of data to the wire requires a new subtype here, which forces:
 *   (a) a corresponding entry in `apps/plugin/DATA_DISCLOSURE.md`, and
 *   (b) an explicit case in `EgressGate.egress(...)`.
 *
 * Each subtype's KDoc names the section of `DATA_DISCLOSURE.md` it maps to.
 * Free-form payload bags (e.g. `Map<String, Any>`) are deliberately not
 * permitted — that's what kept this surface area auditable.
 *
 * Encoded with strict kotlinx.serialization (`ignoreUnknownKeys = false`)
 * so a typo in a field name fails the build rather than silently shipping.
 */
@Serializable
sealed class OutboundPayload {

    /** DATA_DISCLOSURE.md §1 — Session handshake. */
    @Serializable
    @SerialName("session.hello")
    data class SessionHello(
        val deviceKey: String,
        val playerName: String?,
        val pluginVersion: String,
        val rsAccountType: String?,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §2 — User chat input. */
    @Serializable
    @SerialName("chat.userMessage")
    data class ChatUserMessage(
        val sessionId: String,
        val turnId: String,
        val text: String,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §3 — Compact game-state preamble. */
    @Serializable
    @SerialName("chat.statePreamble")
    data class ChatStatePreamble(
        val sessionId: String,
        val turnId: String,
        val preambleJson: String,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §4 — Tool call results returned to the backend's agent. */
    @Serializable
    @SerialName("tool.result")
    data class ToolResult(
        val sessionId: String,
        val turnId: String,
        val toolCallId: String,
        val toolName: String,
        val resultJson: String,
        val isError: Boolean,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §5 — User explicitly cancelling an in-flight turn. */
    @Serializable
    @SerialName("chat.cancel")
    data class ChatCancel(
        val sessionId: String,
        val turnId: String,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §6 — Heartbeat / liveness ping for the persistent socket. */
    @Serializable
    @SerialName("session.heartbeat")
    data class SessionHeartbeat(
        val sessionId: String,
        val clientUptimeMs: Long,
    ) : OutboundPayload()
}
