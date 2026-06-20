package co.rowm.osrsllm.cloud

import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THE single egress door for player data leaving this plugin.
 *
 * REVIEWER NOTE — RAI-38:
 * If you are auditing the plugin for what player data can leave the device,
 * THIS is the function to read: [egress]. It is the ONLY production code
 * path that performs a transport-layer send. Grep for the send-call substring
 * (variable name + dot + send open-paren) and you will find exactly one hit,
 * a few lines below.
 *
 * Preconditions checked on every call:
 *   1. The player has accepted the consent dialog ([ConsentState.accepted]).
 *   2. Cloud chat is enabled in plugin config ([cloudChatEnabled]).
 *   3. The transport URL is `wss://` (enforced by [BackendUrl]'s init block).
 *   4. The payload is a member of the [OutboundPayload] sealed family.
 *
 * Any violation throws — the egress simply does not happen.
 */
@Singleton
class EgressGate @Inject constructor(
    private val transport: BackendWsClient,
    private val auditLog: AuditLog,
) {

    private val log = LoggerFactory.getLogger(EgressGate::class.java)
    private val json = Json {
        // Strict mode — adding a new field requires a new OutboundPayload subtype.
        ignoreUnknownKeys = false
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    /**
     * Send a typed payload to the backend. Throws on any precondition violation.
     *
     * @param payload one of the [OutboundPayload] subtypes — the sealed hierarchy
     *   names every wire shape we transmit and is mirrored 1:1 in DATA_DISCLOSURE.md.
     * @param consent the consent snapshot captured at plugin startup.
     * @param cloudChatEnabled the current value of the `cloudChatEnabled` config flag.
     */
    fun egress(
        payload: OutboundPayload,
        consent: ConsentState,
        cloudChatEnabled: Boolean,
    ) {
        require(consent.accepted && cloudChatEnabled) {
            "EgressGate refused: consent.accepted=${consent.accepted}, cloudChatEnabled=$cloudChatEnabled"
        }
        val webSocket = transport.currentSession()
            ?: error("EgressGate refused: backend WSS not connected")

        val text = json.encodeToString(OutboundPayload.serializer(), payload)
        runBlocking {
            // === THE ONE AND ONLY egress write in the entire plugin ===
            webSocket.send(Frame.Text(text))
            // =========================================================
        }
        val kind = payload::class.simpleName ?: "Unknown"
        auditLog.record(payloadKind = kind, sizeBytes = text.length)
        log.debug("Egress kind={} bytes={}", kind, text.length)
    }
}
