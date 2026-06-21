package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RAI-25 — per-keyword routing rules. Each test names a representative user
 * message and asserts the families the router pulls in, matching the brief
 * exactly. New rules MUST come with a new test in this file.
 */
class ContextRouterTest {

    private val router = ContextRouter()

    // ── Brief acceptance case ────────────────────────────────────────────

    @Test
    fun `acceptance - what is my slayer task routes to slayer plus combat`() {
        // The brief's headline acceptance: "what's my slayer task?" → core + slayer + combat
        val out = router.route("what's my slayer task?")
        assertTrue("slayer expected: $out", out.contains(ToolFamily.SLAYER))
        assertTrue("combat expected: $out", out.contains(ToolFamily.COMBAT))
        // CORE is implicit — verify via allowedTools includes core members.
        val tools = ToolRegistry.allowedTools(out)
        assertTrue("core tool wiki_search should be exposed", tools.contains("wiki_search"))
        assertTrue("slayer tool get_slayer_task should be exposed", tools.contains("get_slayer_task"))
    }

    // ── Per-rule keyword triggers ────────────────────────────────────────

    @Test
    fun `slayer task assignment routes to slayer plus combat`() {
        for (msg in listOf(
            "what's my slayer task?",
            "next assignment please",
            "is this task worth doing",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected SLAYER, got $out", out.contains(ToolFamily.SLAYER))
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
        }
    }

    @Test
    fun `clue coordinate cipher anagram routes to core only`() {
        for (msg in listOf(
            "I have a clue scroll, where do I go?",
            "this is a coordinate clue 03 13 N 03 02 W",
            "help me solve this anagram",
            "this cipher reads as TSDJD",
            "puzzle box riddle help",
        )) {
            val out = router.route(msg)
            // Brief explicitly says clue is CORE — the router returns no
            // non-core families for these messages.
            assertFalse("[$msg] should not pull SLAYER, got $out", out.contains(ToolFamily.SLAYER))
            assertFalse("[$msg] should not pull COMBAT, got $out", out.contains(ToolFamily.COMBAT))
            // Sanity: core is always in the resolved tool list.
            val tools = ToolRegistry.allowedTools(out)
            assertTrue("[$msg] get_active_clue must be in core", tools.contains("get_active_clue"))
        }
    }

    @Test
    fun `bank withdraw deposit tag tab routes to banking`() {
        for (msg in listOf(
            "open my bank please",
            "withdraw 4 lobsters",
            "deposit all my logs",
            "set up a tag for vorkath",
            "make me a new tab",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected BANKING, got $out", out.contains(ToolFamily.BANKING))
        }
    }

    @Test
    fun `quest and hard diary routes to quest plus quest_items`() {
        for (msg in listOf(
            "what quest should i do next",
            "monkey madness 2 quest help",
            "help me finish the hard diary",
            "varrock diary requirements",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected QUEST, got $out", out.contains(ToolFamily.QUEST))
            assertTrue("[$msg] expected QUEST_ITEMS, got $out", out.contains(ToolFamily.QUEST_ITEMS))
        }
    }

    @Test
    fun `kill boss fight monster dps routes to combat plus slayer plus nav`() {
        for (msg in listOf(
            "I want to kill vorkath",
            "best boss for 80 attack",
            "let's fight zulrah",
            "how much dps does this give",
            "this monster is hitting hard",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
            assertTrue("[$msg] expected SLAYER, got $out", out.contains(ToolFamily.SLAYER))
            assertTrue("[$msg] expected NAV, got $out", out.contains(ToolFamily.NAV))
        }
    }

    @Test
    fun `ge grand exchange buy sell price routes to ge`() {
        for (msg in listOf(
            "what's the ge price of dragon bones",
            "i want to buy a fire cape",
            "selling my dragon claws",
            "current price of nature runes",
            "best flip right now",
            "Grand Exchange status",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected GE, got $out", out.contains(ToolFamily.GE))
        }
    }

    @Test
    fun `highlight mark flag tile outline routes to highlights`() {
        for (msg in listOf(
            "highlight all the demons please",
            "mark the boss spawn",
            "flag every cave horror",
            "outline the doors",
            "put a tile marker on this square",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected HIGHLIGHTS, got $out", out.contains(ToolFamily.HIGHLIGHTS))
        }
    }

    @Test
    fun `loadout gear equipment routes to loadouts plus banking`() {
        for (msg in listOf(
            "what's my best loadout for vorkath",
            "save this gear setup",
            "equipment recommendation please",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected LOADOUTS, got $out", out.contains(ToolFamily.LOADOUTS))
            assertTrue("[$msg] expected BANKING (gear needs withdrawal), got $out", out.contains(ToolFamily.BANKING))
        }
    }

    @Test
    fun `nav phrases route to nav`() {
        for (msg in listOf(
            "go to varrock",
            "how do i get to fight caves",
            "teleport to home",
            "how do i reach prifddinas",
            "run to the bank",
            "fastest way to barrows",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected NAV, got $out", out.contains(ToolFamily.NAV))
        }
    }

    @Test
    fun `fish fishing routes to fishing`() {
        for (msg in listOf(
            "where can i fish lobsters",
            "best fishing spot for sharks",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected FISHING, got $out", out.contains(ToolFamily.FISHING))
        }
    }

    @Test
    fun `xp level train routes to skills`() {
        for (msg in listOf(
            "what's the best xp/hr at 80 mining",
            "how do i level firemaking fast",
            "what should i train next",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected SKILLS, got $out", out.contains(ToolFamily.SKILLS))
        }
    }

    // ── RAI-5 Tier 0 catalog additions ───────────────────────────────────

    @Test
    fun `prayer flick protect piety rigour augury routes to combat`() {
        for (msg in listOf(
            "what prayer should i use",
            "how do i flick prayers at akkha",
            "should i pray protect from magic",
            "is piety better than rigour here",
            "augury vs mystic might at zulrah",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
        }
    }

    @Test
    fun `projectile and tick keywords route to combat`() {
        for (msg in listOf(
            "what's the projectile from vorkath",
            "tick eat the dragonfire",
            "how many ticks until the nuke",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
        }
    }

    @Test
    fun `raid cox tob toa keywords route to raids plus combat`() {
        for (msg in listOf(
            "starting a raid",
            "how do cox points work",
            "tob verzik phase 3",
            "chambers of xeric strategy",
            "toa 500 invocation",
            "theatre of blood entry req",
            "tombs of amascut scaling",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected RAIDS, got $out", out.contains(ToolFamily.RAIDS))
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
        }
    }

    @Test
    fun `raid boss names route to raids plus combat`() {
        for (msg in listOf(
            "olm head phase",
            "akkha sand crab spawn",
            "kephri scarab swarms",
            "sotetseg maze tile",
            "verzik p2 bounce",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected RAIDS, got $out", out.contains(ToolFamily.RAIDS))
            assertTrue("[$msg] expected COMBAT, got $out", out.contains(ToolFamily.COMBAT))
        }
    }

    @Test
    fun `routeWire includes raids on wire for raid messages`() {
        val wire = router.routeWire("how do i beat verzik phase 2")
        assertTrue("raids on the wire", wire.contains("raids"))
        assertTrue("combat on the wire", wire.contains("combat"))
        assertTrue("core on the wire", wire.contains("core"))
    }

    @Test
    fun `loot drop stack routes to groundstate`() {
        for (msg in listOf(
            "is there any loot worth picking up",
            "what's the rare drop here",
            "there's a big stack on the ground",
        )) {
            val out = router.route(msg)
            assertTrue("[$msg] expected GROUNDSTATE, got $out", out.contains(ToolFamily.GROUNDSTATE))
        }
    }

    // ── Game-state nudges ───────────────────────────────────────────────

    @Test
    fun `bank open nudges banking on`() {
        val out = router.route("hi", ContextRouter.GameStateContext(bankOpen = true))
        assertTrue(out.contains(ToolFamily.BANKING))
    }

    @Test
    fun `ge open nudges ge on`() {
        val out = router.route("hi", ContextRouter.GameStateContext(geOpen = true))
        assertTrue(out.contains(ToolFamily.GE))
    }

    @Test
    fun `in combat nudges combat on`() {
        val out = router.route("hi", ContextRouter.GameStateContext(inCombat = true))
        assertTrue(out.contains(ToolFamily.COMBAT))
    }

    @Test
    fun `active slayer task nudges slayer plus combat on`() {
        val out = router.route("hi", ContextRouter.GameStateContext(hasActiveSlayerTask = true))
        assertTrue(out.contains(ToolFamily.SLAYER))
        assertTrue(out.contains(ToolFamily.COMBAT))
    }

    // ── Misc invariants ─────────────────────────────────────────────────

    @Test
    fun `empty message returns empty family set`() {
        assertEquals(emptySet<ToolFamily>(), router.route(""))
    }

    @Test
    fun `unmatched casual message returns empty family set`() {
        assertEquals(emptySet<ToolFamily>(), router.route("hi there friend"))
    }

    @Test
    fun `routeWire always appends core to the list`() {
        val wire = router.routeWire("highlight the wizard")
        assertTrue("core must be on the wire", wire.contains("core"))
        assertTrue("highlights must be on the wire", wire.contains("highlights"))
    }

    @Test
    fun `routeWire dedupes core when nothing else matches`() {
        val wire = router.routeWire("just chatting")
        assertEquals(listOf("core"), wire)
    }

    @Test
    fun `case insensitive keyword matching`() {
        val lower = router.route("how do i KILL Vorkath")
        assertTrue("uppercase KILL still matches COMBAT", lower.contains(ToolFamily.COMBAT))
    }
}
