package co.rowm.osrsllm.ui

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Section card used by `TibblyConfigView`. Title row + chevron + a body
 * pane that collapses on click.
 *
 * The header is a plain `JLabel` styled to mimic the RuneLite section
 * chrome (gold heading, grey caret) so the in-panel config feels like a
 * native RuneLite settings group rather than a bolted-on form.
 *
 * State is local to the component: callers don't need to track open/
 * closed flags. Initial expanded state is set via `expandedByDefault`.
 */
internal class CollapsibleSection(
    private val title: String,
    body: JPanel,
    expandedByDefault: Boolean = true,
) : JPanel(BorderLayout()) {

    private val caret = JLabel(if (expandedByDefault) CARET_OPEN else CARET_CLOSED)
    private val titleLabel = JLabel(title)
    private val header = JPanel(BorderLayout()).apply {
        background = ColorScheme.DARKER_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    private val bodyHolder = JPanel(BorderLayout()).apply {
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(10, 10, 12, 10)
        add(body, BorderLayout.CENTER)
        isVisible = expandedByDefault
    }

    init {
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR.brighter()),
            BorderFactory.createEmptyBorder(2, 0, 2, 0),
        )

        titleLabel.foreground = HEADING_COLOR
        titleLabel.font = FontManager.getRunescapeBoldFont()
        caret.foreground = Color.LIGHT_GRAY
        caret.font = FontManager.getRunescapeSmallFont()
        caret.horizontalAlignment = SwingConstants.RIGHT
        caret.preferredSize = Dimension(16, 16)

        header.add(titleLabel, BorderLayout.WEST)
        header.add(caret, BorderLayout.EAST)
        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggle()
            }
        })

        add(header, BorderLayout.NORTH)
        add(bodyHolder, BorderLayout.CENTER)
    }

    private fun toggle() {
        val open = !bodyHolder.isVisible
        bodyHolder.isVisible = open
        caret.text = if (open) CARET_OPEN else CARET_CLOSED
        revalidate()
        repaint()
    }

    /** Test-only — does the body show right now? */
    internal fun isExpanded(): Boolean = bodyHolder.isVisible

    /** Test-only — programmatically toggle without a mouse click. */
    internal fun toggleForTest() = toggle()

    /** Test-only — for screenshot diffing / a11y output. */
    internal fun titleForTest(): String = title

    private companion object {
        const val CARET_OPEN = "▾"
        const val CARET_CLOSED = "▸"
        val HEADING_COLOR: Color = Color(232, 196, 122)
    }
}
