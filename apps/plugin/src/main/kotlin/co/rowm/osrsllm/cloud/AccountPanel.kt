package co.rowm.osrsllm.cloud

import co.rowm.osrsllm.OsrsLlmHelperConfig
import net.runelite.api.Client
import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import net.runelite.client.ui.PluginPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.io.File
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.SwingWorker

/**
 * D-8 plugin-pivot: the **Tibbly account panel** that lives inside RuneLite.
 *
 * REVIEWER NOTE — hub compliance:
 *
 *   1. **Opt-in by default.** This panel renders the account sections ONLY
 *      when both `config.cloudChatEnabled()` AND `config.consentAccepted()`
 *      are true. Otherwise it shows a single explainer card pointing the
 *      player to RuneLite's plugin-config sidebar to flip those toggles.
 *
 *   2. **Every byte leaves via [EgressGate].** The panel never opens its
 *      own URL connection. All reads go through [AccountSummaryClient],
 *      which delegates to [EgressGate.egressHttp]. The Gradle gate
 *      `:checkNoPlaintextUrls` already enforces `https://`-only on the
 *      production source set.
 *
 *   3. **No raw token counts visible.** Every player-visible number comes
 *      straight from the backend's tier-aware proxy ([UsageProxy]). The
 *      Gradle gate `:checkAccountPanelNoRawTokens` greps this source for
 *      the four token-arithmetic identifiers listed in the gate definition
 *      itself — if you find yourself wanting to write one of those, stop
 *      and surface the proxy instead.
 *
 *   4. **No secrets in labels.** The raw device key, raw email, and Stripe
 *      customer id never appear in any [JLabel], audit message, or log
 *      line emitted by this file. The backend's `/v1/account/summary`
 *      already strips those server-side; this panel just doesn't refer to
 *      them by name.
 *
 * # Architecture
 *
 * Three layers, mirroring [PairingModal.kt]:
 *
 *   - **Composition root** ([AccountPanel]) — wires data → sub-panels.
 *   - **Sub-panels** ([HeaderSection], [UsageSection], [OsrsAccountsSection],
 *     [DevicesSection], [SubscriptionSection], [PairingSection],
 *     [DataPrivacySection]) — each renders one row of the spec table.
 *   - **Form primitives** ([sectionTitle], [card], [secondaryButton]).
 *
 * # Threading
 *
 * Reads run inside a [SwingWorker] so the network blocking call doesn't
 * stall the EDT. The single [refresh] entry-point is safe to call repeatedly
 * from a [Timer] tick or button action; we coalesce by cancelling the
 * outstanding worker before spawning a new one.
 */
class AccountPanel(
    private val config: OsrsLlmHelperConfig,
    private val accountClient: AccountSummaryClient,
    private val pairingFlow: PairingFlow,
    private val clientSupplier: () -> Client?,
) : PluginPanel() {

    private val log = LoggerFactory.getLogger(AccountPanel::class.java)

    private val header = HeaderSection()
    private val usage = UsageSection()
    private val osrsAccounts = OsrsAccountsSection(onForget = ::forgetOsrsAccount)
    private val devices = DevicesSection()
    private val subscription = SubscriptionSection(onOpenPortal = ::openCustomerPortal)
    private val pairing = PairingSection(onPair = ::startPairing)
    private val dataPrivacy = DataPrivacySection(
        onExport = ::downloadExport,
        onDelete = ::deleteAccount,
    )

    private val explainer = JLabel(
        "<html><body style='width: 200px'>" +
            "<b>Tibbly account</b><br><br>" +
            "Cloud chat is off. Account management lives in this panel " +
            "once you flip <i>Enable cloud chat</i> and <i>I have read " +
            "and accept data sharing</i> in the plugin config.<br><br>" +
            "Until then the plugin stays fully local — no data leaves " +
            "your machine.</body></html>",
        SwingConstants.LEFT,
    )

    private var currentWorker: SwingWorker<*, *>? = null

    init {
        layout = BorderLayout(0, 8)
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        explainer.foreground = Color.LIGHT_GRAY
        explainer.font = FontManager.getRunescapeSmallFont()
        renderLayout()
    }

    /**
     * Re-render the layout based on the current config snapshot. Called once
     * at startup and again from [refresh] so a player toggling cloud-chat off
     * sees the explainer immediately.
     */
    private fun renderLayout() {
        removeAll()
        val title = JLabel("Tibbly")
        title.font = FontManager.getRunescapeBoldFont().deriveFont(18f)
        title.foreground = Color.WHITE
        add(title, BorderLayout.NORTH)

        if (!shouldShowAccount()) {
            add(explainer, BorderLayout.CENTER)
            revalidate()
            repaint()
            return
        }

        val center = JPanel()
        center.layout = BoxLayout(center, BoxLayout.Y_AXIS)
        center.background = background

        for (section in listOf(header, usage, osrsAccounts, devices, subscription, pairing, dataPrivacy)) {
            section.alignmentX = LEFT_ALIGNMENT
            center.add(section)
            center.add(Box.createVerticalStrut(10))
        }
        add(center, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /** Predicate driving the explainer-vs-account split. */
    private fun shouldShowAccount(): Boolean =
        config.cloudChatEnabled() && config.consentAccepted()

    /**
     * Kick off a fresh background fetch of summary + usage. Safe to call
     * from the EDT — the work happens in a [SwingWorker].
     */
    fun refresh() {
        renderLayout()
        if (!shouldShowAccount()) return

        currentWorker?.cancel(true)
        val worker = object : SwingWorker<Pair<AccountSummary?, UsageProxy?>, Unit>() {
            override fun doInBackground(): Pair<AccountSummary?, UsageProxy?> {
                val playerName = runCatching { clientSupplier()?.localPlayer?.name }.getOrNull()
                val summary = when (val r = accountClient.fetchSummary(playerName)) {
                    is AccountSummaryClient.SummaryResult.Ok -> r.summary
                    is AccountSummaryClient.SummaryResult.Error -> null
                }
                val proxy = when (val r = accountClient.fetchUsageProxy()) {
                    is AccountSummaryClient.UsageResult.Ok -> r.proxy
                    is AccountSummaryClient.UsageResult.Error -> null
                }
                return summary to proxy
            }

            override fun done() {
                if (isCancelled) return
                try {
                    val (summary, proxy) = get()
                    if (summary != null) {
                        header.bind(summary)
                        osrsAccounts.bind(summary.pairedOsrsAccounts)
                        devices.bind(summary.pairedDevices)
                        subscription.bind(summary)
                    } else {
                        header.bindError()
                    }
                    if (proxy != null) {
                        usage.bind(proxy)
                    } else {
                        usage.bindError()
                    }
                } catch (e: Exception) {
                    log.debug("AccountPanel refresh failed: {}", e.message)
                    header.bindError()
                    usage.bindError()
                }
            }
        }
        currentWorker = worker
        worker.execute()
    }

    /* ---------------------------------------------------- callbacks ---- */

    private fun forgetOsrsAccount(account: PairedOsrsAccount) {
        val ok = JOptionPane.showConfirmDialog(
            this,
            "Forget ${account.displayName}? You can pair it again any time.",
            "Forget OSRS account",
            JOptionPane.OK_CANCEL_OPTION,
        )
        if (ok != JOptionPane.OK_OPTION) return
        runInBackground(SwingRunnable {
            val success = accountClient.forgetOsrsAccount(account.id)
            SwingUtilities.invokeLater {
                if (success) refresh()
                else JOptionPane.showMessageDialog(this, "Couldn't forget that account. Try again.")
            }
        })
    }

    private fun openCustomerPortal() {
        runInBackground(SwingRunnable {
            val url = accountClient.openCustomerPortal()
            SwingUtilities.invokeLater {
                if (url.isNullOrBlank()) {
                    JOptionPane.showMessageDialog(this, "Couldn't open Stripe — try again in a moment.")
                    return@invokeLater
                }
                runCatching {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
                }.onFailure { log.warn("Failed to open Stripe portal: {}", it.message) }
            }
        })
    }

    private fun startPairing() {
        val playerName = runCatching { clientSupplier()?.localPlayer?.name }.getOrNull()
        runInBackground(SwingRunnable {
            val issued = runCatching { pairingFlow.requestCode(playerName) }.getOrNull()
            SwingUtilities.invokeLater {
                if (issued == null) {
                    JOptionPane.showMessageDialog(this, "Couldn't reach Tibbly. Check your connection.")
                    return@invokeLater
                }
                PairingModal.show(SwingUtilities.getWindowAncestor(this), pairingFlow, issued) {
                    refresh()
                }
            }
        })
    }

    private fun downloadExport() {
        runInBackground(SwingRunnable {
            val body = accountClient.downloadExport()
            SwingUtilities.invokeLater {
                if (body.isNullOrEmpty()) {
                    JOptionPane.showMessageDialog(this, "Couldn't download your data. Try again.")
                    return@invokeLater
                }
                val chooser = JFileChooser()
                chooser.selectedFile = File("tibbly-export.json")
                val res = chooser.showSaveDialog(this)
                if (res == JFileChooser.APPROVE_OPTION) {
                    runCatching { chooser.selectedFile.writeText(body, Charsets.UTF_8) }
                        .onSuccess {
                            JOptionPane.showMessageDialog(this, "Saved to ${chooser.selectedFile.absolutePath}")
                        }
                        .onFailure {
                            JOptionPane.showMessageDialog(this, "Couldn't write the file: ${it.message}")
                        }
                }
            }
        })
    }

    private fun deleteAccount() {
        val confirmation = JOptionPane.showInputDialog(
            this,
            "This deletes your Tibbly account, chats, and pairings. Type DELETE to confirm.",
            "Delete account",
            JOptionPane.WARNING_MESSAGE,
        ) ?: return
        if (confirmation.trim().uppercase() != "DELETE") {
            JOptionPane.showMessageDialog(this, "Not deleted — confirmation phrase didn't match.")
            return
        }
        runInBackground(SwingRunnable {
            val ok = accountClient.deleteAccount()
            SwingUtilities.invokeLater {
                if (ok) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Your account is gone. You can keep using the local-only plugin features.",
                    )
                    refresh()
                } else {
                    JOptionPane.showMessageDialog(this, "Delete failed — please retry.")
                }
            }
        })
    }
}

/* ============================================================ sections === */

/**
 * Tier badge + connection state + master "delete my data" link.
 *
 * The deletion link is intentionally subtle (tertiary action). The main
 * delete affordance lives in [DataPrivacySection]; this is just a
 * shortcut for the player who already knows what they want.
 */
private class HeaderSection : JPanel() {
    private val tierBadge = JLabel("—")
    private val statusLabel = JLabel(" ")
    private val errorLabel = JLabel(" ")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        add(sectionTitle("Account"))
        add(Box.createVerticalStrut(4))
        tierBadge.font = FontManager.getRunescapeBoldFont().deriveFont(14f)
        tierBadge.foreground = TIER_COLOR_NEUTRAL
        statusLabel.font = FontManager.getRunescapeSmallFont()
        statusLabel.foreground = Color.LIGHT_GRAY
        errorLabel.font = FontManager.getRunescapeSmallFont()
        errorLabel.foreground = Color(220, 120, 120)
        add(tierBadge)
        add(Box.createVerticalStrut(2))
        add(statusLabel)
        add(errorLabel)
    }

    fun bind(summary: AccountSummary) {
        errorLabel.text = " "
        when (summary.tier) {
            "hobbyist" -> {
                tierBadge.text = "Hobbyist"
                tierBadge.foreground = TIER_COLOR_HOBBYIST
            }
            "pro" -> {
                tierBadge.text = "Pro"
                tierBadge.foreground = TIER_COLOR_PRO
            }
            "iron" -> {
                tierBadge.text = "Iron"
                tierBadge.foreground = TIER_COLOR_IRON
            }
            else -> {
                tierBadge.text = "Free"
                tierBadge.foreground = TIER_COLOR_NEUTRAL
            }
        }
        statusLabel.text = when (summary.subscriptionStatus) {
            null -> "No subscription"
            "active" -> "Subscription active"
            "past_due" -> "Payment past due"
            "canceled" -> "Canceled"
            "trialing" -> "Free trial"
            else -> summary.subscriptionStatus
        }
    }

    fun bindError() {
        tierBadge.text = "—"
        tierBadge.foreground = TIER_COLOR_NEUTRAL
        statusLabel.text = " "
        errorLabel.text = "Couldn't reach Tibbly."
    }
}

/**
 * Today's usage — rendered VERBATIM from the backend's tier-aware proxy.
 *
 * NEVER do arithmetic on tokens here. The backend already translated. If
 * you find yourself wanting a `prompt`/`completion` field, surface a new
 * proxy variant in the backend instead.
 */
private class UsageSection : JPanel() {
    private val mainLabel = JLabel(" ")
    private val subLabel = JLabel(" ")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        add(sectionTitle("Today"))
        add(Box.createVerticalStrut(4))
        mainLabel.font = FontManager.getRunescapeBoldFont().deriveFont(13f)
        mainLabel.foreground = Color.WHITE
        subLabel.font = FontManager.getRunescapeSmallFont()
        subLabel.foreground = Color.LIGHT_GRAY
        add(mainLabel)
        add(subLabel)
    }

    fun bind(proxy: UsageProxy) {
        when (proxy) {
            is UsageProxy.MessagesLeft -> {
                mainLabel.text = "${proxy.messagesUsedToday} / ${proxy.messagesPerDay} messages used"
                subLabel.text = "Resets daily."
            }
            is UsageProxy.SubscriptionActive -> {
                mainLabel.text = "Subscription active"
                subLabel.text = "Renews on ${formatRenewDate(proxy.renewsAt)}."
            }
            UsageProxy.Unlimited -> {
                mainLabel.text = "Unlimited"
                subLabel.text = " "
            }
        }
    }

    fun bindError() {
        mainLabel.text = "—"
        subLabel.text = "Couldn't load usage."
    }
}

private class OsrsAccountsSection(
    private val onForget: (PairedOsrsAccount) -> Unit,
) : JPanel() {
    private val list = JPanel()
    private val empty = JLabel("No paired OSRS accounts yet.")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        empty.font = FontManager.getRunescapeSmallFont()
        empty.foreground = Color.LIGHT_GRAY
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        list.background = background
        add(sectionTitle("OSRS accounts"))
        add(Box.createVerticalStrut(4))
        add(list)
        add(empty)
    }

    fun bind(accounts: List<PairedOsrsAccount>) {
        list.removeAll()
        if (accounts.isEmpty()) {
            empty.isVisible = true
        } else {
            empty.isVisible = false
            for (acct in accounts) {
                list.add(buildRow(acct))
                list.add(Box.createVerticalStrut(2))
            }
        }
        revalidate()
        repaint()
    }

    private fun buildRow(account: PairedOsrsAccount): JPanel {
        val row = JPanel(BorderLayout(4, 0))
        row.background = ColorScheme.DARK_GRAY_COLOR
        row.maximumSize = Dimension(Int.MAX_VALUE, 24)
        val name = JLabel(account.displayName + if (account.isCurrent) "  (current)" else "")
        name.font = FontManager.getRunescapeSmallFont()
        name.foreground = if (account.isCurrent) Color(200, 220, 100) else Color.WHITE
        val forget = secondaryButton("Forget")
        forget.addActionListener { onForget(account) }
        row.add(name, BorderLayout.CENTER)
        row.add(forget, BorderLayout.EAST)
        return row
    }
}

private class DevicesSection : JPanel() {
    private val list = JPanel()
    private val empty = JLabel("No paired devices yet.")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        empty.font = FontManager.getRunescapeSmallFont()
        empty.foreground = Color.LIGHT_GRAY
        list.layout = BoxLayout(list, BoxLayout.Y_AXIS)
        list.background = background
        add(sectionTitle("Devices"))
        add(Box.createVerticalStrut(4))
        add(list)
        add(empty)
    }

    fun bind(devices: List<PairedDevice>) {
        list.removeAll()
        if (devices.isEmpty()) {
            empty.isVisible = true
        } else {
            empty.isVisible = false
            for (dev in devices) {
                val row = JLabel(
                    (dev.displayName ?: "Unnamed RuneLite") +
                        if (dev.isCurrent) "  (this RuneLite)" else "",
                )
                row.font = FontManager.getRunescapeSmallFont()
                row.foreground = if (dev.isCurrent) Color(200, 220, 100) else Color.WHITE
                list.add(row)
                list.add(Box.createVerticalStrut(2))
            }
        }
        revalidate()
        repaint()
    }
}

private class SubscriptionSection(
    private val onOpenPortal: () -> Unit,
) : JPanel() {
    private val statusLabel = JLabel(" ")
    private val renewLabel = JLabel(" ")
    private val portalButton = JButton("Manage subscription")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        statusLabel.font = FontManager.getRunescapeSmallFont()
        statusLabel.foreground = Color.WHITE
        renewLabel.font = FontManager.getRunescapeSmallFont()
        renewLabel.foreground = Color.LIGHT_GRAY
        portalButton.alignmentX = LEFT_ALIGNMENT
        portalButton.addActionListener { onOpenPortal() }
        add(sectionTitle("Subscription"))
        add(Box.createVerticalStrut(4))
        add(statusLabel)
        add(renewLabel)
        add(Box.createVerticalStrut(6))
        add(portalButton)
    }

    fun bind(summary: AccountSummary) {
        statusLabel.text = if (summary.tier == null) "No active plan." else "Plan: ${summary.tier.replaceFirstChar { it.uppercase() }}"
        renewLabel.text = summary.renewsAt?.let { "Renews ${formatRenewDate(it)}." } ?: " "
    }
}

private class PairingSection(
    private val onPair: () -> Unit,
) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        val help = JLabel("Pair another OSRS character or another RuneLite install.")
        help.font = FontManager.getRunescapeSmallFont()
        help.foreground = Color.LIGHT_GRAY
        val button = JButton("Pair another account")
        button.alignmentX = LEFT_ALIGNMENT
        button.addActionListener { onPair() }
        add(sectionTitle("Pairing"))
        add(Box.createVerticalStrut(4))
        add(help)
        add(Box.createVerticalStrut(6))
        add(button)
    }
}

private class DataPrivacySection(
    private val onExport: () -> Unit,
    private val onDelete: () -> Unit,
) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
        val help = JLabel("<html>Download a JSON copy of your data, or delete the account.</html>")
        help.font = FontManager.getRunescapeSmallFont()
        help.foreground = Color.LIGHT_GRAY
        val export = JButton("Download my data")
        val delete = JButton("Delete my account")
        export.alignmentX = LEFT_ALIGNMENT
        delete.alignmentX = LEFT_ALIGNMENT
        export.addActionListener { onExport() }
        delete.addActionListener { onDelete() }
        delete.foreground = Color(220, 120, 120)
        add(sectionTitle("Data & privacy"))
        add(Box.createVerticalStrut(4))
        add(help)
        add(Box.createVerticalStrut(6))
        add(export)
        add(Box.createVerticalStrut(4))
        add(delete)
    }
}

/* ============================================================ primitives === */

private fun sectionTitle(text: String): JLabel {
    val label = JLabel(text.uppercase())
    label.font = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD)
    label.foreground = Color(170, 170, 170)
    return label
}

private fun secondaryButton(text: String): JButton {
    val b = JButton(text)
    b.font = FontManager.getRunescapeSmallFont()
    b.margin = java.awt.Insets(0, 6, 0, 6)
    b.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    return b
}

private val TIER_COLOR_NEUTRAL = Color(170, 170, 170)
private val TIER_COLOR_HOBBYIST = Color(150, 200, 240)
private val TIER_COLOR_PRO = Color(220, 180, 100)
private val TIER_COLOR_IRON = Color(200, 200, 200)

/**
 * Render an ISO-8601 timestamp as a short human date — "14 Jan" or
 * "14 Jan 2027" if the year differs from now. Failure-tolerant: returns
 * the raw string if parsing fails.
 */
internal fun formatRenewDate(iso: String): String {
    return runCatching {
        val instant = java.time.Instant.parse(iso)
        val zdt = instant.atZone(java.time.ZoneId.systemDefault())
        val now = java.time.ZonedDateTime.now()
        val pattern = if (zdt.year == now.year) "d MMM" else "d MMM yyyy"
        zdt.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
    }.getOrDefault(iso)
}

/** Helper so we don't depend on a kotlinx threading library. */
private fun SwingRunnable(block: () -> Unit): Runnable = Runnable { block() }

/**
 * Fire a one-shot daemon thread for blocking work. Used by the panel's
 * action handlers; the callback is expected to hop back to the EDT with
 * [SwingUtilities.invokeLater] when it touches UI.
 */
private fun runInBackground(runnable: Runnable) {
    val t = Thread(runnable, "tibbly-account-panel")
    t.isDaemon = true
    t.start()
}
