package co.rowm.osrsllm.highlights

import co.rowm.osrsllm.managed.ManagedVisualsRegistry
import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent-managed game object highlights — banks, doors, resource nodes, etc.
 *
 * Two modes per entry:
 *  - **By id**: highlight every object with this id (`x/y/plane` left null).
 *    Useful for "show me every bank booth on this floor".
 *  - **By tile**: highlight a specific instance at (x,y,plane). Useful when
 *    you want to mark ONE banker / ONE door rather than every same-typed one
 *    in the scene.
 *
 * Default color reads from the user's ObjectIndicators plugin so highlights
 * we add look like ones they already make.
 */
@Singleton
class ObjectHighlightService @Inject constructor(
    private val configManager: ConfigManager,
    private val registry: ManagedVisualsRegistry,
) {

    private val log = LoggerFactory.getLogger(ObjectHighlightService::class.java)
    private val store = HighlightStore(
        configManager = configManager,
        registry = registry,
        configGroup = "osrsllm",
        configKey = "highlights_object",
        managedType = ManagedVisualsRegistry.Type.OBJECT_HIGHLIGHT,
        labelPrefix = "Object",
    )

    fun load() = store.load { entry -> store.removeById(entry.id) }

    fun all(): List<HighlightEntry> = store.all()

    fun add(entries: List<HighlightEntry>): Triple<Int, Int, Int> = store.add(entries)

    fun removeById(id: String) = store.removeById(id)

    fun clearAll() = store.clearAll()

    fun colorFor(entry: HighlightEntry): Color = decodeHexColor(entry.colorHex, defaultHighlightColor())

    fun defaultHighlightColor(): Color = readSiblingPluginColor(
        configManager, "objectindicators", "markerColor", DEFAULT_FALLBACK,
    )

    companion object {
        private val DEFAULT_FALLBACK = Color(180, 140, 220, 220)
    }
}
