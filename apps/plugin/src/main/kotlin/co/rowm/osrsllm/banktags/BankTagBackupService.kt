package co.rowm.osrsllm.banktags

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.runelite.client.RuneLite
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.banktags.BankTagsPlugin
import net.runelite.client.plugins.banktags.TagManager
import net.runelite.client.plugins.banktags.tabs.TabManager
import net.runelite.client.plugins.banktags.tabs.TagTab
import net.runelite.client.util.Text
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe-mutation helper for bank tag writes.
 *
 * Two responsibilities:
 *
 * 1) PRELOAD. RuneLite's TabManager.save() rewrites the `banktags.tagtabs` config
 *    from its in-memory tab list. If we add a tab and call save() without the OTHER
 *    existing tabs being in memory, those tabs vanish from the CSV (their per-tab
 *    icon/layout configs survive but become orphans). Before any save() we read the
 *    full CSV from config and inject every missing tab into TabManager so the rewrite
 *    is non-destructive.
 *
 * 2) BACKUP. Even with the preload fix, we snapshot every tab's data (name, icon,
 *    layout, tagged items) to a timestamped JSON file under
 *    ~/.runelite/osrs-llm-helper/banktag-backups/ before any write. Keeps the last
 *    [MAX_BACKUPS] files. Manual recovery is possible by replaying the JSON.
 */
@Singleton
class BankTagBackupService @Inject constructor(
    private val tabManager: TabManager,
    private val tagManager: TagManager,
    private val configManager: ConfigManager,
) {

    private val log = LoggerFactory.getLogger(BankTagBackupService::class.java)
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class BackupTab(
        val name: String,
        val standardizedName: String,
        val icon: Int,
        val layout: String?,
        val items: List<Int>,
        /** Sidecar notes (osrsllm.itemnotes_<tag>) — itemId → free-text. Null when none set. */
        val notes: Map<String, String>? = null,
        /** Raw EquipmentLoadoutService meta JSON (osrsllm.loadout_meta_<tag>). Null when not a loadout. */
        val loadoutMeta: String? = null,
    )

    @Serializable
    data class Backup(
        val timestamp: String,
        val tabs: List<BackupTab>,
    )

    /** Re-entrancy guard so a single logical mutation (tab + layout + notes) gets ONE backup. */
    private val mutationDepth = ThreadLocal.withInitial { 0 }

    /**
     * Run [body] sandwiched between backup() / preload / tabManager.save().
     *
     * Re-entrant: nested calls run [body] directly without taking another backup or
     * issuing another save. This lets a public service method (e.g. createGroupedTab)
     * compose smaller mutating helpers (setItemNotes, layout save) under one umbrella
     * without producing N backup files for one user-visible action.
     */
    fun safelyMutateTabs(body: () -> Unit) {
        val depth = mutationDepth.get()
        mutationDepth.set(depth + 1)
        try {
            if (depth > 0) {
                body()
                return
            }
            runCatching { backup() }.onFailure { log.warn("Bank tag backup failed: {}", it.message) }
            preloadAllTabs()
            body()
            tabManager.save()
        } finally {
            mutationDepth.set(depth)
        }
    }

    fun preloadAllTabs() {
        val csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG)
            ?: return
        val names = Text.fromCSV(csv).filter { it.isNotBlank() }
        for (name in names) {
            val std = Text.standardize(name)
            if (tabManager.find(std) != null) continue
            val iconStr = configManager.getConfiguration(
                BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_ICON_PREFIX + std,
            )
            val iconId = iconStr?.toIntOrNull() ?: 0
            val tab = TagTab().apply {
                this.tag = std
                this.iconItemId = iconId
            }
            tabManager.add(tab)
        }
    }

    fun backup(): File? {
        val dir = File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/banktag-backups")
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("Could not create backup dir {}", dir)
            return null
        }
        val csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG)
        val names = csv?.let { Text.fromCSV(it).filter { n -> n.isNotBlank() } }.orEmpty()

        val tabs = names.map { name ->
            val std = Text.standardize(name)
            val icon = configManager.getConfiguration(
                BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_ICON_PREFIX + std,
            )?.toIntOrNull() ?: 0
            val layout = configManager.getConfiguration(
                BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_LAYOUT_PREFIX + std,
            )
            val items = runCatching { tagManager.getItemsForTag(name) }.getOrDefault(emptyList())
            // Sidecar notes (itemId → text), stored by BankTagService in our own config namespace.
            val notesRaw = configManager.getConfiguration(NOTES_GROUP, NOTES_PREFIX + std)
            val notes = notesRaw?.let {
                runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull()
            }?.takeIf { it.isNotEmpty() }
            // EquipmentLoadoutService meta blob (only present for loadout tabs).
            val loadoutMeta = configManager.getConfiguration(NOTES_GROUP, LOADOUT_META_PREFIX + std)
            BackupTab(name = name, standardizedName = std, icon = icon,
                layout = layout, items = items, notes = notes, loadoutMeta = loadoutMeta)
        }
        val backup = Backup(
            timestamp = Instant.now().toString(),
            tabs = tabs,
        )
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val file = File(dir, "banktags-$ts.json")
        file.writeText(json.encodeToString(Backup.serializer(), backup))
        log.info("Backed up {} bank tags to {}", tabs.size, file.absolutePath)

        // Prune to last MAX_BACKUPS by mtime.
        dir.listFiles { f -> f.isFile && f.name.startsWith("banktags-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_BACKUPS)
            ?.forEach { runCatching { it.delete() } }
        return file
    }

    @Serializable
    data class BackupSummary(
        val file: String,
        val timestamp: String,
        val tabCount: Int,
        val sizeBytes: Long,
    )

    /** Files in the backup dir, newest first. Quick metadata read — no full body parse. */
    fun listBackups(): List<BackupSummary> {
        val dir = backupDir() ?: return emptyList()
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith("banktags-") && f.name.endsWith(".json")
        } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.mapNotNull { f ->
            runCatching {
                val backup = Json.decodeFromString(Backup.serializer(), f.readText())
                BackupSummary(
                    file = f.name,
                    timestamp = backup.timestamp,
                    tabCount = backup.tabs.size,
                    sizeBytes = f.length(),
                )
            }.getOrNull()
        }
    }

    /**
     * Restore configs from a backup file.
     *
     * Always takes a fresh backup of the CURRENT state first (via safelyMutateTabs)
     * so the restore itself is undoable.
     *
     * Modes:
     *  - [RestoreMode.MERGE] — additive. For every tab in the backup, re-apply its
     *    tags/icon/layout/notes/loadoutMeta. Tabs that exist now but weren't in the
     *    backup are left alone. Items currently tagged but not in the backup keep
     *    their tags.
     *  - [RestoreMode.REPLACE] — destructive. First strips every tab listed in the
     *    current `tagtabs` CSV (and their per-tag config keys), then applies the
     *    backup. Result: exactly the tabs in the backup.
     */
    fun restore(filename: String, mode: RestoreMode = RestoreMode.MERGE): RestoreResult {
        val dir = backupDir() ?: error("Backup dir does not exist")
        // Guard against path traversal — filename only, no separators.
        require(!filename.contains('/') && !filename.contains('\\') && filename != "..") {
            "Invalid backup filename"
        }
        val file = File(dir, filename)
        require(file.isFile) { "No backup file '$filename'" }
        val backup = Json.decodeFromString(Backup.serializer(), file.readText())

        val tabsCreated = mutableListOf<String>()
        val tabsUpdated = mutableListOf<String>()

        safelyMutateTabs {
            if (mode == RestoreMode.REPLACE) wipeAllTabs()

            for (bt in backup.tabs) {
                val std = bt.standardizedName.takeIf { it.isNotBlank() } ?: Text.standardize(bt.name)
                val existed = tabManager.find(std) != null

                // Re-apply tags additively. Negative ids = variation tags; the BankTags
                // plugin keys those directly so writing the config restores variation status.
                for (id in bt.items) applyTagAdditive(id, bt.name)

                // Icon + ensure tab object exists.
                val tab = tabManager.find(std) ?: TagTab().apply {
                    this.tag = std
                    tabManager.add(this)
                }
                tab.iconItemId = bt.icon

                // Layout, notes, loadout meta — direct config writes (these own those keys).
                if (bt.layout != null) {
                    configManager.setConfiguration(
                        BankTagsPlugin.CONFIG_GROUP,
                        BankTagsPlugin.TAG_LAYOUT_PREFIX + std,
                        bt.layout,
                    )
                }
                if (bt.notes != null && bt.notes.isNotEmpty()) {
                    configManager.setConfiguration(
                        NOTES_GROUP, NOTES_PREFIX + std,
                        Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), bt.notes),
                    )
                }
                if (bt.loadoutMeta != null) {
                    configManager.setConfiguration(
                        NOTES_GROUP, LOADOUT_META_PREFIX + std, bt.loadoutMeta,
                    )
                }
                if (existed) tabsUpdated += bt.name else tabsCreated += bt.name
            }
        }
        log.info("Restored {} ({} created, {} updated) from {}",
            backup.tabs.size, tabsCreated.size, tabsUpdated.size, file.name)
        return RestoreResult(
            file = filename,
            timestamp = backup.timestamp,
            mode = mode,
            tabsCreated = tabsCreated,
            tabsUpdated = tabsUpdated,
        )
    }

    enum class RestoreMode { MERGE, REPLACE }

    @Serializable
    data class RestoreResult(
        val file: String,
        val timestamp: String,
        val mode: RestoreMode,
        val tabsCreated: List<String>,
        val tabsUpdated: List<String>,
    )

    private fun backupDir(): File? {
        val dir = File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/banktag-backups")
        if (!dir.exists()) return null
        return dir
    }

    /** Add [tag] to whatever tag CSV is already at `banktags.item_<id>`. Preserves other tags. */
    private fun applyTagAdditive(itemId: Int, tag: String) {
        val key = "item_" + itemId
        val existing = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, key) ?: ""
        val tags = LinkedHashSet(Text.fromCSV(existing.lowercase()))
        if (tags.add(Text.standardize(tag))) {
            configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, key, Text.toCSV(tags))
        }
    }

    /** Remove every current tab + its per-tag config. Used by REPLACE mode. */
    private fun wipeAllTabs() {
        val csv = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TABS_CONFIG)
            ?: return
        val names = Text.fromCSV(csv).filter { it.isNotBlank() }
        for (name in names) {
            tagManager.removeTag(name)
            tabManager.remove(name)
            val std = Text.standardize(name)
            configManager.unsetConfiguration(
                BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_LAYOUT_PREFIX + std)
            configManager.unsetConfiguration(
                BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_ICON_PREFIX + std)
            configManager.unsetConfiguration(NOTES_GROUP, NOTES_PREFIX + std)
            configManager.unsetConfiguration(NOTES_GROUP, LOADOUT_META_PREFIX + std)
        }
    }

    companion object {
        private const val MAX_BACKUPS = 10
        /** Config group BankTagService uses for sidecar notes. Mirrors BankTagService.NOTES_GROUP. */
        private const val NOTES_GROUP = "osrsllm"
        private const val NOTES_PREFIX = "itemnotes_"
        /** EquipmentLoadoutService meta-JSON key prefix in the same group. */
        private const val LOADOUT_META_PREFIX = "loadout_meta_"
    }
}
