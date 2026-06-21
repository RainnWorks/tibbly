package co.rowm.osrsllm.cloud

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * RAI-22 — verifies that [CloudChatRunner] reconnects (with exponential
 * backoff) when the server drops the link, and that the reconnect strategy
 * itself is well-behaved.
 *
 * The runner is configured with a tight backoff (50 ms base, 500 ms cap,
 * no jitter) so the test completes in well under a second.
 */
class ReconnectTest {

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
    fun `client reconnects after server drops connection`() = runBlocking {
        // First connection: send auth_ok then stay quiet (we close from the test side).
        server.enqueueScript(
            FakeWsServer.Script(
                onConnect = listOf(
                    """{"type":"auth_ok","userId":"u1","tier":"hobbyist","balanceTokens":100}""",
                ),
            ),
        )
        // Second connection (after server-driven drop): another auth_ok.
        server.enqueueScript(
            FakeWsServer.Script(
                onConnect = listOf(
                    """{"type":"auth_ok","userId":"u1","tier":"hobbyist","balanceTokens":99}""",
                ),
            ),
        )

        val authBalances = CopyOnWriteArrayList<Long>()
        val states = CopyOnWriteArrayList<CloudChatRunner.ConnectionState>()
        val runner = CloudChatRunner(
            transport = transport,
            egressGate = egressGate,
            toolDispatcher = StubToolDispatcher(),
            authSupplier = {
                CloudChatRunner.AuthFrame(
                    deviceKey = "test-device-key-1234567890",
                    playerName = "Zezima",
                    pluginVersion = "0.1.0-test",
                )
            },
            backendUrlSupplier = { BackendUrl("wss://unused.example/plugin") },
            consentSupplier = { ConsentState.snapshot()!! },
            cloudChatEnabledSupplier = { true },
            reconnectStrategy = ReconnectStrategy(baseMillis = 50, capMillis = 500, jitterPercent = 0.0),
            authTimeoutMillis = 3_000L,
            turnTimeoutMillis = 3_000L,
            callbacks = object : CloudChatRunner.Callbacks {
                override fun onAuthenticated(authOk: InboundMessage.AuthOk) {
                    authBalances.add(authOk.balanceTokens)
                }
                override fun onConnectionStateChanged(state: CloudChatRunner.ConnectionState) {
                    states.add(state)
                }
            },
            connector = { transport.connectLoopbackForTest(server.loopbackUrl()) },
        )

        runner.start()
        // Drain the auth frame the server received on the first connection.
        withTimeout(5_000) { server.received.receive() }
        waitUntil(2_000) { authBalances.size >= 1 }
        assertEquals(100L, authBalances.first())
        assertEquals(1, server.connectionCount())

        // Drop the active connection — the runner should reconnect within ~50ms.
        server.closeActiveConnection()

        // The server should accept a second connection.
        waitUntil(3_000) { server.connectionCount() >= 2 }
        // And the runner should re-auth.
        withTimeout(5_000) { server.received.receive() } // second auth frame
        waitUntil(2_000) { authBalances.size >= 2 }
        assertEquals("second auth should report fresh balance", 99L, authBalances[1])

        // State transitions include a RECONNECTING entry after the drop.
        assertTrue(
            "expected RECONNECTING state, got: $states",
            states.contains(CloudChatRunner.ConnectionState.RECONNECTING),
        )

        runner.stop()
    }

    @Test
    fun `reconnect strategy doubles delays up to the cap`() {
        val rng = Random(seed = 42)
        val strategy = ReconnectStrategy(baseMillis = 100, capMillis = 1_600, jitterPercent = 0.0, random = rng)
        val delays = (0..5).map { strategy.nextDelayMillis() }
        // 100, 200, 400, 800, 1600, 1600 (capped)
        assertEquals(listOf(100L, 200L, 400L, 800L, 1600L, 1600L), delays)
    }

    @Test
    fun `reconnect strategy reset returns to base`() {
        val strategy = ReconnectStrategy(baseMillis = 100, capMillis = 10_000, jitterPercent = 0.0)
        strategy.nextDelayMillis(); strategy.nextDelayMillis(); strategy.nextDelayMillis()
        strategy.reset()
        assertEquals(100L, strategy.nextDelayMillis())
    }

    @Test
    fun `reconnect jitter stays within plus or minus the configured percent`() {
        // Pin RNG seed so the test is deterministic.
        val rng = Random(seed = 12345)
        val strategy = ReconnectStrategy(baseMillis = 1_000, capMillis = 1_000, jitterPercent = 0.10, random = rng)
        // 1000 base, capped at 1000 → every delay should be in [900, 1100]
        repeat(20) {
            val d = strategy.nextDelayMillis()
            assertTrue("delay $d outside [900, 1100]", d in 900..1100)
        }
    }

    private fun waitUntil(maxMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + maxMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("Condition not met within ${maxMillis}ms")
    }
}
