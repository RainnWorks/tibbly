package co.rowm.osrsllm

import net.runelite.client.config.Config
import net.runelite.client.config.ConfigGroup
import net.runelite.client.config.ConfigItem
import net.runelite.client.config.Range

@ConfigGroup("osrsllm")
interface OsrsLlmHelperConfig : Config {

    @ConfigItem(
        keyName = "enabled",
        name = "Enable MCP server",
        description = "Start the embedded MCP server on plugin load.",
        position = 0,
    )
    fun enabled(): Boolean = true

    @ConfigItem(
        keyName = "host",
        name = "Bind host",
        description = "Interface to bind the MCP server to. Use 127.0.0.1 to keep it local-only.",
        position = 1,
    )
    fun host(): String = "127.0.0.1"

    @ConfigItem(
        keyName = "port",
        name = "Port",
        description = "Port to serve the MCP endpoint on (http://<host>:<port>/mcp).",
        position = 2,
    )
    @Range(min = 1024, max = 65535)
    fun port(): Int = 51823
}
