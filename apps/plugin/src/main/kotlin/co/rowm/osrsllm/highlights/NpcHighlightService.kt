package co.rowm.osrsllm.highlights

import co.rowm.osrsllm.managed.ManagedVisualsRegistry
import net.runelite.client.config.ConfigManager
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent-managed NPC highlights. Stores highlight requests by NPC composition
 * id (not in-world index, which changes per-tick). The companion overlay
 * iterates `client.npcs` each frame, matches against this list, and renders.
 *
 * # Default color
 * If the agent doesn't pass a `colorHex`, we read the user's NpcIndicators
 * highlight color so our marks match what they're already used to.
 */
@Singleton
class NpcHighlightService @Inject constructor(
    private val configManager: ConfigManager,
    private val registry: ManagedVisualsRegistry,
) {

    private val log = LoggerFactory.getLogger(NpcHighlightService::class.java)
    private val store = HighlightStore(
        configManager = configManager,
        registry = registry,
        configGroup = "osrsllm",
        configKey = "highlights_npc",
        managedType = ManagedVisualsRegistry.Type.NPC_HIGHLIGHT,
        labelPrefix = "NPC",
    )

    fun load() = store.load { entry -> store.removeById(entry.id) }

    fun all(): List<HighlightEntry> = store.all()

    fun add(entries: List<HighlightEntry>): Triple<Int, Int, Int> = store.add(entries)

    fun removeById(id: String) = store.removeById(id)

    fun clearAll() = store.clearAll()

    fun colorFor(entry: HighlightEntry): Color = decodeHexColor(entry.colorHex, defaultHighlightColor())

    /** Mirror the user's NpcIndicators highlight color so we visually match. */
    fun defaultHighlightColor(): Color = readSiblingPluginColor(
        configManager, "npcindicators", "npcColor", DEFAULT_FALLBACK,
    )

    companion object {
        private val DEFAULT_FALLBACK = Color(120, 200, 220, 220)
    }
}
