package co.rowm.osrsllm.cloud

import kotlinx.serialization.json.Json

/**
 * Shared lenient decoder for inbound frames from the backend.
 *
 * The encoder is intentionally not exposed — plugins never *send* an
 * [InboundMessage] shape; they only parse it. Outbound shapes live in
 * [OutboundPayload] and go through [EgressGate].
 *
 * `ignoreUnknownKeys = true` so the backend can roll out new optional fields
 * without breaking older plugins. `classDiscriminator = "type"` matches the
 * protocol.ts `ServerToClient = z.discriminatedUnion("type", …)`.
 */
internal object InboundCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        isLenient = true
    }

    fun decode(frameText: String): InboundMessage =
        json.decodeFromString(InboundMessage.serializer(), frameText)
}
