package co.rowm.osrsllm.banktags

import kotlinx.serialization.Serializable
import net.runelite.client.callback.ClientThread
import net.runelite.client.config.ConfigManager
import net.runelite.client.game.ItemManager
import net.runelite.client.plugins.banktags.BankTagsPlugin
import net.runelite.client.plugins.banktags.BankTagsService
import net.runelite.client.plugins.banktags.TagManager
import net.runelite.client.plugins.banktags.tabs.Layout
import net.runelite.client.plugins.banktags.tabs.LayoutManager
import net.runelite.client.plugins.banktags.tabs.TabManager
import net.runelite.client.plugins.banktags.tabs.TagTab
import net.runelite.client.util.Text
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankTagService @Inject constructor(
    private val tagManager: TagManager,
    private val tabManager: TabManager,
    private val bankTagsService: BankTagsService,
    private val layoutManager: LayoutManager,
    private val itemManager: ItemManager,
    private val clientThread: ClientThread,
    private val configManager: ConfigManager,
    private val backupService: BankTagBackupService,
    private val registry: co.rowm.osrsllm.managed.ManagedVisualsRegistry,
) {

    private val log = LoggerFactory.getLogger(BankTagService::class.java)

    /** Called by the plugin on startup to install our remover with the registry. */
    fun installRemover() {
        registry.registerRemover(object : co.rowm.osrsllm.managed.ManagedVisualsRegistry.Remover {
            override val type = co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.BANK_TAB
            override fun remove(entry: co.rowm.osrsllm.managed.ManagedVisualsRegistry.Entry) {
                val name = (entry.payload["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: entry.id
                this@BankTagService.removeTab(name)
            }
        })
    }

    private fun registerBankTabInRegistry(name: String, itemCount: Int) {
        registry.register(co.rowm.osrsllm.managed.ManagedVisualsRegistry.Entry(
            id = name.lowercase(),
            type = co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.BANK_TAB,
            label = "Bank tab '$name'",
            subtitle = "$itemCount item${if (itemCount == 1) "" else "s"}",
            colorHex = null,
            payload = kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive(name))
            },
            createdAt = System.currentTimeMillis(),
            source = null,
        ))
    }

    @Serializable
    data class TabSummary(val name: String, val itemCount: Int, val iconItemId: Int)

    @Serializable
    data class TabDetail(
        val name: String,
        val itemCount: Int,
        val iconItemId: Int,
        val items: List<TaggedItem>,
    )

    @Serializable
    data class TaggedItem(
        val id: Int,
        val name: String,
        /** Free-text agent-authored note (e.g. "Required: 4-dose", "Recommended for X"). */
        val note: String? = null,
    )

    /** Name of the bank tag the player currently has open, or null. */
    fun activeTag(): String? = runCatching { bankTagsService.activeTag?.takeIf { it.isNotBlank() } }
        .getOrNull()

    fun listTabs(): List<TabSummary> = onClientThread {
        allTabNames().map { name ->
            val items = tagManager.getItemsForTag(name)
            TabSummary(
                name = name,
                itemCount = items.size,
                iconItemId = iconFor(name) ?: items.firstOrNull() ?: 0,
            )
        }
    }

    fun getTab(name: String): TabDetail? = onClientThread {
        val tabName = allTabNames().firstOrNull { it.equals(name, ignoreCase = true) }
            ?: return@onClientThread null
        val itemIds = tagManager.getItemsForTag(tabName)
        val notes = itemNotes(tabName)
        TabDetail(
            name = tabName,
            itemCount = itemIds.size,
            iconItemId = iconFor(tabName) ?: itemIds.firstOrNull() ?: 0,
            items = itemIds.map { id ->
                val displayName = runCatching { itemManager.getItemComposition(id).name }
                    .getOrDefault("Unknown")
                TaggedItem(id = id, name = displayName, note = notes[id])
            },
        )
    }

    // ----- Sidecar notes (RuneLite Bank Tags has no native per-item notes,
    //       so we store them in our own config namespace) -----

    private val noteJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** All notes for this tag as itemId → note. Returns empty map if none set. */
    fun itemNotes(tag: String): Map<Int, String> {
        val raw = configManager.getConfiguration(NOTES_GROUP, notesKey(tag)) ?: return emptyMap()
        return runCatching {
            noteJson.decodeFromString<Map<String, String>>(raw).mapKeys { it.key.toInt() }
        }.onFailure { log.warn("Failed to parse item notes for '{}': {}", tag, it.message) }
            .getOrDefault(emptyMap())
    }

    /**
     * Replace notes for [tag] wholesale. Pass empty map to clear.
     *
     * Routed through the backup service so the pre-write state is snapshotted to
     * disk before any sidecar config mutation — same protection as tab/layout writes.
     */
    fun setItemNotes(tag: String, notes: Map<Int, String>) {
        backupService.safelyMutateTabs {
            writeItemNotes(tag, notes)
        }
    }

    /** Direct sidecar write. Caller is responsible for wrapping in safelyMutateTabs. */
    private fun writeItemNotes(tag: String, notes: Map<Int, String>) {
        if (notes.isEmpty()) {
            configManager.unsetConfiguration(NOTES_GROUP, notesKey(tag))
            return
        }
        val keyed = notes.mapKeys { it.key.toString() }
        configManager.setConfiguration(
            NOTES_GROUP, notesKey(tag),
            noteJson.encodeToString(kotlinx.serialization.serializer(), keyed),
        )
    }

    /** Set a single item's note (merges with existing). null clears just that entry. */
    fun setItemNote(tag: String, itemId: Int, note: String?) {
        val current = itemNotes(tag).toMutableMap()
        if (note.isNullOrBlank()) current.remove(itemId) else current[itemId] = note
        setItemNotes(tag, current)
    }

    private fun notesKey(tag: String) = "itemnotes_" + Text.standardize(tag)

    fun createOrUpdateTab(
        name: String,
        itemIds: List<Int>,
        iconItemId: Int?,
        notes: Map<Int, String>? = null,
        variationIds: Set<Int> = emptySet(),
    ): TabDetail {
        require(name.isNotBlank()) { "Tab name cannot be blank" }
        require(itemIds.isNotEmpty()) { "Need at least one item to tag" }
        val resolvedIcon = iconItemId ?: itemIds.first()
        log.info("createOrUpdateTab name='{}' items={} icon={} notes={} variations={}",
            name, itemIds.size, resolvedIcon, notes?.size ?: 0, variationIds.size)

        onClientThread {
            backupService.safelyMutateTabs {
                for (id in itemIds) {
                    tagManager.addTag(id, name, variationIds.contains(id))
                }
                val existing = tabManager.find(name)
                if (existing != null) {
                    existing.iconItemId = resolvedIcon
                } else {
                    val tab = TagTab()
                    tab.tag = name
                    tab.iconItemId = resolvedIcon
                    tabManager.add(tab)
                }
                if (notes != null) writeItemNotes(name, notes)
            }
        }
        val detail = getTab(name) ?: error("Tab '$name' missing after create")
        registerBankTabInRegistry(name, detail.itemCount)
        return detail
    }

    fun removeTab(name: String): Boolean = onClientThread {
        val tabName = allTabNames().firstOrNull { it.equals(name, ignoreCase = true) }
            ?: return@onClientThread false
        log.info("removeTab name='{}'", tabName)
        backupService.safelyMutateTabs {
            tagManager.removeTag(tabName)
            tabManager.remove(tabName)
            layoutManager.removeLayout(tabName)
            writeItemNotes(tabName, emptyMap())
        }
        registry.remove(co.rowm.osrsllm.managed.ManagedVisualsRegistry.Type.BANK_TAB,
            tabName.lowercase(), skipUndo = true)
        true
    }

    fun openTab(name: String) {
        clientThread.invoke(Runnable {
            bankTagsService.openBankTag(name, BankTagsService.OPTION_ALLOW_MODIFICATIONS)
        })
    }

    /**
     * Create a bank tag tab with a layout grouped row-by-row.
     *
     * Each group fills left-to-right into the 8-wide Bank Tags grid; groups that
     * overflow 8 items wrap to the next row. After each group we advance one extra
     * row to leave a blank separator. Empty groups are skipped (no separator).
     *
     * Useful for "all items needed for quest X, grouped by step" — the player sees
     * each step as its own row band in the bank.
     */
    fun createGroupedTab(
        name: String,
        groups: List<List<Int>>,
        iconItemId: Int? = null,
        notes: Map<Int, String>? = null,
        variationIds: Set<Int> = emptySet(),
    ): TabDetail {
        require(name.isNotBlank()) { "Tab name cannot be blank" }
        val nonEmpty = groups.map { it.filter { id -> id > 0 } }.filter { it.isNotEmpty() }
        require(nonEmpty.isNotEmpty()) { "Need at least one non-empty group" }

        val positions = ArrayList<Pair<Int, Int>>()
        var row = 0
        for (group in nonEmpty) {
            val numRows = (group.size + GRID_WIDTH - 1) / GRID_WIDTH
            group.forEachIndexed { i, itemId ->
                val pos = (row + i / GRID_WIDTH) * GRID_WIDTH + (i % GRID_WIDTH)
                positions += pos to itemId
            }
            row += numRows + 1 // +1 = blank separator row
        }
        val maxPos = positions.maxOf { it.first }
        val arr = IntArray(maxPos + 1) { -1 }
        positions.forEach { (pos, id) -> arr[pos] = id }

        val allItems = nonEmpty.flatten().distinct()
        val resolvedIcon = iconItemId ?: allItems.first()

        onClientThread {
            backupService.safelyMutateTabs {
                for (id in allItems) tagManager.addTag(id, name, variationIds.contains(id))
                val existing = tabManager.find(name)
                if (existing != null) {
                    existing.iconItemId = resolvedIcon
                } else {
                    val tab = TagTab()
                    tab.tag = name
                    tab.iconItemId = resolvedIcon
                    tabManager.add(tab)
                }
                layoutManager.saveLayout(Layout(name, arr))
                if (notes != null) writeItemNotes(name, notes)
            }
            refreshIfActive(name)
        }
        log.info("createGroupedTab name='{}' groups={} items={} rows={} notes={} variations={}",
            name, nonEmpty.size, allItems.size, (maxPos / GRID_WIDTH) + 1,
            notes?.size ?: 0, variationIds.size)
        val detail = getTab(name) ?: error("Tab '$name' missing after create")
        registerBankTabInRegistry(name, detail.itemCount)
        return detail
    }

    /**
     * If [tag] is the tag the player currently has open, re-issue openBankTag so
     * BankTagsPlugin re-reads the layout from config. Without this, the plugin
     * caches the Layout on first open — config writes only take effect after the
     * player closes and reopens the tag *twice* (once to clear active, once to
     * reload). Must be called on the client thread.
     */
    private fun refreshIfActive(tag: String) {
        val active = runCatching { bankTagsService.activeTag }.getOrNull() ?: return
        if (active.equals(tag, ignoreCase = true)) {
            bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS)
        }
    }

    /** Snapshot of the player's currently-open tab + its layout, if any. */
    @Serializable
    data class ActiveLayoutInfo(
        val tag: String,
        val itemCount: Int,
        val laidOutItems: Int,
        val totalSlots: Int,
        val groupRows: Int,
    )

    /**
     * Inspect the tag the player currently has open. Returns null if no tag is open.
     * `laidOutItems` is the number of non-empty positions in the layout;
     * `groupRows` is how many bank rows the layout spans (8-wide grid, so
     * ceil(totalSlots/8)). Empty when the tag has no custom layout.
     */
    fun activeLayout(): ActiveLayoutInfo? = onClientThread {
        val tag = bankTagsService.activeTag?.takeIf { it.isNotBlank() } ?: return@onClientThread null
        val itemCount = tagManager.getItemsForTag(tag).size
        val layout = layoutManager.loadLayout(tag)
        if (layout == null) {
            ActiveLayoutInfo(tag = tag, itemCount = itemCount,
                laidOutItems = 0, totalSlots = 0, groupRows = 0)
        } else {
            val arr = layout.layout
            val laidOut = arr.count { it > 0 }
            val rows = (arr.size + GRID_WIDTH - 1) / GRID_WIDTH
            ActiveLayoutInfo(tag = tag, itemCount = itemCount,
                laidOutItems = laidOut, totalSlots = arr.size, groupRows = rows)
        }
    }

    /**
     * Enumerate all existing tab names by reading the Bank Tags config directly.
     * Uses public constants (`CONFIG_GROUP`, `TAG_TABS_CONFIG`) on `BankTagsPlugin`
     * and the public `Text.fromCSV` helper — no reflection.
     */
    private fun allTabNames(): List<String> {
        val csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG)
            ?: return emptyList()
        return Text.fromCSV(csv)
    }

    /**
     * Look up a tab's icon item id. Prefers the in-memory tab if `TabManager` has
     * already loaded it, otherwise reads the config (matches how Bank Tags persists
     * via `TAG_ICON_PREFIX + Text.standardize(tag)`).
     */
    private fun iconFor(name: String): Int? {
        tabManager.find(name)?.let { return it.iconItemId }
        val key = BankTagsPlugin.TAG_ICON_PREFIX + Text.standardize(name)
        return configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, key)?.toIntOrNull()
    }

    companion object {
        /** Bank Tags grid is 8 columns wide; pos = row * 8 + col. */
        private const val GRID_WIDTH = 8
        /** Config group for sidecar metadata (notes that BankTags itself doesn't store). */
        private const val NOTES_GROUP = "osrsllm"
    }

    private fun <T> onClientThread(body: () -> T): T {
        val future = CompletableFuture<T>()
        clientThread.invoke(Runnable {
            try {
                future.complete(body())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })
        return try {
            future.get(8, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.warn("Client-thread call failed: {}", t.message)
            throw t
        }
    }
}
