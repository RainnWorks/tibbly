package co.rowm.osrsllm.cloud

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * RAI-22 — end-to-end WS round-trip for [CloudChatRunner].
 *
 * Spins up an in-process Ktor WebSocket server, points the runner at it via
 * the loopback-test escape hatch, and asserts:
 *
 *   1. Auth handshake: client → `auth`, server → `auth_ok`, callback fired.
 *   2. User turn: client → `user_message`, server streams 3
 *      `assistant_message_delta`s and an `assistant_message_done`. The
 *      runner aggregates them into the final text and surfaces the
 *      post-turn token balance.
 *   3. Tool call: server → `tool_call_request`, runner dispatches to the
 *      configured [CloudChatRunner.ToolDispatcher], client → `tool_call_result`.
 *
 * The test also verifies every outbound frame went through [EgressGate]
 * (the audit log captures one row per send) — the legibility invariant.
 */
class CloudChatRunnerTest {

    private lateinit var server: FakeWsServer
    private lateinit var transport: BackendWsClient
    private lateinit var auditLog: AuditLog
    private lateinit var egressGate: EgressGate

    @Before
    fun setUp() {
        server = FakeWsServer().also { it.start() }
        transport = BackendWsClient()
        auditLog = AuditLog()
        egressGate = EgressGate(transport = transport, auditLog = auditLog)
        ConsentState.reset()
        ConsentState.freeze(accepted = true)
    }

    @After
    fun tearDown() {
        runCatching { transport.close() }
        runCatching { server.stop() }
        ConsentState.reset()
        auditLog.clear()
    }

    @Test
    fun `round trip auth + user message + assistant deltas + done`() = runBlocking {
        // Script: send auth_ok on connect. The auth frame is reply 0 (the
        // runner sends auth as its first egress, so the auth_ok arrives in
        // the on-connect batch). The user_message is reply 1 — when the
        // server sees it, emit the 3 deltas + done.
        server.enqueueScript(
            FakeWsServer.Script(
                onConnect = listOf(
                    """{"type":"auth_ok","userId":"u1","tier":"hobbyist","balanceTokens":12345}""",
                ),
                replies = listOf(
                    // reply to auth frame — nothing (auth_ok already in onConnect)
                    emptyList(),
                    // reply to user_message
                    listOf(
                        """{"type":"assistant_message_delta","chatId":"c1","delta":"Hello "}""",
                        """{"type":"assistant_message_delta","chatId":"c1","delta":"world"}""",
                        """{"type":"assistant_message_delta","chatId":"c1","delta":"!"}""",
                        """{"type":"assistant_message_done","chatId":"c1","promptTokens":11,"completionTokens":22,"costMicroUsd":33,"balanceTokens":12289}""",
                    ),
                ),
            ),
        )

        val authEvents = CopyOnWriteArrayList<InboundMessage.AuthOk>()
        val deltaEvents = CopyOnWriteArrayList<String>()
        val doneEvents = CopyOnWriteArrayList<InboundMessage.AssistantDone>()

        val runner = newRunner(
            onAuth = { authEvents.add(it) },
            onDelta = { _, d -> deltaEvents.add(d) },
            onDone = { _, d -> doneEvents.add(d) },
        )

        runner.start()
        // Server received the auth frame.
        val authFrame = withTimeout(5_000) { server.received.receive() }
        assertTrue("expected auth frame, got: $authFrame", authFrame.contains("\"type\":\"auth\""))
        assertTrue("auth frame must carry deviceKey", authFrame.contains("\"deviceKey\""))

        // Wait for the runner to see auth_ok.
        waitUntil(3_000) { authEvents.isNotEmpty() }
        assertEquals("u1", authEvents.first().userId)
        assertEquals("hobbyist", authEvents.first().tier)

        // Send a user turn — runner registers turnState, then egresses
        // user_message. The server will then emit deltas + done.
        val result = runner.send(
            chatId = "c1",
            userText = "Hello",
            allowedTools = listOf("core"),
            snapshot = JsonPrimitive("{\"hp\":50}"),
        )

        // The runner must have aggregated all three deltas.
        assertTrue("turn ok: ${result.errorMessage}", result.ok)
        assertEquals("Hello world!", result.text)
        assertEquals(12289L, result.balanceTokens)

        // Done callback fired with the same payload.
        assertEquals(1, doneEvents.size)
        assertEquals(11L, doneEvents.first().promptTokens)
        assertEquals(22L, doneEvents.first().completionTokens)
        assertEquals(12289L, doneEvents.first().balanceTokens)

        // The server saw user_message with the expected fields.
        val userFrame = withTimeout(5_000) { server.received.receive() }
        assertTrue("expected user_message, got: $userFrame", userFrame.contains("\"type\":\"user_message\""))
        assertTrue("expected chatId, got: $userFrame", userFrame.contains("\"chatId\":\"c1\""))
        assertTrue("expected content, got: $userFrame", userFrame.contains("\"content\":\"Hello\""))
        assertTrue("expected allowedTools, got: $userFrame", userFrame.contains("\"allowedTools\""))

        // Audit log: one row per outbound send (auth + user_message).
        val rows = auditLog.entries()
        assertTrue("expected ≥ 2 audit rows, got: $rows", rows.size >= 2)
        val kinds = rows.map { it.payloadKind }.toSet()
        assertTrue("audit must record SessionHello: $kinds", "SessionHello" in kinds)
        assertTrue("audit must record ChatUserMessage: $kinds", "ChatUserMessage" in kinds)

        runner.stop()
    }

    @Test
    fun `server initiated tool call round trip`() = runBlocking {
        // Script: auth_ok on connect, then push a tool_call_request after
        // the auth frame arrives so the dispatcher fires while the runner
        // is in the "ready, waiting for next turn" state.
        server.enqueueScript(
            FakeWsServer.Script(
                onConnect = listOf(
                    """{"type":"auth_ok","userId":"u1","tier":"hobbyist","balanceTokens":12345}""",
                ),
                replies = listOf(
                    // reply to auth frame: push the tool_call_request
                    listOf(
                        """{"type":"tool_call_request","toolCallId":"t1","name":"get_inventory","input":{}}""",
                    ),
                ),
            ),
        )

        val dispatchedNames = CopyOnWriteArrayList<String>()
        val dispatcher = object : CloudChatRunner.ToolDispatcher {
            override suspend fun dispatch(toolName: String, input: JsonElement?): JsonElement {
                dispatchedNames.add(toolName)
                return buildJsonObject { put("items", "[]") }
            }
        }

        val runner = newRunner(toolDispatcher = dispatcher)
        runner.start()

        // First frame received by server: the auth frame.
        withTimeout(5_000) { server.received.receive() }

        // Second frame: the tool_call_result the runner emits in response
        // to the server-initiated tool_call_request.
        val toolResultFrame = withTimeout(5_000) { server.received.receive() }
        assertTrue(
            "expected tool_call_result, got: $toolResultFrame",
            toolResultFrame.contains("\"type\":\"tool_call_result\""),
        )
        assertTrue("expected toolCallId t1", toolResultFrame.contains("\"toolCallId\":\"t1\""))
        assertTrue("expected output payload", toolResultFrame.contains("\"output\""))

        assertEquals(listOf("get_inventory"), dispatchedNames)
        val kinds = auditLog.entries().map { it.payloadKind }.toSet()
        assertTrue("audit must record ToolResult: $kinds", "ToolResult" in kinds)

        runner.stop()
    }

    @Test
    fun `runner surfaces error when egress refused mid-turn`() {
        // Stage a normal connect script so auth handshake works.
        server.enqueueScript(
            FakeWsServer.Script(
                onConnect = listOf(
                    """{"type":"auth_ok","userId":"u1","tier":"hobbyist","balanceTokens":1}""",
                ),
            ),
        )

        // Cloud-chat-enabled supplier flips false at send-time so EgressGate
        // refuses the user_message frame. This proves the runner returns a
        // structured failure rather than crashing.
        val cloudEnabled = java.util.concurrent.atomic.AtomicBoolean(true)
        val runner = newRunner(cloudEnabledSupplier = { cloudEnabled.get() })

        runner.start()
        // Wait for auth handshake to complete before we flip the flag —
        // otherwise the initial SessionHello egress itself would throw.
        waitUntil(3_000) { transport.isConnected() }
        cloudEnabled.set(false)

        val result = runner.send(
            chatId = "c1",
            userText = "Hi",
            allowedTools = listOf("core"),
            snapshot = null,
        )
        assertTrue("send must report failure when egress is refused", !result.ok)
        assertNotNull("error message must be present", result.errorMessage)
        assertTrue(
            "error must mention cloudChatEnabled: ${result.errorMessage}",
            result.errorMessage!!.contains("cloudChatEnabled"),
        )
        runner.stop()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun newRunner(
        toolDispatcher: CloudChatRunner.ToolDispatcher = StubToolDispatcher(),
        cloudEnabledSupplier: () -> Boolean = { true },
        onAuth: (InboundMessage.AuthOk) -> Unit = {},
        onDelta: (String, String) -> Unit = { _, _ -> },
        onDone: (String, InboundMessage.AssistantDone) -> Unit = { _, _ -> },
    ): CloudChatRunner =
        CloudChatRunner(
            transport = transport,
            egressGate = egressGate,
            toolDispatcher = toolDispatcher,
            authSupplier = {
                CloudChatRunner.AuthFrame(
                    deviceKey = "test-device-key-1234567890",
                    playerName = "Zezima",
                    pluginVersion = "0.1.0-test",
                )
            },
            // Not consulted in tests (connector closure takes precedence) but BackendUrl
            // requires wss://, so we hand it a real one and rely on the connector.
            backendUrlSupplier = { BackendUrl("wss://unused.example/plugin") },
            consentSupplier = { ConsentState.snapshot()!! },
            cloudChatEnabledSupplier = cloudEnabledSupplier,
            reconnectStrategy = ReconnectStrategy(baseMillis = 50, capMillis = 500, jitterPercent = 0.0),
            authTimeoutMillis = 3_000L,
            turnTimeoutMillis = 5_000L,
            callbacks = object : CloudChatRunner.Callbacks {
                override fun onAuthenticated(authOk: InboundMessage.AuthOk) = onAuth(authOk)
                override fun onDelta(chatId: String, delta: String) = onDelta(chatId, delta)
                override fun onTurnComplete(chatId: String, done: InboundMessage.AssistantDone) =
                    onDone(chatId, done)
            },
            connector = { transport.connectLoopbackForTest(server.loopbackUrl()) },
        )

    private fun waitUntil(maxMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + maxMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Condition not met within ${maxMillis}ms")
    }
}
