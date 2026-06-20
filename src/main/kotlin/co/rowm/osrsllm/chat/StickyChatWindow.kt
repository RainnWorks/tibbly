package co.rowm.osrsllm.chat

import net.runelite.client.ui.ColorScheme
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * A free-floating chat window that **sticks** to the right edge of the main
 * RuneLite frame — tracks its position + size and snaps in lockstep.
 *
 * The user gets a real resizable chat alongside the game without us having to
 * hack into RuneLite's sidebar layout (which isn't exposed).
 */
class StickyChatWindow(
    private val store: ChatStore,
    private val runner: ClaudeRunner,
    private val onClose: () -> Unit,
) {

    private val log = LoggerFactory.getLogger(StickyChatWindow::class.java)
    private val frame = JFrame("OSRS LLM Helper")
    private val chatPanel = ChatPanel(store, runner)
    private var mainFrame: Window? = null
    private var lastWidth = DEFAULT_WIDTH

    private val mainListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = snap()
        override fun componentResized(e: ComponentEvent) = snap()
        override fun componentShown(e: ComponentEvent) = snap()
        override fun componentHidden(e: ComponentEvent) {
            frame.isVisible = false
        }
    }

    init {
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        val host = JPanel(BorderLayout()).apply {
            background = ColorScheme.DARK_GRAY_COLOR
            add(chatPanel, BorderLayout.CENTER)
        }
        frame.contentPane = host
        frame.preferredSize = Dimension(DEFAULT_WIDTH, 700)
        frame.minimumSize = Dimension(280, 300)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = close()
        })
        // Track user-driven width changes so we preserve the chosen width on snap.
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (mainFrame == null || !frame.isVisible) return
                lastWidth = frame.width.coerceAtLeast(280)
            }
        })
    }

    fun attachAndShow(referenceComponent: Component) {
        mainFrame = SwingUtilities.getWindowAncestor(referenceComponent)
        mainFrame?.addComponentListener(mainListener)
        frame.pack()
        frame.isVisible = true
        snap()
    }

    fun bringToFront() {
        if (frame.state == JFrame.ICONIFIED) frame.state = JFrame.NORMAL
        frame.toFront()
        frame.requestFocus()
    }

    fun close() {
        log.info("Closing chat window")
        mainFrame?.removeComponentListener(mainListener)
        mainFrame = null
        frame.isVisible = false
        frame.dispose()
        onClose()
    }

    private fun snap() {
        val m = mainFrame ?: return
        if (!m.isShowing) return
        SwingUtilities.invokeLater {
            frame.setBounds(m.x + m.width, m.y, lastWidth, m.height)
        }
    }

    companion object {
        private const val DEFAULT_WIDTH = 500
    }
}
