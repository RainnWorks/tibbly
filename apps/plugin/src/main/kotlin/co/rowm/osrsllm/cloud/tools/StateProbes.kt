package co.rowm.osrsllm.cloud.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.runelite.api.Actor
import net.runelite.api.Client
import net.runelite.api.Prayer
import net.runelite.api.Skill
import net.runelite.api.Varbits
import net.runelite.client.callback.ClientThread
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * RAI-5 Tier 0 state-probe handlers — the four "top unblockers" identified in
 * docs/research/runelite-api/catalog.md §3. Each handler is a pure
 * `Client` → JSON string projection so it can be:
 *
 *  - registered as an MCP tool from `local/McpServerService` (the developer-mode
 *    debug surface), and
 *  - reused by a future cloud→local tool dispatcher (today's [StubToolDispatcher]
 *    placeholder) without re-implementing the read.
 *
 * Read-only. No mouse / keyboard / menu-option synthesis (see catalog §1 hard
 * non-goals). Every probe runs on the client thread when it touches live
 * `Client` state — the [onClientThread] helper marshals + times out.
 *
 * # What ships in this loop
 *
 * | Tool | Family | Why |
 * |---|---|---|
 * | `get_account_identity` | CORE | First-turn anchor; HCIM advice ≠ main advice. |
 * | `get_raid_layout` | RAIDS | Marketing-grade CoX/ToB/ToA phase + scaling read. |
 * | `get_target_projectiles` | COMBAT | The "prayer flick" feature in 8 lines. |
 * | `get_active_prayers` | COMBAT | "Did I bring the right prayer" signal. |
 *
 * # Out of scope this loop (queued for next)
 *
 *  - `get_farming_state` (catalog §3 4/5) **shipped as a 2-tool split** —
 *    see [farmingSummary] + [farmingPatches]. The upstream package
 *    (`net.runelite.client.plugins.timetracking.farming`) is package-private,
 *    so we mirror the slice of its patch→varbit table that v1 needs in
 *    [FarmingTables].
 */
object StateProbes {

    /** Single shared compact-JSON serializer — same shape `WikiService.outputJson` uses. */
    val json: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    /** Hard upper bound on per-probe client-thread wait. Keeps a stuck client thread from blocking the WS read loop. */
    private const val CLIENT_THREAD_TIMEOUT_MS = 2000L

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (1/5) — get_account_identity
    // ─────────────────────────────────────────────────────────────────────

    @Serializable
    data class AccountIdentity(
        val name: String?,
        val accountType: String?,
        val isIronman: Boolean,
        val isGroupIronman: Boolean,
        val world: Int,
        val worldFlags: List<String>,
        val worldHost: String?,
        val launcherName: String?,
    )

    fun accountIdentity(client: Client, clientThread: ClientThread): String =
        accountIdentity(onClientThread(clientThread) { readAccountIdentity(client) })

    /** Pure projection — broken out for testability without [ClientThread] orchestration. */
    fun readAccountIdentity(client: Client): AccountIdentity {
        val acct = runCatching { client.accountType }.getOrNull()
        val worldType = runCatching { client.worldType }.getOrNull()
        return AccountIdentity(
            name = runCatching { client.localPlayer?.name }.getOrNull(),
            accountType = acct?.name,
            isIronman = acct?.isIronman == true,
            isGroupIronman = acct?.isGroupIronman == true,
            world = runCatching { client.world }.getOrDefault(0),
            worldFlags = worldType?.map { it.name }?.sorted().orEmpty(),
            worldHost = runCatching { client.worldHost }.getOrNull(),
            launcherName = runCatching { client.launcherDisplayName }.getOrNull(),
        )
    }

    fun accountIdentity(value: AccountIdentity): String =
        json.encodeToString(AccountIdentity.serializer(), value)

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (2/5) — get_raid_layout
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Region IDs for the three raid lobbies, used to disambiguate when more
     * than one of the raid-state varbits is set (rare but possible in
     * transition rooms). Sourced from runelite-api Varbits.java JavaDoc +
     * the community-known map regions:
     *
     *  - CoX:  13386 (Mount Quidamortem lobby) / 12889 / 13136 / 13140 etc
     *  - ToB:  12867 (Verzik lobby) / 13123 / 14642
     *  - ToA:  14160 (Tombs lobby) / 14672 / 15186 / 15184 / 15188
     *
     * We accept "if the relevant raid varbit is non-zero, you're in that raid".
     */
    enum class ActiveRaid { NONE, COX, TOB, TOA }

    @Serializable
    data class RaidLayout(
        val activeRaid: String,
        val mapRegions: List<Int>,
        // CoX
        val coxInRaid: Int,
        val coxState: Int,
        val coxTotalPoints: Int,
        // ToB
        val tobState: Int,
        val tobPartyOrbs: List<Int>,
        // ToA
        val toaInvocation: Int,
        val toaPartyDamage: Int,
        val toaPartyHp: List<Int>,
    )

    fun raidLayout(client: Client, clientThread: ClientThread): String =
        raidLayout(onClientThread(clientThread) { readRaidLayout(client) })

    fun readRaidLayout(client: Client): RaidLayout {
        fun vb(id: Int): Int = runCatching { client.getVarbitValue(id) }.getOrDefault(0)
        val coxIn = vb(Varbits.IN_RAID)
        val tobState = vb(Varbits.THEATRE_OF_BLOOD)
        val toaInv = vb(Varbits.TOA_RAID_LEVEL)
        val active = when {
            coxIn > 0 -> ActiveRaid.COX
            tobState > 0 -> ActiveRaid.TOB
            toaInv > 0 -> ActiveRaid.TOA
            else -> ActiveRaid.NONE
        }
        return RaidLayout(
            activeRaid = active.name,
            mapRegions = runCatching { client.mapRegions?.toList() }.getOrNull().orEmpty(),
            coxInRaid = coxIn,
            coxState = vb(Varbits.RAID_STATE),
            coxTotalPoints = vb(Varbits.TOTAL_POINTS),
            tobState = tobState,
            tobPartyOrbs = listOf(
                vb(Varbits.THEATRE_OF_BLOOD_ORB1),
                vb(Varbits.THEATRE_OF_BLOOD_ORB2),
                vb(Varbits.THEATRE_OF_BLOOD_ORB3),
                vb(Varbits.THEATRE_OF_BLOOD_ORB4),
                vb(Varbits.THEATRE_OF_BLOOD_ORB5),
            ),
            toaInvocation = toaInv,
            toaPartyDamage = vb(Varbits.TOA_RAID_DAMAGE),
            toaPartyHp = listOf(
                vb(Varbits.TOA_MEMBER_0_HEALTH),
                vb(Varbits.TOA_MEMBER_1_HEALTH),
                vb(Varbits.TOA_MEMBER_2_HEALTH),
                vb(Varbits.TOA_MEMBER_3_HEALTH),
                vb(Varbits.TOA_MEMBER_4_HEALTH),
                vb(Varbits.TOA_MEMBER_5_HEALTH),
                vb(Varbits.TOA_MEMBER_6_HEALTH),
                vb(Varbits.TOA_MEMBER_7_HEALTH),
            ),
        )
    }

    fun raidLayout(value: RaidLayout): String =
        json.encodeToString(RaidLayout.serializer(), value)

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (3/5) — get_target_projectiles
    // ─────────────────────────────────────────────────────────────────────

    @Serializable
    data class ProjectileInfo(
        val id: Int,
        val remainingTicks: Int,
        /** Convert client cycles → game ticks (~30 client cycles per 600ms tick). */
        val targetIsLocalPlayer: Boolean,
        val targetName: String?,
        val sourceName: String?,
    )

    /**
     * Per-projectile cap to stay under the catalog's 200-token soft budget.
     * 8 entries × ~25 tokens ≈ 200; cluster boss fights can spawn more but
     * the user typically only needs the next few.
     */
    private const val PROJECTILE_CAP = 8

    fun targetProjectiles(client: Client, clientThread: ClientThread): String =
        targetProjectiles(onClientThread(clientThread) { readTargetProjectiles(client) })

    fun readTargetProjectiles(client: Client): List<ProjectileInfo> {
        val local: Actor? = runCatching { client.localPlayer }.getOrNull()
        val all = runCatching { client.projectiles }.getOrNull() ?: return emptyList()
        // Filter to projectiles aimed at the player or at the player's current target.
        // We deliberately don't ship every flying graphic in the scene — too noisy.
        val target = runCatching { local?.interacting }.getOrNull()
        return all.asSequence()
            .filter { p ->
                val pTarget = runCatching { p.interacting }.getOrNull()
                pTarget == local || (target != null && pTarget == target)
            }
            .take(PROJECTILE_CAP)
            .map { p ->
                val pTarget = runCatching { p.interacting }.getOrNull()
                val src = runCatching { p.sourceActor }.getOrNull()
                ProjectileInfo(
                    id = runCatching { p.id }.getOrDefault(-1),
                    remainingTicks = runCatching { p.remainingCycles / 30 }.getOrDefault(0),
                    targetIsLocalPlayer = pTarget == local,
                    targetName = pTarget?.name,
                    sourceName = src?.name,
                )
            }
            .toList()
    }

    fun targetProjectiles(value: List<ProjectileInfo>): String =
        json.encodeToString(ListSerializer(ProjectileInfo.serializer()), value)

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (5/5) — get_active_prayers
    // ─────────────────────────────────────────────────────────────────────

    @Serializable
    data class ActivePrayers(
        val active: List<String>,
        val ruinous: List<String>,
        val prayerPoints: Int,
        val prayerMax: Int,
        val quickPrayer: Boolean,
    )

    fun activePrayers(client: Client, clientThread: ClientThread): String =
        activePrayers(onClientThread(clientThread) { readActivePrayers(client) })

    fun readActivePrayers(client: Client): ActivePrayers {
        val standard = mutableListOf<String>()
        val ruinous = mutableListOf<String>()
        for (prayer in Prayer.values()) {
            val isActive = runCatching { client.isPrayerActive(prayer) }.getOrDefault(false)
            if (!isActive) continue
            val pretty = prettyPrayer(prayer)
            // Ruinous Powers prayers are prefixed `RP_` in the enum.
            if (prayer.name.startsWith("RP_")) ruinous.add(pretty) else standard.add(pretty)
        }
        return ActivePrayers(
            active = standard,
            ruinous = ruinous,
            prayerPoints = runCatching { client.getBoostedSkillLevel(Skill.PRAYER) }.getOrDefault(0),
            prayerMax = runCatching { client.getRealSkillLevel(Skill.PRAYER) }.getOrDefault(0),
            quickPrayer = runCatching { client.getVarbitValue(Varbits.QUICK_PRAYER) == 1 }.getOrDefault(false),
        )
    }

    fun activePrayers(value: ActivePrayers): String =
        json.encodeToString(ActivePrayers.serializer(), value)

    /** "PROTECT_FROM_MAGIC" → "Protect from magic"; strips the RP_ prefix on Ruinous prayers. */
    private fun prettyPrayer(p: Prayer): String {
        val raw = if (p.name.startsWith("RP_")) p.name.removePrefix("RP_") else p.name
        return raw.replace('_', ' ').lowercase()
            .replaceFirstChar { c -> c.uppercase() }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (4/5a) — get_farming_summary
    // ─────────────────────────────────────────────────────────────────────

    @Serializable
    data class FarmingSummary(
        /** Count of patches currently READY-to-harvest across all known regions. */
        val ready: Int,
        /** Count of patches actively GROWING (not yet harvestable). */
        val growing: Int,
        /** Count of DISEASED patches that need curing now. */
        val diseased: Int,
        /** Count of DEAD patches that need clearing. */
        val dead: Int,
        /** Count of EMPTY patches (raked but not planted). */
        val emptyPatches: Int,
        /** Patches we know about but can't classify (e.g. value=UNKNOWN bucket). */
        val unknown: Int,
        /** Total patch slots inspected. Equals the sum of the above. */
        val total: Int,
        /**
         * Caveat: live varbit reads only reflect the player's CURRENT region.
         * Patches in regions the player isn't standing in report their
         * last-seen value (usually 0/EMPTY) until the player visits.
         */
        val note: String = "Live varbit read — patches outside the current region read 0 until visited. See get_farming_patches for per-region detail.",
    )

    fun farmingSummary(client: Client, clientThread: ClientThread): String =
        farmingSummary(onClientThread(clientThread) { readFarmingSummary(client) })

    /** Aggregate every patch in every known region into a 5-bucket histogram. */
    fun readFarmingSummary(client: Client): FarmingSummary {
        var ready = 0; var growing = 0; var diseased = 0
        var dead = 0; var empty = 0; var unknown = 0
        for (region in FarmingTables.REGIONS) {
            for (patch in region.patches) {
                val raw = runCatching { client.getVarbitValue(patch.varbitId) }.getOrDefault(0)
                when (FarmingTables.decode(patch.type, raw)) {
                    FarmingTables.CropState.READY -> ready++
                    FarmingTables.CropState.GROWING -> growing++
                    FarmingTables.CropState.DISEASED -> diseased++
                    FarmingTables.CropState.DEAD -> dead++
                    FarmingTables.CropState.EMPTY -> empty++
                    FarmingTables.CropState.UNKNOWN -> unknown++
                }
            }
        }
        return FarmingSummary(
            ready = ready, growing = growing, diseased = diseased,
            dead = dead, emptyPatches = empty, unknown = unknown,
            total = ready + growing + diseased + dead + empty + unknown,
        )
    }

    fun farmingSummary(value: FarmingSummary): String =
        json.encodeToString(FarmingSummary.serializer(), value)

    // ─────────────────────────────────────────────────────────────────────
    // Tier 0 (4/5b) — get_farming_patches(region)
    // ─────────────────────────────────────────────────────────────────────

    @Serializable
    data class FarmingPatchEntry(
        val patchName: String,
        /** HERB / ALLOTMENT / FRUIT_TREE / TREE / BUSH / FLOWER. */
        val type: String,
        /** READY / GROWING / DISEASED / DEAD / EMPTY / UNKNOWN. */
        val state: String,
        /** Raw transmit-varbit value — useful for downstream wiki disambiguation. */
        val rawVarbit: Int,
    )

    @Serializable
    data class FarmingPatchesReport(
        val region: String,
        val patches: List<FarmingPatchEntry>,
    )

    @Serializable
    data class FarmingPatchesError(
        val error: String,
        val knownRegions: List<String>,
    )

    fun farmingPatches(client: Client, clientThread: ClientThread, region: String): String =
        onClientThread(clientThread) { farmingPatchesString(client, region) }

    /** Pure projection — broken out for testability. */
    fun farmingPatchesString(client: Client, region: String): String {
        val resolved = FarmingTables.resolveRegion(region)
            ?: return json.encodeToString(
                FarmingPatchesError.serializer(),
                FarmingPatchesError(
                    error = "Unknown region '$region'. See knownRegions for valid values.",
                    knownRegions = FarmingTables.knownRegionNames,
                ),
            )
        val entries = resolved.patches.map { patch ->
            val raw = runCatching { client.getVarbitValue(patch.varbitId) }.getOrDefault(0)
            FarmingPatchEntry(
                patchName = patch.patchName,
                type = patch.type.name,
                state = FarmingTables.decode(patch.type, raw).name,
                rawVarbit = raw,
            )
        }
        return json.encodeToString(
            FarmingPatchesReport.serializer(),
            FarmingPatchesReport(region = resolved.name, patches = entries),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Run [body] on the RuneLite client thread, wait up to [CLIENT_THREAD_TIMEOUT_MS]
     * for the result. Throws on timeout / exception — caller wraps that into an MCP
     * tool error.
     */
    private fun <T> onClientThread(clientThread: ClientThread, body: () -> T): T {
        val future = CompletableFuture<T>()
        clientThread.invoke(Runnable {
            try {
                future.complete(body())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })
        return future.get(CLIENT_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }
}
