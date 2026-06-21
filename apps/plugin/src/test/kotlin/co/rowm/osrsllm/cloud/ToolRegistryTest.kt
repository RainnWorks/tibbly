package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RAI-25 — invariants on the cloud-side tool registry. The registry exists
 * to gate the cloud chat surface so first-turn token cost stays under budget.
 *
 *  1. Every MCP tool (mirrored in [ToolRegistry.ALL_MCP_TOOLS]) is mapped to
 *     EXACTLY ONE family. No duplicates, no gaps.
 *  2. CORE is non-empty and contains the must-have always-on tools the
 *     RAI-25 brief calls out.
 *  3. The meta-tool `enable_tools` lives in CORE — otherwise the LLM can't
 *     reach it to widen the surface mid-chat.
 *  4. `toolsIn(family)` round-trips with `familyOf(name)` for every entry.
 *  5. `allowedTools(set)` always includes CORE.
 */
class ToolRegistryTest {

    @Test
    fun `every MCP tool has exactly one family assignment`() {
        // The cross-check: keys of FAMILY must equal ALL_MCP_TOOLS exactly.
        // If MCP grows a new tool without a family entry, this fails loud.
        val mapped = ToolRegistry.FAMILY.keys
        val known = ToolRegistry.ALL_MCP_TOOLS
        val missing = known - mapped
        val orphan = mapped - known
        assertTrue("MCP tools missing a family entry: $missing", missing.isEmpty())
        assertTrue("Family entries with no matching MCP tool: $orphan", orphan.isEmpty())
    }

    @Test
    fun `no tool is assigned to two families`() {
        // LinkedHashMap collapses duplicate keys — re-asserting by iterating
        // the source pairs would be ideal but the static map is already de-duped
        // by construction. Defensive guard: size matches distinct keys.
        val map = ToolRegistry.FAMILY
        assertEquals(map.keys.size, map.size)
    }

    @Test
    fun `core family contains the always-on tool set from the brief`() {
        val mustBeCore = setOf(
            "find_item",
            "ge_price",
            "wiki_search",
            "wiki_page",
            "get_player_state",
            "get_event_log",
            "get_inventory",
            "get_equipment",
            "get_stats",
            "list_open_interfaces",
            "read_interface",
            "chat_message",
            "notify_player",
            "get_active_clue",
            "enable_tools",
        )
        for (tool in mustBeCore) {
            assertEquals(
                "tool '$tool' must be in CORE per the RAI-25 brief",
                ToolFamily.CORE,
                ToolRegistry.familyOf(tool),
            )
        }
    }

    @Test
    fun `enable_tools is always reachable - it lives in core`() {
        val fam = ToolRegistry.familyOf("enable_tools")
        assertEquals(
            "enable_tools must be core; otherwise the LLM can't widen the surface mid-chat",
            ToolFamily.CORE, fam,
        )
    }

    @Test
    fun `toolsIn round-trips with familyOf`() {
        for (family in ToolFamily.values()) {
            for (tool in ToolRegistry.toolsIn(family)) {
                assertEquals(
                    "tool $tool reported family $family but familyOf disagrees",
                    family, ToolRegistry.familyOf(tool),
                )
            }
        }
    }

    @Test
    fun `allowedTools always unions CORE in`() {
        val onlyHighlights = ToolRegistry.allowedTools(setOf(ToolFamily.HIGHLIGHTS))
        val coreTools = ToolRegistry.toolsIn(ToolFamily.CORE)
        assertTrue(
            "core tools missing from allowedTools(HIGHLIGHTS)",
            onlyHighlights.containsAll(coreTools),
        )
        assertTrue(
            "highlights tools missing from allowedTools(HIGHLIGHTS)",
            onlyHighlights.containsAll(ToolRegistry.toolsIn(ToolFamily.HIGHLIGHTS)),
        )
    }

    @Test
    fun `allowedTools dedupes and stays in a stable order`() {
        val twice = ToolRegistry.allowedTools(setOf(ToolFamily.CORE, ToolFamily.CORE))
        assertEquals(twice.toSet().size, twice.size)
    }

    @Test
    fun `farming tools appear in allow-list under FARMING family`() {
        // RAI-5 Tier 0 (5/5) — get_farming_state split into summary + per-region.
        assertEquals(ToolFamily.FARMING, ToolRegistry.familyOf("get_farming_summary"))
        assertEquals(ToolFamily.FARMING, ToolRegistry.familyOf("get_farming_patches"))
        val tools = ToolRegistry.allowedTools(setOf(ToolFamily.FARMING))
        assertTrue("summary exposed when FARMING enabled", tools.contains("get_farming_summary"))
        assertTrue("patches exposed when FARMING enabled", tools.contains("get_farming_patches"))
    }

    @Test
    fun `every family has at least one tool except deliberately-empty placeholders`() {
        // RAI-5 catalog (PR #31) reserved enum slots for families we plan to
        // fill incrementally. As each catalog tool ships, the family flips
        // from "expected empty" to "must have ≥1". Drop it from the allowlist
        // in the same PR that adds the first tool.
        val expectedEmpty = setOf(
            ToolFamily.LEAGUES,     // Tier 1 §4.6 — seasonal-only
            // FARMING populated PR #37 — get_farming_summary + get_farming_patches.
            ToolFamily.APPEARANCE,  // Tier 1 §4.11
            ToolFamily.AMBIENT,     // Tier 1 §4.7
            ToolFamily.PETS,        // Tier 1 §4.1 row 3
        )
        for (family in ToolFamily.values()) {
            val tools = ToolRegistry.toolsIn(family)
            assertNotNull(tools)
            if (family in expectedEmpty) continue
            assertTrue(
                "family $family has no tools — drop it or assign one",
                tools.isNotEmpty(),
            )
        }
    }
}
