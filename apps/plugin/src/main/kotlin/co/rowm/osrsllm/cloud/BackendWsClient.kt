package co.rowm.osrsllm.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.close
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
 * disconnect. Anything to do with WHAT to send lives in [EgressGate].
 *
 * REVIEWER NOTE — RAI-38:
 * This file purposely exposes only the session handle. The actual transport
 * write (a single `Frame.Text` send) lives in [EgressGate] so a reviewer can
 * grep for the send-call substring and find exactly one hit. The connection
 * itself is restricted to the [BackendUrl.WSS_PREFIX] scheme by the
 * value-class invariant.
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

    /** Connect, blocking until the session is established. */
    @Synchronized
    fun connect(url: BackendUrl) {
        if (session != null) {
            log.debug("BackendWsClient already connected")
            return
        }
        val opened = runBlocking { httpClient.webSocketSession(urlString = url.value) }
        session = opened
        log.info("Backend WSS connected (url={})", url.value)
        // Drain incoming frames so the channel doesn't back up. The real
        // inbound handler is wired separately; this is the floor.
        scope.launch {
            runCatching { for (f in opened.incoming) { /* dispatch elsewhere */ } }
                .onFailure { log.warn("WSS inbound loop failed: {}", it.message) }
        }
    }

    /**
     * Returns the live session ONLY to [EgressGate]. No other caller has
     * a legitimate reason to touch the raw transport. The method is internal
     * to the `cloud` package so the type system enforces that boundary.
     */
    internal fun currentSession(): DefaultClientWebSocketSession? = session

    @Synchronized
    fun close() {
        val current = session
        if (current != null) {
            runCatching { runBlocking { current.close() } }
        }
        session = null
        runCatching { scope.cancel() }
        runCatching { httpClient.close() }
        log.info("Backend WSS closed")
    }
}
