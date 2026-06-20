package co.rowm.osrsllm.tools

import co.rowm.osrsllm.AttackStyleSnapshot
import co.rowm.osrsllm.BankSnapshot
import co.rowm.osrsllm.BuffsSnapshot
import co.rowm.osrsllm.CombatAchievementsSnapshot
import co.rowm.osrsllm.CombatInfoSnapshot
import co.rowm.osrsllm.ContainerSnapshot
import co.rowm.osrsllm.DiaryProgressSnapshot
import co.rowm.osrsllm.EquipmentSlotSnapshot
import co.rowm.osrsllm.GameSnapshot
import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.GeOfferSnapshot
import co.rowm.osrsllm.GroundItemSnapshot
import co.rowm.osrsllm.ItemSnapshot
import co.rowm.osrsllm.NearbyNpcSnapshot
import co.rowm.osrsllm.NearbyObjectSnapshot
import co.rowm.osrsllm.PlayerSnapshot
import co.rowm.osrsllm.PohSnapshot
import co.rowm.osrsllm.QuestSnapshot
import co.rowm.osrsllm.SkillSnapshot
import co.rowm.osrsllm.SlayerSnapshot
import co.rowm.osrsllm.SpellbookSnapshot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class OsrsTools(
    private val store: GameStateStore,
    private val tagIndex: co.rowm.osrsllm.bank.ItemTagIndex,
    private val statsResolver: co.rowm.osrsllm.items.ItemStatsResolver,
) {

    @Serializable
    data class BankItemRich(
        val id: Int,
        val name: String,
        val quantity: Int,
        /** Primary tags (curated short list per item, see ItemTagIndex.primaryTagsFor). */
        val tags: List<String> = emptyList(),
        /** Equipment stats — only present when caller asked for them (`includeStats=true`). */
        val stats: co.rowm.osrsllm.items.EquipmentStatsSnapshot? = null,
    )

    private fun enrich(items: List<ItemSnapshot>, includeStats: Boolean): List<BankItemRich> {
        if (items.isEmpty()) return emptyList()
        val statMap: Map<Int, co.rowm.osrsllm.items.EquipmentStatsSnapshot?> =
            if (includeStats) statsResolver.resolveAll(items.map { it.id }) else emptyMap()
        return items.map { item ->
            BankItemRich(
                id = item.id,
                name = item.name,
                quantity = item.quantity,
                tags = tagIndex.primaryTagsFor(item.name),
                stats = statMap[item.id],
            )
        }
    }

    // ---------- Tools ----------

    fun inventory(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        return ok(ContainerSnapshot.serializer(), snap.inventory, snap)
    }

    fun bank(query: String?, limit: Int, includeStats: Boolean = false): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val bank = snap.bank
        if (bank.lastSeenAt == null) {
            return notReady(
                "bank-never-seen",
                "We have no record of the player's bank — they haven't opened it on this " +
                    "account before. Ask the player to open the bank once, then retry. " +
                    "After that, the snapshot persists across sessions.",
                snap,
                lastSeenAt = null,
            )
        }
        val ageMs = System.currentTimeMillis() - bank.lastSeenAt
        val freshnessNote = stalenessNote(ageMs)

        if (query.isNullOrBlank()) {
            val top = bank.items.sortedByDescending { it.quantity.toLong() }.take(limit)
            val summary = BankSummary(
                lastSeenAt = bank.lastSeenAt,
                ageSeconds = ageMs / 1000,
                stalenessNote = freshnessNote,
                totalUnique = bank.items.size,
                totalQuantity = bank.totalQuantity,
                topByQuantity = enrich(top, includeStats),
                hint = "Default response is a summary + top $limit items by quantity. " +
                    "Pass `query` for substring search, `categories` for tag filter, or `includeStats=true` for combat bonuses.",
            )
            return ok(BankSummary.serializer(), summary, snap, lastSeenAt = bank.lastSeenAt)
        }
        val q = query.trim().lowercase()
        val matches = bank.items.asSequence()
            .filter { it.name.lowercase().contains(q) }
            .sortedByDescending { it.quantity.toLong() }
            .take(limit)
            .toList()
        val result = BankSearchResult(
            query = query,
            lastSeenAt = bank.lastSeenAt,
            ageSeconds = ageMs / 1000,
            stalenessNote = freshnessNote,
            matchCount = matches.size,
            items = enrich(matches, includeStats),
        )
        return ok(BankSearchResult.serializer(), result, snap, lastSeenAt = bank.lastSeenAt)
    }

    fun banks(queries: List<String>, limit: Int, includeStats: Boolean = false): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val bank = snap.bank
        if (bank.lastSeenAt == null) {
            return notReady(
                "bank-never-seen",
                "We have no record of the player's bank — they haven't opened it on this " +
                    "account before. Ask the player to open the bank once, then retry.",
                snap,
                lastSeenAt = null,
            )
        }
        val ageMs = System.currentTimeMillis() - bank.lastSeenAt
        val cleaned = queries.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val results = cleaned.map { q ->
            val needle = q.lowercase()
            val matches = bank.items.asSequence()
                .filter { it.name.lowercase().contains(needle) }
                .sortedByDescending { it.quantity.toLong() }
                .take(limit)
                .toList()
            BankQueryResult(query = q, matchCount = matches.size, items = enrich(matches, includeStats))
        }
        val combined = BankMultiSearchResult(
            lastSeenAt = bank.lastSeenAt,
            ageSeconds = ageMs / 1000,
            stalenessNote = stalenessNote(ageMs),
            results = results,
        )
        return ok(BankMultiSearchResult.serializer(), combined, snap, lastSeenAt = bank.lastSeenAt)
    }

    /**
     * Filter the bank by wiki-sourced item tags. Tags are scraped from
     * `Category:Items` subcategories (see scripts/build_item_categories.py).
     * Examples: "food", "potions", "runes", "ammunition", "teleportation_items",
     * "capes", "boots", "amulets", "items_with_charges". Call `list_item_tags`
     * for the full taxonomy + counts.
     *
     * Items match every tag they carry — Saradomin brew(4) shows up under both
     * `food` AND `potions`. Multiple [categories] values OR together.
     */
    fun banksByCategory(categories: List<String>, limit: Int, includeStats: Boolean = false): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val bank = snap.bank
        if (bank.lastSeenAt == null) {
            return notReady(
                "bank-never-seen",
                "We have no record of the player's bank — ask the player to open the bank once, then retry.",
                snap,
                lastSeenAt = null,
            )
        }
        val ageMs = System.currentTimeMillis() - bank.lastSeenAt
        val cleaned = categories.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val results = cleaned.map { cat ->
            val matched = tagIndex.filter(bank.items, cat)
                .sortedByDescending { it.quantity.toLong() }
                .take(limit)
            BankQueryResult(query = cat, matchCount = matched.size, items = enrich(matched, includeStats))
        }
        val combined = BankMultiSearchResult(
            lastSeenAt = bank.lastSeenAt,
            ageSeconds = ageMs / 1000,
            stalenessNote = stalenessNote(ageMs),
            results = results,
        )
        return ok(BankMultiSearchResult.serializer(), combined, snap, lastSeenAt = bank.lastSeenAt)
    }

    /**
     * Rank bank items in [slot] by a caller-supplied [weights] map.
     *
     * Stat paths the caller can weight:
     *   "str", "rstr", "mdmg", "prayer", "aspeed", "weight"
     *   "atk.stab", "atk.slash", "atk.crush", "atk.magic", "atk.ranged"
     *   "def.stab", "def.slash", "def.crush", "def.magic", "def.ranged"
     *
     * Score per item = Σ (stat_value × weight). Negative weights are allowed
     * (e.g. penalise weight). Unknown paths silently contribute 0. Items
     * without equipment stats (food, runes, etc.) are excluded.
     *
     * This is intentionally low-level — "BiS" is contextual (max str for
     * slayer ≠ max mdmg for raids ≠ max prayer for nightmare ≠ max defence
     * for tanking) so we let the AGENT express the objective via weights
     * instead of baking in opinionated formulas.
     */
    fun bestInSlotFromBank(slot: String, weights: Map<String, Double>, top: Int): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val bank = snap.bank
        if (bank.lastSeenAt == null) {
            return notReady("bank-never-seen",
                "Bank not seen yet on this account — ask the player to open the bank once, then retry.",
                snap, lastSeenAt = null)
        }
        val slotKey = slot.lowercase()
        val statMap = statsResolver.resolveAll(bank.items.map { it.id })

        data class Scored(
            val item: ItemSnapshot,
            val stats: co.rowm.osrsllm.items.EquipmentStatsSnapshot,
            val score: Double,
        )

        val ranked = bank.items.asSequence()
            .mapNotNull { it: ItemSnapshot ->
                val s = statMap[it.id] ?: return@mapNotNull null
                if (s.slot != slotKey) return@mapNotNull null
                Scored(it, s, weightedScore(s, weights))
            }
            .sortedByDescending { it.score }
            .take(top)
            .toList()

        val sb = StringBuilder()
        sb.append("{\"slot\":\"").append(slotKey).append("\"")
        sb.append(",\"weights\":{")
        weights.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(k).append("\":").append(v)
        }
        sb.append("},\"results\":[")
        ranked.forEachIndexed { i, r ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":").append(r.item.id)
            sb.append(",\"name\":\"").append(r.item.name.replace("\"", "\\\"")).append('"')
            sb.append(",\"qty\":").append(r.item.quantity)
            sb.append(",\"score\":").append(String.format("%.2f", r.score))
            sb.append(",\"stats\":")
            sb.append(co.rowm.osrsllm.wiki.WikiService.outputJson.encodeToString(
                co.rowm.osrsllm.items.EquipmentStatsSnapshot.serializer(), r.stats))
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun weightedScore(s: co.rowm.osrsllm.items.EquipmentStatsSnapshot,
                              weights: Map<String, Double>): Double {
        var score = 0.0
        for ((path, w) in weights) {
            val value: Double = when (path) {
                "str"        -> s.str.toDouble()
                "rstr"       -> s.rstr.toDouble()
                "mdmg"       -> s.mdmg.toDouble()
                "prayer"     -> s.prayer.toDouble()
                "aspeed"     -> s.aspeed.toDouble()
                "weight"     -> s.weight.toDouble()
                "atk.stab"   -> s.atk.stab.toDouble()
                "atk.slash"  -> s.atk.slash.toDouble()
                "atk.crush"  -> s.atk.crush.toDouble()
                "atk.magic"  -> s.atk.magic.toDouble()
                "atk.ranged" -> s.atk.ranged.toDouble()
                "def.stab"   -> s.def.stab.toDouble()
                "def.slash"  -> s.def.slash.toDouble()
                "def.crush"  -> s.def.crush.toDouble()
                "def.magic"  -> s.def.magic.toDouble()
                "def.ranged" -> s.def.ranged.toDouble()
                else         -> 0.0  // unknown path
            }
            score += value * w
        }
        return score
    }

    /**
     * Discoverability tool: list every tag with its global item count so the agent
     * can pick the right one for queries beyond the obvious (e.g. `warm_clothing`,
     * `temporary_skill_boost`, `recipes_that_require_a_tool`).
     */
    fun listTags(): String {
        val counts = tagIndex.allTagsWithCounts()
        val sb = StringBuilder()
        sb.append("{\"primaryTags\":[")
        co.rowm.osrsllm.bank.ItemTagIndex.PRIMARY_TAGS.forEachIndexed { i, t ->
            if (i > 0) sb.append(',')
            sb.append('"').append(t).append('"')
        }
        sb.append("],\"allTags\":{")
        counts.entries.forEachIndexed { i, (tag, count) ->
            if (i > 0) sb.append(',')
            sb.append('"').append(tag).append("\":").append(count)
        }
        sb.append("}}")
        return sb.toString()
    }

    fun equipment(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        return ok(ListSerializer(EquipmentSlotSnapshot.serializer()), snap.equipment, snap)
    }

    fun stats(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        if (snap.skills.isEmpty()) {
            return notReady(
                "skills-not-loaded",
                "No skill data yet — the game hasn't pushed stat updates since login.",
                snap,
            )
        }
        return ok(ListSerializer(SkillSnapshot.serializer()), snap.skills, snap)
    }

    fun player(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val player = snap.player ?: return notReady(
            "player-not-loaded",
            "Logged in but local player object isn't available yet (still loading the scene).",
            snap,
        )
        return ok(PlayerSnapshot.serializer(), player, snap)
    }

    fun quests(filter: QuestFilter): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        if (snap.quests.isEmpty() || snap.quests.all { it.state == "UNKNOWN" }) {
            return notReady(
                "quests-not-loaded",
                "Quest varbits aren't populated yet — wait one tick after login and retry.",
                snap,
            )
        }
        val out = when (filter) {
            QuestFilter.ALL -> snap.quests
            QuestFilter.IN_PROGRESS -> snap.quests.filter { it.state == "IN_PROGRESS" }
            QuestFilter.NOT_STARTED -> snap.quests.filter { it.state == "NOT_STARTED" }
            QuestFilter.FINISHED -> snap.quests.filter { it.state == "FINISHED" }
        }
        return ok(ListSerializer(QuestSnapshot.serializer()), out, snap)
    }

    fun nearbyNpcs(maxDistance: Int?, limit: Int): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val all = snap.nearbyNpcs
        val filtered = if (maxDistance != null) all.filter { it.distance <= maxDistance } else all
        return ok(ListSerializer(NearbyNpcSnapshot.serializer()), filtered.take(limit), snap)
    }

    fun slayerTask(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val slayer = snap.slayer
            ?: return notReady(
                "no-slayer-task",
                "The player has no active slayer task. Visit a slayer master to get one.",
                snap,
            )
        return ok(SlayerSnapshot.serializer(), slayer, snap)
    }

    fun geOffers(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        // It's normal for the offer list to be empty; that's not a not-ready state.
        return ok(ListSerializer(GeOfferSnapshot.serializer()), snap.geOffers, snap)
    }

    fun buffs(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val b = snap.buffs ?: return notReady(
            "buffs-not-loaded",
            "Buff state not yet snapshot — wait one game tick after login.",
            snap,
        )
        return ok(BuffsSnapshot.serializer(), b, snap)
    }

    fun groundItems(query: String?, limit: Int): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val all = snap.groundItems
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val filtered = if (q != null) all.filter { it.name.lowercase().contains(q) } else all
        return ok(ListSerializer(GroundItemSnapshot.serializer()), filtered.take(limit), snap)
    }

    fun nearbyObjects(query: String?, limit: Int): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val all = snap.nearbyObjects
        val q = query?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val filtered = if (q != null) all.filter { it.name.lowercase().contains(q) } else all
        return ok(ListSerializer(NearbyObjectSnapshot.serializer()), filtered.take(limit), snap)
    }

    fun combat(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val c = snap.combat ?: return notReady(
            "combat-not-loaded",
            "Combat state not yet snapshot — wait one game tick after login.",
            snap,
        )
        return ok(CombatInfoSnapshot.serializer(), c, snap)
    }

    fun diary(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val d = snap.diary ?: return notReady("diary-not-loaded",
            "Diary state not yet snapshot.", snap)
        return ok(DiaryProgressSnapshot.serializer(), d, snap)
    }

    fun spellbook(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val s = snap.spellbook ?: return notReady("spellbook-not-loaded",
            "Spellbook state not yet snapshot.", snap)
        return ok(SpellbookSnapshot.serializer(), s, snap)
    }

    fun attackStyle(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val a = snap.attackStyle ?: return notReady("attack-style-not-loaded",
            "Attack style not yet snapshot.", snap)
        return ok(AttackStyleSnapshot.serializer(), a, snap)
    }

    fun combatAchievements(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val ca = snap.combatAchievements ?: return notReady("ca-not-loaded",
            "Combat achievement state not yet snapshot.", snap)
        return ok(CombatAchievementsSnapshot.serializer(), ca, snap)
    }

    fun poh(): String {
        val snap = store.snapshot()
        notReadyIfLoggedOut(snap)?.let { return it }
        val p = snap.poh ?: return notReady("poh-not-loaded",
            "POH state not loaded — player may not have a house yet.", snap)
        return ok(PohSnapshot.serializer(), p, snap)
    }

    enum class QuestFilter { ALL, IN_PROGRESS, NOT_STARTED, FINISHED }

    // ---------- Envelope helpers ----------

    private fun notReadyIfLoggedOut(snap: GameSnapshot): String? =
        if (!snap.loggedIn) notReady(
            "not-logged-in",
            "Player is not logged in. No live game state is available. Ask the player to " +
                "log into their OSRS account and retry, or answer from general knowledge.",
            snap,
        ) else null

    private fun stalenessNote(ageMs: Long): String? = when {
        ageMs < 30_000 -> null
        ageMs < 120_000 -> "Data is ${ageMs / 1000}s old."
        ageMs < 3_600_000 -> "Data is ${ageMs / 60_000}m old — the player may have banked since."
        ageMs < 86_400_000 -> "Data is ${ageMs / 3_600_000}h old (persisted from a previous session). The player may have banked items in/out since."
        else -> "Data is ${ageMs / 86_400_000}d old (persisted from a previous session). Likely stale — ask the player to open the bank if accuracy matters."
    }

    private fun <T> ok(
        serializer: KSerializer<T>,
        value: T,
        snap: GameSnapshot,
        lastSeenAt: Long? = null,
    ): String {
        val inner = json.encodeToString(serializer, value)
        val meta = metaJson(snap, lastSeenAt)
        return """{"status":"ok","meta":$meta,"data":$inner}"""
    }

    private fun notReady(
        code: String,
        message: String,
        snap: GameSnapshot,
        lastSeenAt: Long? = null,
    ): String {
        val meta = metaJson(snap, lastSeenAt)
        return """{"status":"not-ready","notReady":${escStr(code)},"message":${escStr(message)},"meta":$meta,"data":null}"""
    }

    private fun escStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

    private fun metaJson(snap: GameSnapshot, lastSeenAt: Long?): String =
        json.encodeToString(
            Meta.serializer(),
            Meta(snapshotAt = snap.timestamp, loggedIn = snap.loggedIn, lastSeenAt = lastSeenAt),
        )

    @Serializable
    private data class Meta(
        val snapshotAt: Long,
        val loggedIn: Boolean,
        val lastSeenAt: Long? = null,
    )

    @Serializable
    private data class BankSummary(
        val lastSeenAt: Long,
        val ageSeconds: Long,
        val stalenessNote: String? = null,
        val totalUnique: Int,
        val totalQuantity: Long,
        val topByQuantity: List<BankItemRich>,
        val hint: String,
    )

    @Serializable
    private data class BankSearchResult(
        val query: String,
        val lastSeenAt: Long,
        val ageSeconds: Long,
        val stalenessNote: String? = null,
        val matchCount: Int,
        val items: List<BankItemRich>,
    )

    @Serializable
    private data class BankQueryResult(
        val query: String,
        val matchCount: Int,
        val items: List<BankItemRich>,
    )

    @Serializable
    private data class BankMultiSearchResult(
        val lastSeenAt: Long,
        val ageSeconds: Long,
        val stalenessNote: String? = null,
        val results: List<BankQueryResult>,
    )

    companion object {
        // Compact JSON — pretty-printing doubles response token count for no value
        // to the model. The agent can read minified JSON just as well as indented.
        val json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
