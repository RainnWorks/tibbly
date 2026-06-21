package co.rowm.osrsllm.plugin

import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.LoggingSetup
import co.rowm.osrsllm.OsrsLlmHelperConfig
import co.rowm.osrsllm.OsrsLlmHelperPanel
import co.rowm.osrsllm.banktags.BankTagService
import co.rowm.osrsllm.chat.ChatPanel
import co.rowm.osrsllm.chat.ChatStore
import co.rowm.osrsllm.chat.ClaudeRunner
import co.rowm.osrsllm.chat.HarnessContext
import co.rowm.osrsllm.cloud.BackendUrl
import co.rowm.osrsllm.cloud.BackendWsClient
import co.rowm.osrsllm.cloud.ConfigManagerDeviceKeyStore
import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.DeviceKey
import co.rowm.osrsllm.cloud.PairingFlow
import co.rowm.osrsllm.events.EventLogService
import co.rowm.osrsllm.local.McpServerService
import co.rowm.osrsllm.overlay.AiChannelService
import co.rowm.osrsllm.overlay.AssistantOverlay
import co.rowm.osrsllm.overlay.OverlayChatController
import co.rowm.osrsllm.session.HitsplatHistoryService
import co.rowm.osrsllm.session.LootService
import co.rowm.osrsllm.session.XpRateService
import co.rowm.osrsllm.widgets.WidgetTracker
import com.google.inject.Provides
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import net.runelite.client.config.ConfigManager
import net.runelite.client.eventbus.EventBus
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.events.ConfigChanged
import net.runelite.client.input.KeyManager
import net.runelite.client.input.MouseManager
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDependency
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.banktags.BankTagsPlugin
import net.runelite.client.ui.ClientToolbar
import net.runelite.client.ui.NavigationButton
import net.runelite.client.ui.overlay.OverlayManager
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.inject.Inject

@PluginDescriptor(
    name = "00_OSRS LLM Helper",
    description = "MCP server exposing live game state to an LLM agent",
    tags = ["llm", "ai", "mcp", "assistant"],
)
@PluginDependency(BankTagsPlugin::class)
@PluginDependency(net.runelite.client.plugins.xptracker.XpTrackerPlugin::class)
@PluginDependency(net.runelite.client.plugins.slayer.SlayerPlugin::class)
@PluginDependency(net.runelite.client.plugins.cluescrolls.ClueScrollPlugin::class)
@PluginDependency(net.runelite.client.plugins.party.PartyPlugin::class)
class OsrsLlmHelperPlugin : Plugin() {

    private val log = LoggerFactory.getLogger(OsrsLlmHelperPlugin::class.java)

    @Inject private lateinit var config: OsrsLlmHelperConfig
    @Inject private lateinit var gameStateStore: GameStateStore
    @Inject private lateinit var eventLogService: EventLogService
    @Inject private lateinit var xpRateService: XpRateService
    @Inject private lateinit var lootService: LootService
    @Inject private lateinit var hitsplatHistoryService: HitsplatHistoryService
    @Inject private lateinit var mcpServerService: McpServerService
    @Inject private lateinit var backendWsClient: BackendWsClient
    @Inject private lateinit var pairingFlow: PairingFlow
    @Inject private lateinit var widgetTracker: WidgetTracker
    @Inject private lateinit var bankTagService: BankTagService
    @Inject private lateinit var clientToolbar: ClientToolbar
    @Inject private lateinit var eventBus: EventBus
    @Inject private lateinit var overlayManager: OverlayManager
    @Inject private lateinit var mouseManager: MouseManager
    @Inject private lateinit var keyManager: KeyManager
    @Inject private lateinit var assistantOverlay: AssistantOverlay
    @Inject private lateinit var overlayChatController: OverlayChatController
    @Inject private lateinit var aiChannelService: AiChannelService
    @Inject private lateinit var tileMarkerOverlay: co.rowm.osrsllm.tilemarker.TileMarkerOverlay
    @Inject private lateinit var tileMarkerService: co.rowm.osrsllm.tilemarker.TileMarkerService
    @Inject private lateinit var persistentMarkers: co.rowm.osrsllm.tilemarker.PersistentTileMarkerService
    @Inject private lateinit var managedRegistry: co.rowm.osrsllm.managed.ManagedVisualsRegistry
    @Inject private lateinit var npcHighlights: co.rowm.osrsllm.highlights.NpcHighlightService
    @Inject private lateinit var npcHighlightOverlay: co.rowm.osrsllm.highlights.NpcHighlightOverlay
    @Inject private lateinit var objectHighlights: co.rowm.osrsllm.highlights.ObjectHighlightService
    @Inject private lateinit var objectHighlightOverlay: co.rowm.osrsllm.highlights.ObjectHighlightOverlay
    @Inject private lateinit var groundItemHighlights: co.rowm.osrsllm.highlights.GroundItemHighlightService
    @Inject private lateinit var groundItemHighlightOverlay: co.rowm.osrsllm.highlights.GroundItemHighlightOverlay
    @Inject private lateinit var inventoryHighlights: co.rowm.osrsllm.highlights.InventoryItemHighlightService
    @Inject private lateinit var inventoryHighlightOverlay: co.rowm.osrsllm.highlights.InventoryItemHighlightOverlay
    @Inject private lateinit var visualsControlOverlay: co.rowm.osrsllm.tilemarker.VisualsControlOverlay
    @Inject private lateinit var client: net.runelite.api.Client
    @Inject private lateinit var clientThread: net.runelite.client.callback.ClientThread
    @Inject private lateinit var aiChatCommand: co.rowm.osrsllm.integrations.AiChatCommand
    @Inject private lateinit var slayerIntegration: co.rowm.osrsllm.integrations.SlayerIntegration
    @Inject private lateinit var xpTrackerIntegration: co.rowm.osrsllm.integrations.XpTrackerIntegration
    @Inject private lateinit var clueScrollIntegration: co.rowm.osrsllm.integrations.ClueScrollIntegration
    @Inject private lateinit var partyIntegration: co.rowm.osrsllm.integrations.PartyIntegration

    private var navButton: NavigationButton? = null
    private var panel: OsrsLlmHelperPanel? = null
    private var chatNavButton: NavigationButton? = null
    private var chatPanel: ChatPanel? = null
    private var managedNavButton: NavigationButton? = null
    private var managedPanel: co.rowm.osrsllm.managed.ManagedVisualsPanel? = null
    private val chatStore = ChatStore()
    private val claudeRunner = ClaudeRunner(
        mcpUrlSupplier = {
            runCatching {
                if (::mcpServerService.isInitialized) mcpServerService.boundUrl() else null
            }.getOrNull()
        },
        allowedToolsSupplier = {
            runCatching {
                if (::mcpServerService.isInitialized) mcpServerService.allowedToolNames() else emptyList()
            }.getOrDefault(emptyList())
        },
        contextSupplier = {
            runCatching {
                if (::gameStateStore.isInitialized && ::eventLogService.isInitialized) {
                    HarnessContext.build(
                        store = gameStateStore,
                        eventLog = eventLogService,
                        widgets = if (::widgetTracker.isInitialized) widgetTracker else null,
                        bankTags = if (::bankTagService.isInitialized) bankTagService else null,
                        slayer = if (::slayerIntegration.isInitialized) slayerIntegration else null,
                        xpTracker = if (::xpTrackerIntegration.isInitialized) xpTrackerIntegration else null,
                        clueScroll = if (::clueScrollIntegration.isInitialized) clueScrollIntegration else null,
                        party = if (::partyIntegration.isInitialized) partyIntegration else null,
                    )
                } else null
            }.getOrNull()
        },
    )

    override fun startUp() {
        LoggingSetup.configure()
        log.info("Plugin starting (developerMode={}, cloudChatEnabled={})",
            config.developerMode(), config.cloudChatEnabled())

        // Freeze consent ONCE for this plugin lifetime — see ConsentState KDoc.
        // A mid-session config flip cannot start sending data without a plugin restart.
        ConsentState.freeze(accepted = config.consentAccepted())
        eventBus.register(gameStateStore)
        eventBus.register(eventLogService)
        eventBus.register(xpRateService)
        eventBus.register(lootService)
        eventBus.register(hitsplatHistoryService)
        eventBus.register(widgetTracker)

        val sidebar = OsrsLlmHelperPanel(gameStateStore, mcpServerService, pairingFlow)
        panel = sidebar
        val button = NavigationButton.builder()
            .tooltip("OSRS LLM Helper")
            .icon(createStatusIcon())
            .priority(7)
            .panel(sidebar)
            .build()
        navButton = button
        clientToolbar.addNavigation(button)

        val chat = ChatPanel(chatStore, claudeRunner)
        chatPanel = chat
        val chatBtn = NavigationButton.builder()
            .tooltip("OSRS LLM Chat")
            .icon(createChatIcon())
            .priority(8)
            .panel(chat)
            .build()
        chatNavButton = chatBtn
        clientToolbar.addNavigation(chatBtn)

        // Local MCP server is DEVELOPER-ONLY. The release build that ships to
        // the RuneLite Plugin Hub does NOT bind any listening socket — the
        // player's data only ever leaves through EgressGate (see SECURITY_DESIGN.md).
        if (config.developerMode() && config.localMcpEnabled()) {
            log.info("Developer mode active — starting local MCP server on {}:{}",
                config.localMcpHost(), config.localMcpPort())
            mcpServerService.start(config.localMcpHost(), config.localMcpPort())
        }

        // Production path: open the WSS connection to the backend if the player has
        // accepted consent AND cloud chat is enabled. All actual sends are funnelled
        // through EgressGate.egress() — there is no other write path.
        if (config.consentAccepted() && config.cloudChatEnabled()) {
            runCatching { backendWsClient.connect(BackendUrl(config.backendUrl())) }
                .onFailure { log.warn("Backend connect failed: {}", it.message) }
        }

        // On-chat overlay: register slash commands (!ai / ::ai), the overlay renderer,
        // and forward mouse events to it so clicks and scroll wheel work.
        overlayManager.add(assistantOverlay)
        overlayManager.add(tileMarkerOverlay)
        overlayManager.add(visualsControlOverlay)
        mouseManager.registerMouseListener(visualsControlOverlay.mouseListener)
        mouseManager.registerMouseListener(assistantOverlay.mouseListener)
        mouseManager.registerMouseWheelListener(assistantOverlay.mouseWheelListener)
        keyManager.registerKeyListener(assistantOverlay.keyListener)
        eventBus.register(overlayChatController) // for CommandExecuted (::ai)
        eventBus.register(aiChannelService)      // for GameTick → AI tab label
        overlayChatController.register()
        aiChatCommand.register()

        // Registry must be loaded before downstream services install their removers
        // — the load() path fires a listener notification, and we want the panel to
        // pick up the persisted entries on first paint.
        managedRegistry.load()
        bankTagService.installRemover()
        persistentMarkers.load() // registers its own remover
        npcHighlights.load()
        objectHighlights.load()
        groundItemHighlights.load()
        inventoryHighlights.load()
        overlayManager.add(npcHighlightOverlay)
        overlayManager.add(objectHighlightOverlay)
        overlayManager.add(groundItemHighlightOverlay)
        overlayManager.add(inventoryHighlightOverlay)

        val managed = co.rowm.osrsllm.managed.ManagedVisualsPanel(managedRegistry)
        managedPanel = managed
        val managedBtn = NavigationButton.builder()
            .tooltip("OSRS LLM — Manage agent visuals")
            .icon(createStatusIcon())
            .priority(9)
            .panel(managed)
            .build()
        managedNavButton = managedBtn
        clientToolbar.addNavigation(managedBtn)
    }

    override fun shutDown() {
        log.info("Plugin shutting down")
        runCatching { aiChatCommand.unregister() }
        runCatching { overlayChatController.unregister() }
        runCatching { aiChannelService.restoreTradeLabel() }
        runCatching { eventBus.unregister(aiChannelService) }
        runCatching { eventBus.unregister(overlayChatController) }
        runCatching { keyManager.unregisterKeyListener(assistantOverlay.keyListener) }
        runCatching { mouseManager.unregisterMouseListener(assistantOverlay.mouseListener) }
        runCatching { mouseManager.unregisterMouseWheelListener(assistantOverlay.mouseWheelListener) }
        runCatching { mouseManager.unregisterMouseListener(visualsControlOverlay.mouseListener) }
        runCatching { overlayManager.remove(assistantOverlay) }
        runCatching { overlayManager.remove(tileMarkerOverlay) }
        runCatching { overlayManager.remove(visualsControlOverlay) }
        runCatching { overlayManager.remove(npcHighlightOverlay) }
        runCatching { overlayManager.remove(objectHighlightOverlay) }
        runCatching { overlayManager.remove(groundItemHighlightOverlay) }
        runCatching { overlayManager.remove(inventoryHighlightOverlay) }
        runCatching {
            val aiOwnsArrow = tileMarkerService.aiHintArrowActive(client)
            tileMarkerService.clearAll()
            if (aiOwnsArrow) clientThread.invoke(Runnable { client.clearHintArrow() })
        }
        mcpServerService.stop()
        runCatching { backendWsClient.close() }
        ConsentState.reset()
        eventBus.unregister(gameStateStore)
        eventBus.unregister(eventLogService)
        eventBus.unregister(xpRateService)
        eventBus.unregister(lootService)
        eventBus.unregister(hitsplatHistoryService)
        eventBus.unregister(widgetTracker)
        eventLogService.clear()
        xpRateService.clear()
        lootService.clear()
        hitsplatHistoryService.clear()
        widgetTracker.clear()
        navButton?.let { clientToolbar.removeNavigation(it) }
        chatNavButton?.let { clientToolbar.removeNavigation(it) }
        managedNavButton?.let { clientToolbar.removeNavigation(it) }
        navButton = null
        chatNavButton = null
        managedNavButton = null
        panel?.stop()
        panel = null
        chatPanel?.detach()
        chatPanel = null
        managedPanel?.detach()
        managedPanel = null
        gameStateStore.clear()
    }

    @Subscribe
    fun onConfigChanged(event: ConfigChanged) {
        if (event.group != "osrsllm") return
        log.info("Config changed: {} = {}", event.key, event.newValue)
        when (event.key) {
            "localMcpEnabled", "localMcpHost", "localMcpPort" -> {
                if (config.developerMode()) {
                    mcpServerService.restartWith(
                        enabled = config.localMcpEnabled(),
                        host = config.localMcpHost(),
                        port = config.localMcpPort(),
                    )
                }
            }
        }
    }

    @Provides
    fun provideConfig(configManager: ConfigManager): OsrsLlmHelperConfig =
        configManager.getConfig(OsrsLlmHelperConfig::class.java)

    @Provides @Singleton
    fun provideChatStore(): ChatStore = chatStore

    @Provides @Singleton
    fun provideClaudeRunner(): ClaudeRunner = claudeRunner

    // RAI-23: device-key storage + backend-URL supplier for the pairing flow.
    @Provides @Singleton
    fun provideDeviceKeyStore(store: ConfigManagerDeviceKeyStore): DeviceKey.Store = store

    @Provides @Singleton
    fun providePairingBackendUrlSupplier(cfg: OsrsLlmHelperConfig): PairingFlow.BackendUrlSupplier =
        PairingFlow.BackendUrlSupplier { BackendUrl(cfg.backendUrl()) }

    private fun createStatusIcon(): BufferedImage = textIcon("AI", Color(74, 144, 226))

    private fun createChatIcon(): BufferedImage {
        val size = 24
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(220, 180, 100)
            g.fillRoundRect(1, 3, size - 2, size - 8, 6, 6)
            g.color = Color.WHITE
            g.fillOval(6, 11, 3, 3)
            g.fillOval(11, 11, 3, 3)
            g.fillOval(16, 11, 3, 3)
        } finally {
            g.dispose()
        }
        return img
    }

    private fun textIcon(text: String, bg: Color): BufferedImage {
        val size = 24
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = bg
            g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6)
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 12)
            val fm = g.fontMetrics
            val x = (size - fm.stringWidth(text)) / 2
            val y = (size - fm.height) / 2 + fm.ascent
            g.drawString(text, x, y)
        } finally {
            g.dispose()
        }
        return img
    }
}
