package co.rowm.osrsllm.highlights

import co.rowm.osrsllm.managed.ManagedVisualsRegistry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared data class for every highlight type (NPC, object, ground item, inventory).
 * One struct, different rendering — keeps registry shape and panel UI uniform.
 *
 * - `targetId` is the type's primary identifier (NPC composition id, object id, item id).
 * - For OBJECT highlights only, optional [x]/[y]/[plane] pin the highlight to a single
 *   in-world instance instead of every object of that id.
 */
@Serializable
data class HighlightEntry(
    val targetId: Int,
    val name: String? = null,
    val colorHex: String? = null,
    val label: String? = null,
    val source: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val plane: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val id: String
        get() = if (x != null && y != null) "$targetId@$x,$y,${plane ?: 0}" else targetId.toString()
}

/**
 * Pluggable persistent store for one highlight type. Owns config persistence,
 * the in-memory atomic reference, and the [ManagedVisualsRegistry] wiring.
 * Concrete services hold one of these and add type-specific helpers (default
 * color lookup, name resolution, etc.).
 */
class HighlightStore(
    private val configManager: ConfigManager,
    private val registry: ManagedVisualsRegistry,
    val configGroup: String,
    val configKey: String,
    val managedType: String,
    val labelPrefix: String,
) {
    private val log = LoggerFactory.getLogger("HighlightStore[$managedType]")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ref = AtomicReference<List<HighlightEntry>>(emptyList())
    private val serializer: KSerializer<List<HighlightEntry>> = ListSerializer(HighlightEntry.serializer())

    fun all(): List<HighlightEntry> = ref.get()

    fun forTargetId(id: Int): HighlightEntry? = ref.get().firstOrNull { it.targetId == id && it.x == null }

    fun forTile(targetId: Int, x: Int, y: Int, plane: Int): HighlightEntry? =
        ref.get().firstOrNull { it.targetId == targetId && it.x == x && it.y == y && it.plane == plane }

    fun load(remover: (HighlightEntry) -> Unit) {
        val raw = configManager.getConfiguration(configGroup, configKey)
        val list = if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(serializer, raw) }
            .onFailure { log.warn("load failed: {}", it.message) }
            .getOrDefault(emptyList())
        ref.set(list)
        log.info("Loaded {} {} entries", list.size, managedType)

        registry.registerRemover(object : ManagedVisualsRegistry.Remover {
            override val type = managedType
            override fun remove(entry: ManagedVisualsRegistry.Entry) {
                // The sidebar's ✕ button — undo by composite id. The entry was
                // registered with the same id we generate from HighlightEntry.id.
                val current = ref.get().firstOrNull { it.id == entry.id } ?: return
                remover(current)
            }
        })

        // Re-register every loaded entry with the central registry so the sidebar
        // shows them after a plugin/client restart.
        for (e in list) registry.register(toManagedEntry(e))
    }

    /** Add or update. Idempotent on [HighlightEntry.id]. Returns (added, updated, total). */
    fun add(entries: List<HighlightEntry>): Triple<Int, Int, Int> {
        if (entries.isEmpty()) return Triple(0, 0, ref.get().size)
        var added = 0; var updated = 0
        val byId = LinkedHashMap<String, HighlightEntry>()
        ref.get().forEach { byId[it.id] = it }
        for (e in entries) {
            val existing = byId[e.id]
            if (existing == null) added++
            else if (existing != e) updated++
            byId[e.id] = e
            registry.register(toManagedEntry(e))
        }
        ref.set(byId.values.toList())
        persist()
        return Triple(added, updated, byId.size)
    }

    /** Remove by id. Returns true if anything went. */
    fun removeById(id: String, skipUndo: Boolean = true): Boolean {
        var changed = false
        val kept = ref.get().filter { kept ->
            if (kept.id == id) { changed = true; false } else true
        }
        if (changed) {
            ref.set(kept)
            persist()
            registry.remove(managedType, id, skipUndo = skipUndo)
        }
        return changed
    }

    fun clearAll(): Int {
        val n = ref.get().size
        val ids = ref.get().map { it.id }
        ref.set(emptyList())
        persist()
        for (id in ids) registry.remove(managedType, id, skipUndo = true)
        return n
    }

    private fun persist() {
        val list = ref.get()
        if (list.isEmpty()) {
            configManager.unsetConfiguration(configGroup, configKey)
        } else {
            configManager.setConfiguration(configGroup, configKey,
                json.encodeToString(serializer, list))
        }
    }

    private fun toManagedEntry(e: HighlightEntry): ManagedVisualsRegistry.Entry {
        val nameOrId = e.name ?: e.targetId.toString()
        val label = "$labelPrefix $nameOrId"
        val subtitleParts = buildList {
            e.label?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (e.x != null && e.y != null) add("(${e.x}, ${e.y})")
        }
        return ManagedVisualsRegistry.Entry(
            id = e.id,
            type = managedType,
            label = label,
            subtitle = subtitleParts.joinToString(" · ").takeIf { it.isNotEmpty() },
            colorHex = e.colorHex,
            payload = buildJsonObject {
                put("targetId", JsonPrimitive(e.targetId))
                e.name?.let { put("name", JsonPrimitive(it)) }
                e.x?.let { put("x", JsonPrimitive(it)) }
                e.y?.let { put("y", JsonPrimitive(it)) }
                e.plane?.let { put("plane", JsonPrimitive(it)) }
            },
            createdAt = e.createdAt,
            source = e.source,
        )
    }
}

/** Hex `#RRGGBB` / `#AARRGGBB` → Color. Returns [fallback] on parse failure. */
fun decodeHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
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
                    (argb shr 16) and 0xFF, (argb shr 8) and 0xFF,
                    argb and 0xFF, (argb ushr 24) and 0xFF,
                )
            }
            else -> fallback
        }
    }.getOrDefault(fallback)
}

/**
 * Try to read a Color-typed value from another plugin's config group/key. The
 * RuneLite [ConfigManager] stores Color as a hex string under the hood — we
 * round-trip via [decodeHexColor] so this works even when the sibling plugin's
 * config class isn't on our classpath.
 */
fun readSiblingPluginColor(
    configManager: ConfigManager, group: String, key: String, fallback: Color,
): Color = runCatching {
    val raw = configManager.getConfiguration(group, key) ?: return@runCatching fallback
    // RuneLite serialises Colors as "rgba=R,G,B,A" or as a number — handle both.
    if (raw.startsWith("rgba=") || raw.startsWith("rgb=")) {
        val parts = raw.substringAfter('=').split(",").mapNotNull { it.trim().toIntOrNull() }
        when (parts.size) {
            3 -> Color(parts[0], parts[1], parts[2], 220)
            4 -> Color(parts[0], parts[1], parts[2], parts[3])
            else -> fallback
        }
    } else {
        val asInt = raw.toIntOrNull()
        if (asInt != null) {
            Color(
                (asInt shr 16) and 0xFF, (asInt shr 8) and 0xFF, asInt and 0xFF,
                ((asInt ushr 24) and 0xFF).takeIf { it > 0 } ?: 220,
            )
        } else decodeHexColor(raw, fallback)
    }
}.getOrDefault(fallback)
