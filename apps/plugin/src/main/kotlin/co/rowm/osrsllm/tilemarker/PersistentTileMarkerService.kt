package co.rowm.osrsllm.tilemarker

import co.rowm.osrsllm.pathfinder.TilePoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.runelite.client.RuneLite
import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permanent tile markers placed by the agent. The agent's transient markers
 * (single-call mark_tile, show_path_to) live on [TileMarkerService.markers] /
 * [TileMarkerService.path] and clear on demand. Persistent markers stay until
 * removed explicitly — agent uses them for "remember this herb patch", "this
 * is where I safespot greater demons", fairy ring stand-tiles, etc.
 *
 * # Storage model
 *
 * We OWN the storage rather than mutating RuneLite's Ground Markers plugin
 * config. That plugin caches its in-memory points list and only reloads it on
 * region/worldview changes — writing into its config from outside doesn't
 * refresh its overlay until the player walks across a region boundary. Owning
 * our own store gives the agent immediate visual feedback and avoids the
 * package-private-API reach-around.
 *
 * Markers persist as JSON in `osrsllm.persistent_markers`. On startup
 * [load] is called from the plugin which hydrates the in-memory list; from
 * then on every mutation re-serialises the whole list (it's small — typical
 * users will have tens, not thousands).
 *
 * # Backups
 *
 * Every mutation snapshots the pre-write state to a rolling JSON file under
 * `~/.runelite/osrs-llm-helper/persistent-marker-backups/`. Last
 * [MAX_BACKUPS] are kept, same pattern as [BankTagBackupService].
 *
 * # Rendering
 *
 * Drawn by the existing [TileMarkerOverlay] alongside transient markers, so
 * we don't need a second overlay or pipeline.
 */
@Singleton
class PersistentTileMarkerService @Inject constructor(
    private val configManager: ConfigManager,
    private val registry: co.rowm.osrsllm.managed.ManagedVisualsRegistry,
) {

    private val log = LoggerFactory.getLogger(PersistentTileMarkerService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Stored(
        val x: Int, val y: Int, val plane: Int,
        val colorHex: String,
        val label: String? = null,
    )

    @Serializable
    data class WriteResult(
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val removed: Int,
        val total: Int,
        val backupFile: String?,
    )

    private val markersRef = AtomicReference<List<Stored>>(emptyList())

    fun load() {
        val raw = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY)
        val list = if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(Stored.serializer()), raw) }
            .onFailure { log.warn("Failed to load persistent markers: {}", it.message) }
            .getOrDefault(emptyList())
        markersRef.set(list)
        log.info("Loaded {} persistent tile markers", list.size)

        // Register a remover so the sidebar's ✕ button can route back into us.
        registry.registerRemover(object : co.rowm.osrsllm.managed.ManagedVisualsRegistry.Remover {
            override val type = co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.TILE_MARKER
            override fun remove(entry: co.rowm.osrsllm.managed.ManagedVisualsRegistry.Entry) {
                val x = entry.payload["x"]?.let { p -> (p as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: return
                val y = entry.payload["y"]?.let { p -> (p as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: return
                val plane = entry.payload["plane"]?.let { p -> (p as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() } ?: 0
                this@PersistentTileMarkerService.remove(listOf(Triple(x, y, plane)))
            }
        })
    }

    /** Current set as the overlay's [TileMarker] representation. */
    fun renderable(): List<TileMarker> = markersRef.get().map { s ->
        TileMarker(
            tile = TilePoint(s.x, s.y, s.plane),
            color = decodeHexColor(s.colorHex),
            label = s.label,
        )
    }

    fun all(): List<Stored> = markersRef.get()

    /**
     * Add or update markers. If a marker exists at the same (x,y,plane) its
     * color/label are overwritten rather than duplicating.
     */
    fun add(inputs: List<Stored>, source: String? = null): WriteResult {
        if (inputs.isEmpty()) return WriteResult(0, 0, 0, 0, markersRef.get().size, null)
        val backup = snapshot()
        var added = 0; var updated = 0; var unchanged = 0
        val byKey = LinkedHashMap<Triple<Int, Int, Int>, Stored>()
        markersRef.get().forEach { byKey[Triple(it.x, it.y, it.plane)] = it }
        for (i in inputs) {
            val key = Triple(i.x, i.y, i.plane)
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = i
                added++
            } else if (existing.colorHex == i.colorHex && existing.label == i.label) {
                unchanged++
            } else {
                byKey[key] = i
                updated++
            }
        }
        markersRef.set(byKey.values.toList())
        persist()
        for (i in inputs) registerInRegistry(i, source)
        return WriteResult(
            added = added, updated = updated, unchanged = unchanged, removed = 0,
            total = byKey.size, backupFile = backup?.name,
        )
    }

    private fun registerInRegistry(s: Stored, source: String?) {
        val id = "${s.x}:${s.y}:${s.plane}"
        registry.register(co.rowm.osrsllm.managed.ManagedVisualsRegistry.Entry(
            id = id,
            type = co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.TILE_MARKER,
            label = s.label ?: "Tile (${s.x}, ${s.y}${if (s.plane != 0) " p${s.plane}" else ""})",
            subtitle = "(${s.x}, ${s.y}${if (s.plane != 0) ", plane ${s.plane}" else ""})",
            colorHex = s.colorHex,
            payload = kotlinx.serialization.json.buildJsonObject {
                put("x", kotlinx.serialization.json.JsonPrimitive(s.x))
                put("y", kotlinx.serialization.json.JsonPrimitive(s.y))
                put("plane", kotlinx.serialization.json.JsonPrimitive(s.plane))
            },
            createdAt = System.currentTimeMillis(),
            source = source,
        ))
    }

    /**
     * Remove markers either by exact tile coordinates or by case-insensitive
     * substring match on the label. Both filters apply additively.
     */
    fun remove(tiles: List<Triple<Int, Int, Int>> = emptyList(), byLabel: String? = null): WriteResult {
        if (tiles.isEmpty() && byLabel.isNullOrBlank()) {
            return WriteResult(0, 0, 0, 0, markersRef.get().size, null)
        }
        val backup = snapshot()
        val tileSet = tiles.toSet()
        val needle = byLabel?.lowercase()
        val removedEntries = mutableListOf<Stored>()
        val kept = markersRef.get().filter { s ->
            val tileMatch = Triple(s.x, s.y, s.plane) in tileSet
            val labelMatch = needle != null && s.label?.lowercase()?.contains(needle) == true
            if (tileMatch || labelMatch) {
                removedEntries += s; false
            } else true
        }
        markersRef.set(kept)
        persist()
        // Skip undo since we already removed in-game; just tidy the registry.
        for (s in removedEntries) {
            registry.remove(co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.TILE_MARKER,
                "${s.x}:${s.y}:${s.plane}", skipUndo = true)
        }
        return WriteResult(
            added = 0, updated = 0, unchanged = 0, removed = removedEntries.size,
            total = kept.size, backupFile = backup?.name,
        )
    }

    /** Wipe all persistent markers. Backed up first. */
    fun clearAll(): WriteResult {
        val backup = snapshot()
        val priors = markersRef.get()
        markersRef.set(emptyList())
        persist()
        for (s in priors) {
            registry.remove(co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.TILE_MARKER,
                "${s.x}:${s.y}:${s.plane}", skipUndo = true)
        }
        return WriteResult(
            added = 0, updated = 0, unchanged = 0, removed = priors.size,
            total = 0, backupFile = backup?.name,
        )
    }

    // ----- internals -----

    private fun persist() {
        val list = markersRef.get()
        if (list.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY)
        } else {
            configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY,
                json.encodeToString(ListSerializer(Stored.serializer()), list))
        }
    }

    private fun snapshot(): File? {
        val dir = File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/persistent-marker-backups")
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("Could not create backup dir {}", dir)
            return null
        }
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault()).format(Instant.now())
        val file = File(dir, "persistent-markers-$ts.json")
        file.writeText(json.encodeToString(ListSerializer(Stored.serializer()), markersRef.get()))
        dir.listFiles { f -> f.isFile && f.name.startsWith("persistent-markers-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUPS)
            ?.forEach { runCatching { it.delete() } }
        return file
    }

    /** Hex "#RRGGBB" or "#AARRGGBB" → java.awt.Color. Falls back to plugin default. */
    private fun decodeHexColor(hex: String): Color {
        val trimmed = hex.trim().removePrefix("#")
        return runCatching {
            when (trimmed.length) {
                6 -> {
                    val rgb = Integer.parseUnsignedInt(trimmed, 16)
                    Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, 220)
                }
                8 -> {
                    val argb = Integer.parseUnsignedInt(trimmed, 16)
                    Color(
                        (argb shr 16) and 0xFF,
                        (argb shr 8) and 0xFF,
                        argb and 0xFF,
                        (argb ushr 24) and 0xFF,
                    )
                }
                else -> TileMarkerService.DEFAULT_MARKER_COLOR
            }
        }.getOrDefault(TileMarkerService.DEFAULT_MARKER_COLOR)
    }

    companion object {
        private const val CONFIG_GROUP = "osrsllm"
        private const val CONFIG_KEY = "persistent_markers"
        private const val MAX_BACKUPS = 10
    }
}
