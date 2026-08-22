package co.rowm.osrsllm.ui

import co.rowm.osrsllm.cloud.PairingFlow
import co.rowm.osrsllm.ui.sections.AccountSection
import co.rowm.osrsllm.ui.sections.CompanionSection
import co.rowm.osrsllm.ui.sections.DeveloperSection
import co.rowm.osrsllm.ui.sections.StubSections
import net.runelite.client.ui.ColorScheme
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

/**
 * Config side of the unified Tibbly panel.
 *
 * Layout: a vertical stack of `CollapsibleSection`s inside a scroll
 * pane so a small RuneLite sidebar still reaches every setting. Order
 * mirrors the player's mental model:
 *
 *   1. Account            — pair, billing, BYOK, chat mode
 *   2. Companion          — variant, name, personality, verbosity, proactive master
 *   3. Proactive triggers — per-category checkboxes (stub)
 *   4. Privacy & data     — consent, what Tibbly can see, export / delete (stub)
 *   5. Notifications      — hotkey + mute (stub)
 *   6. Developer          — server URL, MCP host/port, dev mode
 *   7. About              — version + links (stub)
 */
internal class TibblyConfigView(
    private val settings: TibblySettings,
    private val pairingFlow: PairingFlow?,
    billingPortalUrl: String = DEFAULT_BILLING_PORTAL_URL,
) : JPanel(BorderLayout()) {

    private val accountSection = AccountSection(settings, pairingFlow, billingPortalUrl)

    private val accountBlock = accountSection.build()
    private val companionBlock = CompanionSection.build(settings)
    private val triggersBlock = StubSections.triggers()
    private val privacyBlock = StubSections.privacy()
    private val notificationsBlock = StubSections.notifications()
    private val developerBlock = DeveloperSection.build(settings)
    private val aboutBlock = StubSections.about()

    init {
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        val stack = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = ColorScheme.DARK_GRAY_COLOR
        }
        listOf(
            accountBlock,
            companionBlock,
            triggersBlock,
            privacyBlock,
            notificationsBlock,
            developerBlock,
            aboutBlock,
        ).forEach { stack.add(it) }
        stack.add(Box.createVerticalGlue())

        val scroll = JScrollPane(stack).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
            viewport.background = ColorScheme.DARK_GRAY_COLOR
            background = ColorScheme.DARK_GRAY_COLOR
            verticalScrollBar.unitIncrement = 12
        }
        add(scroll, BorderLayout.CENTER)
    }

    /** Test-only accessors — exposed so unit tests can drive sections. */
    internal fun accountSectionForTest(): AccountSection = accountSection
    internal fun sectionsForTest(): List<CollapsibleSection> = listOf(
        accountBlock,
        companionBlock,
        triggersBlock,
        privacyBlock,
        notificationsBlock,
        developerBlock,
        aboutBlock,
    )

    internal companion object {
        const val DEFAULT_BILLING_PORTAL_URL: String = "https://tibbly.io/account/billing"
    }
}
