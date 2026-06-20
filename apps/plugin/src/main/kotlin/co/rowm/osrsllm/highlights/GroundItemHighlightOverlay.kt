package co.rowm.osrsllm.highlights

import net.runelite.api.Client
import net.runelite.api.Perspective
import net.runelite.api.coords.LocalPoint
import net.runelite.client.game.ItemManager
import net.runelite.client.ui.overlay.Overlay
import net.runelite.client.ui.overlay.OverlayLayer
import net.runelite.client.ui.overlay.OverlayPosition
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Polygon
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highlights tiles whose stacks contain a target item id. We walk the scene
 * tiles each frame, peek each tile's `groundItems` list, and outline the tile
 * if it contains any matching id. Cheap because the inner loops are tiny.
 *
 * Label is drawn centred on the tile when set.
 */
@Singleton
class GroundItemHighlightOverlay @Inject constructor(
    private val client: Client,
    private val service: GroundItemHighlightService,
    private val itemManager: ItemManager,
) : Overlay() {

    init {
        position = OverlayPosition.DYNAMIC
        layer = OverlayLayer.ABOVE_SCENE
        priority = PRIORITY_LOW
    }

    override fun render(graphics: Graphics2D): Dimension? {
        val entries = service.all()
        if (entries.isEmpty()) return null
        val byTargetId: Map<Int, HighlightEntry> = entries.associateBy { it.targetId }

        val scene = client.scene ?: return null
        val planeFloor = client.plane
        val tilesByPlane = scene.tiles ?: return null
        val planeTiles = tilesByPlane.getOrNull(planeFloor) ?: return null

        graphics.stroke = BasicStroke(2f)
        for (xRow in planeTiles) {
            for (tile in xRow) {
                if (tile == null) continue
                val groundItems = tile.groundItems ?: continue
                // Find the first matching item on this tile, take its highlight entry.
                var entry: HighlightEntry? = null
                var totalMatching = 0
                for (gi in groundItems) {
                    val match = byTargetId[gi.id] ?: continue
                    if (entry == null) entry = match
                    totalMatching += gi.quantity.coerceAtLeast(1)
                }
                val resolved = entry ?: continue
                val color = service.colorFor(resolved)
                val poly: Polygon = Perspective.getCanvasTilePoly(client, tile.localLocation) ?: continue
                graphics.color = Color(color.red, color.green, color.blue, 70)
                graphics.fillPolygon(poly)
                graphics.color = color
                graphics.drawPolygon(poly)
                val label = (resolved.label?.takeIf { it.isNotBlank() }
                    ?: resolved.name) + if (totalMatching > 1) " ×$totalMatching" else ""
                drawCenteredLabel(graphics, poly, label, color)
            }
        }
        return null
    }

    private fun drawCenteredLabel(g: Graphics2D, poly: Polygon, text: String, color: Color) {
        val bounds = poly.bounds
        val cx = bounds.centerX.toInt()
        val cy = bounds.centerY.toInt()
        val fm = g.fontMetrics
        val tx = cx - fm.stringWidth(text) / 2
        val ty = cy + fm.ascent / 2
        g.color = Color.BLACK
        g.drawString(text, tx + 1, ty + 1)
        g.color = color
        g.drawString(text, tx, ty)
    }
}
