package co.rowm.osrsllm.managed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry of agent-created in-game visuals so a user can remove
 * anything an LLM added without having to ask the LLM to undo it.
 *
 * # Why a registry
 *
 * The agent can mutate the game in several visually-distinct ways: persistent
 * tile markers, bank tag tabs, NPC highlights, item highlights, object
 * highlights, etc. Each is handled by a different service (some write to our
 * own config, some write to a sibling plugin's config) and each has its own
 * removal path. Without a unified registry the user would have to know which
 * plugin/menu to dig into to clean up after an agent action.
 *
 * Every agent-driven addition flows through [register]. The sidebar panel
 * iterates [list] and offers a one-click [remove] that dispatches back to the
 * type-specific [Remover] which knows how to undo the addition.
 *
 * # Persistence
 *
 * Entries are serialised as JSON to `osrsllm.managed_visuals` config on each
 * mutation so the registry survives restarts. Removers re-register on plugin
 * startup; if a type doesn't have a registered remover (e.g. plugin upgrade
 * dropped one), the entry still appears in the panel but the remove button
 * is disabled.
 *
 * # Thread model
 *
 * Mutations are serialised on [synchronizedLock]; reads return immutable
 * snapshots so the Swing UI thread can iterate safely. Listeners ([addListener])
 * fire AFTER mutations complete on the calling thread.
 */
@Singleton
class ManagedVisualsRegistry @Inject constructor(
    private val configManager: ConfigManager,
) {

    private val log = LoggerFactory.getLogger(ManagedVisualsRegistry::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val synchronizedLock = Any()

    /** Type discriminator for entries. Strings (not enum) so future types don't break stored JSON. */
    object Type {
        const val TILE_MARKER = "tile_marker"
        const val BANK_TAB = "bank_tab"
        const val NPC_HIGHLIGHT = "npc_highlight"
        const val OBJECT_HIGHLIGHT = "object_highlight"
        const val GROUND_ITEM_HIGHLIGHT = "ground_item_highlight"
        const val INVENTORY_ITEM_HIGHLIGHT = "inventory_item_highlight"
        const val HINT_ARROW = "hint_arrow"
    }

    @Serializable
    data class Entry(
        /** Stable id. For type=bank_tab we use the tab name; for tile markers a composite of coords; otherwise UUID. */
        val id: String,
        val type: String,
        /** Human-readable summary, rendered in the sidebar. */
        val label: String,
        /** Short type-specific status (item count, region, etc.). Shown after the label. */
        val subtitle: String? = null,
        /** Display attributes — color swatch in hex, etc. */
        @SerialName("colorHex") val colorHex: String? = null,
        /** Free-form type-specific payload the remover uses to undo. */
        val payload: JsonObject,
        /** When the agent created it (epoch ms). */
        val createdAt: Long,
        /** Optional user-visible tag — chat session id, request quote, etc. */
        val source: String? = null,
    )

    interface Remover {
        val type: String
        /** Undo the visual described by [entry]. Throws on hard failure; missing-state should no-op. */
        fun remove(entry: Entry)
    }

    /** Listener for sidebar refreshes. Called after [register] / [remove] / [clearAll]. */
    fun interface Listener {
        fun onRegistryChanged()
    }

    private val entries = CopyOnWriteArrayList<Entry>()
    private val removers = mutableMapOf<String, Remover>()
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun registerRemover(remover: Remover) {
        synchronized(synchronizedLock) {
            removers[remover.type] = remover
            log.info("Registered remover for type={}", remover.type)
        }
    }

    fun addListener(l: Listener): Listener {
        listeners += l
        return l
    }

    fun removeListener(l: Listener) {
        listeners -= l
    }

    /** Replace any existing entry with the same (type, id), then persist + notify. */
    fun register(entry: Entry) {
        synchronized(synchronizedLock) {
            entries.removeIf { it.type == entry.type && it.id == entry.id }
            entries += entry
            persist()
        }
        fireChanged()
    }

    /**
     * Remove a registered entry by (type, id). Dispatches to the type's [Remover]
     * if one is registered; otherwise just removes from the registry (leaving any
     * residual in-game state intact, which usually means the user already cleared
     * it manually).
     *
     * Set [skipUndo] when the underlying service has already removed the visual
     * and just wants to tidy the registry (e.g. the remove was user-initiated
     * via the sibling plugin's own menu).
     */
    fun remove(type: String, id: String, skipUndo: Boolean = false): Entry? {
        val entry = synchronized(synchronizedLock) {
            val e = entries.firstOrNull { it.type == type && it.id == id } ?: return null
            entries -= e
            persist()
            e
        }
        if (!skipUndo) {
            removers[type]?.let { r ->
                runCatching { r.remove(entry) }
                    .onFailure { log.warn("Remover for {} threw: {}", type, it.message) }
            }
        }
        fireChanged()
        return entry
    }

    fun list(): List<Entry> = entries.toList()

    fun listByType(type: String): List<Entry> = entries.filter { it.type == type }

    /** Wipe everything. Intended for the sidebar's "remove all" — calls each remover. */
    fun clearAll() {
        val copy = entries.toList()
        synchronized(synchronizedLock) {
            entries.clear()
            persist()
        }
        for (e in copy) {
            removers[e.type]?.let { r ->
                runCatching { r.remove(e) }.onFailure {
                    log.warn("Bulk remove for {} threw: {}", e.type, it.message)
                }
            }
        }
        fireChanged()
    }

    fun load() {
        val raw = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY)
        if (raw.isNullOrBlank()) {
            log.info("Managed visuals registry empty")
            return
        }
        val loaded = runCatching { json.decodeFromString(ListSerializer(Entry.serializer()), raw) }
            .onFailure { log.warn("Failed to load managed visuals: {}", it.message) }
            .getOrDefault(emptyList())
        synchronized(synchronizedLock) {
            entries.clear()
            entries.addAll(loaded)
        }
        log.info("Loaded {} managed visual entries", loaded.size)
        fireChanged()
    }

    private fun persist() {
        if (entries.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY)
        } else {
            configManager.setConfiguration(
                CONFIG_GROUP, CONFIG_KEY,
                json.encodeToString(ListSerializer(Entry.serializer()), entries.toList()),
            )
        }
    }

    private fun fireChanged() {
        for (l in listeners) runCatching { l.onRegistryChanged() }
    }

    companion object {
        const val CONFIG_GROUP = "osrsllm"
        const val CONFIG_KEY = "managed_visuals"
        fun newId(): String = UUID.randomUUID().toString()
    }
}
