package co.rowm.osrsllm.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives a chat session against the Rowm cloud backend (RAI-22).
 *
 * Responsibilities:
 *   1. Open the WSS link via [BackendWsClient] on [start], send the [auth]
 *      handshake through [EgressGate], wait for `auth_ok`.
 *   2. On each user turn ([send]), egress a `user_message` with the player's
 *      input, the [ContextRouter]'s `allowedTools[]`, and a compact game
 *      snapshot, then aggregate `assistant_message_delta` frames into a
 *      single completed reply.
 *   3. Bridge backend-initiated `tool_call_request` frames to the local
 *      [ToolDispatcher] and ship the answer back as `tool_call_result`.
 *   4. On a server-driven close, reconnect with exponential backoff
 *      ([ReconnectStrategy]) for as long as the runner is started.
 *
 * Threading: everything happens inside the runner's own [scope]. The chat
 * panel calls [send] from the EDT — we marshal back to the EDT via the
 * supplied [callbacks].
 *
 * RAI-22 NOTE: All outbound writes go through [EgressGate.egress]. There is
 * no `webSocket.send` call here — the [SECURITY_DESIGN.md] grep audit must
 * stay at exactly one hit.
 */
class CloudChatRunner(
    private val transport: BackendWsClient,
    private val egressGate: EgressGate,
    private val toolDispatcher: ToolDispatcher,
    private val authSupplier: () -> AuthFrame,
    private val backendUrlSupplier: () -> BackendUrl,
    private val consentSupplier: () -> ConsentState,
    private val cloudChatEnabledSupplier: () -> Boolean,
    private val callbacks: Callbacks,
    private val reconnectStrategy: ReconnectStrategy = ReconnectStrategy(),
    private val authTimeoutMillis: Long = 5_000L,
    private val turnTimeoutMillis: Long = 120_000L,
    /**
     * Test seam: how the runner asks [transport] to connect. Defaults to
     * `transport.connect(backendUrlSupplier())`. Tests inject a closure that
     * calls [BackendWsClient.connectLoopbackForTest] instead so they can use
     * an in-process Ktor server without standing up TLS.
     */
    private val connector: () -> Unit = { transport.connect(backendUrlSupplier()) },
) {

    private val log = LoggerFactory.getLogger(CloudChatRunner::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private val running = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    @Volatile
    private var authReady: CompletableDeferred<InboundMessage.AuthOk>? = null

    @Volatile
    private var currentTurn: TurnState? = null

    /**
     * The auth handshake. Supplied by the plugin layer (device key from
     * RuneLite config + live player name from `Client.localPlayer`).
     */
    data class AuthFrame(
        val deviceKey: String,
        val playerName: String?,
        val pluginVersion: String,
    )

    /** Hooks fired from the runner's coroutine — implementers must marshal to the EDT. */
    interface Callbacks {
        /** Streamed text delta for the current turn. */
        fun onDelta(chatId: String, delta: String) {}

        /** Turn finished cleanly. `balanceTokens` updates the token HUD (RAI-24). */
        fun onTurnComplete(chatId: String, done: InboundMessage.AssistantDone) {}

        /** Server-side error (validation, rate limit, model failure). */
        fun onError(code: String, message: String) {}

        /** Auth handshake succeeded — useful for surfacing tier + balance on the panel. */
        fun onAuthenticated(authOk: InboundMessage.AuthOk) {}

        /** Connection lifecycle (panel may show a small indicator). */
        fun onConnectionStateChanged(state: ConnectionState) {}
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, AUTHENTICATING, READY, RECONNECTING }

    /** Per-turn bookkeeping: collects deltas and signals completion. */
    private data class TurnState(
        val chatId: String,
        val deltas: StringBuilder = StringBuilder(),
        val done: CompletableDeferred<InboundMessage.AssistantDone> = CompletableDeferred(),
    )

    /**
     * Result of a [send] call. `text` is the full aggregated assistant reply,
     * `balanceTokens` is the post-turn token balance (or null if the server
     * didn't supply one, e.g. on error or cancellation).
     */
    data class SendResult(
        val ok: Boolean,
        val text: String,
        val balanceTokens: Long? = null,
        val errorMessage: String? = null,
    )

    private val inboundListener = object : BackendWsClient.InboundListener {
        override fun onMessage(message: InboundMessage) {
            when (message) {
                is InboundMessage.AuthOk -> {
                    log.info("Auth OK: userId={} tier={} balance={}",
                        message.userId, message.tier, message.balanceTokens)
                    authReady?.complete(message)
                    runCatching { callbacks.onAuthenticated(message) }
                    runCatching { callbacks.onConnectionStateChanged(ConnectionState.READY) }
                }
                is InboundMessage.AuthError -> {
                    log.warn("Auth error: reason={}", message.reason)
                    authReady?.completeExceptionally(
                        IllegalStateException("auth_error:${message.reason}"),
                    )
                    runCatching { callbacks.onError("auth_error", message.reason) }
                }
                is InboundMessage.AssistantDelta -> {
                    val turn = currentTurn
                    if (turn != null && turn.chatId == message.chatId) {
                        turn.deltas.append(message.delta)
                    }
                    runCatching { callbacks.onDelta(message.chatId, message.delta) }
                }
                is InboundMessage.AssistantDone -> {
                    val turn = currentTurn
                    if (turn != null && turn.chatId == message.chatId) {
                        turn.done.complete(message)
                    }
                    runCatching { callbacks.onTurnComplete(message.chatId, message) }
                }
                is InboundMessage.ToolCallRequest -> {
                    scope.launch { handleToolCall(message) }
                }
                is InboundMessage.ServerError -> {
                    log.warn("Server error: code={} msg={}", message.code, message.message)
                    currentTurn?.done?.completeExceptionally(
                        IllegalStateException("server_error:${message.code}:${message.message}"),
                    )
                    runCatching { callbacks.onError(message.code, message.message) }
                }
                is InboundMessage.Pong -> { /* heartbeat ack — no-op */ }
            }
        }

        override fun onMalformed(rawText: String, error: Throwable) {
            log.debug("Dropping malformed frame ({} chars): {}", rawText.length, error.message)
        }

        override fun onClosed(reason: String) {
            log.info("WSS closed: reason={}", reason)
            currentTurn?.done?.completeExceptionally(
                IllegalStateException("connection_closed:$reason"),
            )
            runCatching { callbacks.onConnectionStateChanged(ConnectionState.DISCONNECTED) }
            if (running.get()) {
                runCatching { callbacks.onConnectionStateChanged(ConnectionState.RECONNECTING) }
                reconnectJob = scope.launch { reconnectLoop() }
            }
        }
    }

    /**
     * Start the runner: open the socket, send auth, wait for `auth_ok`.
     * Idempotent — calling [start] while running is a no-op.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            log.debug("CloudChatRunner already running")
            return
        }
        transport.setListener(inboundListener)
        runCatching { callbacks.onConnectionStateChanged(ConnectionState.CONNECTING) }
        runBlocking { connectAndAuth() }
    }

    /** Stop and tear down. After [stop], call [start] to restart. */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        reconnectJob?.cancel()
        currentTurn?.done?.cancel()
        currentTurn = null
        runCatching { transport.setListener(null) }
        runCatching { transport.close() }
        runCatching { scope.cancel() }
        runCatching { callbacks.onConnectionStateChanged(ConnectionState.DISCONNECTED) }
    }

    /**
     * Send a user turn. Blocks until [InboundMessage.AssistantDone] is
     * received, [turnTimeoutMillis] elapses, or the socket dies. Safe to
     * call from any thread.
     */
    fun send(
        chatId: String,
        userText: String,
        allowedTools: List<String>?,
        snapshot: JsonElement?,
    ): SendResult {
        if (!running.get()) return SendResult(ok = false, text = "", errorMessage = "runner_not_started")
        if (!transport.isConnected()) return SendResult(ok = false, text = "", errorMessage = "disconnected")

        val turn = TurnState(chatId = chatId)
        currentTurn = turn

        return try {
            val payload = OutboundPayload.ChatUserMessage(
                chatId = chatId,
                content = userText,
                allowedTools = allowedTools,
                context = snapshot?.let { OutboundPayload.MessageContext(snapshot = it) },
            )
            egress(payload)

            val done = runBlocking {
                withTimeoutOrNull(turnTimeoutMillis) { turn.done.await() }
            }
            if (done == null) {
                SendResult(
                    ok = false,
                    text = turn.deltas.toString(),
                    errorMessage = "turn_timeout_${turnTimeoutMillis}ms",
                )
            } else {
                SendResult(
                    ok = true,
                    text = turn.deltas.toString(),
                    balanceTokens = done.balanceTokens,
                )
            }
        } catch (t: Throwable) {
            log.warn("send failed: {}", t.message)
            SendResult(ok = false, text = turn.deltas.toString(), errorMessage = t.message)
        } finally {
            currentTurn = null
        }
    }

    /** Cancel an in-flight turn on the backend side. */
    fun cancel(chatId: String) {
        runCatching { egress(OutboundPayload.ChatCancel(chatId = chatId)) }
            .onFailure { log.warn("cancel egress failed: {}", it.message) }
        currentTurn?.done?.completeExceptionally(IllegalStateException("user_cancel"))
        currentTurn = null
    }

    /** Heartbeat / keepalive — used by callers that want to keep the socket warm. */
    fun ping(uptimeMs: Long) {
        runCatching { egress(OutboundPayload.SessionHeartbeat(clientUptimeMs = uptimeMs)) }
            .onFailure { log.debug("ping egress failed: {}", it.message) }
    }

    /** Dispatch a server-initiated tool call to the local registry, ship the result. */
    private suspend fun handleToolCall(req: InboundMessage.ToolCallRequest) {
        log.info("tool_call_request id={} name={}", req.toolCallId, req.name)
        val result = runCatching { toolDispatcher.dispatch(req.name, req.input) }
        val payload = if (result.isSuccess) {
            val out = result.getOrNull()
            OutboundPayload.ToolResult(
                toolCallId = req.toolCallId,
                output = out,
                errorString = null,
            )
        } else {
            val err = result.exceptionOrNull()?.message ?: "unknown_error"
            OutboundPayload.ToolResult(
                toolCallId = req.toolCallId,
                output = null,
                errorString = err,
            )
        }
        runCatching { egress(payload) }
            .onFailure { log.warn("tool_call_result egress failed: {}", it.message) }
    }

    /** Funnel through [EgressGate] using the current consent + config snapshots. */
    private fun egress(payload: OutboundPayload) {
        egressGate.egress(
            payload = payload,
            consent = consentSupplier(),
            cloudChatEnabled = cloudChatEnabledSupplier(),
        )
    }

    private suspend fun connectAndAuth() {
        runCatching { callbacks.onConnectionStateChanged(ConnectionState.CONNECTING) }
        connector()
        runCatching { callbacks.onConnectionStateChanged(ConnectionState.AUTHENTICATING) }

        val auth = authSupplier()
        val ready = CompletableDeferred<InboundMessage.AuthOk>()
        authReady = ready
        egress(
            OutboundPayload.SessionHello(
                deviceKey = auth.deviceKey,
                playerName = auth.playerName,
                pluginVersion = auth.pluginVersion,
            ),
        )
        withTimeoutOrNull(authTimeoutMillis) { ready.await() }
            ?: run {
                log.warn("auth_ok did not arrive within {}ms", authTimeoutMillis)
                runCatching { callbacks.onError("auth_timeout", "no auth_ok within ${authTimeoutMillis}ms") }
            }
        reconnectStrategy.reset()
    }

    private suspend fun reconnectLoop() {
        while (running.get()) {
            val delayMs = reconnectStrategy.nextDelayMillis()
            log.info("Reconnect attempt — waiting {}ms", delayMs)
            delay(delayMs)
            if (!running.get()) return
            val ok = runCatching { connectAndAuth() }.isSuccess
            if (ok && transport.isConnected()) {
                log.info("Reconnected")
                return
            }
        }
    }

    /**
     * The hook the runner calls to execute a tool the backend asked for. The
     * plugin layer wires this to the existing MCP registry so the test does
     * not need a real game client.
     */
    interface ToolDispatcher {
        /**
         * Execute [toolName] with [input] (raw JSON the backend sent) and
         * return the JSON-serializable output. Throw to signal a tool error.
         */
        suspend fun dispatch(toolName: String, input: JsonElement?): JsonElement
    }

    companion object {
        /** Helper for callers that want a quick turn-id when they don't track turns elsewhere. */
        fun newTurnId(): String = UUID.randomUUID().toString().take(12)

        /** Helper for callers that want to pack a snapshot string as a JsonElement. */
        fun snapshotOf(jsonString: String?): JsonElement? =
            jsonString?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) }
    }
}
