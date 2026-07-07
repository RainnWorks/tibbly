package co.rowm.osrsllm.ui.sections

import co.rowm.osrsllm.ui.CollapsibleSection
import co.rowm.osrsllm.ui.FormBuilder
import co.rowm.osrsllm.ui.TibblySettings
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Developer section — server URL, local MCP host/port, dev-mode master
 * toggle. Collapsed by default; mostly for our own iteration loop and
 * power users who want to point the plugin at a self-hosted backend.
 *
 * Note on the backend URL: it must remain a secure WebSocket scheme in
 * prod builds. The plugin's own startup validation refuses plaintext,
 * so even an accidental non-secure entry here gets rejected at
 * connect-time rather than silently downgrading the security boundary.
 */
internal object DeveloperSection {

    fun build(settings: TibblySettings): CollapsibleSection {
        val body = FormBuilder.body()

        val devMode = FormBuilder.checkBox("Developer mode", settings.developerMode)
        devMode.addActionListener { settings.developerMode = devMode.isSelected }
        body.add(
            FormBuilder.row(
                label = "Dev mode",
                input = devMode,
                help = "Surfaces the local MCP server and verbose logs. Off in hub-installed builds.",
            ),
        )

        val backend = FormBuilder.textField(settings.backendUrl)
        backend.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                if (settings.backendUrl != backend.text) settings.backendUrl = backend.text
            }
        })
        body.add(
            FormBuilder.row(
                label = "Backend URL",
                input = backend,
                help = "WSS endpoint of the chat backend. Plaintext refused at startup.",
            ),
        )

        val localMcp = FormBuilder.checkBox("Run local MCP server", settings.localMcpEnabled)
        localMcp.addActionListener { settings.localMcpEnabled = localMcp.isSelected }
        body.add(
            FormBuilder.row(
                label = "Local MCP",
                input = localMcp,
                help = "Only takes effect when Developer mode is on.",
            ),
        )

        val mcpHost = FormBuilder.textField(settings.localMcpHost)
        mcpHost.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                if (settings.localMcpHost != mcpHost.text) settings.localMcpHost = mcpHost.text
            }
        })
        body.add(FormBuilder.row("MCP host", mcpHost, "Loopback only by default."))

        val mcpPort = FormBuilder.textField(settings.localMcpPort.toString())
        mcpPort.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                val parsed = mcpPort.text.toIntOrNull()?.coerceIn(1024, 65535)
                if (parsed != null && settings.localMcpPort != parsed) {
                    settings.localMcpPort = parsed
                } else {
                    // Restore the displayed value if the user typed garbage.
                    mcpPort.text = settings.localMcpPort.toString()
                }
            }
        })
        body.add(FormBuilder.row("MCP port", mcpPort, "1024-65535."))

        return CollapsibleSection("Developer", body as JPanel, expandedByDefault = false)
    }
}
