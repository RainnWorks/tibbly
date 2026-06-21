package co.rowm.osrsllm.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
 *
 * # Wire alignment with `packages/shared-types/src/protocol.ts`
 *
 * The `type` discriminator and field names mirror the Zod schemas in
 * `packages/shared-types/src/protocol.ts` so the backend's
 * `parseClientMessage(raw)` accepts our payloads without translation.
 * Renaming a field or `@SerialName` here is a breaking wire change — update
 * the protocol.ts Zod schema in the same commit.
 *
 * | Kotlin subtype            | TS schema (protocol.ts)        | DISCLOSURE §  |
 * |---------------------------|--------------------------------|---------------|
 * | `SessionHello`            | `ClientAuthMsg`                | §1            |
 * | `ChatUserMessage`         | `ClientUserMessageMsg`         | §2 + §3       |
 * | `ToolResult`              | `ClientToolCallResultMsg`      | §4            |
 * | `ChatCancel`              | `ClientCancelMsg`              | §5            |
 * | `SessionHeartbeat`        | `ClientPingMsg`                | §6            |
 */
@Serializable
sealed class OutboundPayload {

    /** DATA_DISCLOSURE.md §1 — Session handshake / authentication. */
    @Serializable
    @SerialName("auth")
    data class SessionHello(
        val deviceKey: String,
        val playerName: String? = null,
        val pluginVersion: String,
    ) : OutboundPayload()

    /**
     * DATA_DISCLOSURE.md §2 + §3 — User chat input plus optional state preamble.
     *
     * The preamble (§3) is carried inside `context.snapshot` to keep the wire
     * shape aligned with `ClientUserMessageMsg` in protocol.ts. `allowedTools`
     * carries the families the [ContextRouter] picked for this turn so the
     * backend can narrow the tool surface (RAI-25 token gating).
     */
    @Serializable
    @SerialName("user_message")
    data class ChatUserMessage(
        val chatId: String,
        val content: String,
        val allowedTools: List<String>? = null,
        val context: MessageContext? = null,
    ) : OutboundPayload()

    /**
     * The §3 preamble, carried opaquely inside [ChatUserMessage.context]. The
     * backend treats `snapshot` as a free-form record (matches
     * `ChatSnapshot = z.record(z.string(), z.unknown())` in protocol.ts).
     */
    @Serializable
    data class MessageContext(
        val snapshot: JsonElement? = null,
    )

    /** DATA_DISCLOSURE.md §4 — Tool call results returned to the backend's agent. */
    @Serializable
    @SerialName("tool_call_result")
    data class ToolResult(
        val toolCallId: String,
        val output: JsonElement? = null,
        val errorString: String? = null,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §5 — User explicitly cancelling an in-flight turn. */
    @Serializable
    @SerialName("cancel")
    data class ChatCancel(
        val chatId: String,
    ) : OutboundPayload()

    /** DATA_DISCLOSURE.md §6 — Heartbeat / liveness ping for the persistent socket. */
    @Serializable
    @SerialName("ping")
    data class SessionHeartbeat(
        val clientUptimeMs: Long? = null,
    ) : OutboundPayload()
}
