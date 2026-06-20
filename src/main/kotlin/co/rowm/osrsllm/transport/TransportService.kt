package co.rowm.osrsllm.transport

import co.rowm.osrsllm.GameSnapshot
import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.items.ItemNameResolver
import co.rowm.osrsllm.pathfinder.PathfinderService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/** An item the player should use to take this transport. */
@Serializable
data class ItemUse(
    val id: Int,
    val name: String,
    /** "inventory" | "equipment" | "bank" — where the player currently has it. */
    val location: String,
    val count: Int = 1,
)

/**
 * A requirement entry — used for both `missing` (unmet) and `met` (verbose-only,
 * satisfied) lists. Discriminated by `type`:
 *  - "skill" → skill, needed, have
 *  - "quest" → quest
 *  - "item"  → item (human-readable, OR-joined alternatives). For met item reqs the
 *               same info is also in `use` with the held item + location.
 */
@Serializable
data class Requirement(
    val type: String,
    val skill: String? = null,
    val needed: Int? = null,
    val have: Int? = null,
    val quest: String? = null,
    val item: String? = null,
)

@Serializable
data class TransportSearchHit(
    val id: String,
    val category: String,
    val name: String,
    val destinationX: Int,
    val destinationY: Int,
    val plane: Int,
    val durationTicks: Int,
    val consumable: Boolean,
    val wildernessLimit: Int? = null,
    /**
     * Rough ticks to RUN from the player's current position straight to the destination
     * (Chebyshev distance / 2, rounded up — assumes energy + ignores obstacles). Compare
     * vs `durationTicks` to decide if walking is faster than teleporting. Null when the
     * player isn't logged in OR the destination is on a different plane.
     */
    val runTicksFromPlayer: Int? = null,
    /** Chebyshev tile distance from player; useful as a sanity check on runTicks. */
    val tileDistanceFromPlayer: Int? = null,
    /**
     * "ready"     — every req met AND all items on person (use immediately)
     * "bank-only" — at least one item only in bank (withdraw first)
     * "locked"    — at least one req unmet; only returned with includeUnavailable=true
     */
    val readiness: String,
    /** Items the player should use. Empty for transports without item requirements. */
    val use: List<ItemUse> = emptyList(),
    /** Unmet requirements. Always populated when readiness=='locked'; empty otherwise. */
    val missing: List<Requirement> = emptyList(),
    /** Satisfied requirements — only populated when `verbose=true` on the request. */
    val met: List<Requirement> = emptyList(),
)

@Serializable
data class TransportSearchResponse(
    val query: String?,
    val totalRecords: Int,
    val matched: Int,
    val results: List<TransportSearchHit>,
    val note: String? = null,
)

@Singleton
class TransportService @Inject constructor(
    private val gameStateStore: GameStateStore,
    private val itemManager: ItemManager,
    private val itemNameResolver: ItemNameResolver,
    private val pathfinder: PathfinderService,
) {

    private val log = LoggerFactory.getLogger(TransportService::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val records: List<TransportRecord> = loadDb()

    private companion object {
        const val READY = "ready"
        const val BANK_ONLY = "bank-only"
        const val LOCKED = "locked"
    }

    init {
        log.info("Loaded {} transport records", records.size)
    }

    /** Substring match on transport name. Returns distinct destination coords. */
    fun searchDestinations(query: String, limit: Int = 10): List<TransportRecord> {
        val q = query.trim().lowercase().takeIf { it.isNotBlank() } ?: return emptyList()
        return records.asSequence()
            .filter { it.name.lowercase().contains(q) }
            .distinctBy { "${it.destination.x}:${it.destination.y}:${it.destination.plane}" }
            .take(limit)
            .toList()
    }

    fun search(
        query: String?,
        includeUnavailable: Boolean = false,
        limit: Int = 20,
        verbose: Boolean = false,
    ): TransportSearchResponse {
        val snap = gameStateStore.snapshot()
        val inventoryCounts = inventoryCounts(snap)
        val equipmentCounts = equipmentCounts(snap)
        val bank = bankCounts(snap)
        val q = query?.trim()?.takeIf { it.isNotBlank() }?.lowercase()

        // Filter records first so we can prime the item-name cache with just the IDs
        // we'll actually look up (one client-thread round trip per search, not per item).
        val candidates = records.filter { rec -> q == null || rec.name.lowercase().contains(q) }
        val itemIds = candidates.asSequence()
            .flatMap { it.itemReqs.asSequence().flatten().map(ItemNeed::itemId) }
            .toSet()
        if (itemIds.isNotEmpty()) itemNameResolver.resolveAll(itemIds)

        val withAvailability: List<Pair<TransportRecord, Availability>> = candidates
            .map { rec -> rec to evaluate(rec, snap, inventoryCounts, equipmentCounts, bank) }

        val filtered = withAvailability
            .filter { (_, av) -> includeUnavailable || av.available }

        // Sort: ready first, then bank-only, then locked; within each tier by duration.
        val readinessRank = mapOf(READY to 0, BANK_ONLY to 1, LOCKED to 2)
        val ranked = filtered.sortedWith(
            compareBy(
                { readinessRank[it.second.readiness] ?: 99 },
                { it.first.durationTicks },
                { it.second.missing.size },
                { it.first.name },
            ),
        )

        val results = ranked.take(limit).map { (rec, av) ->
            val walkTiles = bfsWalkTiles(snap, rec.destination)
            val chebyDist = tileDistance(snap, rec.destination)
            // Prefer real BFS distance; fall back to Chebyshev when BFS gives up
            // (target unreachable / too far / different plane).
            val tileDist = walkTiles ?: chebyDist
            TransportSearchHit(
                id = rec.id,
                category = rec.category,
                name = rec.name,
                destinationX = rec.destination.x,
                destinationY = rec.destination.y,
                plane = rec.destination.plane,
                durationTicks = rec.durationTicks,
                consumable = rec.consumable,
                wildernessLimit = rec.wildernessLimit,
                runTicksFromPlayer = tileDist?.let { (it + 1) / 2 },
                tileDistanceFromPlayer = tileDist,
                readiness = av.readiness,
                use = av.use,
                missing = av.missing,
                met = if (verbose) av.met else emptyList(),
            )
        }

        val note = when {
            q != null && filtered.isEmpty() ->
                "No transports match '$q'. Try a different keyword (e.g. 'Edgeville', 'Karamja', 'fairy ring') or set includeUnavailable=true."
            !includeUnavailable && ranked.isNotEmpty() ->
                "Only usable options shown (readiness ∈ {ready, bank-only}). Set includeUnavailable=true to also see locked transports."
            else -> null
        }

        return TransportSearchResponse(
            query = query,
            totalRecords = records.size,
            matched = filtered.size,
            results = results,
            note = note,
        )
    }

    private data class Availability(
        val readiness: String,
        val use: List<ItemUse>,
        val missing: List<Requirement>,
        val met: List<Requirement>,
    ) {
        val available: Boolean get() = readiness != LOCKED
    }

    private fun evaluate(
        rec: TransportRecord,
        snap: GameSnapshot,
        onPersonInventory: Map<Int, Int>,
        equipment: Map<Int, Int>,
        bank: Map<Int, Int>,
    ): Availability {
        val missing = mutableListOf<Requirement>()
        val met = mutableListOf<Requirement>()
        val use = mutableListOf<ItemUse>()
        var anyBankOnly = false

        for ((skillName, level) in rec.skillReqs) {
            val playerLevel = snap.skills.firstOrNull { it.skill.equals(skillName, ignoreCase = true) }
                ?.level ?: 0
            val req = Requirement(type = "skill", skill = skillName, needed = level, have = playerLevel)
            if (playerLevel < level) missing += req else met += req
        }

        for (questName in rec.questReqs) {
            val finished = snap.quests.any {
                it.name.equals(questName, ignoreCase = true) && it.state == "FINISHED"
            }
            val req = Requirement(type = "quest", quest = questName)
            if (finished) met += req else missing += req
        }

        // Item requirements — ANDed groups, each = OR alternatives.
        for (group in rec.itemReqs) {
            val invHit = group.firstOrNull { (onPersonInventory[it.itemId] ?: 0) >= it.count }
            val eqpHit = invHit ?: group.firstOrNull { (equipment[it.itemId] ?: 0) >= it.count }
            val bankHit = eqpHit ?: group.firstOrNull {
                ((onPersonInventory[it.itemId] ?: 0) + (equipment[it.itemId] ?: 0) + (bank[it.itemId] ?: 0)) >= it.count
            }
            val human = group.joinToString(" or ") { describeItem(it) }
            when {
                invHit != null -> {
                    use += ItemUse(invHit.itemId, itemName(invHit.itemId), "inventory", invHit.count)
                    met += Requirement(type = "item", item = human)
                }
                eqpHit != null -> {
                    use += ItemUse(eqpHit.itemId, itemName(eqpHit.itemId), "equipment", eqpHit.count)
                    met += Requirement(type = "item", item = human)
                }
                bankHit != null -> {
                    anyBankOnly = true
                    use += ItemUse(bankHit.itemId, itemName(bankHit.itemId), "bank", bankHit.count)
                    met += Requirement(type = "item", item = human)
                }
                else -> missing += Requirement(type = "item", item = human)
            }
        }

        val readiness = when {
            missing.isNotEmpty() -> LOCKED
            anyBankOnly -> BANK_ONLY
            else -> READY
        }
        return Availability(
            readiness = readiness,
            use = if (readiness == LOCKED) emptyList() else use,
            missing = missing,
            met = met,
        )
    }

    private fun itemName(id: Int): String = itemNameResolver.resolve(id)

    private fun describeItem(need: ItemNeed): String {
        val name = itemName(need.itemId)
        return if (need.count > 1) "$name × ${need.count}" else name
    }

    /**
     * Real walkable tile-step distance via [PathfinderService] (BFS over collision
     * map). Respects walls, water, corner-clips. Returns null when the BFS budget is
     * exhausted (target unreachable or too far) or the planes differ — callers fall
     * back to [tileDistance] (Chebyshev) for a cruder estimate.
     */
    private fun bfsWalkTiles(snap: GameSnapshot, dest: WorldPointXYZ): Int? {
        val px = snap.player?.locationX ?: return null
        val py = snap.player?.locationY ?: return null
        val pz = snap.player.plane ?: 0
        return pathfinder.walkTiles(px, py, pz, dest.x, dest.y, dest.plane)
    }

    /**
     * Chebyshev (king-move) tile distance from the player to a destination, or null if
     * the player isn't logged in or the destination is on a different plane (no
     * useful walk path across planes without stairs/ladders).
     */
    private fun tileDistance(snap: GameSnapshot, dest: WorldPointXYZ): Int? {
        val px = snap.player?.locationX ?: return null
        val py = snap.player?.locationY ?: return null
        val pz = snap.player.plane ?: 0
        if (dest.plane != pz) return null
        val dx = kotlin.math.abs(dest.x - px)
        val dy = kotlin.math.abs(dest.y - py)
        return kotlin.math.max(dx, dy)
    }

    private fun inventoryCounts(snap: GameSnapshot): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for (item in snap.inventory.items) map.merge(item.id, item.quantity) { a, b -> a + b }
        return map
    }

    private fun equipmentCounts(snap: GameSnapshot): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for (slot in snap.equipment) {
            val item = slot.item ?: continue
            map.merge(item.id, item.quantity) { a, b -> a + b }
        }
        return map
    }

    private fun bankCounts(snap: GameSnapshot): Map<Int, Int> =
        snap.bank.items.associate { it.id to it.quantity }

    private fun loadDb(): List<TransportRecord> {
        val stream = javaClass.classLoader.getResourceAsStream("transports.json")
            ?: run {
                log.warn("transports.json missing from classpath — transport tool will return empty")
                return emptyList()
            }
        val all = try {
            val text = stream.bufferedReader().use { it.readText() }
            json.decodeFromString(TransportDb.serializer(), text).transports
        } catch (t: Throwable) {
            log.error("Failed to parse transports.json", t)
            return emptyList()
        }
        return dedupe(all)
    }

    /**
     * Strip records that are indistinguishable from the agent's perspective: same name,
     * destination, category, and requirements. The import inherited multiple rows for
     * the same logical transport (boats with multi-tile clickboxes, POH portals imported
     * 3×, etc.) — they only differ by `origin`, which we never expose to the agent.
     *
     * For 1311 raw records we typically collapse to ~770, a 40% reduction in tool output
     * size and a big cleanup of repeated entries the agent had no way to disambiguate.
     */
    private fun dedupe(all: List<TransportRecord>): List<TransportRecord> {
        val seen = HashSet<String>(all.size)
        val out = ArrayList<TransportRecord>(all.size)
        for (r in all) {
            val key = buildString {
                append(r.name).append('|')
                append(r.category).append('|')
                append(r.destination.x).append(',').append(r.destination.y).append(',').append(r.destination.plane).append('|')
                append(r.durationTicks).append('|')
                append(r.consumable).append('|')
                append(r.wildernessLimit).append('|')
                // Order-sensitive but consistent — itemReqs come from the same source so
                // duplicates will have the same list order.
                append(r.itemReqs.joinToString(";") { grp ->
                    grp.joinToString(",") { "${it.itemId}x${it.count}" }
                }).append('|')
                append(r.skillReqs.entries.sortedBy { it.key }
                    .joinToString(",") { "${it.key}:${it.value}" }).append('|')
                append(r.questReqs.sorted().joinToString(","))
            }
            if (seen.add(key)) out += r
        }
        val removed = all.size - out.size
        if (removed > 0) {
            log.info("Transport DB: deduped {} duplicate records ({} → {})",
                removed, all.size, out.size)
        }
        return out
    }
}
