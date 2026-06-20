package co.rowm.osrsllm

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import net.runelite.client.ui.PluginPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.TimeUnit
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class OsrsLlmHelperPanel(
    private val gameStateStore: GameStateStore,
    private val mcpServerService: McpServerService,
) : PluginPanel() {

    private val log = LoggerFactory.getLogger(OsrsLlmHelperPanel::class.java)
    private val statusLabel = JLabel("MCP: starting…")
    private val urlLabel = JLabel(" ")
    private val playerLabel = JLabel(" ")
    private val inventoryLabel = JLabel(" ")
    private val bankLabel = JLabel(" ")
    private val questsLabel = JLabel(" ")
    private val installButton = JButton("Install in Claude CLI")
    private val copyButton = JButton("Copy command")
    private val installStatus = JLabel(" ")
    private val refresher: Timer

    init {
        layout = BorderLayout(0, 8)
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val title = JLabel("OSRS LLM Helper")
        title.font = FontManager.getRunescapeBoldFont()
        title.foreground = Color.WHITE
        add(title, BorderLayout.NORTH)

        val rows = JPanel(GridLayout(0, 1, 0, 4))
        rows.background = background
        listOf(statusLabel, urlLabel, playerLabel, inventoryLabel, bankLabel, questsLabel).forEach {
            it.foreground = Color.LIGHT_GRAY
            it.font = FontManager.getRunescapeSmallFont()
            rows.add(it)
        }
        add(rows, BorderLayout.CENTER)

        val bottom = JPanel()
        bottom.layout = BoxLayout(bottom, BoxLayout.Y_AXIS)
        bottom.background = background
        installButton.alignmentX = LEFT_ALIGNMENT
        copyButton.alignmentX = LEFT_ALIGNMENT
        installStatus.alignmentX = LEFT_ALIGNMENT
        installStatus.foreground = Color.LIGHT_GRAY
        installStatus.font = FontManager.getRunescapeSmallFont()
        installButton.addActionListener { runInstall() }
        copyButton.addActionListener { copyCommandToClipboard() }
        bottom.add(installButton)
        bottom.add(Box.createVerticalStrut(4))
        bottom.add(copyButton)
        bottom.add(Box.createVerticalStrut(6))
        bottom.add(installStatus)
        add(bottom, BorderLayout.SOUTH)

        refresher = Timer(1000) { refresh() }
        refresher.isRepeats = true
        refresher.start()
        refresh()
    }

    fun stop() = refresher.stop()

    private fun refresh() {
        SwingUtilities.invokeLater {
            val url = mcpServerService.boundUrl()
            val running = url != null
            if (running) {
                statusLabel.text = "MCP: listening"
                urlLabel.text = url
            } else {
                statusLabel.text = "MCP: stopped"
                urlLabel.text = " "
            }
            installButton.isEnabled = running
            copyButton.isEnabled = running

            val snap = gameStateStore.snapshot()
            playerLabel.text = when {
                !snap.loggedIn -> "Status: logged out"
                snap.player?.name != null -> "Player: ${snap.player.name}"
                else -> "Status: logged in"
            }
            val invCount = snap.inventory.items.sumOf { it.quantity.toLong() }
            inventoryLabel.text = "Inventory: $invCount items"
            bankLabel.text = if (snap.bank.lastSeenAt == null) {
                "Bank: not yet opened"
            } else {
                "Bank: ${snap.bank.totalQuantity} items"
            }
            val inProgress = snap.quests.count { it.state == "IN_PROGRESS" }
            val finished = snap.quests.count { it.state == "FINISHED" }
            questsLabel.text = "Quests: $finished done • $inProgress in progress"
        }
    }

    private fun claudeCommand(): String? {
        val url = mcpServerService.boundUrl() ?: return null
        // Silently remove any existing registration (likely from a different port/scope)
        // before adding fresh, so reinstall is idempotent.
        return "(claude mcp remove osrs --scope user >/dev/null 2>&1 || true); " +
            "(claude mcp remove osrs --scope local >/dev/null 2>&1 || true); " +
            "claude mcp add osrs --scope user --transport http $url"
    }

    private fun copyCommandToClipboard() {
        val cmd = claudeCommand() ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(cmd), null)
        setStatus("Command copied to clipboard.", Color.LIGHT_GRAY)
    }

    private fun runInstall() {
        val cmd = claudeCommand() ?: return
        log.info("Install button clicked. Command: {}", cmd)
        setStatus("Running claude mcp add…", Color.LIGHT_GRAY)
        installButton.isEnabled = false
        Thread({
            val installResult = runCatching { execInLoginShell(cmd) }
                .getOrElse { Result(-1, it.message ?: "error") }
            log.info("Install command exit={}, output:\n{}", installResult.exit, installResult.output)

            if (installResult.exit != 0) {
                SwingUtilities.invokeLater {
                    installButton.isEnabled = mcpServerService.boundUrl() != null
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(cmd), null)
                    val summary = installResult.output.lineSequence()
                        .lastOrNull { it.isNotBlank() } ?: "(no output)"
                    setStatus(
                        "<html>Install failed (exit ${installResult.exit}).<br/>Command copied to clipboard.<br/>$summary</html>",
                        Color(220, 160, 100),
                    )
                }
                return@Thread
            }

            SwingUtilities.invokeLater {
                setStatus("Installed. Verifying connection…", Color.LIGHT_GRAY)
            }

            val listResult = runCatching { execInLoginShell("claude mcp list") }
                .getOrElse { Result(-1, it.message ?: "error") }
            log.info("claude mcp list exit={}, output:\n{}", listResult.exit, listResult.output)

            SwingUtilities.invokeLater {
                installButton.isEnabled = mcpServerService.boundUrl() != null
                val status = parseListStatus(listResult.output, "osrs")
                log.info("Parsed status: {}", status)
                when (status) {
                    ListStatus.CONNECTED ->
                        setStatus("✓ osrs installed and connected", Color(120, 200, 120))
                    ListStatus.FAILED ->
                        setStatus(
                            "<html>Installed, but claude can't connect.<br/>" +
                                "Check the MCP server is listening and try again.</html>",
                            Color(220, 100, 100),
                        )
                    ListStatus.NOT_FOUND ->
                        setStatus(
                            "<html>Install reported success but 'osrs' isn't in<br/>" +
                                "<code>claude mcp list</code>. Run it manually to debug.</html>",
                            Color(220, 160, 100),
                        )
                    ListStatus.UNKNOWN ->
                        setStatus(
                            "<html>Installed. Couldn't parse <code>claude mcp list</code> —<br/>" +
                                "run it manually to confirm.</html>",
                            Color(220, 200, 100),
                        )
                }
            }
        }, "osrsllm-claude-install").apply { isDaemon = true }.start()
    }

    private enum class ListStatus { CONNECTED, FAILED, NOT_FOUND, UNKNOWN }

    private fun parseListStatus(output: String, name: String): ListStatus {
        if (output.isBlank()) return ListStatus.UNKNOWN
        val matchLine = output.lines().firstOrNull { it.contains(name, ignoreCase = true) }
            ?: return ListStatus.NOT_FOUND
        val lower = matchLine.lowercase()
        val positive = lower.contains("✓") || lower.contains("connected") ||
            lower.contains(" ok") || lower.contains("healthy")
        val negative = lower.contains("✗") || lower.contains("failed") ||
            lower.contains("error") || lower.contains("unreachable")
        return when {
            negative && !positive -> ListStatus.FAILED
            positive && !negative -> ListStatus.CONNECTED
            else -> ListStatus.UNKNOWN
        }
    }

    private fun setStatus(text: String, color: Color) {
        installStatus.text = text
        installStatus.foreground = color
    }

    private data class Result(val exit: Int, val output: String)

    private fun execInLoginShell(cmd: String): Result {
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/zsh"
        val pb = ProcessBuilder(shell, "-l", "-c", cmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val finished = proc.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return Result(-1, "timed out after 15s\n$output")
        }
        return Result(proc.exitValue(), output)
    }
}
