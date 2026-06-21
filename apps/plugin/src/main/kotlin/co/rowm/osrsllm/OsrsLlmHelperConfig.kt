package co.rowm.osrsllm

import net.runelite.client.config.Config
import net.runelite.client.config.ConfigGroup
import net.runelite.client.config.ConfigItem
import net.runelite.client.config.ConfigSection
import net.runelite.client.config.Range

/**
 * Stable section id for the BYOK / chat-mode group in the RuneLite config
 * UI. Lives at package scope so we can reference it as a compile-time
 * constant inside [ConfigItem] annotations on [OsrsLlmHelperConfig].
 *
 * The actual [ConfigSection] annotation that carries the human-facing
 * label is attached to a `@JvmField` field inside the [OsrsLlmHelperConfig]
 * companion below — RuneLite's `getDeclaredFields()` scan finds it there.
 */
public const val CHAT_MODE_SECTION: String = "chatMode"

@ConfigGroup("osrsllm")
interface OsrsLlmHelperConfig : Config {

    // -------------------------------------------------------------------------
    // Cloud chat (production path) — the only data egress route in the
    // hub-shipped build. See SECURITY_DESIGN.md and cloud/EgressGate.kt.
    // -------------------------------------------------------------------------

    @ConfigItem(
        keyName = "consentAccepted",
        name = "I have read and accept data sharing",
        description = "Off by default. Must be on for any data to leave this plugin. Changes apply on next plugin restart.",
        position = 1,
    )
    fun consentAccepted(): Boolean = false

    @ConfigItem(
        keyName = "cloudChatEnabled",
        name = "Enable cloud chat",
        description = "When on, the plugin opens a single secure connection to the Rowm backend on startup. Off keeps the plugin fully local.",
        position = 2,
    )
    fun cloudChatEnabled(): Boolean = false

    @ConfigItem(
        keyName = "backendUrl",
        name = "Backend URL",
        description = "WSS endpoint of the chat backend. Must start with the secure WebSocket scheme. Plaintext is refused at startup.",
        position = 3,
    )
    fun backendUrl(): String = "wss://api.tibbly.io/plugin"

    // -------------------------------------------------------------------------
    // Chat mode (advanced) — Tier 2/3 selector for the three-tier value model.
    // See docs/architecture/HUB_RELEASE_STRATEGY.md.
    //
    // This section sits BELOW the main settings so a casual player isn't
    // confronted with provider knobs they don't need. The dropdown picks
    // the chat surface; the key + model fields apply only to the BYO
    // variants. Defaults preserve existing behaviour (Cloud).
    // -------------------------------------------------------------------------

    @ConfigItem(
        keyName = "chatMode",
        name = "Chat mode",
        description = "Tibbly cloud uses the managed backend. " +
            "Direct (BYO key) sends your messages straight to the provider — " +
            "Tibbly does not see them. Tools only keeps the in-RuneLite " +
            "panels and overlays but disables the chat surface.",
        position = 21,
        section = CHAT_MODE_SECTION,
    )
    fun chatModeChoice(): ChatModeChoice = ChatModeChoice.CLOUD

    @ConfigItem(
        keyName = "byoApiKey",
        name = "BYO API key",
        description = "Your provider API key. Stored locally in RuneLite config. " +
            "Never logged. Never sent to Tibbly. Only used when Chat mode is set " +
            "to a Direct (BYO key) option.",
        position = 22,
        section = CHAT_MODE_SECTION,
        secret = true,
    )
    fun byoApiKey(): String = ""

    @ConfigItem(
        keyName = "byoModel",
        name = "BYO model",
        description = "Optional model id (e.g. claude-sonnet-4-5, gpt-4o-mini, " +
            "anthropic/claude-opus-4-7). Leave blank to use the per-provider " +
            "default chosen by the plugin.",
        position = 23,
        section = CHAT_MODE_SECTION,
    )
    fun byoModel(): String = ""

    @ConfigItem(
        keyName = "byoTelemetryOptIn",
        name = "Share anonymous BYO usage telemetry",
        description = "Off by default. When on, the plugin sends a tiny anonymous " +
            "ping (mode + provider + latency) to Tibbly so we can publish a uptime " +
            "dashboard. Never includes your messages, API key, or game state.",
        position = 24,
        section = CHAT_MODE_SECTION,
    )
    fun byoTelemetryOptIn(): Boolean = false

    // -------------------------------------------------------------------------
    // Developer-only local MCP server (no production traffic; see local/).
    // Default OFF. Only meaningful when developerMode is also on.
    // -------------------------------------------------------------------------

    @ConfigItem(
        keyName = "developerMode",
        name = "Developer mode (advanced)",
        description = "Enables the local MCP server below. Off in hub-installed plugins.",
        position = 30,
    )
    fun developerMode(): Boolean = false

    @ConfigItem(
        keyName = "localMcpEnabled",
        name = "Local MCP server (dev)",
        description = "Start the local MCP server. Only takes effect when Developer mode is on.",
        position = 31,
    )
    fun localMcpEnabled(): Boolean = false

    @ConfigItem(
        keyName = "localMcpHost",
        name = "Local MCP host (dev)",
        description = "Loopback interface for the local MCP server. 127.0.0.1 keeps it local-only.",
        position = 32,
    )
    fun localMcpHost(): String = "127.0.0.1"

    @ConfigItem(
        keyName = "localMcpPort",
        name = "Local MCP port (dev)",
        description = "Port for the developer-only local MCP server.",
        position = 33,
    )
    @Range(min = 1024, max = 65535)
    fun localMcpPort(): Int = 51823

    companion object {
        /**
         * RuneLite groups config items under a section by matching the
         * `section` attribute on each [ConfigItem] against the value of a
         * `String` field that carries a [ConfigSection] annotation. The scan
         * uses `Class.getDeclaredFields()` on the interface class, so the
         * field must live at interface (companion) scope. `@JvmField` emits
         * a plain static String field, which is exactly what the scan
         * expects.
         */
        @JvmField
        @ConfigSection(
            name = "Chat mode (advanced)",
            description = "Pick where the chat panel sends your messages. " +
                "Defaults to Tibbly cloud. BYO mode talks directly to the " +
                "provider with your own API key — Tibbly never sees the request.",
            position = 20,
            closedByDefault = true,
        )
        public val chatModeSectionAnnotationCarrier: String = CHAT_MODE_SECTION
    }

    /**
     * Resolve the typed [ChatMode] from the persisted UI choice. Convenience
     * for the plugin startup path so it can switch on a sealed hierarchy
     * rather than the UI-shaped enum.
     */
    fun chatMode(): ChatMode = chatModeChoice().toChatMode()
}
