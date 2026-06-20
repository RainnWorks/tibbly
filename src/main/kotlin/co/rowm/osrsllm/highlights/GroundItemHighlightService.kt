package co.rowm.osrsllm.highlights

import co.rowm.osrsllm.managed.ManagedVisualsRegistry
import net.runelite.client.config.ConfigManager
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent-managed ground-item highlights. Highlights every dropped item with a
 * matching id on the loaded scene — agent can call this with the player's
 * "valuables" list and the user will instantly see any drops worth picking up.
 *
 * Default color reads from the user's GroundItems plugin `highlightedColor`
 * config so the marks match what they already see.
 */
@Singleton
class GroundItemHighlightService @Inject constructor(
    private val configManager: ConfigManager,
    private val registry: ManagedVisualsRegistry,
    private val itemManager: ItemManager,
) {

    private val log = LoggerFactory.getLogger(GroundItemHighlightService::class.java)
    private val store = HighlightStore(
        configManager = configManager,
        registry = registry,
        configGroup = "osrsllm",
        configKey = "highlights_grounditem",
        managedType = ManagedVisualsRegistry.Type.GROUND_ITEM_HIGHLIGHT,
        labelPrefix = "Ground",
    )

    fun load() = store.load { entry -> store.removeById(entry.id) }

    fun all(): List<HighlightEntry> = store.all()

    fun add(entries: List<HighlightEntry>): Triple<Int, Int, Int> = store.add(entries)

    fun removeById(id: String) = store.removeById(id)

    fun clearAll() = store.clearAll()

    fun colorFor(entry: HighlightEntry): Color = decodeHexColor(entry.colorHex, defaultHighlightColor())

    /** Resolve an item id → display name via ItemManager, for use in registry labels. */
    fun resolveName(itemId: Int): String? =
        runCatching { itemManager.getItemComposition(itemId).name }.getOrNull()

    fun defaultHighlightColor(): Color = readSiblingPluginColor(
        configManager, "grounditems", "highlightedColor", DEFAULT_FALLBACK,
    )

    companion object {
        private val DEFAULT_FALLBACK = Color(232, 100, 100, 220)
    }
}
