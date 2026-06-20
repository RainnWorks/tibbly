package co.rowm.osrsllm.banktags

import co.rowm.osrsllm.items.ItemNameResolver
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.runelite.client.callback.ClientThread
import net.runelite.client.config.ConfigManager
import net.runelite.client.game.ItemManager
import net.runelite.client.plugins.banktags.BankTagsPlugin
import net.runelite.client.plugins.banktags.TagManager
import net.runelite.client.plugins.banktags.tabs.TabManager
import net.runelite.client.plugins.banktags.tabs.TagTab
import net.runelite.client.util.Text
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Equipment Loadout" is an opinionated view over a Bank Tag layout.
 *
 * Bank Tag layouts are an 8-wide grid: `layout[pos] = itemId` (-1 = empty).
 * RuneLite's built-in `DefaultLayout` auto-layout places worn equipment in columns
 * 0-2 (rows 0-4) and inventory items in columns 4-7 — that's the same convention
 * we use here, so layouts written by this service render natively in Bank Tags
 * and can be edited by drag-and-drop in-game.
 *
 * Sidecar metadata (notes, use-case tag, per-slot switches, requirements,
 * decoration) is stored as a JSON blob at `osrsllm.loadout_meta_<name>` since
 * the int[] layout can't carry it.
 */
@Singleton
class EquipmentLoadoutService @Inject constructor(
    private val tagManager: TagManager,
    private val tabManager: TabManager,
    private val layoutManager: net.runelite.client.plugins.banktags.tabs.LayoutManager,
    private val itemManager: ItemManager,
    private val clientThread: ClientThread,
    private val configManager: ConfigManager,
    private val backupService: BankTagBackupService,
    private val itemNameResolver: ItemNameResolver,
) {

    private val log = LoggerFactory.getLogger(EquipmentLoadoutService::class.java)
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    // --- Position map (matches LayoutManager.DefaultLayout) ---
    //   Grid is 8 columns wide. row = pos/8, col = pos%8.
    //   Equipment fills cols 0-2 rows 0-4; inventory fills cols 4-7 rows 0-6;
    //   rune pouch contents at row 5 cols 0-3. Col 3 + anything else = decoration/free.
    private val wornPositions: Map<String, Int> = linkedMapOf(
        "HEAD" to 1,
        "CAPE" to 8,
        "AMULET" to 9,
        "AMMO" to 10,
        "WEAPON" to 16,
        "BODY" to 17,
        "SHIELD" to 18,
        "LEGS" to 25,
        "GLOVES" to 32,
        "BOOTS" to 33,
        "RING" to 34,
    )
    private val inventoryPositions: List<Int> = listOf(
        4, 5, 6, 7,
        12, 13, 14, 15,
        20, 21, 22, 23,
        28, 29, 30, 31,
        36, 37, 38, 39,
        44, 45, 46, 47,
        52, 53, 54, 55,
    )
    private val runePouchPositions: List<Int> = listOf(40, 41, 42, 43)
    private val knownPositions: Set<Int> =
        (wornPositions.values + inventoryPositions + runePouchPositions).toSet()
    private val maxKnownPos = 55

    @Serializable
    data class ItemRef(val id: Int, val name: String, val qty: Int = 1)

    @Serializable
    data class WornEntry(
        val slot: String,
        val item: ItemRef?,
        val switches: List<ItemRef> = emptyList(),
        val note: String? = null,
    )

    @Serializable
    data class InventoryEntry(
        val slot: Int,
        val item: ItemRef?,
        val switches: List<ItemRef> = emptyList(),
        val note: String? = null,
    )

    @Serializable
    data class DecorationEntry(
        val pos: Int,
        val row: Int,
        val col: Int,
        val item: ItemRef,
        val note: String? = null,
    )

    @Serializable
    data class Requirements(
        val quests: List<String> = emptyList(),
        val levels: Map<String, Int> = emptyMap(),
        val combatAchievements: List<String> = emptyList(),
    )

    @Serializable
    data class EquipmentLoadout(
        val name: String,
        val icon: ItemRef? = null,
        val useCase: String? = null,
        val notes: String? = null,
        val worn: List<WornEntry>,
        val inventory: List<InventoryEntry>,
        val runePouch: List<ItemRef> = emptyList(),
        val decoration: List<DecorationEntry> = emptyList(),
        val requirements: Requirements? = null,
    )

    /** Persisted sidecar — everything the int[] layout can't carry. */
    @Serializable
    private data class LoadoutMeta(
        val useCase: String? = null,
        val notes: String? = null,
        val wornNotes: Map<String, String> = emptyMap(),
        val wornSwitches: Map<String, List<Int>> = emptyMap(),
        val inventoryNotes: Map<String, String> = emptyMap(),  // key = slot index as string
        val inventorySwitches: Map<String, List<Int>> = emptyMap(),
        val decorationNotes: Map<String, String> = emptyMap(), // key = pos as string
        @SerialName("decorationPositions") val decorationPositions: List<Int> = emptyList(),
        val requirements: Requirements? = null,
    )

    // --- Public API ---

    fun listLoadouts(): List<String> {
        val tabs = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG)
            ?.let { Text.fromCSV(it) }.orEmpty()
        return tabs.filter { layoutCsv(it) != null }
    }

    fun getLoadout(name: String): EquipmentLoadout? {
        val csv = layoutCsv(name) ?: return null
        val layout = csv.split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
        val meta = readMeta(name) ?: LoadoutMeta()
        return parseLayout(name, layout, meta)
    }

    fun removeLoadout(name: String): Boolean {
        val existed = layoutCsv(name) != null
        configManager.unsetConfiguration(BankTagsPlugin.CONFIG_GROUP,
            BankTagsPlugin.TAG_LAYOUT_PREFIX + Text.standardize(name))
        configManager.unsetConfiguration(OSRSLLM_GROUP, metaKey(name))
        return existed
    }

    /**
     * Write a structured loadout into both the Bank Tags layout config (so the in-game
     * Bank Tags UI renders it) AND the sidecar metadata config.
     *
     * - `worn`: slot name → itemId (`null`/0 to clear). Slots: HEAD CAPE AMULET AMMO
     *   WEAPON BODY SHIELD LEGS GLOVES BOOTS RING.
     * - `inventory`: list of {slot: 0..27, itemId}.
     * - `runePouch`: optional list of up to 4 rune item ids.
     * - `decoration`: list of {pos, itemId, note?} for free-grid items (e.g. col 3 or
     *   below row 6) — purely visual.
     * - Metadata (notes, useCase, switches, requirements) overwrites the sidecar JSON.
     */
    fun saveLoadout(
        name: String,
        worn: Map<String, Int?>,
        inventory: Map<Int, Int?>,
        runePouch: List<Int?> = emptyList(),
        decoration: List<DecorationInput> = emptyList(),
        iconItemId: Int? = null,
        useCase: String? = null,
        notes: String? = null,
        wornNotes: Map<String, String> = emptyMap(),
        wornSwitches: Map<String, List<Int>> = emptyMap(),
        inventoryNotes: Map<Int, String> = emptyMap(),
        inventorySwitches: Map<Int, List<Int>> = emptyMap(),
        decorationNotes: Map<Int, String> = emptyMap(),
        requirements: Requirements? = null,
    ): EquipmentLoadout {
        require(name.isNotBlank()) { "Loadout name cannot be blank" }
        worn.keys.forEach { require(it in wornPositions) { "Unknown worn slot '$it'" } }
        inventory.keys.forEach { require(it in 0..27) { "Inventory slot $it out of range 0..27" } }
        require(runePouch.size <= 4) { "Rune pouch has 4 slots max" }
        decoration.forEach { require(it.pos in 0..63) { "Decoration pos ${it.pos} out of grid (0..63)" } }

        val layout = buildLayoutArray(worn, inventory, runePouch, decoration)
        val itemsToTag = collectTaggedItems(worn, inventory, runePouch, decoration)
        val resolvedIcon = iconItemId ?: itemsToTag.firstOrNull() ?: error("Loadout must contain at least one item")

        val meta = LoadoutMeta(
            useCase = useCase,
            notes = notes,
            wornNotes = wornNotes,
            wornSwitches = wornSwitches.mapValues { it.value },
            inventoryNotes = inventoryNotes.mapKeys { it.key.toString() },
            inventorySwitches = inventorySwitches.mapKeys { it.key.toString() }
                .mapValues { it.value },
            decorationNotes = decorationNotes.mapKeys { it.key.toString() },
            decorationPositions = decoration.map { it.pos },
            requirements = requirements,
        )

        onClientThread {
            backupService.safelyMutateTabs {
                for (id in itemsToTag) tagManager.addTag(id, name, false)
                val tab = tabManager.find(name) ?: TagTab().also {
                    it.tag = name
                    tabManager.add(it)
                }
                tab.iconItemId = resolvedIcon
                layoutManager.saveLayout(
                    net.runelite.client.plugins.banktags.tabs.Layout(name, layout))
                writeMeta(name, meta)
            }
        }
        log.info("saveLoadout name='{}' layoutSize={} items={} decoration={}",
            name, layout.size, itemsToTag.size, decoration.size)
        return getLoadout(name) ?: error("Loadout '$name' missing after save")
    }

    data class DecorationInput(val pos: Int, val itemId: Int, val note: String? = null)

    // --- Internals ---

    private fun layoutCsv(name: String): String? = configManager.getConfiguration(
        BankTagsPlugin.CONFIG_GROUP,
        BankTagsPlugin.TAG_LAYOUT_PREFIX + Text.standardize(name),
    )

    private fun metaKey(name: String) = "loadout_meta_" + Text.standardize(name)

    private fun readMeta(name: String): LoadoutMeta? {
        val raw = configManager.getConfiguration(OSRSLLM_GROUP, metaKey(name)) ?: return null
        return runCatching { json.decodeFromString(LoadoutMeta.serializer(), raw) }
            .onFailure { log.warn("Failed to parse loadout meta for '{}': {}", name, it.message) }
            .getOrNull()
    }

    private fun writeMeta(name: String, meta: LoadoutMeta) {
        val raw = json.encodeToString(LoadoutMeta.serializer(), meta)
        configManager.setConfiguration(OSRSLLM_GROUP, metaKey(name), raw)
    }

    private fun parseLayout(name: String, layout: IntArray, meta: LoadoutMeta): EquipmentLoadout {
        // Pre-resolve every item id referenced in the layout (worn + inventory + rune
        // pouch + decoration + switches + icon) in one client-thread batch so subsequent
        // per-slot lookups are cache hits. Avoids the previous "Unknown" everywhere
        // bug where `itemManager.getItemComposition` was called off the client thread.
        val allIds = buildSet<Int> {
            for (v in layout) if (v > 0) add(v)
            meta.wornSwitches.values.forEach { addAll(it) }
            meta.inventorySwitches.values.forEach { addAll(it) }
        }
        val nameMap = if (allIds.isNotEmpty()) itemNameResolver.resolveAll(allIds) else emptyMap()

        fun atPos(pos: Int): Int = if (pos in layout.indices) layout[pos] else -1
        fun item(id: Int, qty: Int = 1): ItemRef? = if (id <= 0) null else {
            ItemRef(id, nameMap[id] ?: "item $id", qty)
        }

        val worn = wornPositions.map { (slot, pos) ->
            val id = atPos(pos)
            WornEntry(
                slot = slot,
                item = item(id),
                switches = meta.wornSwitches[slot].orEmpty().mapNotNull { item(it) },
                note = meta.wornNotes[slot],
            )
        }
        val inventory = inventoryPositions.mapIndexed { invSlot, pos ->
            val id = atPos(pos)
            InventoryEntry(
                slot = invSlot,
                item = item(id),
                switches = (meta.inventorySwitches[invSlot.toString()] ?: emptyList())
                    .mapNotNull { item(it) },
                note = meta.inventoryNotes[invSlot.toString()],
            )
        }
        val runes = runePouchPositions.mapNotNull { item(atPos(it)) }

        // Decoration: any non-empty position outside the known set, or explicitly
        // recorded in meta. Trust meta first; fall back to scanning the array.
        val decorationPositions = (meta.decorationPositions +
            layout.indices.filter { it !in knownPositions && atPos(it) > 0 })
            .toSortedSet()
        val decoration = decorationPositions.mapNotNull { pos ->
            val id = atPos(pos)
            val ref = item(id) ?: return@mapNotNull null
            DecorationEntry(
                pos = pos,
                row = pos / 8,
                col = pos % 8,
                item = ref,
                note = meta.decorationNotes[pos.toString()],
            )
        }

        // Tab icon: prefer in-memory TabManager, else config.
        val iconId = (tabManager.find(name)?.iconItemId
            ?: configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP,
                BankTagsPlugin.TAG_ICON_PREFIX + Text.standardize(name))?.toIntOrNull()
            ?: -1)
        val icon = item(iconId)

        return EquipmentLoadout(
            name = name,
            icon = icon,
            useCase = meta.useCase,
            notes = meta.notes,
            worn = worn,
            inventory = inventory,
            runePouch = runes,
            decoration = decoration,
            requirements = meta.requirements,
        )
    }

    private fun buildLayoutArray(
        worn: Map<String, Int?>,
        inventory: Map<Int, Int?>,
        runePouch: List<Int?>,
        decoration: List<DecorationInput>,
    ): IntArray {
        val needed = (worn.values.filterNotNull() + inventory.values.filterNotNull() +
            runePouch.filterNotNull() + decoration.map { it.itemId }).any { it > 0 }
        if (!needed) return intArrayOf()

        // Size the array up to the highest used position (incl. decoration).
        val maxPos = (decoration.maxOfOrNull { it.pos } ?: 0).coerceAtLeast(maxKnownPos)
        val arr = IntArray(maxPos + 1) { -1 }
        worn.forEach { (slot, id) ->
            val pos = wornPositions[slot] ?: return@forEach
            if (id != null && id > 0) arr[pos] = id
        }
        inventory.forEach { (slot, id) ->
            if (slot !in 0..27) return@forEach
            val pos = inventoryPositions[slot]
            if (id != null && id > 0) arr[pos] = id
        }
        runePouch.forEachIndexed { i, id ->
            if (i in runePouchPositions.indices && id != null && id > 0) {
                arr[runePouchPositions[i]] = id
            }
        }
        decoration.forEach { d ->
            if (d.itemId > 0) arr[d.pos] = d.itemId
        }
        return arr
    }

    private fun collectTaggedItems(
        worn: Map<String, Int?>,
        inventory: Map<Int, Int?>,
        runePouch: List<Int?>,
        decoration: List<DecorationInput>,
    ): List<Int> {
        val ids = LinkedHashSet<Int>()
        worn.values.filterNotNull().filter { it > 0 }.forEach { ids += it }
        inventory.values.filterNotNull().filter { it > 0 }.forEach { ids += it }
        runePouch.filterNotNull().filter { it > 0 }.forEach { ids += it }
        decoration.map { it.itemId }.filter { it > 0 }.forEach { ids += it }
        return ids.toList()
    }

    private fun <T> onClientThread(body: () -> T): T {
        val future = CompletableFuture<T>()
        clientThread.invoke(Runnable {
            try { future.complete(body()) } catch (t: Throwable) { future.completeExceptionally(t) }
        })
        return try {
            future.get(8, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.warn("Loadout client-thread call failed: {}", t.message)
            throw t
        }
    }

    companion object {
        /** Config group for our own sidecar metadata. */
        const val OSRSLLM_GROUP = "osrsllm"
    }
}
