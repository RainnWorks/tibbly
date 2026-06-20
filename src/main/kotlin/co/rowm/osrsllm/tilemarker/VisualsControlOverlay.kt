package co.rowm.osrsllm.tilemarker

import net.runelite.api.Client
import net.runelite.client.callback.ClientThread
import net.runelite.client.input.MouseAdapter
import net.runelite.client.input.MouseListener
import net.runelite.client.ui.overlay.Overlay
import net.runelite.client.ui.overlay.OverlayLayer
import net.runelite.client.ui.overlay.OverlayPosition
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Floating "✕ Clear AI visuals" button that appears whenever the agent has
 * painted anything on the world — a path, individual tile markers, or the
 * native hint arrow.
 *
 * Click it once to remove ALL of them in one go (no need to ask the agent to
 * call `clear_visuals`). The user gets a reliable escape hatch every time the
 * AI clutters the screen.
 *
 * Position: DETACHED so the user can drag it wherever it fits their HUD.
 * Renders nothing when there's nothing to clear, so it doesn't take up screen
 * space during normal play.
 */
@Singleton
class VisualsControlOverlay @Inject constructor(
    private val client: Client,
    private val clientThread: ClientThread,
    private val tileMarkerService: TileMarkerService,
) : Overlay() {

    init {
        position = OverlayPosition.DETACHED
        layer = OverlayLayer.ABOVE_WIDGETS
        priority = PRIORITY_HIGH
        preferredSize = Dimension(BTN_W, BTN_H)
    }

    private var renderedBounds = Rectangle(0, 0, 0, 0)
    private var hovered = false

    override fun render(graphics: Graphics2D): Dimension? {
        if (!hasAnyVisuals()) {
            renderedBounds = Rectangle(0, 0, 0, 0)
            return null
        }
        renderedBounds = Rectangle(bounds)

        // Background + border (warning-coloured so the user clocks it instantly).
        graphics.color = if (hovered) HOVER_BG else BG
        graphics.fillRect(0, 0, BTN_W, BTN_H)
        graphics.color = ACCENT
        graphics.drawRect(0, 0, BTN_W - 1, BTN_H - 1)
        graphics.color = ACCENT
        graphics.font = LABEL_FONT
        graphics.drawString(LABEL, 8, 16)
        return Dimension(BTN_W, BTN_H)
    }

    private fun hasAnyVisuals(): Boolean =
        tileMarkerService.path().isNotEmpty() ||
            tileMarkerService.markers().isNotEmpty() ||
            tileMarkerService.aiHintArrowActive(client)

    /** Subscribed to MouseManager by the plugin. */
    val mouseListener: MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent): MouseEvent {
            if (renderedBounds.contains(e.point)) {
                // Snapshot ownership BEFORE clearing service state.
                val aiOwnsArrow = tileMarkerService.aiHintArrowActive(client)
                tileMarkerService.clearAll()
                if (aiOwnsArrow) clientThread.invoke(Runnable { client.clearHintArrow() })
                e.consume()
            }
            return e
        }

        override fun mouseMoved(e: MouseEvent): MouseEvent {
            hovered = renderedBounds.contains(e.point)
            return e
        }

        override fun mouseExited(e: MouseEvent): MouseEvent {
            hovered = false
            return e
        }
    }

    companion object {
        private const val BTN_W = 150
        private const val BTN_H = 22
        private const val LABEL = "✕  Clear AI visuals"
        private val BG = Color(20, 20, 22, 235)
        private val HOVER_BG = Color(50, 35, 25, 235)
        private val ACCENT = Color(232, 174, 78, 230)
        private val LABEL_FONT = Font("SansSerif", Font.PLAIN, 12)
    }
}
