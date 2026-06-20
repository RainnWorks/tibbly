package co.rowm.osrsllm.bank

import co.rowm.osrsllm.ItemSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wiki-sourced item tag index. Loaded once from `item-categories.json` (built by
 * scripts/build_item_categories.py, sourced from Category:Items subcategories).
 *
 * Two layers of opinion:
 *
 *  1. **Normalization** — in-game item names carry suffixes the wiki doesn't
 *     (charge counts like "(4)", trim variants like "(t)", explicit charge
 *     numbers like "Karil's coif 100"). We strip those before lookup so
 *     `"Amulet of glory(4)"` resolves to the wiki's `"Amulet of glory"`.
 *
 *  2. **Priority tiers** — 96 unique tags is too many to surface by default
 *     ("members_items" is on 9k items and barely informative). [PRIMARY_TAGS]
 *     is the curated short list that gets returned by [primaryTagsFor]. The
 *     full set is available via [tagsFor] or via the `list_item_tags` MCP tool
 *     when the agent needs niche filtering.
 */
@Singleton
class ItemTagIndex @Inject constructor() {

    private val log = LoggerFactory.getLogger(ItemTagIndex::class.java)

    /** wiki-canonical lowercase name → set of tags */
    private val byName: Map<String, Set<String>> by lazy { load() }

    /** tag → item count, for `list_item_tags` */
    private val tagCounts: Map<String, Int> by lazy {
        val counts = HashMap<String, Int>()
        for (tags in byName.values) for (t in tags) counts[t] = (counts[t] ?: 0) + 1
        counts.toSortedMap()
    }

    private fun load(): Map<String, Set<String>> {
        val stream = javaClass.classLoader.getResourceAsStream("item-categories.json")
            ?: run {
                log.warn("item-categories.json missing — bank tag filtering disabled. " +
                    "Run `./gradlew refreshItemCategories` or `python3 scripts/build_item_categories.py`.")
                return emptyMap()
            }
        return try {
            val text = stream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(text) as JsonObject
            val out = HashMap<String, Set<String>>(root.size)
            for ((name, value) in root) {
                val tags = (value as JsonArray).map { it.jsonPrimitive.content }.toSet()
                out[name.lowercase()] = tags
            }
            log.info("Loaded item tag index: {} items, {} unique tags",
                out.size, out.values.flatten().toSet().size)
            out
        } catch (t: Throwable) {
            log.error("Failed to parse item-categories.json", t)
            emptyMap()
        }
    }

    // ---- Public API ----

    /** All tags associated with [itemName] (in-game name; normalized internally). */
    fun tagsFor(itemName: String): Set<String> =
        byName[normalize(itemName)] ?: emptySet()

    /**
     * Top-priority tags only (default surfacing). Returns at most [max] entries,
     * preserving [PRIMARY_TAGS] order. If no primary tags match, falls back to
     * any tags the item has — better something than nothing.
     */
    fun primaryTagsFor(itemName: String, max: Int = 3): List<String> {
        val all = tagsFor(itemName)
        if (all.isEmpty()) return emptyList()
        val primary = PRIMARY_TAGS.filter { it in all }.take(max)
        if (primary.isNotEmpty()) return primary
        // No primary matches — show up to `max` of whatever this item has.
        return all.sorted().take(max)
    }

    /** Items whose name (normalized) carries [tag]. Matches against [items]. */
    fun filter(items: List<ItemSnapshot>, tag: String): List<ItemSnapshot> {
        val t = tag.lowercase().trim()
        return items.filter { t in tagsFor(it.name) }
    }

    /** All known tags with their global item counts, sorted alphabetically. */
    fun allTagsWithCounts(): Map<String, Int> = tagCounts

    // ---- Normalization ----

    /**
     * Map in-game item display name → wiki page name, in lowercase.
     * Strips:
     *   - charge suffixes:    "Amulet of glory(4)"  → "amulet of glory"
     *   - trim/g/b variants:  "Rune platebody (g)"  → "rune platebody"
     *   - explicit charges:   "Karil's coif 100"    → "karil's coif"
     *   - status markers:     "Ahrim's robetop 0"   → "ahrim's robetop"
     */
    private fun normalize(name: String): String {
        var n = name.trim().lowercase()
        n = n.replace(Regex("""\s*\(\d+\)$"""), "")          // (4), (1), (2), (3)
        n = n.replace(Regex("""\s*\([atgbi]\)$"""), "")      // (t), (g), (b), (a), (i)
        n = n.replace(Regex("""\s*\(or\)$"""), "")           // ornament kit variant
        n = n.replace(Regex("""\s+\d+$"""), "")              // trailing " 100", " 75", " 0"
        return n
    }

    companion object {
        /**
         * Curated short list of tags that the agent should see by default for
         * each item. Order is the surfacing priority — leftmost wins ties.
         *
         * Picks: real semantic categories. Skips meta-tags (members_items,
         * tradeable_items, items_appearing_in_drop_table) — those are noise for
         * "show me my food / weapons / teleports" questions.
         */
        val PRIMARY_TAGS: List<String> = listOf(
            // Consumables
            "food", "edible_items", "potions", "prayer_items",
            // Combat
            "weapons", "ammunition", "chinchompas",
            // Equipment slots
            "amulets", "rings", "bracelets", "capes", "boots", "gloves",
            "masks", "ornament_kits",
            // Skilling
            "runes", "magic_tablets", "logs", "metal_bars", "gems",
            "herblore", "seeds", "fishing_equipment", "pickaxes", "tools",
            // Movement/utility
            "teleportation_items",
            // Money / charges
            "currency", "items_with_charges",
            // Other commonly relevant
            "skilling_equipment", "warm_clothing", "self_damaging_items",
            "temporary_skill_boost", "storage_items",
        )
    }
}
