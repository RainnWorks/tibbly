package co.rowm.osrsllm.ui.sections

import co.rowm.osrsllm.ChatModeChoice
import co.rowm.osrsllm.cloud.PairingFlow
import co.rowm.osrsllm.cloud.PairingModal
import co.rowm.osrsllm.ui.CollapsibleSection
import co.rowm.osrsllm.ui.FormBuilder
import co.rowm.osrsllm.ui.TibblySettings
import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Account section — pairing state machine + manage-billing + sign-out +
 * BYOK toggle. The chat-mode dropdown lives here too because BYOK lives
 * under "Account" in the player's mental model.
 *
 * Pairing UX state machine (from the design discussion):
 *   Unpaired                 →  big Pair button
 *   WaitingForConfirmation   →  spinner + code + cancel
 *   Paired(email, tier)      →  status card + actions
 *
 * For this first cut the section delegates to the existing
 * `PairingFlow` + `PairingModal` rather than re-implementing the modal
 * in-panel — that keeps the surface area small and lets us iterate on
 * the in-panel state machine without churning the rest of the plugin.
 */
internal class AccountSection(
    private val settings: TibblySettings,
    private val pairingFlow: PairingFlow?,
    private val billingPortalUrl: String,
) {

    private val log = LoggerFactory.getLogger(AccountSection::class.java)

    private val statusLabel = JLabel(" ").apply {
        font = FontManager.getRunescapeBoldFont()
        foreground = Color(232, 196, 122)
    }
    private val subStatusLabel = JLabel(" ").apply {
        font = FontManager.getRunescapeSmallFont()
        foreground = Color(190, 190, 190)
    }
    private val pairButton = JButton("Pair this device").apply { isFocusPainted = false }
    private val manageBillingButton = JButton("Manage billing").apply { isFocusPainted = false }

    @Volatile
    private var pairedPlayerName: String? = null

    fun build(): CollapsibleSection {
        val body = FormBuilder.body()

        val statusCard = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = ColorScheme.DARKER_GRAY_COLOR
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter(), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
            )
            alignmentX = Component.LEFT_ALIGNMENT
            add(statusLabel)
            add(Box.createRigidArea(Dimension(0, 4)))
            add(subStatusLabel)
        }
        body.add(statusCard)
        body.add(Box.createRigidArea(Dimension(0, 8)))

        val buttonRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            background = ColorScheme.DARK_GRAY_COLOR
            alignmentX = Component.LEFT_ALIGNMENT
        }
        pairButton.addActionListener { onPairClicked() }
        manageBillingButton.addActionListener { openBillingPortal() }
        manageBillingButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        buttonRow.add(pairButton)
        buttonRow.add(Box.createRigidArea(Dimension(8, 0)))
        buttonRow.add(manageBillingButton)
        body.add(buttonRow)

        body.add(Box.createRigidArea(Dimension(0, 12)))
        body.add(FormBuilder.caption("BYOK lets you send chat directly to the provider with your own key. Tibbly never sees the request."))

        val chatMode = FormBuilder.combo(ChatModeChoice.values(), settings.chatModeChoice)
        chatMode.addActionListener {
            val sel = chatMode.selectedItem as? ChatModeChoice ?: return@addActionListener
            settings.chatModeChoice = sel
            updateByokRowsVisibility()
        }
        body.add(FormBuilder.row("Chat mode", chatMode, "Cloud is the default."))

        // JPasswordField — parity with the legacy `secret = true` annotation
        // that RuneLite used to render the key masked.
        byokKeyField = javax.swing.JPasswordField(settings.byoApiKey).apply {
            preferredSize = Dimension(140, 22)
            background = net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR
            foreground = Color(220, 220, 220)
            caretColor = Color.WHITE
            border = BorderFactory.createLineBorder(
                net.runelite.client.ui.ColorScheme.DARKER_GRAY_COLOR.brighter(),
                1,
            )
        }
        byokKeyField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                val typed = String(byokKeyField.password)
                if (settings.byoApiKey != typed) settings.byoApiKey = typed
            }
        })
        byokKeyRow = FormBuilder.row("BYO key", byokKeyField, "Stored locally. Never sent to Tibbly.")
        body.add(byokKeyRow)

        byokModelField = FormBuilder.textField(settings.byoModel)
        byokModelField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                if (settings.byoModel != byokModelField.text) settings.byoModel = byokModelField.text
            }
        })
        byokModelRow = FormBuilder.row("BYO model", byokModelField, "Optional — leave blank for the provider default.")
        body.add(byokModelRow)

        updateByokRowsVisibility()
        refreshStatus()

        return CollapsibleSection("Account", body as JPanel, expandedByDefault = true)
    }

    private lateinit var byokKeyField: javax.swing.JPasswordField
    private lateinit var byokKeyRow: JPanel
    private lateinit var byokModelField: javax.swing.JTextField
    private lateinit var byokModelRow: JPanel

    /** Test-only — direct handle on the key field (asserts password masking). */
    internal fun byokKeyFieldForTest(): javax.swing.JPasswordField = byokKeyField

    private fun updateByokRowsVisibility() {
        val mode = settings.chatModeChoice
        val byok = mode == ChatModeChoice.BYO_ANTHROPIC ||
            mode == ChatModeChoice.BYO_OPENAI ||
            mode == ChatModeChoice.BYO_OPENROUTER
        byokKeyRow.isVisible = byok
        byokModelRow.isVisible = byok
        byokKeyRow.parent?.revalidate()
        byokKeyRow.parent?.repaint()
    }

    /**
     * Pair button entry point. Mirrors the legacy `OsrsLlmHelperPanel`
     * implementation: spawn off the EDT, request a code, then hand off
     * to the existing modal to poll for claim and report the outcome.
     */
    private fun onPairClicked() {
        val flow = pairingFlow ?: run {
            setStatus("Pairing not wired in this build.", "")
            return
        }
        pairButton.isEnabled = false
        setStatus("Requesting code…", "")

        Thread({
            val issuedResult = runCatching { flow.requestCode(null) }
            SwingUtilities.invokeLater {
                pairButton.isEnabled = true
                issuedResult.fold(
                    onSuccess = { issued ->
                        val parent = SwingUtilities.getWindowAncestor(pairButton)
                        PairingModal.show(parent, flow, issued) { outcome ->
                            handlePairingOutcome(outcome)
                        }
                    },
                    onFailure = { e ->
                        log.warn("Pairing request failed", e)
                        setStatus("Pairing failed.", e.message ?: "")
                    },
                )
            }
        }, "tibbly-pairing-request").apply { isDaemon = true }.start()
    }

    private fun handlePairingOutcome(outcome: PairingFlow.PollOutcome) {
        when (outcome) {
            is PairingFlow.PollOutcome.Claimed -> {
                pairedPlayerName = outcome.playerName
                pairButton.text = "Re-pair"
                val name = outcome.playerName.orEmpty()
                val suffix = if (name.isBlank()) "" else " · @$name"
                setStatus("Paired$suffix", "You're set. Chat away.")
            }
            PairingFlow.PollOutcome.Expired ->
                setStatus("Code expired", "Try again — codes last 10 minutes.")
            is PairingFlow.PollOutcome.Error ->
                setStatus("Pairing failed", outcome.message)
            PairingFlow.PollOutcome.Pending -> {
                /* modal owns the spinner — nothing for the section to do */
            }
        }
    }

    private fun openBillingPortal() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(billingPortalUrl))
            } else {
                setStatus("Open browser manually:", billingPortalUrl)
            }
        } catch (e: Exception) {
            log.warn("Open billing portal failed", e)
            setStatus("Could not open browser", billingPortalUrl)
        }
    }

    private fun refreshStatus() {
        val name = pairedPlayerName
        if (name.isNullOrBlank()) {
            setStatus("Unpaired", "Pair this device to use the cloud chat.")
        } else {
            setStatus("Paired · @$name", "")
        }
    }

    private fun setStatus(title: String, sub: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = title
            subStatusLabel.text = sub
        }
    }

    /** Test-only — last successful pairing's player name. */
    internal fun pairedPlayerNameOrNull(): String? = pairedPlayerName
}
