package co.rowm.osrsllm

import co.rowm.osrsllm.cloud.PairingFlow
import co.rowm.osrsllm.cloud.PairingModal
import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import net.runelite.client.ui.PluginPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Sidebar status panel. Shows the player a quick read of their connection
 * state (logged in / logged out, inventory / bank / quest summary) and lets
 * them initiate the pair-with-account flow.
 *
 * Historically this panel also hosted "Install in Claude CLI" and "Copy
 * command" buttons that spawned a login shell to run `claude mcp add osrs`.
 * That entire surface was deleted ahead of the RuneLite Plugin Hub
 * submission per the hub maintainer's audit (see
 * `docs/reviews/plugin-hub-readiness-001.md` blockers 1 + 2). The hub
 * policy forbids any subprocess invocation in the shipped artifact
 * (PR #11453 precedent), so the dev-only convenience had to go.
 */
class OsrsLlmHelperPanel(
    private val gameStateStore: GameStateStore,
    private val pairingFlow: PairingFlow? = null,
) : PluginPanel() {

    private val log = LoggerFactory.getLogger(OsrsLlmHelperPanel::class.java)
    private val statusLabel = JLabel("Status: starting…")
    private val playerLabel = JLabel(" ")
    private val inventoryLabel = JLabel(" ")
    private val bankLabel = JLabel(" ")
    private val questsLabel = JLabel(" ")
    private val pairButton = JButton("Pair with account")
    private val pairStatus = JLabel(" ")
    private val refresher: Timer

    @Volatile
    private var pairedPlayerName: String? = null

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
        listOf(statusLabel, playerLabel, inventoryLabel, bankLabel, questsLabel).forEach {
            it.foreground = Color.LIGHT_GRAY
            it.font = FontManager.getRunescapeSmallFont()
            rows.add(it)
        }
        add(rows, BorderLayout.CENTER)

        val bottom = JPanel()
        bottom.layout = BoxLayout(bottom, BoxLayout.Y_AXIS)
        bottom.background = background
        pairButton.alignmentX = LEFT_ALIGNMENT
        pairStatus.alignmentX = LEFT_ALIGNMENT
        pairStatus.foreground = Color.LIGHT_GRAY
        pairStatus.font = FontManager.getRunescapeSmallFont()
        pairButton.addActionListener { startPairing() }
        pairButton.isEnabled = pairingFlow != null
        bottom.add(pairButton)
        bottom.add(Box.createVerticalStrut(4))
        bottom.add(pairStatus)
        add(bottom, BorderLayout.SOUTH)

        refresher = Timer(1000) { refresh() }
        refresher.isRepeats = true
        refresher.start()
        refresh()
    }

    fun stop() = refresher.stop()

    private fun refresh() {
        SwingUtilities.invokeLater {
            val snap = gameStateStore.snapshot()
            playerLabel.text = when {
                !snap.loggedIn -> "Status: logged out"
                snap.player?.name != null -> "Player: ${snap.player.name}"
                else -> "Status: logged in"
            }
            statusLabel.text = if (snap.loggedIn) "Status: connected" else "Status: idle"
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

    // ------------------------------------------------------------------
    // RAI-23 — pair-with-account flow
    // ------------------------------------------------------------------

    private fun startPairing() {
        val flow = pairingFlow
        if (flow == null) {
            setPairStatus("Pairing not wired in this build.", Color(220, 160, 100))
            return
        }
        pairButton.isEnabled = false
        setPairStatus("Requesting code…", Color.LIGHT_GRAY)

        Thread({
            val playerName = runCatching { gameStateStore.snapshot().player?.name }.getOrNull()
            val issuedResult = runCatching { flow.requestCode(playerName) }

            SwingUtilities.invokeLater {
                pairButton.isEnabled = true
                issuedResult.fold(
                    onSuccess = { issued ->
                        setPairStatus("Code issued — enter it on the dashboard.", Color.LIGHT_GRAY)
                        val parent = SwingUtilities.getWindowAncestor(this)
                        PairingModal.show(parent, flow, issued) { outcome ->
                            handlePairingOutcome(outcome, playerName)
                        }
                    },
                    onFailure = { e ->
                        log.warn("Pairing request failed", e)
                        setPairStatus("Pairing failed: ${e.message}", Color(220, 100, 100))
                    },
                )
            }
        }, "osrsllm-pairing-request").apply { isDaemon = true }.start()
    }

    private fun handlePairingOutcome(outcome: PairingFlow.PollOutcome, playerName: String?) {
        when (outcome) {
            is PairingFlow.PollOutcome.Claimed -> {
                val name = outcome.playerName ?: playerName
                pairedPlayerName = name
                val nameSuffix = if (name.isNullOrBlank()) "" else " @$name"
                setPairStatus("Paired with$nameSuffix", Color(120, 200, 120))
                pairButton.text = "Re-pair"
            }
            PairingFlow.PollOutcome.Expired ->
                setPairStatus("Pairing code expired.", Color(220, 160, 100))
            is PairingFlow.PollOutcome.Error ->
                setPairStatus("Pairing error: ${outcome.message}", Color(220, 100, 100))
            PairingFlow.PollOutcome.Pending -> {
                // shouldn't reach here
            }
        }
    }

    private fun setPairStatus(text: String, color: Color) {
        pairStatus.text = text
        pairStatus.foreground = color
    }

    /** Test/debug accessor — last successful pairing's player name. */
    internal fun pairedPlayerNameOrNull(): String? = pairedPlayerName
}
