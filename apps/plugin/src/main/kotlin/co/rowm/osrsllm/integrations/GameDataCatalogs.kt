package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import net.runelite.client.game.AgilityShortcut
import net.runelite.client.game.FishingSpot
import net.runelite.client.game.ItemMapping
import javax.inject.Singleton

/**
 * Read-only adapters over RuneLite's static enums and lookup tables —
 * [AgilityShortcut], [FishingSpot], [ItemMapping]. These are shipped with the
 * client, so the data is already on disk; we just expose them through the MCP
 * surface so the agent can ask "where do I fish lobsters" / "what shortcut
 * gets me to X" / "which planks come from this log" without scraping the wiki.
 */
@Singleton
class GameDataCatalogs {

    // ---- Agility shortcuts ----

    @Serializable
    data class ShortcutEntry(
        val id: String,
        val level: Int,
        val description: String,
        val x: Int, val y: Int, val plane: Int,
        /** When non-zero, the world-map tooltip override (some shortcuts span tiles). */
        val mapX: Int = 0, val mapY: Int = 0,
    )

    /** All built-in agility shortcuts in RuneLite's catalog. */
    fun allShortcuts(): List<ShortcutEntry> = AgilityShortcut.values()
        .filter { it.worldLocation != null }
        .map { sc ->
            val w = sc.worldLocation!!
            val m = sc.worldMapLocation
            ShortcutEntry(
                id = sc.name,
                level = sc.level,
                description = sc.description ?: sc.name,
                x = w.x, y = w.y, plane = w.plane,
                mapX = m?.x ?: 0, mapY = m?.y ?: 0,
            )
        }

    // ---- Fishing spots ----

    @Serializable
    data class FishingSpotEntry(
        val id: String,
        val name: String,
        val tooltip: String?,
        /** Game NPC ids that ARE this fishing spot. */
        val npcIds: List<Int>,
    )

    fun allFishingSpots(): List<FishingSpotEntry> = FishingSpot.values().map { fs ->
        FishingSpotEntry(
            id = fs.name,
            name = fs.name,
            tooltip = fs.worldMapTooltip,
            npcIds = fs.ids.toList(),
        )
    }

    /** Search fishing spots by catch text (e.g. 'lobster', 'shark', 'karambwan'). */
    fun fishingSpotsFor(query: String): List<FishingSpotEntry> {
        val needle = query.lowercase()
        return allFishingSpots().filter {
            (it.tooltip?.lowercase()?.contains(needle) == true)
                || it.name.lowercase().contains(needle)
        }
    }

    // ---- Item variation / mapping ----

    @Serializable
    data class ItemMappingEntry(
        /** Canonical tradeable id for this mapping (the GE-priced one). */
        val tradeableId: Int,
        /** Alt (untradeable) ids that resolve to the tradeable. */
        val untradeableIds: List<Int>,
        val quantity: Long,
    )

    /**
     * Look up RuneLite's ItemMapping family for an id — returns every mapping
     * the id participates in. Useful for "what's this herb's grimy variant?" /
     * "this is a broken barrows piece, what's the repaired form?".
     */
    fun mappingFor(itemId: Int): List<ItemMappingEntry> = runCatching {
        ItemMapping.map(itemId)?.map { m ->
            ItemMappingEntry(
                tradeableId = m.tradeableItem,
                untradeableIds = m.untradableItems.toList(),
                quantity = m.quantity,
            )
        }.orEmpty()
    }.getOrDefault(emptyList())
}
