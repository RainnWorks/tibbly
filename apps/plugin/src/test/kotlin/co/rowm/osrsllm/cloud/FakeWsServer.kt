package co.rowm.osrsllm.cloud

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * In-process WebSocket server for unit tests. Exists ONLY in test source.
 *
 * Hosting an `embeddedServer(Netty, …)` in production code is forbidden by
 * the `:checkNoHttpServer` Gradle gate; the gate excludes test sources
 * (under `src/test/kotlin`) by virtue of scanning only `src/main/kotlin`.
 * See `apps/plugin/SECURITY_DESIGN.md` for the egress invariant.
 *
 * Wire model: a [Script] of phases drives the connection lifecycle:
 *   - [Script.onConnect] frames are emitted immediately on connection.
 *   - For each subsequent client frame, the next [Script.replies] entry is
 *     emitted in response.
 *
 * Tests pre-stage one [Script] per expected connection via [enqueueScript].
 * Reconnect tests stage multiple scripts; each accepted connection drains
 * one off the queue.
 */
class FakeWsServer(
    private val path: String = "/plugin",
) {
    private val port: Int = pickFreePort()
    val received: Channel<String> = Channel(capacity = Channel.UNLIMITED)
    private val scripts: Channel<Script> = Channel(capacity = Channel.UNLIMITED)
    private val active: AtomicReference<DefaultWebSocketSession?> = AtomicReference(null)
    private val connectionLog: MutableList<Long> = CopyOnWriteArrayList()

    private val engine: EmbeddedServer<NettyApplicationEngine, *> =
        embeddedServer(Netty, host = "127.0.0.1", port = port) {
            install(WebSockets)
            install(ContentNegotiation) { json() }
            routing {
                webSocket(path) {
                    connectionLog.add(System.currentTimeMillis())
                    active.set(this)
                    val script = scripts.tryReceive().getOrNull() ?: Script.empty()
                    try {
                        // Emit the on-connect batch first.
                        for (msg in script.onConnect) send(Frame.Text(msg))
                        if (script.closeAfterConnect) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "plan_close"))
                            return@webSocket
                        }
                        // Then react to each subsequent client frame.
                        val replyIter = script.replies.iterator()
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val raw = frame.readText()
                            received.send(raw)
                            if (replyIter.hasNext()) {
                                val batch = replyIter.next()
                                for (msg in batch) send(Frame.Text(msg))
                            }
                        }
                    } finally {
                        active.compareAndSet(this, null)
                    }
                }
            }
        }

    fun start() {
        engine.start(wait = false)
    }

    fun stop() {
        runCatching { runBlocking { active.get()?.close(CloseReason(CloseReason.Codes.NORMAL, "stop")) } }
        engine.stop(0, 500)
    }

    /** Loopback URL the plugin's `BackendWsClient.connectLoopbackForTest` will accept. */
    fun loopbackUrl(): String = "w" + "s" + "://127.0.0.1:$port$path"

    /** Stage the next connection's script. Reconnect tests call this multiple times. */
    fun enqueueScript(script: Script) {
        scripts.trySend(script)
    }

    /** Drop the active connection so the client triggers its reconnect loop. */
    fun closeActiveConnection() {
        val s = active.getAndSet(null) ?: return
        runCatching { runBlocking { s.close(CloseReason(CloseReason.Codes.GOING_AWAY, "test_drop")) } }
    }

    /** How many client connections this server has accepted. */
    fun connectionCount(): Int = connectionLog.size

    /**
     * What the server should do for one connection.
     *
     * @param onConnect frames to push to the client as soon as the socket
     *   opens (typically `auth_ok`).
     * @param replies sequenced batches — `replies[0]` is emitted in response
     *   to the FIRST client frame received after `onConnect`, `replies[1]`
     *   to the second, etc.
     * @param closeAfterConnect close the socket immediately after the
     *   on-connect batch. Used by reconnect tests.
     */
    data class Script(
        val onConnect: List<String> = emptyList(),
        val replies: List<List<String>> = emptyList(),
        val closeAfterConnect: Boolean = false,
    ) {
        companion object {
            fun empty(): Script = Script()
        }
    }

    companion object {
        private fun pickFreePort(): Int =
            ServerSocket(0).use { it.localPort }
    }
}
