package co.rowm.osrsllm.local

import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.HitsplatHistorySnapshot
import co.rowm.osrsllm.bank.ItemTagIndex
import co.rowm.osrsllm.banktags.BankTagService
import co.rowm.osrsllm.banktags.EquipmentLoadoutService
import co.rowm.osrsllm.events.EventLogService
import co.rowm.osrsllm.events.GameEvent
import co.rowm.osrsllm.market.PriceLookupResponse
import co.rowm.osrsllm.market.PriceService
import co.rowm.osrsllm.tools.OsrsTools
import co.rowm.osrsllm.poi.PoiSearchResponse
import co.rowm.osrsllm.poi.PoiService
import co.rowm.osrsllm.session.HitsplatHistoryService
import co.rowm.osrsllm.session.LootService
import co.rowm.osrsllm.session.LootSnapshot
import co.rowm.osrsllm.session.XpRateService
import co.rowm.osrsllm.session.XpRateSnapshot
import co.rowm.osrsllm.transport.TransportSearchResponse
import co.rowm.osrsllm.transport.TransportService
import co.rowm.osrsllm.widgets.WidgetReader
import co.rowm.osrsllm.widgets.WidgetTracker
import co.rowm.osrsllm.wiki.WikiPageResponse
import co.rowm.osrsllm.wiki.WikiSearchResponse
import co.rowm.osrsllm.wiki.WikiService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import net.runelite.client.game.ItemManager
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpServerService @Inject constructor(
    private val gameStateStore: GameStateStore,
    private val bankTagService: BankTagService,
    private val bankTagBackupService: co.rowm.osrsllm.banktags.BankTagBackupService,
    private val eventLogService: EventLogService,
    private val priceService: PriceService,
    private val transportService: TransportService,
    private val poiService: PoiService,
    private val xpRateService: XpRateService,
    private val lootService: LootService,
    private val hitsplatHistoryService: HitsplatHistoryService,
    private val widgetTracker: WidgetTracker,
    private val widgetReader: WidgetReader,
    private val loadoutService: EquipmentLoadoutService,
    private val itemManager: ItemManager,
    private val itemTagIndex: ItemTagIndex,
    private val itemStatsResolver: co.rowm.osrsllm.items.ItemStatsResolver,
    private val pathfinderService: co.rowm.osrsllm.pathfinder.PathfinderService,
    private val tileMarkerService: co.rowm.osrsllm.tilemarker.TileMarkerService,
    private val questDatabase: co.rowm.osrsllm.quests.QuestDatabase,
    private val client: net.runelite.api.Client,
    private val clientThread: net.runelite.client.callback.ClientThread,
    private val slayerIntegration: co.rowm.osrsllm.integrations.SlayerIntegration,
    private val xpTrackerIntegration: co.rowm.osrsllm.integrations.XpTrackerIntegration,
    private val clueScrollIntegration: co.rowm.osrsllm.integrations.ClueScrollIntegration,
    private val partyIntegration: co.rowm.osrsllm.integrations.PartyIntegration,
    private val npcHpService: co.rowm.osrsllm.integrations.NpcHpService,
    private val playerNotifier: co.rowm.osrsllm.integrations.PlayerNotifier,
    private val userMarkers: co.rowm.osrsllm.integrations.UserMarkersService,
    private val gameDataCatalogs: co.rowm.osrsllm.integrations.GameDataCatalogs,
    private val chatOutput: co.rowm.osrsllm.integrations.ChatOutput,
    private val persistentMarkers: co.rowm.osrsllm.tilemarker.PersistentTileMarkerService,
    private val npcHighlights: co.rowm.osrsllm.highlights.NpcHighlightService,
    private val objectHighlights: co.rowm.osrsllm.highlights.ObjectHighlightService,
    private val groundItemHighlights: co.rowm.osrsllm.highlights.GroundItemHighlightService,
    private val inventoryHighlights: co.rowm.osrsllm.highlights.InventoryItemHighlightService,
) {

    private val log = LoggerFactory.getLogger(McpServerService::class.java)
    private val wikiService = WikiService()
    /** Default marker fill used when the agent doesn't pass an explicit color. */
    private val DEFAULT_PERSISTENT_HEX = "#DCE8AE4E"
    private var engine: EmbeddedServer<*, *>? = null
    private var boundHost: String? = null
    private var boundPort: Int? = null

    /**
     * Single source of truth for MCP tool names. Every `register*` helper appends here
     * so `allowedToolNames()` stays in sync — no need to maintain a duplicate list in
     * ClaudeRunner. Adding a new tool = call one of the helpers; the allow list updates
     * automatically.
     */
    private val toolNames = mutableListOf<String>()

    /** "mcp__osrs__<name>" entries for claude's --allowedTools flag. */
    fun allowedToolNames(): List<String> = toolNames.map { "mcp__osrs__$it" }

    @Synchronized
    fun start(host: String, port: Int) {
        if (engine != null) {
            log.debug("MCP server already running on {}:{}", boundHost, boundPort)
            return
        }
        val tools = OsrsTools(gameStateStore, itemTagIndex, itemStatsResolver)
        val mcpServer = buildServer(tools)
        engine = embeddedServer(Netty, host = host, port = port) {
            install(ContentNegotiation) { json(McpJson) }
            mcpStreamableHttp { mcpServer }
        }.also { it.start(wait = false) }
        boundHost = host
        boundPort = port
        log.info("MCP server listening on http://{}:{}/mcp", host, port)
    }

    @Synchronized
    fun stop() {
        engine?.let {
            log.info("Stopping MCP server on {}:{}", boundHost, boundPort)
            it.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        }
        engine = null
        boundHost = null
        boundPort = null
    }

    fun boundUrl(): String? {
        val h = boundHost ?: return null
        val p = boundPort ?: return null
        return "http://$h:$p/mcp"
    }

    @Synchronized
    fun restartWith(enabled: Boolean, host: String, port: Int) {
        val needsRestart = enabled && (boundHost != host || boundPort != port)
        if (!enabled) {
            stop()
        } else if (engine == null) {
            start(host, port)
        } else if (needsRestart) {
            stop()
            start(host, port)
        }
    }

    private fun buildServer(tools: OsrsTools): Server {
        val server = Server(
            serverInfo = Implementation(name = "osrs-llm-helper", version = "0.1.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )
        registerTools(server, tools)
        return server
    }

    private fun registerTools(server: Server, tools: OsrsTools) {
        // Manually-registered tools (have input schemas or custom error handling) that
        // don't go through addLoggedTool. TODO: migrate these to addArgTool / addFallibleTool
        // so name tracking is automatic on the registration call.
        toolNames.addAll(listOf(
            "get_bank", "get_quests", "get_nearby_npcs", "get_event_log",
            "get_ground_items", "get_nearby_objects",
            "find_location", "find_nearest_pois", "find_transport", "ge_price",
            "list_bank_tabs", "get_bank_tab", "create_bank_tab", "remove_bank_tab", "open_bank_tab",
            "create_grouped_bank_tab",
            "list_bank_tag_backups", "restore_bank_tag_backup",
            "wiki_search", "wiki_page", "get_quest_guide", "get_quest_data",
            "list_open_interfaces", "read_interface",
            "save_equipment_loadout",
            "find_item",
            "list_item_tags",
            "show_path_to", "mark_tile", "point_at", "clear_visuals",
            "best_in_slot_from_bank",
            "get_slayer_task", "get_xp_rates", "get_active_clue", "get_party",
            "notify_player", "chat_message",
            "find_agility_shortcut", "find_fishing_spot", "get_item_mapping",
            "list_user_markers",
            "add_persistent_markers", "remove_persistent_markers", "list_persistent_markers",
            "get_npc_max_hp",
        ))

        addLoggedTool(server, "get_inventory",
            "Get the current contents of the player's 28-slot inventory. " +
                "Returns each item's id, name, and quantity. Updates live whenever inventory changes."
        ) { tools.inventory() }

        server.addTool(
            name = "get_bank",
            description = "Inspect the bank. WITHOUT args: summary (totals + top N by quantity). " +
                "WITH `query`: single substring search. " +
                "WITH `queries` (array): BATCHED substring search — one call returns matches for every " +
                "term in `data.results[*]`. " +
                "WITH `categories` (array): filter by wiki-sourced tags — `food`, `potions`, `runes`, " +
                "`ammunition`, `teleportation_items`, `capes`, `boots`, `amulets`, etc. Call " +
                "`list_item_tags` to discover the full taxonomy + counts; the most useful tags are in " +
                "`primaryTags`. `categories` and `queries` can both be set in the same call. " +
                "RULE: for multiple lookups, put EVERY term in a single `queries`/`categories` array. " +
                "No penalty for 50+ entries — the bank scan is O(n) once. Splitting across multiple " +
                "get_bank calls wastes a roundtrip each.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Single substring of item name (e.g. 'shark'). Omit for summary mode. Use `queries` instead when looking up multiple things.")
                    }
                    putJsonObject("queries") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put("description", "Batch of substrings, e.g. ['ring of','karil','prayer potion']. Returns one section per query in `data.results[]`. Preferred over multiple sequential get_bank calls.")
                    }
                    putJsonObject("categories") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put("description", "Filter by item tags (wiki-sourced). Examples: 'food', 'potions', 'runes', 'ammunition', 'teleportation_items', 'capes'. Call list_item_tags for the full set.")
                    }
                    putJsonObject("includeStats") {
                        put("type", "boolean")
                        put("description", "Include equipment stats inline per item (slot, atk/def bonuses, str/rstr/prayer/mdmg/aspeed, weight). Default false. Set true when comparing/ranking equippable items.")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results PER query (default 25, hard cap 100). Applies to summary topByQuantity and each search.")
                    }
                },
            ),
        ) { request ->
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 25).coerceIn(1, 100)
            val queriesArg = request.arguments?.get("queries") as? kotlinx.serialization.json.JsonArray
            val queries: List<String> = queriesArg
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
            val categoriesArg = request.arguments?.get("categories") as? kotlinx.serialization.json.JsonArray
            val categories: List<String> = categoriesArg
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
            val singleQuery = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val includeStats = request.arguments?.get("includeStats")?.jsonPrimitive?.content
                ?.equals("true", ignoreCase = true) == true
            val start = System.currentTimeMillis()
            val result = when {
                categories.isNotEmpty() -> tools.banksByCategory(categories, limit, includeStats)
                queries.isNotEmpty() -> tools.banks(queries, limit, includeStats)
                else -> tools.bank(singleQuery, limit, includeStats)
            }
            log.info("Tool get_bank(query='{}', queries={}, categories={}, includeStats={}, limit={}) → {} chars in {}ms",
                singleQuery, queries, categories, includeStats, limit, result.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(result)))
        }

        addLoggedTool(server, "get_equipment",
            "Get the player's worn equipment by slot (HEAD, CAPE, AMULET, WEAPON, BODY, " +
                "SHIELD, LEGS, GLOVES, BOOTS, RING, AMMO)."
        ) { tools.equipment() }

        addLoggedTool(server, "get_stats",
            "Get the player's skill levels — real level, currently boosted level, and total " +
                "experience for each skill."
        ) { tools.stats() }

        addLoggedTool(server, "get_slayer_task",
            "Get the player's current slayer task: master, creature, remaining kills, " +
                "streak, slayer points. Returns not-ready if no active task. " +
                "creatureId is Jagex's slayer creature index — wiki_search the master + 'task list' to translate."
        ) { tools.slayerTask() }

        addLoggedTool(server, "get_ge_offers",
            "Get the player's 8 Grand Exchange slots. Each slot reports state " +
                "(BUYING/SELLING/BOUGHT/SOLD/CANCELLED_BUY/CANCELLED_SELL), item ID + name, " +
                "filled vs total quantity, price-per-item, total spent. Empty slots aren't returned."
        ) { tools.geOffers() }

        server.addTool(
            name = "get_ground_items",
            description = "Items lying on the ground within ~15 tiles of the player. Use this for " +
                "'is there anything to loot near me'. Returns id, name, quantity, distance, coords. " +
                "Pass `query` to substring-match name (e.g. 'rune', 'arrow').",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Optional substring filter (case-insensitive)")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max items (1-30, default 30)")
                    }
                },
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 30).coerceIn(1, 30)
            val result = tools.groundItems(q, limit)
            log.info("Tool get_ground_items(q='{}', limit={}) → {} chars", q, limit, result.length)
            CallToolResult(content = listOf(TextContent(result)))
        }

        server.addTool(
            name = "get_nearby_objects",
            description = "Interactive game objects within ~12 tiles of the player (banks, doors, " +
                "altars, ladders, ranges, ore rocks, trees, etc.). Each entry includes its name, " +
                "available actions (e.g. ['Use', 'Climb-up']), distance, and coords. Use to find " +
                "specific in-game objects to interact with. Pass `query` to substring-match.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Optional substring filter (e.g. 'bank', 'altar', 'ladder')")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max objects (1-25, default 25)")
                    }
                },
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 25).coerceIn(1, 25)
            val result = tools.nearbyObjects(q, limit)
            log.info("Tool get_nearby_objects(q='{}', limit={}) → {} chars", q, limit, result.length)
            CallToolResult(content = listOf(TextContent(result)))
        }

        addLoggedTool(server, "get_combat_info",
            "Combat state: current player animation ID (what they're doing) and target details " +
                "if interacting (target name, combat level, health ratio + scale + computed %, " +
                "target animation, target distance). Use for boss fights — check `healthPercent` " +
                "to know how low the enemy is."
        ) { tools.combat() }

        addLoggedTool(server, "get_diary_progress",
            "Achievement diary completion per region (Ardougne, Desert, Falador, Fremennik, " +
                "Kandarin, Karamja, Kourend & Kebos, Lumbridge & Draynor, Morytania, Varrock, " +
                "Western Provinces, Wilderness). Each region shows easy/medium/hard/elite " +
                "completion booleans plus a tiersComplete count. Useful for diary cape goals, " +
                "Ardougne cloak teleports, Karamja gloves perks, etc."
        ) { tools.diary() }

        addLoggedTool(server, "get_spellbook",
            "Current spellbook (Standard, Ancient, Lunar, Arceuus). Relevant for spell-cast " +
                "tools and which teleports/curses are available."
        ) { tools.spellbook() }

        addLoggedTool(server, "get_attack_style",
            "Current attack style index (0-3) + equipped weapon type id + a brief description. " +
                "Combined with get_equipment this tells you what combat style the player is using."
        ) { tools.attackStyle() }

        addLoggedTool(server, "get_xp_rates",
            "Per-skill XP gained this session + extrapolated XP/hour. Session starts on plugin " +
                "load. Use for 'how's my training going' or 'what's my XP rate on slayer'."
        ) {
            WikiService.outputJson.encodeToString(XpRateSnapshot.serializer(), xpRateService.snapshot())
        }

        addLoggedTool(server, "get_session_loot",
            "All NPC drops received this session, aggregated per NPC. Each NPC entry has kill " +
                "count + total items received with quantities. Combine with ge_price for value " +
                "calculations."
        ) {
            WikiService.outputJson.encodeToString(LootSnapshot.serializer(), lootService.snapshot())
        }

        addLoggedTool(server, "get_hitsplat_history",
            "Last ~200 hitsplats involving the player, newest first. Each event has direction " +
                "(in/out), amount, type id (HitsplatID — wiki-lookup if needed: 0=DAMAGE_ME, " +
                "65=POISON, 5=VENOM, 74=BURN, etc.), target name, and timestamp. Also reports " +
                "lifetime damage dealt/taken totals."
        ) {
            WikiService.outputJson.encodeToString(
                HitsplatHistorySnapshot.serializer(), hitsplatHistoryService.snapshot())
        }

        addLoggedTool(server, "get_combat_achievements",
            "Combat Achievement tier completion: easy/medium/hard/elite/master/grandmaster booleans " +
                "+ tiersComplete count. Useful for CA-gated unlocks (e.g. elite void, max cape options)."
        ) { tools.combatAchievements() }

        addLoggedTool(server, "get_poh",
            "Player-owned house location id + name (Rimmington, Taverley, Pollnivneach, Rellekka, " +
                "Brimhaven, Yanille, Hosidius, Prifddinas, Aldarin, Civitas illa Fortis). " +
                "Returns not-ready if the player hasn't built a house yet."
        ) { tools.poh() }

        addLoggedTool(server, "get_buffs",
            "Get active buff timers (in ticks; multiply by 0.6 for seconds): stamina, " +
                "antifire, super antifire, vengeance (active/cooldown), imbued heart cooldown, " +
                "death charge cooldown, divine super combat/range/magic, NMZ/COX overload refreshes, poison."
        ) { tools.buffs() }

        addLoggedTool(server, "get_player_state",
            "Get the player's current state: name, combat level, world, position, hitpoints, " +
                "prayer, run energy, and special attack energy."
        ) { tools.player() }

        // ── RAI-5 Tier 0 state-probes (catalog §3) ──────────────────────────
        // All four read-only. Implementations live in `cloud/tools/StateProbes.kt`
        // so they're trivially re-usable by the cloud→local tool dispatcher
        // when it lands (today the cloud path is StubToolDispatcher).
        addLoggedTool(server, "get_account_identity",
            "First-turn account anchor. Returns {name, accountType (NORMAL/IRONMAN/" +
                "ULTIMATE_IRONMAN/HARDCORE_IRONMAN/GROUP_IRONMAN/HARDCORE_GROUP_IRONMAN), " +
                "isIronman, isGroupIronman, world, worldFlags[] (MEMBERS/PVP/DEADMAN/" +
                "SEASONAL/TOURNAMENT_WORLD/...), worldHost, launcherName}. The LLM should " +
                "call this BEFORE giving any account-type-specific advice (e.g. don't " +
                "suggest GE trades to an Ultimate Iron, don't risk HCIM at Vorkath turn 1)."
        ) {
            try { co.rowm.osrsllm.cloud.tools.StateProbes.accountIdentity(client, clientThread) }
            catch (t: Throwable) { "{\"error\":\"get_account_identity failed: ${t.message}\"}" }
        }

        addLoggedTool(server, "get_raid_layout",
            "Read the active raid state — CoX / ToB / ToA. Returns {activeRaid (NONE/COX/" +
                "TOB/TOA), mapRegions[], coxInRaid, coxState, coxTotalPoints, tobState, " +
                "tobPartyOrbs[5] (alive/dead/dc), toaInvocation (e.g. 300/500), " +
                "toaPartyDamage, toaPartyHp[8]}. activeRaid==NONE outside a raid; in that " +
                "case the numeric varbits all read 0 and the LLM can short-circuit. Use for " +
                "phase / room / scaling questions (Verzik phase X, Akkha sand crab, Olm " +
                "head-phase mage / range, etc.)."
        ) {
            try { co.rowm.osrsllm.cloud.tools.StateProbes.raidLayout(client, clientThread) }
            catch (t: Throwable) { "{\"error\":\"get_raid_layout failed: ${t.message}\"}" }
        }

        addLoggedTool(server, "get_target_projectiles",
            "Read projectiles currently flying toward the local player or their current " +
                "interaction target — the data behind 'prayer flick' advice. Returns up to " +
                "8 entries of {id, remainingTicks (game ticks, not client cycles), " +
                "targetIsLocalPlayer, targetName, sourceName}. Empty array == nothing " +
                "incoming. Pair with get_active_prayers / get_event_log to decide flick " +
                "timing for boss attacks (Akkha mage/range, Verzik dawn, Vorkath dragonfire)."
        ) {
            try { co.rowm.osrsllm.cloud.tools.StateProbes.targetProjectiles(client, clientThread) }
            catch (t: Throwable) { "{\"error\":\"get_target_projectiles failed: ${t.message}\"}" }
        }

        addLoggedTool(server, "get_active_prayers",
            "Read the prayers currently active on the local player. Returns {active[] " +
                "(standard book), ruinous[] (Ruinous Powers book), prayerPoints (current), " +
                "prayerMax (real level), quickPrayer (true if quick-prayer is toggled on)}. " +
                "Names are human-readable ('Piety', 'Protect from magic', not the enum " +
                "form). Use to verify the player is on the right prayer before / during " +
                "combat — pairs with get_target_projectiles for tick-perfect flick advice."
        ) {
            try { co.rowm.osrsllm.cloud.tools.StateProbes.activePrayers(client, clientThread) }
            catch (t: Throwable) { "{\"error\":\"get_active_prayers failed: ${t.message}\"}" }
        }

        // ── RAI-5 Tier 0 (5/5) — farming probes split into summary + detail.
        // Implementation in cloud/tools/StateProbes.kt + FarmingTables.kt. The
        // upstream `timetracking.farming` package is package-private so we
        // mirror a slice of its patch→varbit map; see FarmingTables KDoc for
        // covered regions and v2 deferred items.
        addLoggedTool(server, "get_farming_summary",
            "Region-agnostic farming snapshot. Returns " +
                "{ready, growing, diseased, dead, emptyPatches, unknown, total, note}. " +
                "Use to answer 'do I have anything to harvest' / 'are any of my patches " +
                "diseased'. Reads live transmit varbits across every known region — " +
                "patches in regions the player hasn't visited recently will report 0 " +
                "(EMPTY). For per-region detail call get_farming_patches(region=)."
        ) {
            try { co.rowm.osrsllm.cloud.tools.StateProbes.farmingSummary(client, clientThread) }
            catch (t: Throwable) { "{\"error\":\"get_farming_summary failed: ${t.message}\"}" }
        }

        server.addTool(
            name = "get_farming_patches",
            description = "Per-region farming patch detail. Required arg `region` (one of: " +
                "Catherby, Falador, Morytania, Ardougne, Hosidius, Farming Guild, Gnome " +
                "Stronghold, Tree Gnome Village, Brimhaven, Lletya, Lumbridge, Varrock, " +
                "Taverley, Trollheim, Weiss, Prifddinas, Civitas illa Fortis — common " +
                "aliases like 'ardy' / 'kourend' also accepted). Returns " +
                "{region, patches: [{patchName, type, state, rawVarbit}]}. State is one " +
                "of READY/GROWING/DISEASED/DEAD/EMPTY/UNKNOWN. Use for 'are my Catherby " +
                "herbs ready' / 'anything in the Farming Guild fruit tree patch'.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("region") {
                        put("type", "string")
                        put("description", "Region name (canonical or alias, case-insensitive)")
                    }
                },
                required = listOf("region"),
            ),
        ) { request ->
            val region = request.arguments?.get("region")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val start = System.currentTimeMillis()
            val result = if (region == null) {
                "{\"error\":\"get_farming_patches requires a 'region' argument\"," +
                    "\"knownRegions\":${
                        co.rowm.osrsllm.cloud.tools.FarmingTables.knownRegionNames
                            .joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    }}"
            } else {
                try {
                    co.rowm.osrsllm.cloud.tools.StateProbes.farmingPatches(client, clientThread, region)
                } catch (t: Throwable) {
                    "{\"error\":\"get_farming_patches failed: ${t.message}\"}"
                }
            }
            log.info("Tool get_farming_patches(region='{}') → {} chars in {}ms",
                region, result.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(result)))
        }

        server.addTool(
            name = "get_quests",
            description = "Get quest progress. Optional argument: state (one of ALL, IN_PROGRESS, " +
                "NOT_STARTED, FINISHED). Defaults to IN_PROGRESS to keep responses focused.",
        ) { request ->
            val stateArg = request.arguments?.get("state")?.jsonPrimitive?.content?.uppercase()
            val filter = runCatching { OsrsTools.QuestFilter.valueOf(stateArg ?: "IN_PROGRESS") }
                .getOrDefault(OsrsTools.QuestFilter.IN_PROGRESS)
            val start = System.currentTimeMillis()
            val result = tools.quests(filter)
            log.info("Tool get_quests(state={}) → {} chars in {}ms",
                filter, result.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(result)))
        }

        server.addTool(
            name = "get_event_log",
            description = "Read recent in-game events as a compact plain-text feed, newest first, " +
                "each line prefixed with a relative time like `[3m ago]`. The log captures damage " +
                "in/out, XP gains, level-ups, kills, deaths, login/logout, and chat messages. " +
                "Use this for 'what just happened', 'did I take damage', 'how much XP gained', " +
                "'recent chat from X'. Pass `query` for case-insensitive substring search across " +
                "summaries — e.g. 'damage' matches both took + dealt; 'killed' finds kills; " +
                "a player name finds chat from them. The log is bounded (~300 events).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("sinceSeconds") {
                        put("type", "integer")
                        put("description", "Only events within the last N seconds (default 300 = 5 min, 0 = no time limit)")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Optional substring to search event summaries (case-insensitive)")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max events (default 30, max 200)")
                    }
                },
            ),
        ) { request ->
            val sinceSeconds = request.arguments?.get("sinceSeconds")?.jsonPrimitive?.intOrNull ?: 300
            val sinceMs = if (sinceSeconds > 0) System.currentTimeMillis() - sinceSeconds * 1000L else null
            val query = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 30
            val text = eventLogService.queryText(sinceMs, query, limit)
            log.info("Tool get_event_log(sinceSec={}, query='{}', limit={}) → {} chars",
                sinceSeconds, query, limit, text.length)
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "find_location",
            description = "Look up a named location anywhere we know about — banks, altars, " +
                "farming patches, transport destinations, etc. Substring matches the query " +
                "against POI names AND transport destinations. Returns coords + source. Use this " +
                "when the user mentions a specific place ('Draynor farming patch', " +
                "'Edgeville bank', 'Lletya') and you need its coordinates. The player's " +
                "current position is included if known so you can sort by distance.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Place name or partial name, e.g. 'Draynor herb', 'Edgeville', 'Lletya', 'Camelot bank'")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max matches (1-30, default 10)")
                    }
                },
                required = listOf("query"),
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content.orEmpty()
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 30)
            val snap = gameStateStore.snapshot()
            val playerX = snap.player?.locationX
            val playerY = snap.player?.locationY

            data class Hit(
                val name: String,
                val source: String,
                val x: Int,
                val y: Int,
                val plane: Int,
                val tileDistance: Int?,
                val notes: String?,
            )

            val poiHits = poiService.searchByName(q, limit * 2).map {
                val dist = if (playerX != null && playerY != null) {
                    kotlin.math.max(kotlin.math.abs(it.x - playerX), kotlin.math.abs(it.y - playerY))
                } else null
                Hit(it.name, "poi:${it.type}", it.x, it.y, it.plane, dist, it.notes)
            }
            val transportHits = transportService.searchDestinations(q, limit * 2).map {
                val dist = if (playerX != null && playerY != null) {
                    kotlin.math.max(kotlin.math.abs(it.destination.x - playerX),
                        kotlin.math.abs(it.destination.y - playerY))
                } else null
                Hit(it.name, "transport:${it.category}",
                    it.destination.x, it.destination.y, it.destination.plane, dist, null)
            }
            val combined = (poiHits + transportHits)
                .distinctBy { "${it.x}:${it.y}:${it.plane}:${it.name.lowercase()}" }
                .sortedBy { it.tileDistance ?: Int.MAX_VALUE }
                .take(limit)

            // Build response JSON manually to avoid yet another @Serializable wrapper
            val sb = StringBuilder()
            sb.append("{\"query\":\"").append(q.replace("\"", "\\\"")).append("\",")
            sb.append("\"fromX\":").append(playerX ?: "null").append(",")
            sb.append("\"fromY\":").append(playerY ?: "null").append(",")
            sb.append("\"hits\":[")
            combined.forEachIndexed { i, h ->
                if (i > 0) sb.append(",")
                sb.append("{\"name\":\"").append(h.name.replace("\"", "\\\"")).append("\"")
                sb.append(",\"source\":\"").append(h.source).append("\"")
                sb.append(",\"x\":").append(h.x).append(",\"y\":").append(h.y).append(",\"plane\":").append(h.plane)
                if (h.tileDistance != null) {
                    sb.append(",\"tileDistance\":").append(h.tileDistance)
                    // Rough ticks to run there from current pos: ceil(dist / 2) — assumes
                    // energy + ignores obstacles. Lets the agent compare "just run" vs
                    // teleport options without doing the math itself.
                    sb.append(",\"runTicks\":").append((h.tileDistance + 1) / 2)
                }
                if (h.notes != null) sb.append(",\"notes\":\"").append(h.notes.replace("\"", "\\\"")).append("\"")
                sb.append("}")
            }
            sb.append("]}")
            log.info("Tool find_location(q='{}', limit={}) → {} hits", q, limit, combined.size)
            CallToolResult(content = listOf(TextContent(sb.toString())))
        }

        server.addTool(
            name = "find_nearest_pois",
            description = "Find the nearest points-of-interest of a given type relative to the " +
                "player's current location. Returned results are sorted by tile distance " +
                "(Chebyshev). Combine with find_transport to plan a route: a far bank you can " +
                "teleport near is usually quicker than a close one on foot. Currently supported " +
                "types: bank. (More types coming.)",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("type") {
                        put("type", "string")
                        put("description", "POI type to search for, e.g. 'bank'")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results (1-20, default 5)")
                    }
                },
                required = listOf("type"),
            ),
        ) { request ->
            val type = request.arguments?.get("type")?.jsonPrimitive?.content.orEmpty()
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 20)
            val response = poiService.findNearest(type, limit)
            val out = WikiService.outputJson.encodeToString(
                PoiSearchResponse.serializer(), response)
            log.info("Tool find_nearest_pois(type='{}', limit={}) → {} hits",
                type, limit, response.results.size)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "find_transport",
            description = "Find ways the player can travel — teleport tabs/items/spells/jewelry, " +
                "spirit trees, fairy rings, gnome gliders, minecarts, boats, etc. Filtered to " +
                "what's actually usable: inventory + equipment + bank scanned, quests + skills " +
                "checked. Sorted by readiness then duration. " +
                "Hit shape: {name, destination, durationTicks, runTicksFromPlayer?, tileDistanceFromPlayer?, consumable, readiness, use?, missing?, met?}. " +
                "`runTicksFromPlayer` is a rough estimate of how long it'd take to just RUN there " +
                "from the player's current spot (Chebyshev / 2 ticks, ignoring obstacles). If it's " +
                "smaller than `durationTicks`, tell the player to walk rather than teleport. " +
                "Null when the player isn't logged in or the destination is on a different plane." +
                "  readiness='ready'      → use immediately. `use` lists ItemUse {id,name,location,count}. " +
                "  readiness='bank-only'  → at least one item in bank only; withdraw first. " +
                "  readiness='locked'     → only with includeUnavailable=true. `missing` lists blockers " +
                "                            ({type:'skill',skill,needed,have} | {type:'quest',quest} | {type:'item',item}). " +
                "RULE: When answering 'how do I get to X', recommend ONLY what's in results[]; the " +
                "tool has already filtered. Tell the player WHICH item to use (from `use`) and " +
                "where it is. Don't supplement from general knowledge. " +
                "Pass `destination` to substring-match. " +
                "Set `includeUnavailable=true` when the player asks 'what could I unlock' / 'why can't " +
                "I use X'. Set `verbose=true` to also get `met[]` (satisfied requirements) — useful " +
                "when explaining WHY an option is or isn't ready.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("destination") {
                        put("type", "string")
                        put("description", "Substring of destination/transport name. Omit to list all available transports.")
                    }
                    putJsonObject("includeUnavailable") {
                        put("type", "boolean")
                        put("description", "Include locked transports (readiness='locked' with missing reqs). Default false.")
                    }
                    putJsonObject("verbose") {
                        put("type", "boolean")
                        put("description", "Also include `met[]` per hit: satisfied skill/quest/item requirements. Default false (kept lean for the common case).")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results (1-50, default 20)")
                    }
                },
            ),
        ) { request ->
            val dest = request.arguments?.get("destination")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val includeUnavailable = request.arguments?.get("includeUnavailable")?.jsonPrimitive?.content
                ?.equals("true", ignoreCase = true) == true
            val verbose = request.arguments?.get("verbose")?.jsonPrimitive?.content
                ?.equals("true", ignoreCase = true) == true
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 50)
            val start = System.currentTimeMillis()
            val response = transportService.search(dest, includeUnavailable, limit, verbose)
            val out = WikiService.outputJson.encodeToString(
                TransportSearchResponse.serializer(), response)
            log.info("Tool find_transport(dest='{}', unavailable={}, verbose={}, limit={}) → matched={}, returned={} in {}ms",
                dest, includeUnavailable, verbose, limit, response.matched, response.results.size,
                System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "ge_price",
            description = "Look up live OSRS Grand Exchange prices via the wiki realtime API. " +
                "Pass an item name (or partial); returns matching items with their current high " +
                "(insta-buy) price, low (insta-sell) price, average, and how many minutes since " +
                "each side last traded. Use for 'how much is X worth', 'value of my drop', " +
                "'cheapest scimitar', etc. Untradeable items won't appear.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Item name or partial name, e.g. 'whip', 'dragon scimitar', 'shark'")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max matches to price (1-15, default 5)")
                    }
                },
                required = listOf("query"),
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content.orEmpty()
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 5
            val response = try {
                priceService.lookup(q, limit)
            } catch (t: Throwable) {
                log.warn("ge_price failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("ge_price failed: ${t.message}")),
                    isError = true,
                )
            }
            val out = WikiService.outputJson.encodeToString(
                PriceLookupResponse.serializer(), response)
            log.info("Tool ge_price(query='{}', limit={}) → {} items, {} chars",
                q, limit, response.items.size, out.length)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_nearby_npcs",
            description = "List NPCs visible near the player on the same floor (chebyshev tile distance). " +
                "Sorted nearest-first. Useful for 'what's around me', combat targeting, locating shopkeepers.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("maxDistance") {
                        put("type", "integer")
                        put("description", "Max tile distance (1-25, default 25)")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max NPCs returned (1-30, default 30)")
                    }
                },
            ),
        ) { request ->
            val maxDist = request.arguments?.get("maxDistance")?.jsonPrimitive?.intOrNull?.coerceIn(1, 25)
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 30).coerceIn(1, 30)
            val result = tools.nearbyNpcs(maxDist, limit)
            log.info("Tool get_nearby_npcs(maxDist={}, limit={}) → {} chars", maxDist, limit, result.length)
            CallToolResult(content = listOf(TextContent(result)))
        }

        // Bank tag tools
        server.addTool(
            name = "list_bank_tabs",
            description = "List all existing bank tag tabs (used by RuneLite's Bank Tags plugin). " +
                "Returns each tab with its name, item count, and icon item ID.",
        ) { _ ->
            val out = try {
                val tabs = bankTagService.listTabs()
                WikiService.outputJson.encodeToString(
                    ListSerializer(BankTagService.TabSummary.serializer()), tabs)
            } catch (t: Throwable) {
                log.warn("list_bank_tabs failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("list_bank_tabs failed: ${t.message}")),
                    isError = true,
                )
            }
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_bank_tab",
            description = "Read one bank tag tab — returns its tagged item IDs with names. " +
                "Use to inspect what's in a tab before modifying it.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Tab name (case-insensitive)")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content.orEmpty()
            val out = try {
                val detail = bankTagService.getTab(name)
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("No tab named '$name'")),
                        isError = true,
                    )
                WikiService.outputJson.encodeToString(BankTagService.TabDetail.serializer(), detail)
            } catch (t: Throwable) {
                log.warn("get_bank_tab failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("get_bank_tab failed: ${t.message}")),
                    isError = true,
                )
            }
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "create_bank_tab",
            description = "Create or update a bank tag tab. Tags each given item with the tab " +
                "name and creates/updates the tab in the Bank Tags plugin. Existing tags on " +
                "those items are kept.\n\n" +
                "Two input shapes:\n" +
                "  1. `itemIds: [id, id, ...]` — plain id list (no per-item notes or variations).\n" +
                "  2. `items: [{id, note?, variation?}, ...]` — richer entries. `note` shows in " +
                "get_bank_tab. `variation: true` tags the WHOLE variation family of that item, " +
                "not just the specific id — so passing one Saradomin platebody with variation=true " +
                "makes the tab match every Saradomin armour piece the player owns. Use variation " +
                "for quest items that say 'any pickaxe', 'any rune', 'any god item', etc. — pair " +
                "with find_item's `variants` field to know which ids have a family worth grouping.\n\n" +
                "Notes are sidecar (Bank Tags has no native note storage) but persist across " +
                "sessions and replace any prior notes on this tab.\n\n" +
                "If both shapes are present, `items` wins. Pair with find_item (batched) to " +
                "resolve names → ids first. Every write is auto-backed-up — see " +
                "list_bank_tag_backups / restore_bank_tag_backup.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Tab name, e.g. 'Dragon Slayer prep'")
                    }
                    putJsonObject("itemIds") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "integer") }
                        put("description", "Item IDs (no notes/variations). Use `items` for either.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") { put("type", "integer") }
                                putJsonObject("note") { put("type", "string") }
                                putJsonObject("variation") {
                                    put("type", "boolean")
                                    put("description", "Tag the whole variation family of this item, not just the specific id. Default false.")
                                }
                            }
                            put("required", kotlinx.serialization.json.JsonArray(
                                listOf(kotlinx.serialization.json.JsonPrimitive("id"))))
                        }
                        put("description", "Items with optional per-item notes and variation flag.")
                    }
                    putJsonObject("iconItemId") {
                        put("type", "integer")
                        put("description", "Item ID for the tab's icon. Defaults to the first item.")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content.orEmpty()
            data class RichItem(val id: Int, val note: String?, val variation: Boolean)
            val richItems: List<RichItem>? = (request.arguments?.get("items") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val note = obj["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val variation = obj["variation"]?.jsonPrimitive?.content
                        ?.equals("true", ignoreCase = true) == true
                    RichItem(id, note, variation)
                }
            val itemIds: List<Int> = richItems?.map { it.id }
                ?: (request.arguments?.get("itemIds") as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }
                ?: emptyList()
            if (itemIds.isEmpty()) {
                return@addTool err("create_bank_tab requires 'itemIds' or 'items'")
            }
            val icon = request.arguments?.get("iconItemId")?.jsonPrimitive?.intOrNull
            val notesMap: Map<Int, String>? = richItems
                ?.mapNotNull { ri -> ri.note?.let { ri.id to it } }
                ?.toMap()
                ?.takeIf { it.isNotEmpty() }
            val variationIds: Set<Int> = richItems
                ?.filter { it.variation }
                ?.map { it.id }
                ?.toSet() ?: emptySet()
            val out = try {
                val detail = bankTagService.createOrUpdateTab(name, itemIds, icon, notesMap, variationIds)
                WikiService.outputJson.encodeToString(BankTagService.TabDetail.serializer(), detail)
            } catch (t: Throwable) {
                log.warn("create_bank_tab failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("create_bank_tab failed: ${t.message}")),
                    isError = true,
                )
            }
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "remove_bank_tab",
            description = "Delete a bank tag tab and remove its tag from all items.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") { put("type", "string") }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content.orEmpty()
            val ok = try {
                bankTagService.removeTab(name)
            } catch (t: Throwable) {
                log.warn("remove_bank_tab failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("remove_bank_tab failed: ${t.message}")),
                    isError = true,
                )
            }
            CallToolResult(content = listOf(TextContent(
                if (ok) "{\"removed\":\"$name\"}" else "{\"error\":\"no tab named '$name'\"}",
            )))
        }

        server.addTool(
            name = "open_bank_tab",
            description = "Switch the bank's active view to a tab (the player must have the bank open).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") { put("type", "string") }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content.orEmpty()
            try { bankTagService.openTab(name) } catch (t: Throwable) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("open_bank_tab failed: ${t.message}")),
                    isError = true,
                )
            }
            CallToolResult(content = listOf(TextContent("{\"opened\":\"$name\"}")))
        }

        server.addTool(
            name = "list_bank_tag_backups",
            description = "List rolling snapshots of the player's bank tag configuration. " +
                "We auto-snapshot before every tag/layout/note write — the most recent 10 are " +
                "kept in ~/.runelite/osrs-llm-helper/banktag-backups/. Each entry includes " +
                "{file, timestamp, tabCount, sizeBytes} sorted newest-first. " +
                "Use restore_bank_tag_backup with one of these `file` names to roll back.",
        ) { _ ->
            val out = try {
                val list = bankTagBackupService.listBackups()
                WikiService.outputJson.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        co.rowm.osrsllm.banktags.BankTagBackupService.BackupSummary.serializer()),
                    list,
                )
            } catch (t: Throwable) {
                log.warn("list_bank_tag_backups failed", t)
                return@addTool err("list_bank_tag_backups failed: ${t.message}")
            }
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "restore_bank_tag_backup",
            description = "Roll back the player's bank tag configuration to a snapshot from " +
                "list_bank_tag_backups. A FRESH backup of the current state is taken before " +
                "the restore (so the restore itself is undoable via another restore call).\n\n" +
                "Modes:\n" +
                "  - 'merge' (default): re-apply tabs/layouts/notes from the backup additively. " +
                "Tabs that exist now but weren't in the backup are left alone. Use this when you " +
                "want to recover something the agent (or the user) deleted, without disturbing " +
                "newer tabs.\n" +
                "  - 'replace': WIPE all current tabs first, then apply the backup. Use this when " +
                "you want the bank to look EXACTLY like the snapshot. Destructive — the most " +
                "recent auto-backup is the only safety net.\n" +
                "Returns: {file, timestamp, mode, tabsCreated[], tabsUpdated[]}.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("file") {
                        put("type", "string")
                        put("description", "Backup filename from list_bank_tag_backups (e.g. 'banktags-20260516-143012.json').")
                    }
                    putJsonObject("mode") {
                        put("type", "string")
                        put("description", "'merge' (default, additive) or 'replace' (wipe-first).")
                    }
                },
                required = listOf("file"),
            ),
        ) { request ->
            val file = request.arguments?.get("file")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool err("restore_bank_tag_backup requires 'file'")
            val mode = when (request.arguments?.get("mode")?.jsonPrimitive?.content?.lowercase()) {
                null, "merge" -> co.rowm.osrsllm.banktags.BankTagBackupService.RestoreMode.MERGE
                "replace" -> co.rowm.osrsllm.banktags.BankTagBackupService.RestoreMode.REPLACE
                else -> return@addTool err("mode must be 'merge' or 'replace'")
            }
            val out = try {
                val result = bankTagBackupService.restore(file, mode)
                WikiService.outputJson.encodeToString(
                    co.rowm.osrsllm.banktags.BankTagBackupService.RestoreResult.serializer(), result)
            } catch (t: Throwable) {
                log.warn("restore_bank_tag_backup failed", t)
                return@addTool err("restore_bank_tag_backup failed: ${t.message}")
            }
            log.info("Tool restore_bank_tag_backup file='{}' mode={}", file, mode)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "create_grouped_bank_tab",
            description = "Create a bank tag with a ROW-GROUPED layout. Pass `groups` as an array of " +
                "arrays of item ids: each inner array becomes a horizontal band of items in the bank " +
                "view, separated from the next group by one blank row. Groups with more than 8 items " +
                "wrap onto multiple rows automatically (the grid is 8 wide). " +
                "Use this when items should be VISUALLY GROUPED — e.g. 'all items needed for quest X, " +
                "grouped by step', 'slayer task supplies, grouped by category', 'PVM consumables, " +
                "grouped by tier'. " +
                "Optionally pass `notes` as a map of itemId → free-text note (e.g. 'Required: " +
                "3-dose', 'Recommended — anti-dragon shield'); shown in get_bank_tab. Notes " +
                "replace any prior notes on this tab.\n\n" +
                "Optionally pass `variations` as a list of item ids that should be tagged " +
                "variation-wide — the tab will then match every variant of those items the " +
                "player owns (e.g. one Saradomin platebody id with variation matches all " +
                "Saradomin armour). Use for 'any pickaxe', 'any rune', etc. find_item returns " +
                "a `variants` count so you know which ids have a family.\n\n" +
                "For an equipment loadout (worn slots on the left, inventory on the right), use " +
                "save_equipment_loadout instead. " +
                "Inspect later with get_bank_tab; remove with remove_bank_tab. " +
                "Tip: use find_item (queries[]) to resolve all ids in one call before calling this.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Tab name, e.g. 'Dragon Slayer items', 'Slayer supplies'")
                    }
                    putJsonObject("groups") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "integer") }
                        }
                        put("description", "Array of arrays of item ids. Each inner array is one group laid out on its own row band, separated from the next group by 1 blank row. Groups > 8 items wrap to the next row inside the group.")
                    }
                    putJsonObject("notes") {
                        put("type", "object")
                        put("description", "Optional. Map of itemId (as string) → note string. Each note shows alongside the item in get_bank_tab.")
                    }
                    putJsonObject("variations") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "integer") }
                        put("description", "Optional. Item ids (must also appear in `groups`) that should be tagged variation-wide.")
                    }
                    putJsonObject("icon") {
                        put("type", "integer")
                        put("description", "Tab icon item id (defaults to the first item in the first group)")
                    }
                },
                required = listOf("name", "groups"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("create_grouped_bank_tab requires 'name'")),
                    isError = true,
                )
            val groupsArr = request.arguments?.get("groups") as? kotlinx.serialization.json.JsonArray
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("create_grouped_bank_tab requires 'groups' (array of arrays)")),
                    isError = true,
                )
            val groups: List<List<Int>> = groupsArr.mapNotNull { inner ->
                (inner as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
                }
            }
            val icon = request.arguments?.get("icon")?.jsonPrimitive?.intOrNull
            val notesObj = request.arguments?.get("notes") as? kotlinx.serialization.json.JsonObject
            val notes: Map<Int, String> = notesObj?.mapNotNull { (k, v) ->
                val id = k.toIntOrNull() ?: return@mapNotNull null
                val note = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                id to note
            }?.toMap() ?: emptyMap()
            val variationIds: Set<Int> = (request.arguments?.get("variations")
                as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }
                ?.toSet() ?: emptySet()
            try {
                val detail = bankTagService.createGroupedTab(name, groups, icon,
                    notes.takeIf { it.isNotEmpty() }, variationIds)
                val out = WikiService.outputJson.encodeToString(
                    BankTagService.TabDetail.serializer(), detail)
                log.info("Tool create_grouped_bank_tab name='{}' groups={} notes={} variations={}",
                    name, groups.size, notes.size, variationIds.size)
                CallToolResult(content = listOf(TextContent(out)))
            } catch (t: Throwable) {
                log.warn("create_grouped_bank_tab failed", t)
                CallToolResult(
                    content = listOf(TextContent("create_grouped_bank_tab failed: ${t.message}")),
                    isError = true,
                )
            }
        }

        server.addTool(
            name = "wiki_search",
            description = "Full-text search the OSRS Wiki and return matched snippets " +
                "(short excerpts with the query in context). The snippets often answer " +
                "common questions directly — only follow up with wiki_page if you need " +
                "more detail. Each hit has title, URL, and snippet.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query — full sentences and keywords both work, e.g. 'gemstone crab strategy', 'best weapon for slayer task'")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max hits (1-20, default 5)")
                    }
                },
                required = listOf("query"),
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content.orEmpty()
            val limit = request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 5
            val start = System.currentTimeMillis()
            val response: String = try {
                val out = wikiService.search(q, limit)
                WikiService.outputJson.encodeToString(WikiSearchResponse.serializer(), out)
            } catch (t: Throwable) {
                log.warn("wiki_search failed: {}", t.message)
                return@addTool CallToolResult(
                    content = listOf(TextContent("wiki_search failed: ${t.message}")),
                    isError = true,
                )
            }
            log.info("Tool wiki_search(query='{}', limit={}) → {} chars in {}ms",
                q, limit, response.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(response)))
        }

        server.addTool(
            name = "wiki_page",
            description = "Fetch an OSRS Wiki page as plain text + section TOC. " +
                "Without `section`: full extract (default cap 4000 chars) + section list. " +
                "With `section`: just that section's content. " +
                "Strategy: prefer wiki_search snippets first; if you need more, jump STRAIGHT to a " +
                "section by passing both `title` and `section` index — don't fetch the full page " +
                "then fetch a section, that's two roundtrips. " +
                "For quest help use get_quest_guide instead.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "Exact page title from wiki_search, e.g. 'Dragon Slayer I'")
                    }
                    putJsonObject("section") {
                        put("type", "string")
                        put("description", "Optional section index from the TOC (e.g. '2', '3.1'). Omit for full page.")
                    }
                    putJsonObject("maxChars") {
                        put("type", "integer")
                        put("description", "Max chars to return (default 4000, hard cap 12000). Increase for long strategy sections.")
                    }
                },
                required = listOf("title"),
            ),
        ) { request ->
            val title = request.arguments?.get("title")?.jsonPrimitive?.content.orEmpty()
            val sectionArg = request.arguments?.get("section")?.jsonPrimitive
            val section: String? = when {
                sectionArg == null -> null
                sectionArg.content.isBlank() -> null
                else -> sectionArg.content
            }
            val maxChars = (request.arguments?.get("maxChars")?.jsonPrimitive?.intOrNull ?: 4000)
                .coerceIn(500, 12000)
            val start = System.currentTimeMillis()
            val response: String = try {
                val out = wikiService.page(title, section, maxChars)
                WikiService.outputJson.encodeToString(WikiPageResponse.serializer(), out)
            } catch (t: Throwable) {
                log.warn("wiki_page failed: {}", t.message)
                return@addTool CallToolResult(
                    content = listOf(TextContent("wiki_page failed: ${t.message}")),
                    isError = true,
                )
            }
            log.info("Tool wiki_page(title='{}', section={}, maxChars={}) → {} chars in {}ms",
                title, section, maxChars, response.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(response)))
        }

        server.addTool(
            name = "get_quest_guide",
            description = "Fetch the Quick guide for a quest in ONE call. Equivalent to " +
                "wiki_page('<quest>/Quick guide') but with the right title formatting baked in so " +
                "you don't need a separate wiki_search. " +
                "Without `section`: returns the full Quick guide + section TOC (each major step " +
                "is usually one section). With `section`: drills into that step. " +
                "ALWAYS use this for quest help; don't fetch the full quest page (it's much longer " +
                "and has lore/spoilers the player doesn't need).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Quest name as it appears on the wiki, e.g. 'Dragon Slayer I', 'Song of the Elves'")
                    }
                    putJsonObject("section") {
                        put("type", "string")
                        put("description", "Optional Quick-guide step index from the TOC (e.g. '2', '3.1'). Omit to get the whole guide.")
                    }
                    putJsonObject("maxChars") {
                        put("type", "integer")
                        put("description", "Max chars to return (default 4000, hard cap 12000)")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("get_quest_guide requires 'name'")),
                    isError = true,
                )
            val sectionArg = request.arguments?.get("section")?.jsonPrimitive
            val section: String? = when {
                sectionArg == null -> null
                sectionArg.content.isBlank() -> null
                else -> sectionArg.content
            }
            val maxChars = (request.arguments?.get("maxChars")?.jsonPrimitive?.intOrNull ?: 4000)
                .coerceIn(500, 12000)
            val title = "$name/Quick guide"
            val start = System.currentTimeMillis()
            val response: String = try {
                val out = wikiService.page(title, section, maxChars)
                WikiService.outputJson.encodeToString(WikiPageResponse.serializer(), out)
            } catch (t: Throwable) {
                log.warn("get_quest_guide failed: {}", t.message)
                return@addTool CallToolResult(
                    content = listOf(TextContent("get_quest_guide failed: ${t.message}")),
                    isError = true,
                )
            }
            log.info("Tool get_quest_guide(name='{}', section={}) → {} chars in {}ms",
                name, section, response.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(response)))
        }

        server.addTool(
            name = "get_quest_data",
            description = "Local quest database lookup — no wiki round-trip. Returns the full " +
                "structured record for a quest: members/QP/difficulty/length/series, " +
                "requirements + recommended-stats text, STRUCTURED items list " +
                "({name, qty, optional, note}) showing required + recommended gear/consumables, " +
                "and every walkthrough section with title + content. " +
                "PREFER this over `get_quest_guide` / `wiki_page` — instant local lookup, no " +
                "wiki rate limits. Falls back to those only when the quest isn't in the DB. " +
                "Fuzzy name matching (case-insensitive substring) so 'dragon slayer' resolves to " +
                "'Dragon Slayer I'.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Quest name (case-insensitive; substring match works)")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val name = request.arguments?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("get_quest_data requires 'name'")),
                    isError = true,
                )
            val record = questDatabase.get(name)
            if (record == null) {
                return@addTool CallToolResult(content = listOf(TextContent(
                    "{\"found\":false,\"hint\":\"no quest matched '$name' in local DB. " +
                        "Try get_quest_guide for a live wiki fetch, or list quests via list_local_quests.\"}"
                )))
            }
            val out = WikiService.outputJson.encodeToString(
                co.rowm.osrsllm.quests.QuestRecord.serializer(), record)
            log.info("Tool get_quest_data(name='{}') → {} → {} chars",
                name, record.name, out.length)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "best_in_slot_from_bank",
            description = "Rank the player's BANK items in a given equipment slot by a " +
                "stat-weighted score YOU define. There's no universal 'best in slot' — the " +
                "right item depends on the use case (max str at Gem Crab ≠ max mdmg at TOA ≠ " +
                "max prayer at Nightmare ≠ pure defence for tanking). Pass `weights` so the " +
                "ranking matches the actual scenario.\n\n" +
                "Stat paths (any number, any sign):\n" +
                "  Single: 'str', 'rstr', 'mdmg', 'prayer', 'aspeed', 'weight'\n" +
                "  Attack: 'atk.stab', 'atk.slash', 'atk.crush', 'atk.magic', 'atk.ranged'\n" +
                "  Defence: 'def.stab', 'def.slash', 'def.crush', 'def.magic', 'def.ranged'\n" +
                "Score per item = Σ (stat × weight). Negative weights penalise (e.g. {weight: -1}).\n\n" +
                "Examples:\n" +
                "  Max str for slayer: {\"str\": 1}\n" +
                "  Mage DPS: {\"mdmg\": 100, \"atk.magic\": 1}\n" +
                "  Ranged DPS: {\"rstr\": 3, \"atk.ranged\": 2}\n" +
                "  Prayer bonus only: {\"prayer\": 1}\n" +
                "  Melee tank: {\"def.stab\": 1, \"def.slash\": 1, \"def.crush\": 1}\n" +
                "Returns top N with id/name/score/stats so you can explain WHY each ranks " +
                "where. Items without equipment stats are skipped.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("slot") {
                        put("type", "string")
                        put("description", "'head'|'cape'|'amulet'|'weapon'|'body'|'shield'|'legs'|'gloves'|'boots'|'ring'|'ammo'")
                    }
                    putJsonObject("weights") {
                        put("type", "object")
                        put("description", "Stat path → multiplier. See description for path list. " +
                            "All weights must be numbers.")
                    }
                    putJsonObject("top") {
                        put("type", "integer")
                        put("description", "Max results (1-15, default 5)")
                    }
                },
                required = listOf("slot", "weights"),
            ),
        ) { request ->
            val slot = request.arguments?.get("slot")?.jsonPrimitive?.content?.lowercase()?.takeIf { it.isNotBlank() }
                ?: return@addTool err("best_in_slot_from_bank requires 'slot'")
            val weightsArg = request.arguments?.get("weights") as? kotlinx.serialization.json.JsonObject
                ?: return@addTool err("best_in_slot_from_bank requires 'weights' object")
            val weights = weightsArg.mapNotNull { (k, v) ->
                val n = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                if (n == null) null else k to n
            }.toMap()
            if (weights.isEmpty()) {
                return@addTool err("'weights' must contain at least one stat-path → number entry")
            }
            val top = (request.arguments?.get("top")?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 15)
            val out = tools.bestInSlotFromBank(slot, weights, top)
            CallToolResult(content = listOf(TextContent(out)))
        }

        // ============== RuneLite plugin integrations ==============

        server.addTool(
            name = "get_slayer_task",
            description = "Read the player's active Slayer task from RuneLite's Slayer plugin. " +
                "Returns {task, location?, initialAmount, remainingAmount, killed, visibleTargetNpcIds[]} " +
                "or {task: null} when there's no task assigned. The preamble already includes a " +
                "summary; call this for the full payload (e.g. to enumerate on-screen target NPC ids).",
        ) { _ ->
            val task = slayerIntegration.current()
            val out = if (task == null) "{\"task\":null}"
            else WikiService.outputJson.encodeToString(
                co.rowm.osrsllm.integrations.SlayerIntegration.SlayerTask.serializer(), task)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_xp_rates",
            description = "Per-skill XP/hour rates and goal estimates from RuneLite's XP " +
                "Tracker. Returns only skills with non-zero progress. Each entry: " +
                "{skill, xpPerHour, actionsPerHour, actionsDone, actionsLeft, xpToGoal, " +
                "timeToGoal?}. Goal-related fields are populated when the user has set a " +
                "target level in the XP Tracker UI. Use to answer 'am I being efficient', " +
                "'time til X', 'how many more kills til level Y'.",
        ) { _ ->
            val rates = xpTrackerIntegration.activeRates()
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.XpTrackerIntegration.SkillRate.serializer()),
                rates,
            )
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_active_clue",
            description = "Read the active clue scroll's parsed contents from RuneLite's " +
                "Clue Scrolls plugin. Returns {type, text?, npcs[], location?, locations[], " +
                "requiresSpade, requiresLight, hint?}. type is one of cryptic / anagram / " +
                "cipher / coordinate / emote / hot-cold / fairy-ring / map / music / " +
                "three-step-cryptic / falo-the-bard / skill-challenge / map / beginner-map. " +
                "When the plugin has solved the clue, `location` is populated with " +
                "{x, y, plane} — pair with show_path_to for one-shot routing.",
        ) { _ ->
            val clue = clueScrollIntegration.current()
            val out = if (clue == null) "{\"active\":false}"
            else WikiService.outputJson.encodeToString(
                co.rowm.osrsllm.integrations.ClueScrollIntegration.ActiveClue.serializer(), clue)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_party",
            description = "Read the player's RuneLite party membership: " +
                "{inParty, memberCount, members:[{displayName, loggedIn, location?}]}. " +
                "Returns inParty=false when not in a party. Location is each member's last " +
                "known world map position from the Party plugin.",
        ) { _ ->
            val out = WikiService.outputJson.encodeToString(
                co.rowm.osrsllm.integrations.PartyIntegration.PartySnapshot.serializer(),
                partyIntegration.snapshot(),
            )
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "notify_player",
            description = "Fire an OS-level notification (system tray / dock) via RuneLite's " +
                "Notifier. Use ONLY when the user is likely AFK and there's something they " +
                "need to see — herbs grown, kill goal reached, low HP threshold tripped, " +
                "bank tag prep finished. Don't use for casual chatter — that's what " +
                "chat_message is for. " +
                "Pass `force: true` to override the user's RuneLite tray-notify setting if " +
                "the message is genuinely urgent. Returns {notified: true}.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("message") { put("type", "string"); put("description", "Up to 240 chars.") }
                    putJsonObject("force") { put("type", "boolean"); put("description", "Override user's tray-disabled setting. Default false.") }
                },
                required = listOf("message"),
            ),
        ) { request ->
            val msg = request.arguments?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool err("notify_player requires 'message'")
            val force = request.arguments?.get("force")?.jsonPrimitive?.content
                ?.equals("true", ignoreCase = true) == true
            playerNotifier.notify(msg, forceTray = force)
            log.info("Tool notify_player(msg='{}', force={})", msg.take(60), force)
            CallToolResult(content = listOf(TextContent("{\"notified\":true}")))
        }

        server.addTool(
            name = "chat_message",
            description = "Speak a single line into the player's in-game OSRS chatbox via " +
                "RuneLite's ChatMessageManager. Prefixed with a colored [AI] tag so the " +
                "player can tell it apart from game text. Use for hands-free guidance " +
                "during combat / movement (\"eat now\", \"safespot the next tile\") where " +
                "looking at the overlay would lose them a tick. Max 250 chars; newlines " +
                "stripped. For OS-level alerts use notify_player. For long-form responses " +
                "stick with the overlay (it's already wired up).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("message") { put("type", "string") }
                },
                required = listOf("message"),
            ),
        ) { request ->
            val msg = request.arguments?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool err("chat_message requires 'message'")
            chatOutput.say(msg)
            log.info("Tool chat_message('{}')", msg.take(60))
            CallToolResult(content = listOf(TextContent("{\"sent\":true}")))
        }

        server.addTool(
            name = "find_agility_shortcut",
            description = "Search RuneLite's built-in catalog of agility shortcuts. " +
                "Filter by maximum required level (so the player can actually use it), " +
                "and optionally by description text. Returns " +
                "[{id, level, description, x, y, plane, mapX, mapY}]. Pair with " +
                "show_path_to for navigation. Useful for 'is there a shortcut to X' / " +
                "'what agility levels unlock useful shortcuts at my level'.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("maxLevel") {
                        put("type", "integer")
                        put("description", "Only return shortcuts the player can use at this agility level. Default: 99.")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Optional substring match on description (e.g. 'wilderness', 'falador').")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results (1-50, default 25).")
                    }
                },
            ),
        ) { request ->
            val maxLevel = (request.arguments?.get("maxLevel")?.jsonPrimitive?.intOrNull ?: 99).coerceIn(1, 99)
            val q = request.arguments?.get("query")?.jsonPrimitive?.content?.lowercase()?.takeIf { it.isNotBlank() }
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 25).coerceIn(1, 50)
            val matches = gameDataCatalogs.allShortcuts()
                .filter { it.level <= maxLevel }
                .filter { q == null || it.description.lowercase().contains(q) }
                .take(limit)
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.GameDataCatalogs.ShortcutEntry.serializer()),
                matches)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "find_fishing_spot",
            description = "Search RuneLite's FishingSpot catalog by fish/tooltip text. " +
                "Returns [{id, name, tooltip, npcIds}] — npcIds are the NPC composition " +
                "ids that ARE this spot in-game; cross-reference with get_nearby_npcs to " +
                "find one on the player's screen.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Catch name or partial — 'lobster', 'shark', 'karambwan', 'anglerfish'.")
                    }
                },
                required = listOf("query"),
            ),
        ) { request ->
            val q = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool err("find_fishing_spot requires 'query'")
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.GameDataCatalogs.FishingSpotEntry.serializer()),
                gameDataCatalogs.fishingSpotsFor(q),
            )
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "get_item_mapping",
            description = "RuneLite's ItemMapping family for an item id. Resolves things " +
                "like grimy↔clean herbs, broken↔repaired barrows, charged↔uncharged " +
                "equipment to their canonical tradeable forms. Returns " +
                "[{tradeableId, untradeableIds[], quantity}] — usually 0 or 1 entries. " +
                "Use to answer 'what's the GE form of this' or 'what alt ids does the " +
                "agent need to consider when matching'.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("itemId") { put("type", "integer") }
                },
                required = listOf("itemId"),
            ),
        ) { request ->
            val id = request.arguments?.get("itemId")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("get_item_mapping requires integer 'itemId'")
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.GameDataCatalogs.ItemMappingEntry.serializer()),
                gameDataCatalogs.mappingFor(id),
            )
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "list_user_markers",
            description = "Read user-flagged in-game state from sibling RuneLite plugins. " +
                "Returns {tiles[], objects[], npcsByName[], npcsById[], notes?}.\n" +
                "  - tiles: ground-marker tiles {x,y,plane,color?,label?} from the Ground " +
                "Markers plugin. Includes every tile the user has ever flagged.\n" +
                "  - objects: highlighted objects {id, name, x, y, plane, color?} from " +
                "Object Markers. Useful resource nodes the user wants tracked.\n" +
                "  - npcsByName / npcsById: NPCs the user has tagged via NPC Indicators.\n" +
                "  - notes: free-text contents of the user's RuneLite Notes panel.\n" +
                "Use this to know what the user already cares about — surface markers " +
                "near their current location, or reference their notes when relevant.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("maxDistance") {
                        put("type", "integer")
                        put("description", "If set, only return tile/object markers within this many world tiles of the player. Defaults to no filter.")
                    }
                },
            ),
        ) { request ->
            val maxDist = request.arguments?.get("maxDistance")?.jsonPrimitive?.intOrNull
            val px = gameStateStore.snapshot().player?.locationX
            val py = gameStateStore.snapshot().player?.locationY
            val tiles = userMarkers.groundMarkers().let { all ->
                if (maxDist == null || px == null || py == null) all
                else all.filter { kotlin.math.abs(it.x - px) <= maxDist && kotlin.math.abs(it.y - py) <= maxDist }
            }
            val objects = userMarkers.objectMarkers().let { all ->
                if (maxDist == null || px == null || py == null) all
                else all.filter { kotlin.math.abs(it.x - px) <= maxDist && kotlin.math.abs(it.y - py) <= maxDist }
            }
            val highlighted = userMarkers.highlightedNpcs()
            val notes = userMarkers.userNotes()
            val sb = StringBuilder()
            sb.append("{\"tiles\":")
            sb.append(WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.UserMarkersService.TileMarker.serializer()),
                tiles))
            sb.append(",\"objects\":")
            sb.append(WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.integrations.UserMarkersService.ObjectMarker.serializer()),
                objects))
            sb.append(",\"npcsByName\":[")
            sb.append(highlighted.byName.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" })
            sb.append("],\"npcsById\":[")
            sb.append(highlighted.byId.joinToString(","))
            sb.append(']')
            if (notes != null) {
                sb.append(",\"notes\":\"").append(notes.replace("\"", "\\\"").replace("\n", "\\n")).append('"')
            }
            sb.append('}')
            CallToolResult(content = listOf(TextContent(sb.toString())))
        }

        server.addTool(
            name = "add_persistent_markers",
            description = "Place permanent tile markers on the OSRS scene. Stored in our own " +
                "plugin config — survive restarts, appear immediately, can be removed by tile " +
                "or by label. Use for things worth keeping: herb patch tiles, safespots, " +
                "agility lap stand-points, slayer task spawn corners, fairy ring stand-tiles.\n\n" +
                "Idempotent: re-adding a tile at the same (x,y,plane) overwrites its color/label " +
                "rather than duplicating. Coords are absolute world coords (same shape as " +
                "find_location / show_path_to). Color is hex (#RRGGBB or #AARRGGBB) — alpha " +
                "defaults to dc. Labels show in-game over the tile and are how you'll address " +
                "them later (see remove_persistent_markers).\n\n" +
                "A rolling backup of the existing marker set is taken before every write (10 " +
                "kept under ~/.runelite/osrs-llm-helper/persistent-marker-backups/) so you can " +
                "be aggressive without worrying about clobbering prior work.\n\n" +
                "Returns {added, updated, unchanged, removed:0, total, backupFile}.\n\n" +
                "For TRANSIENT visualisations the user will dismiss quickly, prefer mark_tile " +
                "/ show_path_to. Use THIS tool for things they'd want to keep coming back to. " +
                "Note: this is independent of the RuneLite Ground Markers plugin — for the " +
                "user's existing plugin-set markers see list_user_markers.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("tiles") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("x") { put("type", "integer") }
                                putJsonObject("y") { put("type", "integer") }
                                putJsonObject("plane") { put("type", "integer"); put("description", "0-3, default 0.") }
                                putJsonObject("color") { put("type", "string"); put("description", "#RRGGBB or #AARRGGBB.") }
                                putJsonObject("label") { put("type", "string"); put("description", "In-game label shown over the tile.") }
                            }
                            put("required", kotlinx.serialization.json.JsonArray(listOf(
                                kotlinx.serialization.json.JsonPrimitive("x"),
                                kotlinx.serialization.json.JsonPrimitive("y"))))
                        }
                    }
                },
                required = listOf("tiles"),
            ),
        ) { request ->
            val tilesArr = request.arguments?.get("tiles") as? kotlinx.serialization.json.JsonArray
                ?: return@addTool err("add_persistent_markers requires 'tiles' array")
            val tiles = tilesArr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val x = o["x"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val y = o["y"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val color = o["color"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_PERSISTENT_HEX
                co.rowm.osrsllm.tilemarker.PersistentTileMarkerService.Stored(
                    x = x, y = y,
                    plane = o["plane"]?.jsonPrimitive?.intOrNull ?: 0,
                    colorHex = color,
                    label = o["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                )
            }
            if (tiles.isEmpty()) return@addTool err("add_persistent_markers got no valid tiles")
            val result = try { persistentMarkers.add(tiles) } catch (t: Throwable) {
                log.warn("add_persistent_markers failed", t)
                return@addTool err("add_persistent_markers failed: ${t.message}")
            }
            log.info("Tool add_persistent_markers count={} → +{} ~{} ={} total={}",
                tiles.size, result.added, result.updated, result.unchanged, result.total)
            CallToolResult(content = listOf(TextContent(
                WikiService.outputJson.encodeToString(
                    co.rowm.osrsllm.tilemarker.PersistentTileMarkerService.WriteResult.serializer(), result)
            )))
        }

        server.addTool(
            name = "remove_persistent_markers",
            description = "Remove permanent tile markers. Two modes (combinable):\n" +
                "  - `tiles: [{x, y, plane?}, ...]` — remove markers at specific coordinates.\n" +
                "  - `label: \"...\"` — remove every marker whose label contains this substring " +
                "(case-insensitive). Use when the agent placed labeled markers earlier and now " +
                "wants to clean up by tag (e.g. 'Dragon slayer prep').\n" +
                "A backup is taken first, same as add_persistent_markers. Returns the standard " +
                "WriteResult; check `removed` for how many tiles went.\n\n" +
                "Pass `all: true` to wipe every persistent marker (also backed up). Don't use " +
                "this casually — the user may have agent-placed markers from prior sessions.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("tiles") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("x") { put("type", "integer") }
                                putJsonObject("y") { put("type", "integer") }
                                putJsonObject("plane") { put("type", "integer") }
                            }
                            put("required", kotlinx.serialization.json.JsonArray(listOf(
                                kotlinx.serialization.json.JsonPrimitive("x"),
                                kotlinx.serialization.json.JsonPrimitive("y"))))
                        }
                    }
                    putJsonObject("label") {
                        put("type", "string")
                        put("description", "Substring-match remove on the marker label.")
                    }
                    putJsonObject("all") {
                        put("type", "boolean")
                        put("description", "Wipe every persistent marker. Default false.")
                    }
                },
            ),
        ) { request ->
            val tilesArr = request.arguments?.get("tiles") as? kotlinx.serialization.json.JsonArray
            val tiles = tilesArr?.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val x = o["x"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val y = o["y"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                Triple(x, y, o["plane"]?.jsonPrimitive?.intOrNull ?: 0)
            } ?: emptyList()
            val label = request.arguments?.get("label")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val wipeAll = request.arguments?.get("all")?.jsonPrimitive?.content
                ?.equals("true", ignoreCase = true) == true
            if (tiles.isEmpty() && label == null && !wipeAll) {
                return@addTool err("remove_persistent_markers requires 'tiles', 'label', or 'all: true'")
            }
            val result = try {
                if (wipeAll) persistentMarkers.clearAll()
                else persistentMarkers.remove(tiles, label)
            } catch (t: Throwable) {
                log.warn("remove_persistent_markers failed", t)
                return@addTool err("remove_persistent_markers failed: ${t.message}")
            }
            log.info("Tool remove_persistent_markers tiles={} label='{}' all={} → removed={} total={}",
                tiles.size, label ?: "", wipeAll, result.removed, result.total)
            CallToolResult(content = listOf(TextContent(
                WikiService.outputJson.encodeToString(
                    co.rowm.osrsllm.tilemarker.PersistentTileMarkerService.WriteResult.serializer(), result)
            )))
        }

        server.addTool(
            name = "list_persistent_markers",
            description = "List every permanent tile marker the agent has placed (the kind " +
                "created with add_persistent_markers). Returns " +
                "[{x, y, plane, colorHex, label?}]. Use to see what's already marked before " +
                "adding more, or to find specific tiles by label for cleanup.",
        ) { _ ->
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.tilemarker.PersistentTileMarkerService.Stored.serializer()),
                persistentMarkers.all())
            CallToolResult(content = listOf(TextContent(out)))
        }

        // ============== Agent highlight tools ==============
        // All four families flow through ManagedVisualsRegistry — the user can
        // remove any of them from the sidebar without going through the agent.

        registerHighlightTools(server,
            type = "npc",
            addName = "add_npc_highlights",
            removeName = "remove_npc_highlight",
            listName = "list_npc_highlights",
            description = "Highlight NPCs by composition id across the player's scene. " +
                "Once added, every NPC with a matching id is outlined in-game until removed. " +
                "Use `list_npc_highlights` to see what's active, `remove_npc_highlight` (by id) " +
                "to clear individual entries, or the sidebar ✕ button. " +
                "Default color reads from the user's NpcIndicators plugin so highlights look " +
                "consistent with their existing setup. Pair with get_nearby_npcs to grab ids " +
                "and find_item / wiki_search for names. The label, when set, draws above the NPC.",
            getDefaultColor = { npcHighlights.defaultHighlightColor() },
            doAdd = { entries -> npcHighlights.add(entries) },
            doList = { npcHighlights.all() },
            doRemove = { id -> npcHighlights.removeById(id) },
        )

        registerHighlightTools(server,
            type = "object",
            addName = "add_object_highlights",
            removeName = "remove_object_highlight",
            listName = "list_object_highlights",
            description = "Highlight game objects by id (banks, doors, trees, etc.) or by " +
                "(id, x, y, plane) for a single in-world instance. Tile-pinned highlights only " +
                "match the specific object at that coord; un-pinned ones match every object " +
                "with that id on the loaded scene. " +
                "Default color reads from the user's ObjectIndicators plugin. Pair with " +
                "get_nearby_objects or find_location to grab ids and coords.",
            getDefaultColor = { objectHighlights.defaultHighlightColor() },
            doAdd = { entries -> objectHighlights.add(entries) },
            doList = { objectHighlights.all() },
            doRemove = { id -> objectHighlights.removeById(id) },
            supportsTile = true,
        )

        registerHighlightTools(server,
            type = "ground item",
            addName = "add_ground_item_highlights",
            removeName = "remove_ground_item_highlight",
            listName = "list_ground_item_highlights",
            description = "Highlight tiles on the scene that have a target item id in their " +
                "ground stack. Use this to flag valuable drops for the user — pair with " +
                "ge_price + find_item to assemble a 'highlight everything above N gp on the " +
                "ground' workflow. Default color reads from the user's GroundItems plugin.",
            getDefaultColor = { groundItemHighlights.defaultHighlightColor() },
            doAdd = { entries -> groundItemHighlights.add(entries) },
            doList = { groundItemHighlights.all() },
            doRemove = { id -> groundItemHighlights.removeById(id) },
            resolveNameFromId = { itemId -> groundItemHighlights.resolveName(itemId) },
        )

        registerHighlightTools(server,
            type = "inventory item",
            addName = "add_inventory_highlights",
            removeName = "remove_inventory_highlight",
            listName = "list_inventory_highlights",
            description = "Highlight items in the player's inventory / bank / equipment by id. " +
                "Outlines the widget slot wherever the matching item appears (regular inv pane, " +
                "bank view, equipment screen). Useful for 'click this item next', 'these are " +
                "the items you need to deposit', etc. " +
                "Pair with find_item / get_inventory / get_bank to resolve ids first.",
            getDefaultColor = { java.awt.Color(220, 200, 100, 220) },
            doAdd = { entries -> inventoryHighlights.add(entries) },
            doList = { inventoryHighlights.all() },
            doRemove = { id -> inventoryHighlights.removeById(id) },
            resolveNameFromId = { itemId -> inventoryHighlights.resolveName(itemId) },
        )

        server.addTool(
            name = "get_npc_max_hp",
            description = "Look up an NPC's max HP from RuneLite's npc-info backend. " +
                "Returns {npcId, maxHp} or {npcId, maxHp: null} when not in the dataset " +
                "(custom event NPCs, certain bosses with phased HP). Pair with " +
                "get_nearby_npcs to estimate kills-to-goal or current boss HP fraction.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("npcId") { put("type", "integer") }
                },
                required = listOf("npcId"),
            ),
        ) { request ->
            val id = request.arguments?.get("npcId")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("get_npc_max_hp requires integer 'npcId'")
            val hp = npcHpService.maxHp(id)
            val out = if (hp == null) "{\"npcId\":$id,\"maxHp\":null}"
            else "{\"npcId\":$id,\"maxHp\":$hp}"
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "list_open_interfaces",
            description = "List the widget groups currently open on the player's screen " +
                "(bank, GE, NPC dialog, shop, quest journal, etc.). Returns each as " +
                "{id, name} — id is the group id you pass to read_interface; name is a " +
                "human label where we know it (Bank, Grand Exchange, …) or null for " +
                "less-common groups. The harness state preamble also lists these; this " +
                "tool is for re-checking after the screen changes mid-conversation.",
        ) { _ ->
            val open = widgetTracker.open()
            val sb = StringBuilder()
            sb.append("{\"open\":[")
            open.forEachIndexed { i, oi ->
                if (i > 0) sb.append(',')
                sb.append("{\"id\":").append(oi.id)
                if (oi.name != null) sb.append(",\"name\":\"").append(oi.name.replace("\"", "\\\"")).append('"')
                sb.append('}')
            }
            sb.append("]}")
            log.info("Tool list_open_interfaces → {} groups", open.size)
            CallToolResult(content = listOf(TextContent(sb.toString())))
        }

        server.addTool(
            name = "read_interface",
            description = "Walk the widget tree of an open interface and return its visible " +
                "text + item contents as JSON. Use this when the harness state block says " +
                "an interface is open (e.g. Bank, Shop, NPC dialog) and you need to know " +
                "what's actually on screen — dialog text, shop items + prices, quest text, " +
                "deposit-box contents, dialog options the player can pick, etc. " +
                "Output: {groupId, open, nodes:[{d, text?, name?, itemId?, qty?, itemName?, actions?}], truncated?}. " +
                "d is tree depth. Nodes that are purely structural (no text, no item) are omitted. " +
                "Depth-limited and capped at maxNodes (default 200) to avoid context blowups.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("groupId") {
                        put("type", "integer")
                        put("description", "Widget group id (from list_open_interfaces or the state preamble)")
                    }
                    putJsonObject("maxNodes") {
                        put("type", "integer")
                        put("description", "Max nodes returned (1-500, default 200). Increase for big interfaces like collection log.")
                    }
                },
                required = listOf("groupId"),
            ),
        ) { request ->
            val groupId = request.arguments?.get("groupId")?.jsonPrimitive?.intOrNull
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("read_interface requires integer 'groupId'")),
                    isError = true,
                )
            val maxNodes = (request.arguments?.get("maxNodes")?.jsonPrimitive?.intOrNull ?: 200)
                .coerceIn(1, 500)
            val out = try {
                widgetReader.readGroup(groupId, maxNodes = maxNodes)
            } catch (t: Throwable) {
                log.warn("read_interface failed", t)
                return@addTool CallToolResult(
                    content = listOf(TextContent("read_interface failed: ${t.message}")),
                    isError = true,
                )
            }
            log.info("Tool read_interface(groupId={}, maxNodes={}) → {} chars",
                groupId, maxNodes, out.length)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "save_equipment_loadout",
            description = "Save a gear loadout as a Bank Tag with a visual layout matching " +
                "RuneLite's DefaultLayout convention (worn equipment on the left of the bank " +
                "view, 28 inventory slots on the right). " +
                "Convenience wrapper over create_bank_tab — same end result (a tag tab the " +
                "player can open in-game) but you describe it structurally by slot name + " +
                "inventory index, so you don't have to do positioning math. " +
                "Use this when the player says e.g. 'save this as my Barrows gear', 'set up a " +
                "Vorkath loadout for me'. To inspect or remove later, use list_bank_tabs / " +
                "get_bank_tab / remove_bank_tab / open_bank_tab.\n\n" +
                "Slots for `worn`: HEAD CAPE AMULET AMMO WEAPON BODY SHIELD LEGS GLOVES BOOTS RING.\n" +
                "`inventory` items go by 0-27 slot index (0=top-left, increasing left-to-right then top-to-bottom).\n" +
                "`decoration` is for purely visual items in non-equipment, non-inventory grid " +
                "positions (e.g. col 3 of the grid: positions 3, 11, 19, 27, 35; or after pos 55).\n" +
                "Metadata fields (useCase, notes, requirements) are persisted in our own config " +
                "for later recall; they don't affect the Bank Tags UI.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Loadout / bank-tab name, e.g. 'Barrows', 'Vorkath', 'GWD'")
                    }
                    putJsonObject("worn") {
                        put("type", "object")
                        put("description", "Map of slot name → item id, e.g. {\"HEAD\": 4753, \"BODY\": 4757}")
                    }
                    putJsonObject("inventory") {
                        put("type", "array")
                        put("description", "Array of {\"slot\": 0..27, \"itemId\": int}")
                    }
                    putJsonObject("runePouch") {
                        put("type", "array")
                        put("description", "Optional list of up to 4 rune item ids, in pouch order")
                    }
                    putJsonObject("decoration") {
                        put("type", "array")
                        put("description", "Optional array of {\"pos\": int, \"itemId\": int, \"note\"?: string} for visual emphasis (e.g. an arrow item at pos 3 to point at the spec weapon)")
                    }
                    putJsonObject("icon") {
                        put("type", "integer")
                        put("description", "Tab icon item id (defaults to first item used)")
                    }
                    putJsonObject("useCase") {
                        put("type", "string")
                        put("description", "Short use-case tag, e.g. 'Barrows', 'Slayer', 'PVM', 'Skilling'")
                    }
                    putJsonObject("notes") {
                        put("type", "string")
                        put("description", "Free-text description of the loadout (purpose, when to use it, etc.)")
                    }
                    putJsonObject("requirements") {
                        put("type", "object")
                        put("description", "Optional: {\"quests\": [\"Priest in Peril\"], \"levels\": {\"Attack\": 70, \"Prayer\": 43}, \"combatAchievements\": [\"Easy\"]}")
                    }
                },
                required = listOf("name"),
            ),
        ) { request ->
            val args = request.arguments
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("save_equipment_loadout requires arguments")),
                    isError = true,
                )
            try {
                val parsed = parseLoadoutArgs(args)
                val result = loadoutService.saveLoadout(
                    name = parsed.name,
                    worn = parsed.worn,
                    inventory = parsed.inventory,
                    runePouch = parsed.runePouch,
                    decoration = parsed.decoration,
                    iconItemId = parsed.icon,
                    useCase = parsed.useCase,
                    notes = parsed.notes,
                    requirements = parsed.requirements,
                )
                val out = WikiService.outputJson.encodeToString(
                    EquipmentLoadoutService.EquipmentLoadout.serializer(), result)
                log.info("Tool save_equipment_loadout name='{}' worn={} inv={} runes={} deco={}",
                    parsed.name, parsed.worn.size, parsed.inventory.size,
                    parsed.runePouch.size, parsed.decoration.size)
                CallToolResult(content = listOf(TextContent(out)))
            } catch (t: Throwable) {
                log.warn("save_equipment_loadout failed", t)
                CallToolResult(
                    content = listOf(TextContent("save_equipment_loadout failed: ${t.message}")),
                    isError = true,
                )
            }
        }

        server.addTool(
            name = "find_item",
            description = "Look up OSRS item IDs by name. Uses RuneLite's in-memory item index — " +
                "instant, no HTTP. Each match is {id, name, price, variants?} — price is the " +
                "current GE price (0 for untradeables), `variants` (only present when > 1) is " +
                "the number of items in this id's variation family. Use `variants` to decide " +
                "whether to pass `variation: true` in create_bank_tab — e.g. a Saradomin " +
                "platebody with variants=4 means 'tag all god armour' is one call away.\n\n" +
                "ALWAYS use this (not wiki_search) when you need item ids — for " +
                "save_equipment_loadout, create_bank_tab, ge_price, etc.\n" +
                "Single lookup: pass `query`. Multiple in one call: pass `queries` (array).\n" +
                "RULE: if you need to resolve more than one name (a quest item list, a gear " +
                "loadout, anything), put ALL the names in ONE `queries` call. The lookup is " +
                "O(n) over the in-memory index — no per-call cost — and you save the roundtrips. " +
                "Response: `{queries: [{query, matches: [...]}]}` (always wrapped in queries even " +
                "for single mode, so the agent has a stable shape).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Single item name (use `queries` for multiple).")
                    }
                    putJsonObject("queries") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put("description", "Batch — list every name you need to resolve in one call.")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max matches PER query (1-25, default 10). Use 1 to grab just the top hit per name.")
                    }
                },
            ),
        ) { request ->
            val limit = (request.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 25)
            val queriesArg = request.arguments?.get("queries") as? kotlinx.serialization.json.JsonArray
            val single = request.arguments?.get("query")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val queries: List<String> = when {
                queriesArg != null -> queriesArg
                    .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
                single != null -> listOf(single)
                else -> emptyList()
            }
            if (queries.isEmpty()) {
                return@addTool err("find_item requires 'query' (single) or 'queries' (array)")
            }

            val sb = StringBuilder()
            sb.append("{\"queries\":[")
            queries.forEachIndexed { idx, q ->
                if (idx > 0) sb.append(',')
                val hits = runCatching { itemManager.search(q) }.getOrDefault(emptyList())
                val needle = q.lowercase()
                val ordered = hits.sortedWith(
                    compareBy(
                        { (it.name?.lowercase() ?: "") != needle },
                        { !(it.name?.lowercase() ?: "").startsWith(needle) },
                        { (it.name?.lowercase() ?: "").indexOf(needle).coerceAtLeast(0) },
                        { it.name ?: "" },
                    ),
                ).take(limit)
                sb.append("{\"query\":\"").append(q.replace("\"", "\\\"")).append("\",\"matches\":[")
                ordered.forEachIndexed { i, ip ->
                    if (i > 0) sb.append(',')
                    sb.append("{\"id\":").append(ip.id)
                    sb.append(",\"name\":\"").append((ip.name ?: "").replace("\"", "\\\"")).append('"')
                    if (ip.price > 0) sb.append(",\"price\":").append(ip.price)
                    // Variation family size — when > 1, this item has variants the agent can
                    // tag together via create_bank_tab's `variation: true`.
                    val variantCount = runCatching {
                        net.runelite.client.game.ItemVariationMapping.getVariations(
                            net.runelite.client.game.ItemVariationMapping.map(ip.id)
                        )?.size ?: 1
                    }.getOrDefault(1)
                    if (variantCount > 1) sb.append(",\"variants\":").append(variantCount)
                    sb.append('}')
                }
                sb.append("]}")
            }
            sb.append("]}")
            log.info("Tool find_item(queries={}, limit={}) → {} batches",
                queries, limit, queries.size)
            CallToolResult(content = listOf(TextContent(sb.toString())))
        }

        server.addTool(
            name = "show_path_to",
            description = "Compute and DRAW a walking path from the player's current position to " +
                "(x, y, plane?) on the OSRS scene. Outlined tile polygons appear in-game so the " +
                "player can literally follow them. Use this for 'show me the way to X' / 'where " +
                "is Y' once you have target coords (e.g. from find_location or find_nearest_pois). " +
                "Pathfinder respects walls/water/corner-clips — if the destination is reachable, " +
                "you'll get back {tiles, runTicks, displayed}. " +
                "Auto-clears any previously-shown path. Combine with `point_at` to add an arrow.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("x") { put("type", "integer") }
                    putJsonObject("y") { put("type", "integer") }
                    putJsonObject("plane") {
                        put("type", "integer")
                        put("description", "Plane (0/1/2/3). Defaults to player's current plane.")
                    }
                },
                required = listOf("x", "y"),
            ),
        ) { request ->
            val tx = request.arguments?.get("x")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("show_path_to requires integer 'x'")
            val ty = request.arguments?.get("y")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("show_path_to requires integer 'y'")
            val snap = gameStateStore.snapshot()
            val px = snap.player?.locationX ?: return@addTool err("Player not logged in")
            val py = snap.player.locationY ?: return@addTool err("Player position unknown")
            val pz = snap.player.plane ?: 0
            val plane = request.arguments?.get("plane")?.jsonPrimitive?.intOrNull ?: pz
            val path = pathfinderService.walkPath(px, py, pz, tx, ty, plane)
            if (path == null) {
                tileMarkerService.clearPath()
                return@addTool CallToolResult(
                    content = listOf(TextContent(
                        "{\"displayed\":false,\"reason\":\"no walkable path within budget — try find_transport for a teleport\"}"
                    )),
                )
            }
            tileMarkerService.setPath(path)
            val runTicks = ((path.size - 1) + 1) / 2
            val out = "{\"displayed\":true,\"tiles\":${path.size}," +
                "\"runTicks\":$runTicks,\"start\":{\"x\":$px,\"y\":$py,\"plane\":$pz}," +
                "\"end\":{\"x\":$tx,\"y\":$ty,\"plane\":$plane}}"
            log.info("show_path_to({},{},{}) → {} tiles", tx, ty, plane, path.size)
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "mark_tile",
            description = "Highlight a single tile on the OSRS scene with an outlined polygon + " +
                "optional label. Use for marking spots — 'here's the fairy ring', 'this is the " +
                "spawn'. Does NOT replace previous markers — call clear_visuals to reset. " +
                "color: 'green'|'orange'|'red'|'cyan'|'magenta'|'yellow' (default 'orange').",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("x") { put("type", "integer") }
                    putJsonObject("y") { put("type", "integer") }
                    putJsonObject("plane") { put("type", "integer") }
                    putJsonObject("label") { put("type", "string") }
                    putJsonObject("color") { put("type", "string") }
                },
                required = listOf("x", "y"),
            ),
        ) { request ->
            val tx = request.arguments?.get("x")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("mark_tile requires integer 'x'")
            val ty = request.arguments?.get("y")?.jsonPrimitive?.intOrNull
                ?: return@addTool err("mark_tile requires integer 'y'")
            val plane = request.arguments?.get("plane")?.jsonPrimitive?.intOrNull ?: 0
            val label = request.arguments?.get("label")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val colorName = request.arguments?.get("color")?.jsonPrimitive?.content?.lowercase().orEmpty()
            val color = parseColor(colorName, co.rowm.osrsllm.tilemarker.TileMarkerService.DEFAULT_MARKER_COLOR)
            tileMarkerService.addMarker(
                co.rowm.osrsllm.tilemarker.TileMarker(
                    tile = co.rowm.osrsllm.pathfinder.TilePoint(tx, ty, plane),
                    color = color,
                    label = label,
                ),
            )
            log.info("mark_tile({},{},{}) label='{}' color={}", tx, ty, plane, label, colorName)
            CallToolResult(content = listOf(TextContent("{\"marked\":true}")))
        }

        server.addTool(
            name = "point_at",
            description = "Set the orange in-game hint arrow over a target. Use when you want " +
                "to draw the player's attention somewhere — far more visible than a tile marker " +
                "since the arrow tracks the player's camera. " +
                "Pass exactly one of: `tile` (object {x, y, plane?}) or `npc` (string name). " +
                "Clears any previous arrow. Call `clear_visuals` to remove it explicitly.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("tile") {
                        put("type", "object")
                        put("description", "{\"x\": int, \"y\": int, \"plane\"?: int}")
                    }
                    putJsonObject("npc") {
                        put("type", "string")
                        put("description", "Name of an NPC nearby (substring match, case-insensitive)")
                    }
                },
            ),
        ) { request ->
            val tile = request.arguments?.get("tile") as? kotlinx.serialization.json.JsonObject
            val npcName = request.arguments?.get("npc")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            val future = java.util.concurrent.CompletableFuture<String>()
            clientThread.invoke(Runnable {
                try {
                    when {
                        tile != null -> {
                            val tx = tile["x"]?.jsonPrimitive?.intOrNull
                                ?: throw IllegalArgumentException("tile.x missing")
                            val ty = tile["y"]?.jsonPrimitive?.intOrNull
                                ?: throw IllegalArgumentException("tile.y missing")
                            val tp = tile["plane"]?.jsonPrimitive?.intOrNull
                                ?: client.localPlayer?.worldLocation?.plane ?: 0
                            client.setHintArrow(net.runelite.api.coords.WorldPoint(tx, ty, tp))
                            tileMarkerService.recordHintArrow(
                                co.rowm.osrsllm.tilemarker.AiHintArrowRef.Tile(tx, ty, tp),
                            )
                            future.complete("{\"pointed\":true,\"target\":\"tile\"}")
                        }
                        npcName != null -> {
                            val needle = npcName.lowercase()
                            val match = client.npcs.firstOrNull { n ->
                                n != null && n.name?.lowercase()?.contains(needle) == true
                            }
                            if (match == null) {
                                future.complete("{\"pointed\":false,\"reason\":\"no nearby NPC matches '$npcName'\"}")
                            } else {
                                client.setHintArrow(match)
                                tileMarkerService.recordHintArrow(
                                    co.rowm.osrsllm.tilemarker.AiHintArrowRef.Npc(match.index),
                                )
                                future.complete("{\"pointed\":true,\"target\":\"npc\",\"name\":\"${match.name}\"}")
                            }
                        }
                        else -> future.complete("{\"pointed\":false,\"reason\":\"pass either tile or npc\"}")
                    }
                } catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            })
            val out = try {
                future.get(2, java.util.concurrent.TimeUnit.SECONDS)
            } catch (t: Throwable) {
                return@addTool err("point_at failed: ${t.message}")
            }
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = "clear_visuals",
            description = "Remove every tile marker, path, and hint arrow set by previous " +
                "show_path_to / mark_tile / point_at calls. The hint arrow is only cleared if " +
                "WE set it — game-set arrows (quest dialogues etc.) are left alone.",
        ) { _ ->
            val aiOwnsArrow = tileMarkerService.aiHintArrowActive(client)
            tileMarkerService.clearAll()
            if (aiOwnsArrow) clientThread.invoke(Runnable { client.clearHintArrow() })
            CallToolResult(content = listOf(TextContent("{\"cleared\":true}")))
        }

        server.addTool(
            name = "list_item_tags",
            description = "List every wiki-sourced item tag with its global item count. " +
                "Use this when you need a filter for `get_bank(categories=...)` beyond the obvious — " +
                "the wiki has 90+ tags including niche but useful ones like `warm_clothing`, " +
                "`temporary_skill_boost`, `items_with_charges`, `chinchompas`, `seeds`. " +
                "Response: {primaryTags: [...], allTags: {tag: count}}. `primaryTags` is a curated " +
                "short list of the most commonly useful filters; `allTags` is the full taxonomy.",
        ) { _ ->
            val out = tools.listTags()
            CallToolResult(content = listOf(TextContent(out)))
        }
    }

    private fun err(message: String): CallToolResult =
        CallToolResult(content = listOf(TextContent(message)), isError = true)

    /**
     * Boilerplate for the four highlight families (NPC / object / ground item /
     * inventory item). Registers `add_*`, `remove_*`, `list_*` tools that all
     * share the same [co.rowm.osrsllm.highlights.HighlightEntry] schema.
     *
     * Add tools accept an `entries` array of `{targetId, name?, label?, colorHex?, source?, x?, y?, plane?}`
     * — tile fields only meaningful for object highlights. Remove takes a stable id
     * (either `<targetId>` or `<targetId>@<x>,<y>,<plane>` for tile-pinned objects).
     */
    private fun registerHighlightTools(
        server: io.modelcontextprotocol.kotlin.sdk.server.Server,
        type: String,
        addName: String,
        removeName: String,
        listName: String,
        description: String,
        getDefaultColor: () -> java.awt.Color,
        doAdd: (List<co.rowm.osrsllm.highlights.HighlightEntry>) -> Triple<Int, Int, Int>,
        doList: () -> List<co.rowm.osrsllm.highlights.HighlightEntry>,
        doRemove: (String) -> Boolean,
        supportsTile: Boolean = false,
        resolveNameFromId: ((Int) -> String?)? = null,
    ) {
        toolNames.add(addName); toolNames.add(removeName); toolNames.add(listName)

        val entrySchemaProps = buildJsonObject {
            putJsonObject("targetId") { put("type", "integer"); put("description", "The thing to highlight: NPC composition id / object id / item id.") }
            putJsonObject("name") { put("type", "string"); put("description", "Optional display name (e.g. 'Greater demon'). Used in the sidebar label.") }
            putJsonObject("label") { put("type", "string"); put("description", "Optional in-game label drawn next to the highlight.") }
            putJsonObject("colorHex") { put("type", "string"); put("description", "Hex (#RRGGBB / #AARRGGBB). Defaults to the sibling plugin's color when omitted.") }
            putJsonObject("source") { put("type", "string"); put("description", "Optional tag (chat session, quest prep name) shown in the sidebar.") }
            if (supportsTile) {
                putJsonObject("x") { put("type", "integer"); put("description", "Pin to a specific instance. Pair with y, plane.") }
                putJsonObject("y") { put("type", "integer") }
                putJsonObject("plane") { put("type", "integer") }
            }
        }

        server.addTool(
            name = addName,
            description = "$description\n\nAdds are idempotent on id. Returns `{added, updated, total}`.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("entries") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            put("properties", entrySchemaProps)
                            put("required", kotlinx.serialization.json.JsonArray(
                                listOf(kotlinx.serialization.json.JsonPrimitive("targetId"))))
                        }
                    }
                },
                required = listOf("entries"),
            ),
        ) { request ->
            val arr = request.arguments?.get("entries") as? kotlinx.serialization.json.JsonArray
                ?: return@addTool err("$addName requires 'entries' array")
            val entries = arr.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val targetId = o["targetId"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: resolveNameFromId?.invoke(targetId)
                co.rowm.osrsllm.highlights.HighlightEntry(
                    targetId = targetId,
                    name = name,
                    colorHex = o["colorHex"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    label = o["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    source = o["source"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                    x = if (supportsTile) o["x"]?.jsonPrimitive?.intOrNull else null,
                    y = if (supportsTile) o["y"]?.jsonPrimitive?.intOrNull else null,
                    plane = if (supportsTile) o["plane"]?.jsonPrimitive?.intOrNull else null,
                )
            }
            if (entries.isEmpty()) return@addTool err("$addName got no valid entries")
            val result = try { doAdd(entries) } catch (t: Throwable) {
                log.warn("{} failed", addName, t)
                return@addTool err("$addName failed: ${t.message}")
            }
            log.info("Tool {} count={} → +{} ~{} total={}", addName, entries.size, result.first, result.second, result.third)
            val out = "{\"added\":${result.first},\"updated\":${result.second},\"total\":${result.third}}"
            CallToolResult(content = listOf(TextContent(out)))
        }

        server.addTool(
            name = removeName,
            description = "Remove a single $type highlight by id (the stringified targetId, or " +
                "`<targetId>@<x>,<y>,<plane>` for tile-pinned object highlights). Use the " +
                "sidebar's ✕ button for one-click removal without a tool call.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("id") { put("type", "string") }
                },
                required = listOf("id"),
            ),
        ) { request ->
            val id = request.arguments?.get("id")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@addTool err("$removeName requires 'id'")
            val ok = try { doRemove(id) } catch (t: Throwable) {
                log.warn("{} failed", removeName, t)
                return@addTool err("$removeName failed: ${t.message}")
            }
            log.info("Tool {}('{}') → {}", removeName, id, ok)
            CallToolResult(content = listOf(TextContent("{\"removed\":$ok,\"id\":\"$id\"}")))
        }

        server.addTool(
            name = listName,
            description = "List active $type highlights. Returns " +
                "[{targetId, name?, colorHex?, label?, source?, x?, y?, plane?, createdAt}].",
        ) { _ ->
            val out = WikiService.outputJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    co.rowm.osrsllm.highlights.HighlightEntry.serializer()),
                doList(),
            )
            CallToolResult(content = listOf(TextContent(out)))
        }
    }

    private fun parseColor(name: String, default: java.awt.Color): java.awt.Color = when (name) {
        "green"   -> java.awt.Color(140, 214, 168, 220)
        "orange"  -> java.awt.Color(232, 174, 78, 220)
        "red"     -> java.awt.Color(232, 100, 100, 220)
        "cyan"    -> java.awt.Color(120, 200, 220, 220)
        "magenta" -> java.awt.Color(220, 120, 200, 220)
        "yellow"  -> java.awt.Color(232, 232, 120, 220)
        else      -> default
    }

    private data class ParsedLoadoutArgs(
        val name: String,
        val worn: Map<String, Int?>,
        val inventory: Map<Int, Int?>,
        val runePouch: List<Int?>,
        val decoration: List<EquipmentLoadoutService.DecorationInput>,
        val icon: Int?,
        val useCase: String?,
        val notes: String?,
        val requirements: EquipmentLoadoutService.Requirements?,
    )

    private fun parseLoadoutArgs(args: kotlinx.serialization.json.JsonObject): ParsedLoadoutArgs {
        val name = args["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("'name' is required")
        val worn: Map<String, Int?> = (args["worn"] as? kotlinx.serialization.json.JsonObject)
            ?.mapValues { (_, v) ->
                runCatching { (v as kotlinx.serialization.json.JsonPrimitive).intOrNull }.getOrNull()
            }.orEmpty()
        val inventory: Map<Int, Int?> = (args["inventory"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val slot = o["slot"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val itemId = o["itemId"]?.jsonPrimitive?.intOrNull
                slot to itemId
            }?.toMap().orEmpty()
        val runePouch: List<Int?> = (args["runePouch"] as? kotlinx.serialization.json.JsonArray)
            ?.map { el ->
                when (el) {
                    is kotlinx.serialization.json.JsonPrimitive -> el.intOrNull
                    is kotlinx.serialization.json.JsonObject -> el["itemId"]?.jsonPrimitive?.intOrNull
                    else -> null
                }
            }.orEmpty()
        val decoration: List<EquipmentLoadoutService.DecorationInput> =
            (args["decoration"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val pos = o["pos"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val itemId = o["itemId"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val note = o["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                EquipmentLoadoutService.DecorationInput(pos, itemId, note)
            }.orEmpty()
        val icon = args["icon"]?.jsonPrimitive?.intOrNull
        val useCase = args["useCase"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val notes = args["notes"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val requirements = (args["requirements"] as? kotlinx.serialization.json.JsonObject)?.let { req ->
            val quests = (req["quests"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val levels = (req["levels"] as? kotlinx.serialization.json.JsonObject)
                ?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0 }
                ?.filterValues { it > 0 }.orEmpty()
            val cas = (req["combatAchievements"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            EquipmentLoadoutService.Requirements(quests = quests, levels = levels, combatAchievements = cas)
        }
        return ParsedLoadoutArgs(name, worn, inventory, runePouch, decoration, icon, useCase, notes, requirements)
    }

    /**
     * Simple zero-arg tool that returns a JSON string. Registers the name in [toolNames]
     * for the allow list. Use this for read-only state lookups.
     */
    private fun addLoggedTool(server: Server, name: String, description: String, body: () -> String) {
        toolNames.add(name)
        server.addTool(name = name, description = description) { _ ->
            val start = System.currentTimeMillis()
            val result = body()
            log.info("Tool {} → {} chars in {}ms", name, result.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(result)))
        }
    }

    /**
     * Tool with an input schema. Wraps `Server.addTool` so we record the name and apply
     * standard logging. Handler is given the request arguments map (may be null) and
     * returns a JSON string.
     */
    private fun addArgTool(
        server: Server,
        name: String,
        description: String,
        inputSchema: ToolSchema,
        body: (kotlinx.serialization.json.JsonObject?) -> String,
    ) {
        toolNames.add(name)
        server.addTool(name = name, description = description, inputSchema = inputSchema) { request ->
            val start = System.currentTimeMillis()
            val result = body(request.arguments)
            log.info("Tool {} → {} chars in {}ms", name, result.length, System.currentTimeMillis() - start)
            CallToolResult(content = listOf(TextContent(result)))
        }
    }

    /**
     * Tool with potentially-error result. Body returns Result&lt;String, Throwable&gt;-style:
     * Pair&lt;String, Boolean&gt; where second=true marks isError.
     */
    private fun addFallibleTool(
        server: Server,
        name: String,
        description: String,
        inputSchema: ToolSchema? = null,
        body: (kotlinx.serialization.json.JsonObject?) -> String,
    ) {
        toolNames.add(name)
        val handler: suspend io.modelcontextprotocol.kotlin.sdk.server.ClientConnection.(io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest) -> CallToolResult = { request ->
            val start = System.currentTimeMillis()
            try {
                val result = body(request.arguments)
                log.info("Tool {} → {} chars in {}ms", name, result.length, System.currentTimeMillis() - start)
                CallToolResult(content = listOf(TextContent(result)))
            } catch (t: Throwable) {
                log.warn("Tool {} threw: {}", name, t.message)
                CallToolResult(content = listOf(TextContent("$name failed: ${t.message}")), isError = true)
            }
        }
        if (inputSchema != null) {
            server.addTool(name = name, description = description, inputSchema = inputSchema, handler = handler)
        } else {
            server.addTool(name = name, description = description, handler = handler)
        }
    }
}
