package co.rowm.osrsllm.plugin

import co.rowm.osrsllm.ChatMode
import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.LoggingSetup
import co.rowm.osrsllm.OsrsLlmHelperConfig
import co.rowm.osrsllm.OsrsLlmHelperPanel
import co.rowm.osrsllm.banktags.BankTagService
import co.rowm.osrsllm.chat.ChatBackend
import co.rowm.osrsllm.chat.ChatBackendSelector
import co.rowm.osrsllm.chat.ChatPanel
import co.rowm.osrsllm.chat.ChatStore
import co.rowm.osrsllm.chat.HarnessContext
import co.rowm.osrsllm.cloud.AccountPanel
import co.rowm.osrsllm.cloud.AccountSummaryClient
import co.rowm.osrsllm.cloud.BackendUrl
import co.rowm.osrsllm.cloud.BackendWsClient
import co.rowm.osrsllm.cloud.CloudChatBackend
import co.rowm.osrsllm.cloud.CloudChatRunner
import co.rowm.osrsllm.cloud.ConfigManagerDeviceKeyStore
import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.ContextRouter
import co.rowm.osrsllm.cloud.DeviceKey
import co.rowm.osrsllm.cloud.DirectChatBackend
import co.rowm.osrsllm.cloud.DirectChatRunner
import co.rowm.osrsllm.cloud.EgressGate
import co.rowm.osrsllm.cloud.PairingFlow
import co.rowm.osrsllm.cloud.StubToolDispatcher
import co.rowm.osrsllm.cloud.byo.ChatResponse
import co.rowm.osrsllm.events.EventLogService
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
    name = "OSRS LLM Helper",
    description = "Outbound LLM chat assistant for OSRS — pair to your Tibbly account or bring your own provider key.",
    tags = ["chat", "assistant", "external"],
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
    @Inject private lateinit var backendWsClient: BackendWsClient
    @Inject private lateinit var pairingFlow: PairingFlow
    @Inject private lateinit var egressGate: EgressGate
    @Inject private lateinit var accountSummaryClient: AccountSummaryClient
    @Inject private lateinit var contextRouter: ContextRouter
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
    @Inject private lateinit var deviceKey: DeviceKey

    private var navButton: NavigationButton? = null
    private var panel: OsrsLlmHelperPanel? = null
    private var chatNavButton: NavigationButton? = null
    private var chatPanel: ChatPanel? = null
    private var managedNavButton: NavigationButton? = null
    private var managedPanel: co.rowm.osrsllm.managed.ManagedVisualsPanel? = null
    private var accountNavButton: NavigationButton? = null
    private var accountPanel: AccountPanel? = null
    private var accountRefreshTimer: javax.swing.Timer? = null
    private val chatStore = ChatStore()
    private var cloudChatRunner: CloudChatRunner? = null
    private var cloudChatBackend: CloudChatBackend? = null

    /**
     * Tier-2 BYO chat runner. Constructed at startup when consent is on
     * AND `chatMode.isByo == true`. Stays null in cloud-mode and tools-only
     * mode. The runner has no persistent connection — every [DirectChatRunner.send]
     * opens a fresh HTTPS request to the configured provider.
     */
    private var byoChatRunner: DirectChatRunner? = null
    private var byoChatBackend: DirectChatBackend? = null

    /**
     * Backend selector exposed to the OverlayChatController via a stable
     * supplier. The selector itself is built per-startUp so that consent
     * freezing, ws connection, and BYO key plumbing all reflect the current
     * config snapshot.
     */
    @Volatile private var activeBackendSelector: ChatBackendSelector? = null

    private val overlayBackendSupplier =
        object : OverlayChatController.ChatBackendSupplier {
            override fun get(): ChatBackend? = activeBackendSelector
        }

    override fun startUp() {
        LoggingSetup.configure()
        val chatMode = config.chatMode()
        log.info(
            "Plugin starting (developerMode={}, cloudChatEnabled={}, chatMode={})",
            config.developerMode(), config.cloudChatEnabled(), chatMode::class.simpleName,
        )

        // Cross-flag sanity: `cloudChatEnabled` is the legacy boolean and
        // `chatMode` is the new source of truth. If the player has set both
        // ("cloud chat on" + a BYO mode) we LOG A WARNING and obey chatMode,
        // not silently revert. The BYO path NEVER routes via our backend.
        if (config.cloudChatEnabled() && chatMode != ChatMode.Cloud && chatMode != ChatMode.ToolsOnly) {
            log.warn(
                "Conflicting config: cloudChatEnabled=true but chatMode={}. " +
                    "Honouring chatMode — Tibbly backend will NOT be used for chat.",
                chatMode::class.simpleName,
            )
        }

        // Freeze consent ONCE for this plugin lifetime — see ConsentState KDoc.
        // A mid-session config flip cannot start sending data without a plugin restart.
        ConsentState.freeze(accepted = config.consentAccepted())
        eventBus.register(gameStateStore)
        eventBus.register(eventLogService)
        eventBus.register(xpRateService)
        eventBus.register(lootService)
        eventBus.register(hitsplatHistoryService)
        eventBus.register(widgetTracker)

        val sidebar = OsrsLlmHelperPanel(gameStateStore, pairingFlow)
        panel = sidebar
        val button = NavigationButton.builder()
            .tooltip("OSRS LLM Helper")
            .icon(createStatusIcon())
            .priority(7)
            .panel(sidebar)
            .build()
        navButton = button
        clientToolbar.addNavigation(button)

        // Build the chat-panel backend. The legacy local `claude -p` subprocess path
        // was removed ahead of the RuneLite Plugin Hub submission (audit blocker 1 —
        // subprocess invocation is forbidden in production source). The shipped
        // plugin routes turns through one of:
        //   - DirectChatBackend     when chatMode is a BYO variant + key configured
        //   - CloudChatBackend      when cloudChatEnabled + WSS link is live
        //   - null                  tools-only / not configured — panel renders an
        //                           "no backend configured" message
        val backendSelector = ChatBackendSelector(
            backendSupplier = {
                if (config.chatMode().isByo) {
                    byoChatBackend
                } else if (config.cloudChatEnabled() && backendWsClient.isConnected()) {
                    cloudChatBackend
                } else {
                    null
                }
            },
        )
        activeBackendSelector = backendSelector
        val chat = ChatPanel(chatStore, backendSelector)
        chatPanel = chat
        val chatBtn = NavigationButton.builder()
            .tooltip("OSRS LLM Chat")
            .icon(createChatIcon())
            .priority(8)
            .panel(chat)
            .build()
        chatNavButton = chatBtn
        clientToolbar.addNavigation(chatBtn)

        // No local listening socket exists in this build. The `local/McpServerService`
        // class is excluded from the shipped artifact by the shadowJar configuration
        // and verified by `:checkLocalNotInJar`. Player data only ever leaves
        // through EgressGate (see SECURITY_DESIGN.md).

        // Production path: open the WSS connection to the backend and start the chat
        // runner if the player has accepted consent AND cloud chat is enabled AND
        // chatMode is Cloud. All actual sends are funnelled through
        // EgressGate.egress() — there is no other write path. See
        // `apps/plugin/SECURITY_DESIGN.md`.
        //
        // chatMode is the new source of truth. If it's set to a BYO variant the
        // cloud runner DOES NOT START even if cloudChatEnabled is on — the BYO
        // path talks directly to the provider via DirectChatRunner (next PR).
        // If it's ToolsOnly the chat surface is intentionally disabled.
        if (config.consentAccepted() && config.cloudChatEnabled() && chatMode == ChatMode.Cloud) {
            val runner = CloudChatRunner(
                transport = backendWsClient,
                egressGate = egressGate,
                toolDispatcher = StubToolDispatcher(),
                authSupplier = {
                    CloudChatRunner.AuthFrame(
                        deviceKey = deviceKey.getOrCreate(),
                        playerName = runCatching { client.localPlayer?.name }.getOrNull(),
                        pluginVersion = "0.1.0",
                    )
                },
                backendUrlSupplier = { BackendUrl(config.backendUrl()) },
                consentSupplier = {
                    ConsentState.snapshot()
                        ?: ConsentState.freeze(accepted = config.consentAccepted())
                },
                cloudChatEnabledSupplier = { config.cloudChatEnabled() },
                callbacks = object : CloudChatRunner.Callbacks {
                    override fun onError(code: String, message: String) {
                        log.warn("Cloud chat error: code={} msg={}", code, message)
                    }
                    override fun onAuthenticated(authOk: co.rowm.osrsllm.cloud.InboundMessage.AuthOk) {
                        log.info("Cloud chat authenticated: tier={} balance={}",
                            authOk.tier, authOk.balanceTokens)
                    }
                    override fun onConnectionStateChanged(state: CloudChatRunner.ConnectionState) {
                        log.info("Cloud chat connection state: {}", state)
                    }
                },
            )
            cloudChatRunner = runner
            cloudChatBackend = CloudChatBackend(
                runner = runner,
                routerSupplier = { contextRouter },
                snapshotSupplier = {
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
            runCatching { runner.start() }
                .onFailure { log.warn("Backend connect failed: {}", it.message) }
        }

        // BYO chat path (Tier 2 — hub-release strategy). Constructs the
        // DirectChatRunner that talks DIRECTLY to the player's choice of
        // Anthropic / OpenAI / OpenRouter. No request ever touches the Tibbly
        // backend in this branch. The runner reads the API key + model +
        // telemetry preference lazily on each [DirectChatRunner.send] via
        // suppliers so a mid-session config edit is picked up without a
        // plugin restart (consent itself still requires restart per
        // ConsentState's contract).
        if (config.consentAccepted() && chatMode.isByo) {
            val keyProvided = config.byoApiKey().isNotBlank()
            log.info(
                "BYO chat mode active ({}). API key configured: {}.",
                chatMode::class.simpleName, keyProvided,
            )
            val directRunner = DirectChatRunner(
                egressGate = egressGate,
                keySupplier = { config.byoApiKey() },
                modeSupplier = { config.chatMode() },
                modelSupplier = { config.byoModel() },
                consentSupplier = {
                    ConsentState.snapshot()
                        ?: ConsentState.freeze(accepted = config.consentAccepted())
                },
                telemetryOptInSupplier = { config.byoTelemetryOptIn() },
                callbacks = object : DirectChatRunner.Callbacks {
                    override fun onError(code: String, message: String) {
                        log.warn("BYO chat error: code={} msg={}", code, message)
                    }
                    override fun onAssistantMessage(response: ChatResponse) {
                        log.debug(
                            "BYO chat reply: chars={} stop={} in={} out={}",
                            response.text.length, response.stopReason,
                            response.inputTokens, response.outputTokens,
                        )
                    }
                    override fun onTurnStarted(provider: String, model: String) {
                        log.info("BYO chat turn started: provider={} model={}", provider, model)
                    }
                },
            )
            byoChatRunner = directRunner
            byoChatBackend = DirectChatBackend(
                runner = directRunner,
                systemPromptSupplier = {
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
            runCatching { directRunner.start() }
                .onFailure { log.warn("DirectChatRunner start failed: {}", it.message) }
        }

        if (chatMode == ChatMode.ToolsOnly) {
            log.info("ChatMode=ToolsOnly — chat panel surfaces stay live but no provider is wired.")
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
            .priority(10)
            .panel(managed)
            .build()
        managedNavButton = managedBtn
        clientToolbar.addNavigation(managedBtn)

        // Tibbly account panel — D-8 pivot. Lives below the chat panel
        // (priority 9 vs 8). The panel itself decides whether to show the
        // account sections or the "cloud chat is off" explainer based on the
        // current config snapshot, so it's safe to register unconditionally.
        val accountSidebar = AccountPanel(
            config = config,
            accountClient = accountSummaryClient,
            pairingFlow = pairingFlow,
            clientSupplier = { client },
        )
        accountPanel = accountSidebar
        val accountBtn = NavigationButton.builder()
            .tooltip("Tibbly account")
            .icon(createAccountIcon())
            .priority(9)
            .panel(accountSidebar)
            .build()
        accountNavButton = accountBtn
        clientToolbar.addNavigation(accountBtn)

        // Refresh once at start, then every 30s while the panel is mounted.
        accountSidebar.refresh()
        val timer = javax.swing.Timer(30_000) { accountSidebar.refresh() }
        timer.isRepeats = true
        timer.start()
        accountRefreshTimer = timer
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
        runCatching { cloudChatRunner?.stop() }
        cloudChatRunner = null
        cloudChatBackend = null
        runCatching { byoChatRunner?.stop() }
        byoChatRunner = null
        byoChatBackend = null
        activeBackendSelector = null
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
        accountNavButton?.let { clientToolbar.removeNavigation(it) }
        navButton = null
        chatNavButton = null
        managedNavButton = null
        accountNavButton = null
        panel?.stop()
        panel = null
        chatPanel?.detach()
        chatPanel = null
        managedPanel?.detach()
        managedPanel = null
        runCatching { accountRefreshTimer?.stop() }
        accountRefreshTimer = null
        accountPanel = null
        gameStateStore.clear()
    }

    @Subscribe
    fun onConfigChanged(event: ConfigChanged) {
        if (event.group != "osrsllm") return
        log.info("Config changed: {} = {}", event.key, event.newValue)
        // No config key currently demands a runtime re-wire — the chat backend
        // selector reads its sources on every send so flips are picked up
        // without a plugin restart. Keep the @Subscribe so callers wishing to
        // react to a specific key (without spawning yet another listener) can
        // add a branch here.
    }

    @Provides
    fun provideConfig(configManager: ConfigManager): OsrsLlmHelperConfig =
        configManager.getConfig(OsrsLlmHelperConfig::class.java)

    /**
     * The long-lived per-install device key is now sourced from the
     * canonical [DeviceKey] helper (SecureRandom-backed, 40-char nanoid,
     * alphabet-validated). The previous in-Plugin generator concatenated
     * two `UUID.randomUUID()` strings, which produced a hex value of
     * different shape from the [DeviceKey] alphabet/length used by the
     * audit, hash format, and backend tests. See `DeviceKey.kt`.
     *
     * `configManager` is still injected because other code paths need it
     * for RuneLite config reads/writes; the device-key persistence lives
     * inside [ConfigManagerDeviceKeyStore].
     */
    @Inject private lateinit var configManager: ConfigManager

    @Provides @Singleton
    fun provideChatStore(): ChatStore = chatStore

    /**
     * Bind the [OverlayChatController.ChatBackendSupplier] interface so the
     * overlay controller can resolve the active backend selector on each
     * dispatched query without holding a hard reference to it. The supplier
     * itself is a stable closure over [activeBackendSelector].
     */
    @Provides @Singleton
    fun provideOverlayBackendSupplier(): OverlayChatController.ChatBackendSupplier =
        overlayBackendSupplier

    // RAI-23: device-key storage + backend-URL supplier for the pairing flow.
    @Provides @Singleton
    fun provideDeviceKeyStore(store: ConfigManagerDeviceKeyStore): DeviceKey.Store = store

    @Provides @Singleton
    fun providePairingBackendUrlSupplier(cfg: OsrsLlmHelperConfig): PairingFlow.BackendUrlSupplier =
        PairingFlow.BackendUrlSupplier { BackendUrl(cfg.backendUrl()) }

    private fun createStatusIcon(): BufferedImage = textIcon("AI", Color(74, 144, 226))

    private fun createAccountIcon(): BufferedImage = textIcon("T", Color(220, 180, 100))

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
