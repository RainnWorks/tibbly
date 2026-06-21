package co.rowm.osrsllm.cloud

import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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
open class EgressGate @Inject constructor(
    private val transport: BackendWsClient,
    private val auditLog: AuditLog,
) {

    private val log = LoggerFactory.getLogger(EgressGate::class.java)
    private val json = Json {
        // Strict mode — adding a new field requires a new OutboundPayload subtype.
        ignoreUnknownKeys = false
        encodeDefaults = true
        // Aligned with packages/shared-types/src/protocol.ts which uses `type`
        // as the discriminator in `ClientToServer = z.discriminatedUnion("type", …)`.
        classDiscriminator = "type"
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

    /**
     * RAI-23 — HTTP egress for the device-pairing flow.
     *
     * Pairing happens BEFORE the player has authenticated their websocket
     * session, so it has to go over plain request/response — there is no live
     * WSS session to write to yet. This is the second (and last) place in the
     * plugin that emits player-touching bytes to the backend.
     *
     * Constraints — kept tight so the security story stays the same:
     *  - The transport URL is derived from the configured [BackendUrl] by
     *    swapping the WSS scheme for the secure HTTP scheme. The path is
     *    appended verbatim (callers pass e.g. `/v1/pairing/request`).
     *  - There is no auto-redirect, no follow of plaintext locations, no
     *    cookie store. Just an explicit POST/GET with a JSON body.
     *  - On success an audit entry tagged `Http:METHOD path` is written,
     *    mirroring what [egress] does for sealed payloads.
     *
     * @param method HTTP method — `POST` or `GET`.
     * @param backendUrl the configured backend URL (must be `wss://` —
     *   enforced by [BackendUrl]'s init block).
     * @param path the request path, starting with `/`.
     * @param bodyJson optional JSON body. Sent with `Content-Type: application/json`.
     */
    open fun egressHttp(
        method: String,
        backendUrl: BackendUrl,
        path: String,
        bodyJson: String? = null,
        headers: List<Pair<String, String>> = emptyList(),
    ): HttpEgressResponse {
        require(method == "GET" || method == "POST" || method == "DELETE") {
            "EgressGate.egressHttp: unsupported method=$method (only GET/POST/DELETE allowed)"
        }
        require(path.startsWith("/")) {
            "EgressGate.egressHttp: path must start with '/' (got '$path')"
        }
        // Defensive: reject CR/LF in extra headers so a future caller can't
        // smuggle in a CRLF-injection that splits the request. Names also
        // forbid space and colon; values allow space (Bearer tokens).
        for ((name, value) in headers) {
            require(name.isNotBlank() && name.all { it.code in 33..126 && it != ':' }) {
                "EgressGate.egressHttp: header name contains illegal characters"
            }
            require(value.none { it == '\r' || it == '\n' }) {
                "EgressGate.egressHttp: header value contains illegal characters"
            }
        }
        val httpsUrl = toHttpsUrl(backendUrl) + path
        val url = URL(httpsUrl)
        require(url.protocol == "https") {
            "EgressGate.egressHttp: derived URL is not https (got protocol=${url.protocol})"
        }
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            for ((name, value) in headers) {
                conn.setRequestProperty(name, value)
            }
            if (bodyJson != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val sizeOut = bodyJson?.length ?: 0
            auditLog.record(payloadKind = "Http:$method $path", sizeBytes = sizeOut)
            log.debug("HTTP egress {} {} -> {} ({} bytes out, {} bytes in)",
                method, path, status, sizeOut, body.length)
            return HttpEgressResponse(status = status, body = body)
        } catch (e: IOException) {
            log.warn("HTTP egress {} {} failed: {}", method, path, e.message)
            throw e
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * Tier-2 BYO HTTP egress — talks DIRECTLY to a third-party LLM provider
     * (Anthropic, OpenAI, OpenRouter) using the player's own API key. No
     * Tibbly backend involved.
     *
     * Posture (mirrors the WSS [egress] guarantees):
     *
     *  - `host` is checked against [BYO_ALLOWED_HOSTS] EXACTLY. A typo or
     *    a future "add provider X" refactor that forgets to widen the
     *    allow-list throws [EgressBlockedException] before any socket
     *    opens. Any non-https URL is impossible because we construct
     *    `https://<host><path>` literally — see the build.gradle
     *    `:checkNoPlaintextUrls` gate that keeps the prefix tied to that
     *    constant.
     *  - `path` must start with `/`. We never let the caller supply a full
     *    URL because that would defeat the host check.
     *  - Headers are sanitized exactly like the pairing path: names are
     *    ASCII printable + `:`-free; values forbid CR/LF. This blocks
     *    response-splitting / header-injection if a future caller
     *    accidentally interpolates user input.
     *  - The audit row records ONLY the method, host, path, and body size
     *    in bytes. We never log header values (which would include the
     *    API key) and never log the body contents.
     *  - Consent / cloudChatEnabled gates DO NOT apply here. Cloud-chat
     *    is by definition "do not connect to Tibbly", and the BYO path
     *    has its own consent flow (the player explicitly picked a BYO
     *    mode AND pasted a key). The plugin still gates the call site
     *    on `consentAccepted()` before reaching this method — see
     *    `OsrsLlmHelperPlugin.startUp()`.
     *
     * @param host exact-match host (one of [BYO_ALLOWED_HOSTS]).
     * @param path the request path, starting with `/`.
     * @param method one of `GET` or `POST`.
     * @param headers additional headers (Auth + provider-specific).
     * @param body the JSON request body (sent with Content-Type set to JSON).
     */
    public open fun egressHttp(
        host: String,
        path: String,
        method: String = "POST",
        headers: List<Pair<String, String>> = emptyList(),
        body: String? = null,
    ): HttpEgressResponse {
        if (host !in BYO_ALLOWED_HOSTS) {
            throw EgressBlockedException(
                "EgressGate.egressHttp refused host='$host' — not in BYO_ALLOWED_HOSTS.",
            )
        }
        require(method == "GET" || method == "POST") {
            "EgressGate.egressHttp: unsupported method=$method (only GET/POST allowed)"
        }
        require(path.startsWith("/")) {
            "EgressGate.egressHttp: path must start with '/' (got '$path')"
        }
        // Header sanitization — matches the pairing-flow overload's contract so
        // a refactor that consolidates the two can keep the same posture. We do
        // NOT inspect header VALUES other than for CR/LF: an API key is a value,
        // and we never want to look at it.
        for ((name, value) in headers) {
            require(name.isNotBlank() && name.all { it.code in 33..126 && it != ':' }) {
                "EgressGate.egressHttp: header name contains illegal characters"
            }
            require(value.none { it == '\r' || it == '\n' }) {
                "EgressGate.egressHttp: header value contains illegal characters"
            }
        }
        val httpsUrl = "https://" + host + path
        val url = URL(httpsUrl)
        check(url.protocol == "https") {
            // Unreachable by construction (literal prefix above) but kept as a
            // belt-and-braces sentinel for future refactors.
            "EgressGate.egressHttp: derived URL is not https (got protocol=${url.protocol})"
        }
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            for ((name, value) in headers) {
                conn.setRequestProperty(name, value)
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val sizeOut = body?.length ?: 0
            auditLog.record(payloadKind = "Http:$method $host$path", sizeBytes = sizeOut)
            log.debug(
                "HTTP egress {} {}{} -> {} ({} bytes out, {} bytes in)",
                method, host, path, status, sizeOut, responseBody.length,
            )
            return HttpEgressResponse(status = status, body = responseBody)
        } catch (e: IOException) {
            // We never include header values in the log line — the key lives in
            // headers, and exception messages from HttpURLConnection are limited
            // to the URL + cause, never the headers we set.
            log.warn("HTTP egress {} {}{} failed: {}", method, host, path, e.message)
            throw e
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** Result of an [egressHttp] call. */
    data class HttpEgressResponse(val status: Int, val body: String)

    /**
     * Thrown by [egressHttp] when the requested host is not in the
     * [BYO_ALLOWED_HOSTS] allow-list. Distinct from [IllegalArgumentException]
     * so the runner can render a specific "we don't talk to that provider"
     * message rather than the generic "validation failed" path.
     */
    public class EgressBlockedException internal constructor(message: String) : SecurityException(message)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        /**
         * Exact-match host allow-list for Tier-2 BYO direct egress. Adding a
         * provider means adding its host here AND a row in
         * `DATA_DISCLOSURE.md` §D-quater AND a sealed subtype in
         * `cloud/byo/ByoProvider.kt`. Mirroring the four-step contract
         * keeps the security story auditable.
         */
        public val BYO_ALLOWED_HOSTS: Set<String> = setOf(
            "api.anthropic.com",
            "api.openai.com",
            "openrouter.ai",
        )

        /**
         * Derive the `https://host[:port]` origin from a [BackendUrl] (which is
         * always `wss://...` by value-class invariant). The path/query of the
         * original WSS URL is discarded — callers supply their own `path`.
         */
        internal fun toHttpsUrl(backendUrl: BackendUrl): String {
            val raw = backendUrl.value
            require(raw.startsWith(BackendUrl.WSS_PREFIX)) {
                "BackendUrl is not WSS — refusing to derive HTTPS origin from '$raw'"
            }
            // Re-use the "s" from "wss" so we never emit a plaintext literal here.
            val httpsPrefix = "http" + raw.substring(2, BackendUrl.WSS_PREFIX.length)
            val rest = raw.substring(BackendUrl.WSS_PREFIX.length)
            val pathStart = rest.indexOfAny(charArrayOf('/', '?', '#'))
            val hostPort = if (pathStart < 0) rest else rest.substring(0, pathStart)
            return httpsPrefix + hostPort
        }
    }
}
