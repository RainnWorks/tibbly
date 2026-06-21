package co.rowm.osrsllm.companion

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The companion's speech bubble. A Swing component that floats above the
 * companion sprite anchored at the canvas point computed by
 * [CompanionRenderer.lastCanvasAnchor].
 *
 * Behaviour:
 *  - Fades in over [FADE_IN_MS] when [show] is called.
 *  - Reveals characters with a fast typewriter effect ([CHAR_INTERVAL_MS]
 *    per char) so the player can start reading immediately rather than
 *    waiting for the whole reply.
 *  - Auto-dismisses after a duration scaled to text length so short
 *    quips disappear faster than long explanations. Players can dismiss
 *    early by clicking on the bubble.
 *  - Click is forwarded via the [onClick] callback so the plugin wire-in
 *    can open the focused chat panel.
 *
 * Threading: every state mutation happens on the EDT. The component is
 * constructed lazily by the plugin wire-in.
 */
class SpeechBubble(
    private val anchorSupplier: () -> Point?,
    private val onClick: () -> Unit = {},
    private val onDismissed: () -> Unit = {},
) : JComponent() {

    private var fullText: String = ""
    private var revealedChars: Int = 0
    private var alpha: Float = 0f
    @Volatile private var dismissAtMs: Long = 0L
    @Volatile private var shownAtMs: Long = 0L
    @Volatile var isVisibleNow: Boolean = false
        private set
    private val typewriter: Timer = Timer(CHAR_INTERVAL_MS) { onTypewriterTick() }
    private val animator: Timer = Timer(FRAME_INTERVAL_MS) { onAnimatorTick() }

    init {
        isOpaque = false
        font = Font("SansSerif", Font.PLAIN, 12)
        // Click bubble -> open focused chat panel.
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (!isVisibleNow) return
                onClick()
            }
        })
        size = Dimension(0, 0)
        typewriter.isRepeats = true
        animator.isRepeats = true
    }

    /**
     * Display [text] as the new bubble content. Replaces any prior
     * content immediately. Auto-dismiss timer starts from `now()`.
     *
     * Auto-dismiss duration is `min(MAX_DURATION_MS,
     * BASE_DURATION_MS + text.length * PER_CHAR_DURATION_MS)`. Short
     * lines vanish faster.
     */
    fun show(text: String, nowMs: Long = System.currentTimeMillis()) {
        require(SwingUtilities.isEventDispatchThread() || javaClass.desiredAssertionStatus().not()) {
            "SpeechBubble.show must be called on the EDT"
        }
        fullText = text
        revealedChars = 0
        alpha = 0f
        shownAtMs = nowMs
        isVisibleNow = true
        val dur = computeDuration(text.length)
        dismissAtMs = nowMs + dur
        layoutForText()
        typewriter.restart()
        animator.restart()
        repaint()
    }

    /** Dismiss immediately. Safe to call when already dismissed. */
    fun dismiss() {
        if (!isVisibleNow) return
        isVisibleNow = false
        revealedChars = fullText.length
        alpha = 0f
        typewriter.stop()
        animator.stop()
        repaint()
        onDismissed()
    }

    /** Whether the typewriter has finished revealing the text. */
    fun isFullyRevealed(): Boolean = revealedChars >= fullText.length

    /** Current alpha (0..1). Visible for tests. */
    fun currentAlpha(): Float = alpha

    /** Re-anchor over the companion sprite. Idempotent; safe to call on a timer. */
    fun reanchor() {
        val anchor = anchorSupplier() ?: return
        val w = preferredSize.width.takeIf { it > 0 } ?: width
        val h = preferredSize.height.takeIf { it > 0 } ?: height
        if (w <= 0 || h <= 0) return
        setLocation(anchor.x - w / 2, anchor.y - h - 4)
    }

    private fun onTypewriterTick() {
        if (revealedChars < fullText.length) {
            revealedChars = (revealedChars + 1).coerceAtMost(fullText.length)
            repaint()
        } else {
            typewriter.stop()
        }
    }

    private fun onAnimatorTick() {
        val now = System.currentTimeMillis()
        // Fade in.
        if (isVisibleNow && now - shownAtMs < FADE_IN_MS) {
            alpha = ((now - shownAtMs).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
        } else if (isVisibleNow) {
            alpha = 1f
        }
        // Auto-dismiss.
        if (isVisibleNow && now >= dismissAtMs) {
            dismiss()
            return
        }
        // Drag with the renderer's anchor (companion may have walked).
        reanchor()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        if (!isVisibleNow || fullText.isEmpty()) return
        val g2 = (g as Graphics2D).create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
            val fm = g2.fontMetrics
            val padding = 8
            val visibleText = fullText.substring(0, revealedChars)
            val lines = wrapText(visibleText, fm, BUBBLE_MAX_WIDTH - padding * 2)
            val height = fm.height * lines.size + padding * 2
            val maxLineWidth = lines.maxOfOrNull { fm.stringWidth(it) } ?: 0
            val width = (maxLineWidth + padding * 2).coerceAtLeast(40)
            // Bubble background.
            g2.color = Color(245, 235, 200, 230)
            g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10)
            g2.color = Color(60, 50, 30)
            g2.stroke = BasicStroke(1.5f)
            g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10)
            // Pointer tail (downward, into the sprite).
            val tailLeft = width / 2 - 4
            val tailRight = width / 2 + 4
            g2.color = Color(245, 235, 200, 230)
            g2.fillPolygon(intArrayOf(tailLeft, tailRight, width / 2), intArrayOf(height - 2, height - 2, height + 6), 3)
            g2.color = Color(60, 50, 30)
            g2.drawLine(tailLeft, height - 1, width / 2, height + 6)
            g2.drawLine(tailRight, height - 1, width / 2, height + 6)
            // Text.
            g2.color = Color(30, 25, 15)
            var ty = padding + fm.ascent
            for (line in lines) {
                g2.drawString(line, padding, ty)
                ty += fm.height
            }
        } finally {
            g2.dispose()
        }
    }

    /** Recompute preferred size from full text so the bubble doesn't jump as chars reveal. */
    private fun layoutForText() {
        val fm: FontMetrics = getFontMetrics(font)
        val padding = 8
        val lines = wrapText(fullText, fm, BUBBLE_MAX_WIDTH - padding * 2)
        val height = fm.height * lines.size + padding * 2 + TAIL_HEIGHT
        val maxLineWidth = lines.maxOfOrNull { fm.stringWidth(it) } ?: 0
        val width = (maxLineWidth + padding * 2).coerceAtLeast(40)
        preferredSize = Dimension(width, height)
        size = preferredSize
        reanchor()
    }

    private fun wrapText(text: String, fm: FontMetrics, maxPixelWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else current.toString() + " " + w
            if (fm.stringWidth(candidate) <= maxPixelWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current.clear()
                }
                current.append(w)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun computeDuration(chars: Int): Long =
        (BASE_DURATION_MS + chars * PER_CHAR_DURATION_MS).coerceAtMost(MAX_DURATION_MS)

    companion object {
        const val FADE_IN_MS: Long = 200L
        const val CHAR_INTERVAL_MS: Int = 18
        const val FRAME_INTERVAL_MS: Int = 16
        const val BUBBLE_MAX_WIDTH: Int = 220
        const val BASE_DURATION_MS: Long = 1_800L
        const val PER_CHAR_DURATION_MS: Long = 35L
        const val MAX_DURATION_MS: Long = 12_000L
        const val TAIL_HEIGHT: Int = 8
    }
}
