package co.rowm.osrsllm.cloud

import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

/**
 * RAI-23 — modal shown after the player clicks "Pair with account" in the
 * plugin sidebar. Displays the 6-digit code, counts down the 10-minute TTL,
 * offers a copy button and an "I've claimed it" close button.
 *
 * The modal does NOT talk to the network. It is given a [PairingFlow] and
 * a [PairingFlow.CodeIssued] result by its caller; the modal then asks the
 * flow to start polling and routes the outcome back through [onResult].
 *
 * Build via [show] from the Swing thread. Tests skip the dialog and exercise
 * [PairingFlow] directly.
 */
class PairingModal private constructor(
    parent: java.awt.Window?,
    private val pairingFlow: PairingFlow,
    private val code: String,
    private val expiresAt: String,
    private val onResult: (PairingFlow.PollOutcome) -> Unit,
) : JDialog(parent, "Pair with account", ModalityType.MODELESS) {

    private val log = LoggerFactory.getLogger(PairingModal::class.java)
    private val countdownLabel = JLabel("", SwingConstants.CENTER)
    private val codeLabel = JLabel(formatCode(code), SwingConstants.CENTER)
    private val statusLabel = JLabel("Waiting for the dashboard to claim this code…", SwingConstants.CENTER)
    private val copyButton = JButton("Copy code")
    private val closeButton = JButton("I've claimed it")
    private val deadlineMillis: Long = System.currentTimeMillis() + PairingFlow.DEFAULT_POLL_TTL_MILLIS
    private var countdownTimer: Timer? = null

    init {
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = closeWithCancel()
        })
        layout = BorderLayout(0, 10)
        rootPane.border = BorderFactory.createEmptyBorder(16, 20, 16, 20)

        val header = JLabel("Enter this code on the dashboard", SwingConstants.CENTER)
        header.font = header.font.deriveFont(Font.PLAIN, 13f)
        add(header, BorderLayout.NORTH)

        codeLabel.font = Font("Monospaced", Font.BOLD, 28)
        codeLabel.foreground = Color(220, 200, 100)
        codeLabel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        add(codeLabel, BorderLayout.CENTER)

        countdownLabel.font = countdownLabel.font.deriveFont(Font.PLAIN, 12f)
        countdownLabel.foreground = Color.LIGHT_GRAY

        val south = JPanel()
        south.layout = BoxLayout(south, BoxLayout.Y_AXIS)
        south.add(countdownLabel)
        south.add(Box.createVerticalStrut(6))
        south.add(statusLabel)
        south.add(Box.createVerticalStrut(10))

        val buttons = JPanel(GridLayout(1, 2, 8, 0))
        copyButton.addActionListener { copyToClipboard() }
        closeButton.addActionListener { closeWithCancel() }
        buttons.add(copyButton)
        buttons.add(closeButton)
        south.add(buttons)
        add(south, BorderLayout.SOUTH)

        preferredSize = Dimension(360, 220)
        pack()
        setLocationRelativeTo(parent)
        startCountdown()
        startPolling()
    }

    private fun startCountdown() {
        updateCountdown()
        val t = Timer(1_000) { updateCountdown() }
        t.isRepeats = true
        t.start()
        countdownTimer = t
    }

    private fun updateCountdown() {
        val remainingMs = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val mins = (remainingMs / 60_000L).toInt()
        val secs = ((remainingMs / 1_000L) % 60L).toInt()
        countdownLabel.text = String.format("Expires in %d:%02d", mins, secs)
        if (remainingMs <= 0L) {
            countdownTimer?.stop()
            statusLabel.text = "Code expired — close this window and try again."
            closeButton.text = "Close"
            copyButton.isEnabled = false
        }
    }

    private fun startPolling() {
        pairingFlow.startPolling(
            code = code,
            ttlMillis = PairingFlow.DEFAULT_POLL_TTL_MILLIS,
            intervalMillis = PairingFlow.DEFAULT_POLL_INTERVAL_MILLIS,
        ) { outcome ->
            SwingUtilities.invokeLater { handleOutcome(outcome) }
        }
    }

    private fun handleOutcome(outcome: PairingFlow.PollOutcome) {
        when (outcome) {
            is PairingFlow.PollOutcome.Claimed -> {
                statusLabel.text = "Paired! You can close this window."
                closeButton.text = "Done"
                copyButton.isEnabled = false
                countdownTimer?.stop()
                onResult(outcome)
            }
            PairingFlow.PollOutcome.Expired -> {
                statusLabel.text = "Code expired — please try again."
                closeButton.text = "Close"
                copyButton.isEnabled = false
                countdownTimer?.stop()
                onResult(outcome)
            }
            is PairingFlow.PollOutcome.Error -> {
                statusLabel.text = "Polling error: ${outcome.message}"
            }
            PairingFlow.PollOutcome.Pending -> {
                // Should not arrive here — pollUntil only emits terminal states.
            }
        }
    }

    private fun copyToClipboard() {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
        statusLabel.text = "Code copied to clipboard."
    }

    private fun closeWithCancel() {
        countdownTimer?.stop()
        pairingFlow.cancel()
        log.debug("PairingModal closing (expiresAt={})", expiresAt)
        isVisible = false
        dispose()
    }

    companion object {
        /** Display the 6-char code as `ABC-123` for human readability. */
        internal fun formatCode(code: String): String =
            if (code.length == 6) "${code.substring(0, 3)}-${code.substring(3)}" else code

        /** Show the modal. Must be called on the Swing EDT. */
        fun show(
            parent: java.awt.Window?,
            pairingFlow: PairingFlow,
            issued: PairingFlow.CodeIssued,
            onResult: (PairingFlow.PollOutcome) -> Unit,
        ): PairingModal {
            val modal = PairingModal(parent, pairingFlow, issued.code, issued.expiresAt, onResult)
            modal.isVisible = true
            return modal
        }
    }
}
