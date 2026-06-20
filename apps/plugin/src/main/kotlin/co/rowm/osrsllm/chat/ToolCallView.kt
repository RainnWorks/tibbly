package co.rowm.osrsllm.chat

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * One row representing a single tool call in a chat message.
 *
 * Collapsed (default): "▸ tool_name" only.
 * Expanded: input args + truncated result, both monospace.
 *
 * Clicking the header toggles. Re-validates the messages container after each
 * toggle and triggers an optional scroll-to-bottom for the parent.
 */
class ToolCallView(
    private val call: ToolCall,
    private val contentWidth: Int,
    private val wheelListener: MouseWheelListener? = null,
    private val onToggled: () -> Unit = {},
) : JPanel() {

    private var expanded = false
    private val header = JLabel(collapsedText())
    private var detail: JPanel? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(2, 8, 2, 0)

        header.foreground = TOOL_CALL_FG
        header.font = FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC)
        header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        header.alignmentX = Component.LEFT_ALIGNMENT
        header.toolTipText = "Click to ${if (expanded) "collapse" else "expand"}"
        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = toggle()
            override fun mouseEntered(e: MouseEvent) {
                header.foreground = TOOL_CALL_FG_HOVER
            }
            override fun mouseExited(e: MouseEvent) {
                header.foreground = TOOL_CALL_FG
            }
        })
        if (wheelListener != null) header.addMouseWheelListener(wheelListener)
        add(header)
    }

    private fun toggle() {
        expanded = !expanded
        header.text = if (expanded) expandedText() else collapsedText()
        header.toolTipText = if (expanded) "Click to collapse" else "Click to expand"
        if (expanded && detail == null) {
            detail = buildDetail()
            add(detail)
        } else if (!expanded && detail != null) {
            remove(detail)
            detail = null
        }
        revalidate()
        repaint()
        onToggled()
    }

    private fun collapsedText() = "▸ ${call.name}"
    private fun expandedText() = "▾ ${call.name}"

    private fun buildDetail(): JPanel {
        val box = JPanel()
        box.layout = BoxLayout(box, BoxLayout.Y_AXIS)
        box.isOpaque = false
        box.alignmentX = Component.LEFT_ALIGNMENT
        box.border = BorderFactory.createEmptyBorder(2, 8, 2, 0)

        if (call.input.isNotBlank() && call.input.trim() != "{}") {
            box.add(makeBlock("input", call.input.take(800)))
            box.add(Box.createRigidArea(Dimension(0, 3)))
        }
        if (call.result.isNotBlank()) {
            val resultText = call.result.take(1200).let {
                if (call.result.length > 1200) "$it…\n[truncated — full result in chat .md]" else it
            }
            box.add(makeBlock("result", resultText))
        }
        return box
    }

    private fun makeBlock(label: String, text: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.alignmentX = Component.LEFT_ALIGNMENT

        val labelView = JLabel(label).apply {
            foreground = Color(150, 150, 150)
            font = FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC, 9f)
        }
        panel.add(labelView, BorderLayout.NORTH)

        // JLabel with width-constrained HTML wraps cleanly and can't introduce its own
        // scrollbar (unlike JTextArea, which played badly with the surrounding BoxLayouts
        // and produced a spurious second scrollbar on expand).
        val width = (contentWidth - 16).coerceAtLeast(80)
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
            .replace(" ", "&nbsp;") // preserve indentation in pretty-printed JSON
        val display = JLabel(
            "<html><body style='font-family:monospace;font-size:10px;width:${width}px'>$escaped</body></html>",
        ).apply {
            foreground = TOOL_RESULT_FG
            background = TOOL_RESULT_BG
            isOpaque = true
            verticalAlignment = SwingConstants.TOP
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            if (wheelListener != null) addMouseWheelListener(wheelListener)
        }
        panel.add(display, BorderLayout.CENTER)
        return panel
    }

    companion object {
        private val TOOL_CALL_FG = Color(140, 214, 168)
        private val TOOL_CALL_FG_HOVER = Color(180, 240, 200)
        private val TOOL_RESULT_BG = Color(26, 26, 26)
        private val TOOL_RESULT_FG = Color(160, 160, 160)
    }
}
