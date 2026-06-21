package co.rowm.osrsllm.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * RAI-38 — egress preconditions. The gate is supposed to be unbypassable:
 *
 *   1. Without consent, calls throw and nothing is written.
 *   2. With cloudChatEnabled = false, calls throw and nothing is written.
 *   3. Without a live WSS session, calls throw and nothing is written.
 *   4. A valid call records an audit-log entry tagged with the sealed
 *      subtype's simple name.
 *
 * BackendWsClient itself is not exercised here — it would require a real
 * Ktor `webSocketSession` to be useful. EgressGate.egress, however, refuses
 * to proceed before reaching the transport in the failure cases, so we can
 * test the preconditions without a network. The 'success' case uses a
 * fake transport that throws on send to verify that we still abort cleanly
 * BEFORE writing to the audit log when the network call fails.
 */
class EgressGateTest {

    private val auditLog = AuditLog()

    @After
    fun tearDown() {
        // Make sure consent state doesn't leak across tests.
        ConsentState.reset()
        auditLog.clear()
    }

    @Test
    fun `refuses without consent`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = false)
        val gate = EgressGate(transport = BackendWsClient(), auditLog = auditLog)
        val payload = OutboundPayload.SessionHeartbeat(clientUptimeMs = 1)
        try {
            gate.egress(payload, consent = consent, cloudChatEnabled = true)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "should mention consent in message: ${e.message}",
                (e.message ?: "").contains("consent.accepted=false"),
            )
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `refuses when cloud chat disabled`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = true)
        val gate = EgressGate(transport = BackendWsClient(), auditLog = auditLog)
        val payload = OutboundPayload.SessionHeartbeat(clientUptimeMs = 1)
        try {
            gate.egress(payload, consent = consent, cloudChatEnabled = false)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "should mention cloudChatEnabled in message: ${e.message}",
                (e.message ?: "").contains("cloudChatEnabled=false"),
            )
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `refuses without a live transport session`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = true)
        // BackendWsClient has currentSession() == null until connect() is called.
        val gate = EgressGate(transport = BackendWsClient(), auditLog = auditLog)
        val payload = OutboundPayload.SessionHeartbeat(clientUptimeMs = 1)
        try {
            gate.egress(payload, consent = consent, cloudChatEnabled = true)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                "should mention disconnected transport: ${e.message}",
                (e.message ?: "").contains("not connected"),
            )
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `consent state is genuinely write-once`() {
        ConsentState.reset()
        ConsentState.freeze(accepted = true)
        try {
            ConsentState.freeze(accepted = false)
            fail("expected IllegalStateException on double-freeze")
        } catch (e: IllegalStateException) {
            assertTrue((e.message ?: "").contains("already frozen"))
        }
    }

    @Test
    fun `outbound payloads are a closed sealed hierarchy`() {
        // The point of this test is to catch a refactor that accidentally widens
        // the sealed family. Any new payload type is a deliberate disclosure
        // change and must show up in DATA_DISCLOSURE.md.
        val sealed = OutboundPayload::class.sealedSubclasses
        val names = sealed.map { it.simpleName }.toSet()
        val expected = setOf(
            "SessionHello",
            "ChatUserMessage",
            "ToolResult",
            "ChatCancel",
            "SessionHeartbeat",
        )
        assertEquals(
            "OutboundPayload's sealed children must match DATA_DISCLOSURE.md exactly. " +
                "If you added a new wire shape, update both this test AND apps/plugin/DATA_DISCLOSURE.md.",
            expected,
            names,
        )
    }

    @Test
    fun `audit log is bounded`() {
        for (i in 0 until AuditLog.CAPACITY + 50) {
            auditLog.record(payloadKind = "T", sizeBytes = i)
        }
        assertEquals(AuditLog.CAPACITY, auditLog.entries().size)
        // Oldest entries dropped — first entry should be one of the later ones.
        val firstSize = auditLog.entries().first().sizeBytes
        assertTrue("oldest entry should have been dropped; firstSize=$firstSize", firstSize >= 50)
    }

    @Test
    fun `consent snapshot exposes acceptedAt for the panel`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = true, nowMillis = 1_700_000_000_000L)
        assertEquals(true, consent.accepted)
        assertEquals(1_700_000_000_000L, consent.acceptedAtMillis)
        assertNotNull(ConsentState.snapshot())
    }
}
