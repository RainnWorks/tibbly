package co.rowm.osrsllm.cloud.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.runelite.api.Actor
import net.runelite.api.Client
import net.runelite.api.NPC
import net.runelite.api.Player
import net.runelite.api.Prayer
import net.runelite.api.Projectile
import net.runelite.api.Skill
import net.runelite.api.Varbits
import net.runelite.api.WorldType
import net.runelite.api.vars.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import net.runelite.api.Deque as RlDeque
import java.util.EnumSet

/**
 * RAI-5 — unit tests for the four Tier 0 state probes shipped in
 * [StateProbes]. We mock [Client] via a dynamic proxy keyed on method name;
 * the test sets only the fields each probe reads.
 *
 * Coverage shape per probe:
 *  - happy path (real-ish state)
 *  - degenerate (player logged out / no raid / no projectiles / no prayers)
 *
 * The probes' I/O-bound `accountIdentity(Client, ClientThread)` overloads
 * marshal to the client thread; we don't exercise those here (the
 * `readX(client)` overloads are pure and cover the projection logic).
 */
class StateProbesTest {

    private val json = Json { prettyPrint = false }

    // ── get_account_identity ─────────────────────────────────────────────

    @Test
    fun `account_identity happy path returns name account type and world flags`() {
        val player = fakePlayer(name = "Tibbly")
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to player,
                "getAccountType" to AccountType.HARDCORE_IRONMAN,
                "getWorld" to 302,
                "getWorldType" to EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL),
                "getWorldHost" to "oldschool302.runescape.com",
                "getLauncherDisplayName" to "tom@jagex",
            ),
        )
        val ident = StateProbes.readAccountIdentity(client)
        assertEquals("Tibbly", ident.name)
        assertEquals("HARDCORE_IRONMAN", ident.accountType)
        assertTrue("HCIM is an ironman variant", ident.isIronman)
        assertFalse("HCIM is not a group iron", ident.isGroupIronman)
        assertEquals(302, ident.world)
        assertEquals(listOf("MEMBERS", "SEASONAL"), ident.worldFlags)
        assertEquals("oldschool302.runescape.com", ident.worldHost)
        assertEquals("tom@jagex", ident.launcherName)
    }

    @Test
    fun `account_identity logged out returns null name with safe defaults`() {
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to null,
                "getAccountType" to AccountType.NORMAL,
                "getWorld" to 0,
                "getWorldType" to EnumSet.noneOf(WorldType::class.java),
                "getWorldHost" to null,
                "getLauncherDisplayName" to null,
            ),
        )
        val ident = StateProbes.readAccountIdentity(client)
        assertNull(ident.name)
        assertEquals("NORMAL", ident.accountType)
        assertFalse(ident.isIronman)
        assertFalse(ident.isGroupIronman)
        assertEquals(0, ident.world)
        assertTrue("no world flags", ident.worldFlags.isEmpty())
    }

    @Test
    fun `account_identity group ironman is flagged isGroupIronman`() {
        // Note: RuneLite's AccountType.isIronman() only matches IRONMAN /
        // ULTIMATE_IRONMAN / HARDCORE_IRONMAN. Group iron variants are
        // gated by isGroupIronman() — we surface both flags so the LLM
        // can reason about either dimension.
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to fakePlayer(name = "GIMer"),
                "getAccountType" to AccountType.GROUP_IRONMAN,
                "getWorld" to 502,
                "getWorldType" to EnumSet.of(WorldType.MEMBERS),
                "getWorldHost" to null,
                "getLauncherDisplayName" to null,
            ),
        )
        val ident = StateProbes.readAccountIdentity(client)
        assertTrue("GROUP_IRONMAN isGroupIronman", ident.isGroupIronman)
        assertFalse("GROUP_IRONMAN is NOT solo isIronman per RL", ident.isIronman)
        assertEquals("GROUP_IRONMAN", ident.accountType)
    }

    @Test
    fun `account_identity hardcore group ironman is flagged isGroupIronman`() {
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to fakePlayer(name = "HCGIMer"),
                "getAccountType" to AccountType.HARDCORE_GROUP_IRONMAN,
                "getWorld" to 502,
                "getWorldType" to EnumSet.of(WorldType.MEMBERS),
                "getWorldHost" to null,
                "getLauncherDisplayName" to null,
            ),
        )
        val ident = StateProbes.readAccountIdentity(client)
        assertTrue("HARDCORE_GROUP_IRONMAN isGroupIronman", ident.isGroupIronman)
        assertEquals("HARDCORE_GROUP_IRONMAN", ident.accountType)
    }

    @Test
    fun `account_identity json output is compact and parseable`() {
        val ident = StateProbes.AccountIdentity(
            name = "Tibbly",
            accountType = "NORMAL",
            isIronman = false,
            isGroupIronman = false,
            world = 302,
            worldFlags = listOf("MEMBERS"),
            worldHost = null,
            launcherName = null,
        )
        val out = StateProbes.accountIdentity(ident)
        assertFalse("compact JSON: no newlines", out.contains('\n'))
        val parsed = json.parseToJsonElement(out).jsonObject
        assertEquals("Tibbly", parsed["name"]!!.jsonPrimitive.contentOrNull)
        assertEquals(302, parsed["world"]!!.jsonPrimitive.int)
        // explicitNulls=false drops the nullable fields entirely.
        assertFalse("worldHost null should be dropped", parsed.containsKey("worldHost"))
    }

    // ── get_raid_layout ──────────────────────────────────────────────────

    @Test
    fun `raid_layout outside raid reports NONE with zeroed varbits`() {
        val client = fakeClient(
            overrides = mapOf("getMapRegions" to intArrayOf(12342)),
            varbits = emptyMap(),
        )
        val r = StateProbes.readRaidLayout(client)
        assertEquals("NONE", r.activeRaid)
        assertEquals(0, r.coxInRaid)
        assertEquals(0, r.tobState)
        assertEquals(0, r.toaInvocation)
        assertEquals(5, r.tobPartyOrbs.size)
        assertEquals(8, r.toaPartyHp.size)
        assertEquals(listOf(12342), r.mapRegions)
    }

    @Test
    fun `raid_layout inside ToA reports invocation and party hp`() {
        val client = fakeClient(
            overrides = mapOf("getMapRegions" to intArrayOf(14160)),
            varbits = mapOf(
                Varbits.TOA_RAID_LEVEL to 500,
                Varbits.TOA_RAID_DAMAGE to 4200,
                Varbits.TOA_MEMBER_0_HEALTH to 98,
                Varbits.TOA_MEMBER_1_HEALTH to 76,
            ),
        )
        val r = StateProbes.readRaidLayout(client)
        assertEquals("TOA", r.activeRaid)
        assertEquals(500, r.toaInvocation)
        assertEquals(4200, r.toaPartyDamage)
        assertEquals(98, r.toaPartyHp[0])
        assertEquals(76, r.toaPartyHp[1])
        assertEquals(0, r.toaPartyHp[7])
    }

    @Test
    fun `raid_layout inside CoX reports total points`() {
        val client = fakeClient(
            overrides = mapOf("getMapRegions" to intArrayOf(13386)),
            varbits = mapOf(
                Varbits.IN_RAID to 1,
                Varbits.TOTAL_POINTS to 27_345,
                Varbits.RAID_STATE to 3,
            ),
        )
        val r = StateProbes.readRaidLayout(client)
        assertEquals("COX", r.activeRaid)
        assertEquals(1, r.coxInRaid)
        assertEquals(27_345, r.coxTotalPoints)
        assertEquals(3, r.coxState)
    }

    @Test
    fun `raid_layout inside ToB reports party orbs`() {
        val client = fakeClient(
            overrides = mapOf("getMapRegions" to intArrayOf(12867)),
            varbits = mapOf(
                Varbits.THEATRE_OF_BLOOD to 2,
                Varbits.THEATRE_OF_BLOOD_ORB1 to 1, // alive
                Varbits.THEATRE_OF_BLOOD_ORB2 to 2, // dead
                Varbits.THEATRE_OF_BLOOD_ORB3 to 3, // dc'd
            ),
        )
        val r = StateProbes.readRaidLayout(client)
        assertEquals("TOB", r.activeRaid)
        assertEquals(2, r.tobState)
        assertEquals(listOf(1, 2, 3, 0, 0), r.tobPartyOrbs)
    }

    // ── get_target_projectiles ───────────────────────────────────────────

    @Test
    fun `projectiles empty deque returns empty list`() {
        val player = fakePlayer("Self")
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to player,
                "getProjectiles" to fakeProjectileDeque(emptyList()),
            ),
        )
        assertTrue(StateProbes.readTargetProjectiles(client).isEmpty())
    }

    @Test
    fun `projectiles aimed at local player are surfaced with tick math`() {
        val player = fakePlayer("Self")
        val source = fakeNpc("Akkha")
        // remainingCycles = 90 → 3 game ticks (90/30).
        val proj = fakeProjectile(id = 1442, remainingCycles = 90, target = player, sourceActor = source)
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to player,
                "getProjectiles" to fakeProjectileDeque(listOf(proj)),
            ),
        )
        val out = StateProbes.readTargetProjectiles(client)
        assertEquals(1, out.size)
        assertEquals(1442, out[0].id)
        assertEquals(3, out[0].remainingTicks)
        assertTrue(out[0].targetIsLocalPlayer)
        assertEquals("Self", out[0].targetName)
        assertEquals("Akkha", out[0].sourceName)
    }

    @Test
    fun `projectiles aimed at the players target are surfaced too`() {
        val target = fakeNpc("Vorkath")
        val player = fakePlayer("Self", interacting = target)
        // Player is interacting with Vorkath; projectile is aimed at Vorkath.
        val proj = fakeProjectile(id = 393, remainingCycles = 30, target = target, sourceActor = null)
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to player,
                "getProjectiles" to fakeProjectileDeque(listOf(proj)),
            ),
        )
        val out = StateProbes.readTargetProjectiles(client)
        assertEquals(1, out.size)
        assertFalse("aimed at Vorkath, not me", out[0].targetIsLocalPlayer)
        assertEquals("Vorkath", out[0].targetName)
    }

    @Test
    fun `projectiles list is capped at 8 entries`() {
        val player = fakePlayer("Self")
        val projectiles = (0 until 15).map { i ->
            fakeProjectile(id = i, remainingCycles = 30, target = player, sourceActor = null)
        }
        val client = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to player,
                "getProjectiles" to fakeProjectileDeque(projectiles),
            ),
        )
        val out = StateProbes.readTargetProjectiles(client)
        assertEquals(8, out.size)
    }

    // ── get_active_prayers ───────────────────────────────────────────────

    @Test
    fun `active_prayers no prayers active returns empty arrays`() {
        val client = fakeClient(
            activePrayers = emptySet(),
            boostedSkills = mapOf(Skill.PRAYER to 99),
            realSkills = mapOf(Skill.PRAYER to 99),
            varbits = mapOf(Varbits.QUICK_PRAYER to 0),
        )
        val out = StateProbes.readActivePrayers(client)
        assertTrue(out.active.isEmpty())
        assertTrue(out.ruinous.isEmpty())
        assertEquals(99, out.prayerPoints)
        assertEquals(99, out.prayerMax)
        assertFalse(out.quickPrayer)
    }

    @Test
    fun `active_prayers piety + protect_from_magic shows both pretty names`() {
        val client = fakeClient(
            activePrayers = setOf(Prayer.PIETY, Prayer.PROTECT_FROM_MAGIC),
            boostedSkills = mapOf(Skill.PRAYER to 71),
            realSkills = mapOf(Skill.PRAYER to 99),
            varbits = mapOf(Varbits.QUICK_PRAYER to 1),
        )
        val out = StateProbes.readActivePrayers(client)
        assertEquals(setOf("Piety", "Protect from magic"), out.active.toSet())
        assertTrue(out.ruinous.isEmpty())
        assertEquals(71, out.prayerPoints)
        assertEquals(99, out.prayerMax)
        assertTrue(out.quickPrayer)
    }

    @Test
    fun `active_prayers ruinous powers go into the ruinous bucket`() {
        val client = fakeClient(
            activePrayers = setOf(Prayer.RP_REJUVENATION, Prayer.RP_BERSERKER, Prayer.PROTECT_FROM_MELEE),
            boostedSkills = mapOf(Skill.PRAYER to 60),
            realSkills = mapOf(Skill.PRAYER to 99),
            varbits = mapOf(Varbits.QUICK_PRAYER to 0),
        )
        val out = StateProbes.readActivePrayers(client)
        assertEquals(listOf("Protect from melee"), out.active)
        assertEquals(setOf("Rejuvenation", "Berserker"), out.ruinous.toSet())
    }

    // ── get_farming_summary ──────────────────────────────────────────────

    @Test
    fun `farming_summary all empty gives total equals patch count`() {
        val client = fakeClient(varbits = emptyMap())
        val out = StateProbes.readFarmingSummary(client)
        // Every patch reads 0 → EMPTY. Total should equal sum of all patch slots.
        val expectedTotal = co.rowm.osrsllm.cloud.tools.FarmingTables.REGIONS.sumOf { it.patches.size }
        assertEquals(expectedTotal, out.total)
        assertEquals(expectedTotal, out.emptyPatches)
        assertEquals(0, out.ready)
        assertEquals(0, out.diseased)
        assertEquals(0, out.dead)
        assertEquals(0, out.growing)
        assertEquals(0, out.unknown)
    }

    @Test
    fun `farming_summary mixes ready growing diseased dead by varbit value`() {
        // Catherby: Allotment North (FT_A=4771), Allotment South (FT_B=4772),
        // Flower (FT_C=4773), Herb (FT_D=4774), Fruit Tree (FT_A=4771 reuse).
        // Allotment value 10 → READY (potato harvestable band 10..12)
        // Herb value 8 → READY (guam harvestable band 8..10)
        // Flower value 8 → READY (8..15)
        // Setting FT_A=10 also affects Catherby Fruit Tree (uses same varbit).
        val client = fakeClient(
            varbits = mapOf(
                4771 to 10,  // FT_A — Allotment North = READY, Fruit Tree = READY (10 is in 6..15 band)
                4772 to 17,  // FT_B — Allotment South onion harvestable
                4773 to 8,   // FT_C — Flower ready
                4774 to 8,   // FT_D — Herb guam ready
            ),
        )
        val out = StateProbes.readFarmingSummary(client)
        assertTrue("expected some READY patches, got $out", out.ready >= 1)
        // 4771 is reused across Catherby Fruit Tree + Falador Tree + many others,
        // so multiple patches share the same value 10. That's correct upstream
        // behaviour while standing in a single region.
    }

    @Test
    fun `farming_summary herb diseased value lands in diseased bucket`() {
        // Herb varbit value 140 → RANARR DISEASED per upstream HERB.forVarbitValue
        // (band 128..169 = various diseased herbs).
        val client = fakeClient(varbits = mapOf(4774 to 140))
        val out = StateProbes.readFarmingSummary(client)
        assertTrue("expected diseased ≥ 1, got $out", out.diseased >= 1)
    }

    @Test
    fun `farming_summary herb dead value lands in dead bucket`() {
        // Herb varbit value 171 → dead herb (band 170..172).
        val client = fakeClient(varbits = mapOf(4774 to 171))
        val out = StateProbes.readFarmingSummary(client)
        assertTrue("expected dead ≥ 1, got $out", out.dead >= 1)
    }

    @Test
    fun `farming_summary json is compact and parseable`() {
        val summary = StateProbes.FarmingSummary(
            ready = 2, growing = 3, diseased = 1, dead = 0,
            emptyPatches = 5, unknown = 0, total = 11,
        )
        val out = StateProbes.farmingSummary(summary)
        assertFalse("compact JSON: no newlines", out.contains('\n'))
        val parsed = json.parseToJsonElement(out).jsonObject
        assertEquals(2, parsed["ready"]!!.jsonPrimitive.int)
        assertEquals(3, parsed["growing"]!!.jsonPrimitive.int)
        assertEquals(11, parsed["total"]!!.jsonPrimitive.int)
    }

    // ── get_farming_patches(region) ──────────────────────────────────────

    @Test
    fun `farming_patches catherby returns 5 patches`() {
        val client = fakeClient(
            varbits = mapOf(
                4771 to 10,  // FT_A — Allotment North = READY (potato harvest)
                4774 to 38,  // FT_D — Herb = READY (ranarr harvest, 36..38)
            ),
        )
        val out = StateProbes.farmingPatchesString(client, "Catherby")
        val parsed = json.parseToJsonElement(out).jsonObject
        assertEquals("Catherby", parsed["region"]!!.jsonPrimitive.contentOrNull)
        val patches = parsed["patches"]!!.jsonArray
        assertEquals(5, patches.size)  // 2 allotments + flower + herb + fruit tree
        // Find the herb patch entry.
        val herb = patches.first { it.jsonObject["type"]!!.jsonPrimitive.content == "HERB" }
        assertEquals("READY", herb.jsonObject["state"]!!.jsonPrimitive.content)
        assertEquals(38, herb.jsonObject["rawVarbit"]!!.jsonPrimitive.int)
    }

    @Test
    fun `farming_patches accepts region aliases case insensitive`() {
        val client = fakeClient(varbits = emptyMap())
        val out1 = StateProbes.farmingPatchesString(client, "CATHERBY")
        val out2 = StateProbes.farmingPatchesString(client, "kandarin north")
        assertTrue("uppercase matched", json.parseToJsonElement(out1).jsonObject.containsKey("region"))
        assertTrue("alias matched", json.parseToJsonElement(out2).jsonObject.containsKey("region"))
    }

    @Test
    fun `farming_patches unknown region returns error with knownRegions list`() {
        val client = fakeClient(varbits = emptyMap())
        val out = StateProbes.farmingPatchesString(client, "Atlantis")
        val parsed = json.parseToJsonElement(out).jsonObject
        assertNotNull("error key present", parsed["error"])
        assertTrue(
            "Atlantis named in error",
            parsed["error"]!!.jsonPrimitive.content.contains("Atlantis"),
        )
        val known = parsed["knownRegions"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("Catherby is a known region", known.contains("Catherby"))
        assertTrue("Farming Guild is a known region", known.contains("Farming Guild"))
    }

    @Test
    fun `farming_patches farming guild includes herb tree fruit tree`() {
        val client = fakeClient(varbits = emptyMap())
        val out = StateProbes.farmingPatchesString(client, "Farming Guild")
        val parsed = json.parseToJsonElement(out).jsonObject
        val types = parsed["patches"]!!.jsonArray
            .map { it.jsonObject["type"]!!.jsonPrimitive.content }
            .toSet()
        assertTrue("guild has HERB", types.contains("HERB"))
        assertTrue("guild has TREE", types.contains("TREE"))
        assertTrue("guild has FRUIT_TREE", types.contains("FRUIT_TREE"))
        assertTrue("guild has BUSH", types.contains("BUSH"))
    }

    @Test
    fun `farming_patches diseased herb varbit lands in DISEASED state`() {
        val client = fakeClient(varbits = mapOf(4774 to 140))
        val out = StateProbes.farmingPatchesString(client, "Catherby")
        val parsed = json.parseToJsonElement(out).jsonObject
        val herb = parsed["patches"]!!.jsonArray
            .first { it.jsonObject["type"]!!.jsonPrimitive.content == "HERB" }
            .jsonObject
        assertEquals("DISEASED", herb["state"]!!.jsonPrimitive.content)
    }

    // ── JSON envelope sanity ─────────────────────────────────────────────

    @Test
    fun `all four probes emit valid JSON envelopes`() {
        val emptyClient = fakeClient(
            overrides = mapOf(
                "getLocalPlayer" to null,
                "getAccountType" to AccountType.NORMAL,
                "getWorld" to 0,
                "getWorldType" to EnumSet.noneOf(WorldType::class.java),
                "getWorldHost" to null,
                "getLauncherDisplayName" to null,
                "getMapRegions" to intArrayOf(),
                "getProjectiles" to fakeProjectileDeque(emptyList()),
            ),
            activePrayers = emptySet(),
            boostedSkills = mapOf(Skill.PRAYER to 0),
            realSkills = mapOf(Skill.PRAYER to 0),
            varbits = emptyMap(),
        )
        assertNotNull(json.parseToJsonElement(StateProbes.accountIdentity(StateProbes.readAccountIdentity(emptyClient))))
        assertNotNull(json.parseToJsonElement(StateProbes.raidLayout(StateProbes.readRaidLayout(emptyClient))))
        val pe = json.parseToJsonElement(StateProbes.targetProjectiles(StateProbes.readTargetProjectiles(emptyClient)))
        assertTrue("projectiles is a json array", pe is JsonArray)
        assertNotNull(json.parseToJsonElement(StateProbes.activePrayers(StateProbes.readActivePrayers(emptyClient))))
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dynamic-proxy Client helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Build a [Client] proxy that answers a fixed set of method calls. Method
     * names not in the script return Kotlin default values (0 / false / null / empty)
     * so probes can call any of [Client]'s 300+ methods without explicit setup.
     *
     * - `overrides` map: method-name → return value for zero-arg getters.
     * - `varbits` map: varbit ID → value, drives `getVarbitValue(int)`. Default 0.
     * - `activePrayers` set drives `isPrayerActive(Prayer)`.
     * - `boostedSkills` / `realSkills` drive the per-Skill skill-level getters.
     */
    private fun fakeClient(
        overrides: Map<String, Any?> = emptyMap(),
        varbits: Map<Int, Int> = emptyMap(),
        activePrayers: Set<Prayer> = emptySet(),
        boostedSkills: Map<Skill, Int> = emptyMap(),
        realSkills: Map<Skill, Int> = emptyMap(),
    ): Client {
        return Proxy.newProxyInstance(
            Client::class.java.classLoader,
            arrayOf(Client::class.java),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "FakeClient"
                    "getVarbitValue" -> varbits[args?.firstOrNull() as? Int ?: -1] ?: 0
                    "isPrayerActive" -> args?.firstOrNull() in activePrayers
                    "getBoostedSkillLevel" -> boostedSkills[args?.firstOrNull() as? Skill] ?: 0
                    "getRealSkillLevel" -> realSkills[args?.firstOrNull() as? Skill] ?: 0
                    in overrides -> overrides[method.name]
                    else -> defaultForReturn(method)
                }
            },
        ) as Client
    }

    /**
     * Default for an unstubbed method: 0 for numeric primitives, false for
     * boolean, null for objects, empty for arrays. Keeps tests from blowing up
     * when the probe touches an incidental getter (e.g. `getMapRegions`).
     */
    private fun defaultForReturn(method: Method): Any? {
        val r = method.returnType
        return when {
            r == java.lang.Boolean.TYPE -> false
            r == java.lang.Void.TYPE -> null
            r.isPrimitive -> 0
            r.isArray -> java.lang.reflect.Array.newInstance(r.componentType, 0)
            else -> null
        }
    }

    private fun fakePlayer(name: String, interacting: Actor? = null): Player {
        val byName = mutableMapOf<String, Any?>("getName" to name, "getInteracting" to interacting)
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "FakePlayer($name)"
                    else -> byName[method.name] ?: defaultForReturn(method)
                }
            },
        ) as Player
    }

    private fun fakeNpc(name: String): NPC = Proxy.newProxyInstance(
        NPC::class.java.classLoader,
        arrayOf(NPC::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeNpc($name)"
                "getName" -> name
                else -> defaultForReturn(method)
            }
        },
    ) as NPC

    /** Wrap a list of projectiles in the RuneLite `Deque` interface. */
    private fun fakeProjectileDeque(items: List<Projectile>): RlDeque<Projectile> =
        object : RlDeque<Projectile> {
            override fun addLast(t: Projectile) = error("read-only test deque")
            override fun clear() = error("read-only test deque")
            override fun iterator(): MutableIterator<Projectile> {
                val it = items.iterator()
                return object : MutableIterator<Projectile> {
                    override fun hasNext(): Boolean = it.hasNext()
                    override fun next(): Projectile = it.next()
                    override fun remove() = error("read-only test deque")
                }
            }
        }

    private fun fakeProjectile(
        id: Int,
        remainingCycles: Int,
        target: Actor?,
        sourceActor: Actor?,
    ): Projectile = Proxy.newProxyInstance(
        Projectile::class.java.classLoader,
        arrayOf(Projectile::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeProjectile(id=$id)"
                "getId" -> id
                "getRemainingCycles" -> remainingCycles
                "getInteracting" -> target
                "getSourceActor" -> sourceActor
                else -> defaultForReturn(method)
            }
        },
    ) as Projectile
}
