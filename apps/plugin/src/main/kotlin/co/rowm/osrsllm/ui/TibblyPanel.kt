package co.rowm.osrsllm.ui

import co.rowm.osrsllm.chat.ChatPanel
import co.rowm.osrsllm.cloud.PairingFlow
import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import net.runelite.client.ui.PluginPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Unified Tibbly side panel: title bar + chat-or-config body.
 *
 * The title bar shows the brand label and a cog button on the right.
 * Clicking the cog flips the body between the chat surface (existing
 * `ChatPanel`) and the new `TibblyConfigView`. We use a `CardLayout`
 * so the chat history isn't disposed when the player visits config —
 * coming back lands on the same scroll position and selection.
 *
 * This is the single navigation button registered with the RuneLite
 * toolbar; the legacy four-panel split (status / chat / managed /
 * account) is on its way out (see D-8 pivot + DECISION_LOG entry).
 *
 * Construction takes the wired `ChatPanel` rather than the chat deps
 * because the plugin already builds it with the right ChatStore +
 * backend selector chain — re-doing that here would just be a
 * duplicate composition root.
 */
class TibblyPanel(
    private val chatPanel: ChatPanel,
    settings: TibblySettings,
    pairingFlow: PairingFlow?,
) : PluginPanel(false) {

    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply { background = ColorScheme.DARK_GRAY_COLOR }
    private val configView = TibblyConfigView(settings, pairingFlow)
    private val cogLabel = JLabel(COG_GLYPH).apply {
        foreground = Color(190, 190, 190)
        font = FontManager.getRunescapeBoldFont()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        horizontalAlignment = SwingConstants.RIGHT
        toolTipText = "Open settings"
        preferredSize = Dimension(28, 24)
    }
    private val titleLabel = JLabel("Tibbly").apply {
        foreground = Color.WHITE
        font = FontManager.getRunescapeBoldFont()
    }

    private var currentView: View = View.CHAT

    enum class View { CHAT, CONFIG }

    init {
        layout = BorderLayout(0, 0)
        background = ColorScheme.DARK_GRAY_COLOR

        add(buildTitleBar(), BorderLayout.NORTH)
        cards.add(chatPanel, CARD_CHAT)
        cards.add(configView, CARD_CONFIG)
        add(cards, BorderLayout.CENTER)

        cogLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggleView()
            }
        })
    }

    private fun buildTitleBar(): JPanel {
        val bar = JPanel(BorderLayout()).apply {
            background = ColorScheme.DARKER_GRAY_COLOR
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR.brighter()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12),
            )
        }
        bar.add(titleLabel, BorderLayout.WEST)

        val rightCluster = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            background = ColorScheme.DARKER_GRAY_COLOR
        }
        rightCluster.add(Box.createHorizontalGlue())
        rightCluster.add(cogLabel)
        bar.add(rightCluster, BorderLayout.EAST)
        return bar
    }

    /**
     * Flip between the chat and config cards. Also updates the cog label
     * so the player gets a back affordance from the config side.
     */
    fun toggleView() {
        currentView = when (currentView) {
            View.CHAT -> View.CONFIG
            View.CONFIG -> View.CHAT
        }
        applyCurrentView()
    }

    /** Set the view directly — used by tests and by deep-links. */
    fun setView(view: View) {
        if (view == currentView) return
        currentView = view
        applyCurrentView()
    }

    private fun applyCurrentView() {
        when (currentView) {
            View.CHAT -> {
                cardLayout.show(cards, CARD_CHAT)
                cogLabel.text = COG_GLYPH
                cogLabel.toolTipText = "Open settings"
            }
            View.CONFIG -> {
                cardLayout.show(cards, CARD_CONFIG)
                cogLabel.text = BACK_GLYPH
                cogLabel.toolTipText = "Back to chat"
            }
        }
    }

    /** Test-only — which card is showing right now. */
    internal fun currentViewForTest(): View = currentView

    /** Test-only — direct handle on the config view (sections, settings). */
    internal fun configViewForTest(): TibblyConfigView = configView

    private companion object {
        const val CARD_CHAT = "chat"
        const val CARD_CONFIG = "config"

        // Unicode glyphs avoid shipping a new icon asset. The Runescape
        // font carries these and they render cleanly at panel size.
        const val COG_GLYPH = "⚙"
        const val BACK_GLYPH = "←"
    }
}
