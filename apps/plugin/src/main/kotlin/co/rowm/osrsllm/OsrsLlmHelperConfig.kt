package co.rowm.osrsllm

import net.runelite.client.config.Config
import net.runelite.client.config.ConfigGroup
import net.runelite.client.config.ConfigItem
import net.runelite.client.config.Range

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
    // Developer-only local MCP server (no production traffic; see local/).
    // Default OFF. Only meaningful when developerMode is also on.
    // -------------------------------------------------------------------------

    @ConfigItem(
        keyName = "developerMode",
        name = "Developer mode (advanced)",
        description = "Enables the local MCP server below. Off in hub-installed plugins.",
        position = 10,
    )
    fun developerMode(): Boolean = false

    @ConfigItem(
        keyName = "localMcpEnabled",
        name = "Local MCP server (dev)",
        description = "Start the local MCP server. Only takes effect when Developer mode is on.",
        position = 11,
    )
    fun localMcpEnabled(): Boolean = false

    @ConfigItem(
        keyName = "localMcpHost",
        name = "Local MCP host (dev)",
        description = "Loopback interface for the local MCP server. 127.0.0.1 keeps it local-only.",
        position = 12,
    )
    fun localMcpHost(): String = "127.0.0.1"

    @ConfigItem(
        keyName = "localMcpPort",
        name = "Local MCP port (dev)",
        description = "Port for the developer-only local MCP server.",
        position = 13,
    )
    @Range(min = 1024, max = 65535)
    fun localMcpPort(): Int = 51823
}
