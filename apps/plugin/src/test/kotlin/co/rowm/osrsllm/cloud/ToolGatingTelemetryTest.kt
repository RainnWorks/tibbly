package co.rowm.osrsllm.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RAI-25 — `ToolGatingTelemetry` writes to the local AuditLog ONLY. No egress.
 */
class ToolGatingTelemetryTest {

    private val auditLog = AuditLog()
    private val telemetry = ToolGatingTelemetry(auditLog)

    @After
    fun tearDown() = auditLog.clear()

    @Test
    fun `recordExposed writes a tagged entry to the audit log`() {
        telemetry.recordExposed(
            turnId = "t1",
            families = setOf(ToolFamily.CORE, ToolFamily.SLAYER),
            toolCount = 17,
        )
        val entries = auditLog.entries()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertTrue(
            "kind should describe the surface, got '${entry.payloadKind}'",
            entry.payloadKind.startsWith("ToolsExposed(") && entry.payloadKind.contains("turn=t1"),
        )
        assertEquals(17, entry.sizeBytes)
    }

    @Test
    fun `recordInvoked writes a tagged entry to the audit log`() {
        telemetry.recordInvoked(turnId = "t2", invokedTools = setOf("get_slayer_task", "wiki_search"))
        val entries = auditLog.entries()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertTrue(entry.payloadKind.startsWith("ToolsInvoked("))
        assertTrue(entry.payloadKind.contains("turn=t2"))
        assertEquals(2, entry.sizeBytes)
    }

    @Test
    fun `multiple turns accumulate independently`() {
        telemetry.recordExposed("t1", setOf(ToolFamily.CORE), 14)
        telemetry.recordInvoked("t1", setOf("wiki_search"))
        telemetry.recordExposed("t2", setOf(ToolFamily.CORE, ToolFamily.NAV), 19)
        assertEquals(3, auditLog.entries().size)
    }
}
