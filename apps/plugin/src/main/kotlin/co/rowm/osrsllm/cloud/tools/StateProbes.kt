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
 *  - `get_farming_state` — catalog §3 (4/5). Flagged at ~600 tokens; the
 *    RuneLite `timetracking.farming` package is package-private (we can't
 *    reach `FarmingTracker` / `FarmingPatch` directly), so a fair-quality
 *    farming probe needs us to mirror the patch→varbit table ourselves. Too
 *    big to land safely in this loop alongside the other four. Tracked in
 *    OPEN_QUESTIONS / next loop pickup.
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
