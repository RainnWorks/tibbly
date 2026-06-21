package co.rowm.osrsllm.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RAI-25 — the `enable_tools(family)` meta-tool. Returning the right family's
 * tool descriptions is the LLM's main escape hatch when the router under-gates.
 */
class EnableToolsToolTest {

    private val auditLog = AuditLog()
    private val tool = EnableToolsTool(auditLog)

    @After
    fun tearDown() {
        auditLog.clear()
    }

    @Test
    fun `enabling a single family returns that family's tool list`() {
        val result = tool.handle(listOf("highlights"))
        assertTrue("expected Ok, got $result", result is EnableToolsTool.Result.Ok)
        val ok = result as EnableToolsTool.Result.Ok
        assertEquals(setOf(ToolFamily.HIGHLIGHTS), ok.enabled)
        // Spot-check a representative tool from the highlights family.
        assertTrue("add_object_highlights expected", ok.toolNames.contains("add_object_highlights"))
        assertTrue("mark_tile expected", ok.toolNames.contains("mark_tile"))
        // Doesn't bleed into other families.
        assertFalse("slayer tool must not appear", ok.toolNames.contains("get_slayer_task"))
    }

    @Test
    fun `enabling multiple families merges their tool lists`() {
        val result = tool.handle(listOf("slayer", "nav"))
        assertTrue(result is EnableToolsTool.Result.Ok)
        val ok = result as EnableToolsTool.Result.Ok
        assertEquals(setOf(ToolFamily.SLAYER, ToolFamily.NAV), ok.enabled)
        assertTrue("slayer tool expected", ok.toolNames.contains("get_slayer_task"))
        assertTrue("nav tool expected", ok.toolNames.contains("find_transport"))
    }

    @Test
    fun `unknown family returns UnknownFamily error`() {
        val result = tool.handle(listOf("teleportation"))
        assertTrue("expected UnknownFamily, got $result", result is EnableToolsTool.Result.UnknownFamily)
        val err = result as EnableToolsTool.Result.UnknownFamily
        assertEquals(listOf("teleportation"), err.requested)
    }

    @Test
    fun `empty family list returns NoFamilyGiven`() {
        val result = tool.handle(emptyList())
        assertTrue(result is EnableToolsTool.Result.NoFamilyGiven)
    }

    @Test
    fun `mixed known and unknown families returns Ok with the known set`() {
        val result = tool.handle(listOf("highlights", "teleportation"))
        assertTrue("expected Ok, got $result", result is EnableToolsTool.Result.Ok)
        val ok = result as EnableToolsTool.Result.Ok
        assertEquals(setOf(ToolFamily.HIGHLIGHTS), ok.enabled)
    }

    @Test
    fun `success path writes an AuditLog entry tagged EnableTools`() {
        tool.handle(listOf("highlights"))
        val entries = auditLog.entries()
        assertEquals(1, entries.size)
        assertTrue(
            "audit kind should describe what was enabled, got '${entries[0].payloadKind}'",
            entries[0].payloadKind.startsWith("EnableTools(") && entries[0].payloadKind.contains("highlights"),
        )
    }

    @Test
    fun `parseFamilies handles single family field`() {
        val out = tool.parseFamilies(mapOf("family" to "banking"))
        assertEquals(listOf("banking"), out)
    }

    @Test
    fun `parseFamilies handles families array field`() {
        val out = tool.parseFamilies(mapOf("families" to listOf("banking", "ge")))
        assertEquals(listOf("banking", "ge"), out)
    }

    @Test
    fun `parseFamilies merges both fields`() {
        val out = tool.parseFamilies(mapOf(
            "family" to "highlights",
            "families" to listOf("nav", "ge"),
        ))
        assertEquals(listOf("highlights", "nav", "ge"), out)
    }

    @Test
    fun `parseFamilies returns empty on null arguments`() {
        assertEquals(emptyList<String>(), tool.parseFamilies(null))
    }

    @Test
    fun `parseFamilies tolerates blank single field`() {
        assertEquals(emptyList<String>(), tool.parseFamilies(mapOf("family" to "")))
    }

    @Test
    fun `description lists every non-empty family`() {
        val desc = tool.description
        assertNotNull(desc)
        // Should mention each non-empty wire-form family name.
        for (family in ToolFamily.values()) {
            if (ToolRegistry.toolsIn(family).isEmpty()) continue
            assertTrue(
                "description should mention '${family.wireName()}'",
                desc.contains(family.wireName()),
            )
        }
    }
}
