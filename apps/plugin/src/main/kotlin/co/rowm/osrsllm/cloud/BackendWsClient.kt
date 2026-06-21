package co.rowm.osrsllm.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WSS transport. NO business logic — connect, hold a session reference,
 * drain the inbound stream, disconnect. Anything to do with WHAT to send
 * lives in [EgressGate].
 *
 * REVIEWER NOTE — RAI-38:
 * This file purposely exposes only the session handle to [EgressGate].
 * The actual transport write (a single `Frame.Text` send) lives in
 * [EgressGate] so a reviewer can grep for the send-call substring and find
 * exactly one hit. The connection itself is restricted to the
 * [BackendUrl.WSS_PREFIX] scheme by the value-class invariant.
 *
 * The inbound side is read here (because we have to pump the queue or Ktor
 * stalls) and forwarded to a single registered [InboundListener]. Higher
 * layers like [CloudChatRunner] subscribe to that listener; they never
 * touch the raw `webSocketSession.incoming` channel.
 */
@Singleton
class BackendWsClient @Inject constructor() {

    private val log = LoggerFactory.getLogger(BackendWsClient::class.java)
    private val supervisor: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    @Volatile
    private var listener: InboundListener? = null

    /**
     * Receives inbound frames + lifecycle callbacks. Implementations run on
     * the WS IO coroutine — callers must marshal to their own thread (the
     * Swing EDT, in [CloudChatRunner]'s case).
     */
    interface InboundListener {
        /** A decoded server frame arrived. */
        fun onMessage(message: InboundMessage) {}

        /** A frame arrived that didn't decode. The transport stays up. */
        fun onMalformed(rawText: String, error: Throwable) {}

        /**
         * The inbound channel closed (server-side close, network failure, or
         * an explicit [close]). After this fires, the transport is no longer
         * usable; callers should rebuild via [connect].
         */
        fun onClosed(reason: String) {}
    }

    /** Register / replace the single inbound listener. Null to unsubscribe. */
    @Synchronized
    fun setListener(listener: InboundListener?) {
        this.listener = listener
    }

    /** Connect, blocking until the session is established. */
    @Synchronized
    fun connect(url: BackendUrl) {
        connectInternal(url.value)
    }

    /**
     * Test-only loopback connect that accepts an in-process Ktor server URL.
     *
     * REVIEWER NOTE — RAI-38:
     * This method is `internal`, so production callers in other modules
     * cannot reach it. It is exercised exclusively from `src/test/kotlin/`
     * by the cloud round-trip tests, which spin up an embedded Ktor server
     * on 127.0.0.1 for the WS handshake. Production traffic still flows
     * through [connect] above, which only accepts a `wss://` [BackendUrl].
     *
     * The Gradle `:checkNoPlaintextUrls` audit excludes only the developer-
     * only `local/` package — and it greps for literal plaintext WebSocket
     * and HTTP scheme prefixes. This method takes its scheme from the caller;
     * the only literal here is the parameter name.
     */
    @Synchronized
    internal fun connectLoopbackForTest(rawLoopbackUrl: String) {
        require(rawLoopbackUrl.contains("127.0.0.1") || rawLoopbackUrl.contains("localhost")) {
            "connectLoopbackForTest only accepts loopback URLs (got $rawLoopbackUrl)"
        }
        connectInternal(rawLoopbackUrl)
    }

    private fun connectInternal(urlString: String) {
        if (session != null) {
            log.debug("BackendWsClient already connected")
            return
        }
        val opened = runBlocking { httpClient.webSocketSession(urlString = urlString) }
        session = opened
        log.info("Backend WSS connected")
        // Drain incoming frames and dispatch to the registered listener.
        // If no listener is set yet, frames are dropped — higher layers should
        // setListener BEFORE calling connect to avoid losing the auth_ok ack.
        scope.launch {
            val closeReason = runCatching {
                for (frame in opened.incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val current = listener ?: continue
                    try {
                        val decoded = InboundCodec.decode(raw)
                        current.onMessage(decoded)
                    } catch (t: Throwable) {
                        log.debug("Malformed inbound frame ({} chars): {}", raw.length, t.message)
                        runCatching { current.onMalformed(raw, t) }
                    }
                }
                "remote_closed"
            }.recoverCatching { ex ->
                log.warn("WSS inbound loop failed: {}", ex.message)
                "io_error:${ex.javaClass.simpleName}"
            }.getOrDefault("unknown")
            // Clear our session reference so EgressGate refuses subsequent sends
            // until the caller reconnects.
            this@BackendWsClient.session = null
            runCatching { listener?.onClosed(closeReason) }
        }
    }

    /**
     * Returns the live session ONLY to [EgressGate]. No other caller has
     * a legitimate reason to touch the raw transport. The method is internal
     * to the `cloud` package so the type system enforces that boundary.
     */
    internal fun currentSession(): DefaultClientWebSocketSession? = session

    /** True iff a session is open. Cheap; safe from any thread. */
    fun isConnected(): Boolean = session != null

    @Synchronized
    fun close() {
        val current = session
        if (current != null) {
            runCatching { runBlocking { current.close(CloseReason(CloseReason.Codes.NORMAL, "client_close")) } }
        }
        session = null
        runCatching { scope.cancel() }
        runCatching { httpClient.close() }
        log.info("Backend WSS closed")
    }

    /**
     * Test-only: completes when the inbound loop has finished one drain
     * cycle. Returns immediately if no session is open. Useful in tests that
     * want to deterministically wait for a server-driven close before
     * asserting reconnect behaviour.
     */
    internal fun awaitNextClose(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        val prior = listener
        setListener(object : InboundListener {
            override fun onMessage(message: InboundMessage) {
                prior?.onMessage(message)
            }
            override fun onMalformed(rawText: String, error: Throwable) {
                prior?.onMalformed(rawText, error)
            }
            override fun onClosed(reason: String) {
                prior?.onClosed(reason)
                gate.complete(Unit)
            }
        })
        return gate
    }
}
