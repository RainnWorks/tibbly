package co.rowm.osrsllm.highlights

import co.rowm.osrsllm.managed.ManagedVisualsRegistry
import net.runelite.client.config.ConfigManager
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent-managed inventory-item highlights. Useful for "highlight the items
 * you need to deposit", "show me which inventory slot to click next" etc.
 *
 * Highlights apply to the regular inventory pane plus any bank/GE/equipment
 * widget that displays the same item id — that's how RuneLite's
 * `WidgetItemOverlay` traversal already works, so it composes with anywhere
 * the player might be looking at their items.
 *
 * No native sibling config for inventory-item color, so the fallback is our
 * own default.
 */
@Singleton
class InventoryItemHighlightService @Inject constructor(
    private val configManager: ConfigManager,
    private val registry: ManagedVisualsRegistry,
    private val itemManager: ItemManager,
) {

    private val log = LoggerFactory.getLogger(InventoryItemHighlightService::class.java)
    private val store = HighlightStore(
        configManager = configManager,
        registry = registry,
        configGroup = "osrsllm",
        configKey = "highlights_inventory",
        managedType = ManagedVisualsRegistry.Type.INVENTORY_ITEM_HIGHLIGHT,
        labelPrefix = "Item",
    )

    fun load() = store.load { entry -> store.removeById(entry.id) }

    fun all(): List<HighlightEntry> = store.all()

    fun add(entries: List<HighlightEntry>): Triple<Int, Int, Int> = store.add(entries)

    fun removeById(id: String) = store.removeById(id)

    fun clearAll() = store.clearAll()

    fun colorFor(entry: HighlightEntry): Color = decodeHexColor(entry.colorHex, DEFAULT_COLOR)

    fun resolveName(itemId: Int): String? =
        runCatching { itemManager.getItemComposition(itemId).name }.getOrNull()

    companion object {
        private val DEFAULT_COLOR = Color(220, 200, 100, 220)
    }
}
