package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * RAI-23 — pairing orchestration.
 *
 * Uses a fake [EgressGate] subclass that returns scripted HTTP responses, so
 * the tests never hit the network. Verifies:
 *
 *   - `requestCode` sends the hashed device key (NOT the raw key) on the wire.
 *   - The 6-char code + expiry are parsed off the response body.
 *   - `pollOnce` maps 2xx, 404 and 410 onto the right `PollOutcome` cases.
 *   - `startPolling` honours cancellation and TTL.
 */
class PairingFlowTest {

    /** In-memory device-key store for tests. */
    private class FakeStore(initial: String? = null) : DeviceKey.Store {
        var stored: String? = initial
        override fun read(): String? = stored
        override fun write(value: String) { stored = value }
        override fun clear() { stored = null }
    }

    /**
     * Captures each call and lets the test enqueue scripted responses, so the
     * production code path runs unmodified but never opens a socket.
     *
     * `responder` is a function so tests can either replay a fixed sequence
     * (consume from an ArrayDeque) or return the same response forever
     * (poll-loop tests).
     */
    private class FakeEgress(
        private val responder: () -> EgressGate.HttpEgressResponse,
        private val auditLog: AuditLog = AuditLog(),
    ) : EgressGate(transport = BackendWsClient(), auditLog = auditLog) {

        data class Recorded(
            val method: String,
            val path: String,
            val body: String?,
            val backendUrl: String,
        )

        val recorded: MutableList<Recorded> = mutableListOf()

        override fun egressHttp(
            method: String,
            backendUrl: BackendUrl,
            path: String,
            bodyJson: String?,
            headers: List<Pair<String, String>>,
        ): HttpEgressResponse {
            recorded.add(Recorded(method, path, bodyJson, backendUrl.value))
            return responder()
        }
    }

    private fun queued(vararg responses: EgressGate.HttpEgressResponse): () -> EgressGate.HttpEgressResponse {
        val queue = ArrayDeque(responses.toList())
        return { queue.removeFirst() }
    }

    private val backendUrl = BackendUrl("wss://api.example.com/plugin")
    private val urlSupplier = PairingFlow.BackendUrlSupplier { backendUrl }

    @Test
    fun `requestCode posts hashed device key and parses the response`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(
                status = 200,
                body = """{"code":"ABC123","expiresAt":"2026-01-01T00:10:00Z"}""",
            ),
        ))
        val store = FakeStore()
        val flow = PairingFlow(egress, DeviceKey(store), urlSupplier)

        val issued = flow.requestCode(playerName = "Zezima")
        assertEquals("ABC123", issued.code)
        assertEquals("2026-01-01T00:10:00Z", issued.expiresAt)

        assertEquals(1, egress.recorded.size)
        val req = egress.recorded[0]
        assertEquals("POST", req.method)
        assertEquals("/v1/pairing/request", req.path)
        val body = req.body!!
        assertTrue("body should contain Zezima player name: $body", body.contains("\"Zezima\""))

        val rawKey = store.stored!!
        val expectedHash = DeviceKey.hashForTransport(rawKey)
        assertTrue(
            "body must include the hashed device key, not raw. body=$body raw=$rawKey",
            body.contains(expectedHash),
        )
        assertTrue(
            "body must NOT contain the raw device key — that key never leaves the device",
            !body.contains(rawKey),
        )
    }

    @Test
    fun `requestCode without a logged-in player still sends a request`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(
                status = 200,
                body = """{"code":"XYZ789","expiresAt":"2026-01-01T00:10:00Z"}""",
            ),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)

        val issued = flow.requestCode(playerName = null)
        assertEquals("XYZ789", issued.code)
    }

    @Test
    fun `requestCode throws on non-2xx response`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(status = 500, body = "boom"),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        try {
            flow.requestCode(playerName = null)
            fail("expected PairingException")
        } catch (e: PairingFlow.PairingException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `pollOnce maps 404 to Pending`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(status = 404, body = "not found"),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        val outcome = flow.pollOnce("ABC123")
        assertEquals(PairingFlow.PollOutcome.Pending, outcome)
    }

    @Test
    fun `pollOnce maps 410 to Expired`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(status = 410, body = "gone"),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        val outcome = flow.pollOnce("ABC123")
        assertEquals(PairingFlow.PollOutcome.Expired, outcome)
    }

    @Test
    fun `pollOnce maps 200 with claimedAt to Claimed`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(
                status = 200,
                body = """{"claimedAt":"2026-01-01T00:01:00Z","playerName":"Zezima"}""",
            ),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        val outcome = flow.pollOnce("ABC123")
        assertTrue(outcome is PairingFlow.PollOutcome.Claimed)
        assertEquals("Zezima", (outcome as PairingFlow.PollOutcome.Claimed).playerName)
    }

    @Test
    fun `pollOnce maps 200 without claimedAt to Pending`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(status = 200, body = """{"claimedAt":null}"""),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        assertEquals(PairingFlow.PollOutcome.Pending, flow.pollOnce("ABC123"))
    }

    @Test
    fun `pollOnce returns Error when egress throws`() {
        val egress = object : EgressGate(BackendWsClient(), AuditLog()) {
            override fun egressHttp(
                method: String, backendUrl: BackendUrl, path: String, bodyJson: String?,
                headers: List<Pair<String, String>>,
            ): HttpEgressResponse = throw java.io.IOException("simulated network failure")
        }
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        val outcome = flow.pollOnce("ABC123")
        assertTrue(outcome is PairingFlow.PollOutcome.Error)
        assertTrue(
            (outcome as PairingFlow.PollOutcome.Error).message.contains("simulated"),
        )
    }

    @Test
    fun `startPolling reports Claimed once backend returns claimedAt`() {
        val egress = FakeEgress(queued(
            EgressGate.HttpEgressResponse(status = 404, body = ""),
            EgressGate.HttpEgressResponse(status = 404, body = ""),
            EgressGate.HttpEgressResponse(
                status = 200,
                body = """{"claimedAt":"2026-01-01T00:01:00Z","playerName":"Zezima"}""",
            ),
        ))
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)

        val latch = CountDownLatch(1)
        val result = AtomicReference<PairingFlow.PollOutcome?>()
        flow.startPolling(code = "ABC123", ttlMillis = 5_000, intervalMillis = 20) { outcome ->
            result.set(outcome)
            latch.countDown()
        }
        assertTrue("polling did not finish in time", latch.await(3, TimeUnit.SECONDS))
        val outcome = result.get()
        assertTrue("expected Claimed, got $outcome", outcome is PairingFlow.PollOutcome.Claimed)
        assertEquals("Zezima", (outcome as PairingFlow.PollOutcome.Claimed).playerName)
    }

    @Test
    fun `startPolling honours TTL by reporting Expired`() {
        // Responses always Pending; TTL should fire first.
        val egress = FakeEgress({ EgressGate.HttpEgressResponse(status = 404, body = "") })
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)

        val latch = CountDownLatch(1)
        val result = AtomicReference<PairingFlow.PollOutcome?>()
        flow.startPolling(code = "ABC123", ttlMillis = 50, intervalMillis = 10) { outcome ->
            result.set(outcome)
            latch.countDown()
        }
        assertTrue("TTL did not fire", latch.await(2, TimeUnit.SECONDS))
        assertEquals(PairingFlow.PollOutcome.Expired, result.get())
    }

    @Test
    fun `cancel stops polling without reporting an outcome`() {
        val egress = FakeEgress({ EgressGate.HttpEgressResponse(status = 404, body = "") })
        val flow = PairingFlow(egress, DeviceKey(FakeStore()), urlSupplier)
        val callCount = AtomicInteger()
        flow.startPolling(code = "ABC123", ttlMillis = 60_000, intervalMillis = 25) {
            callCount.incrementAndGet()
        }
        Thread.sleep(80)
        flow.cancel()
        val sample = callCount.get()
        Thread.sleep(120)
        // Allow at most one more callback (in-flight at cancel time).
        assertTrue(
            "cancel did not stop the poll thread; before=${sample} after=${callCount.get()}",
            callCount.get() - sample <= 1,
        )
    }

    @Test
    fun `formatCode dashifies a six-char code`() {
        assertEquals("ABC-123", PairingModal.formatCode("ABC123"))
    }

    @Test
    fun `formatCode leaves non-six-char strings alone`() {
        assertEquals("AB", PairingModal.formatCode("AB"))
        assertEquals("ABCDEFG", PairingModal.formatCode("ABCDEFG"))
    }

    @Test
    fun `https origin is derived from the wss BackendUrl`() {
        val httpsOrigin = EgressGate.toHttpsUrl(BackendUrl("wss://api.example.com/plugin"))
        assertEquals("https://api.example.com", httpsOrigin)
    }

    @Test
    fun `https origin keeps an explicit port`() {
        val httpsOrigin = EgressGate.toHttpsUrl(BackendUrl("wss://api.example.com:8443/plugin"))
        assertEquals("https://api.example.com:8443", httpsOrigin)
    }

    @Test
    fun `https origin handles a bare host with no path`() {
        val httpsOrigin = EgressGate.toHttpsUrl(BackendUrl("wss://example.org"))
        assertEquals("https://example.org", httpsOrigin)
    }

    @Test
    fun `urlSupplier returns the configured BackendUrl`() {
        assertEquals(backendUrl, urlSupplier.get())
    }
}
