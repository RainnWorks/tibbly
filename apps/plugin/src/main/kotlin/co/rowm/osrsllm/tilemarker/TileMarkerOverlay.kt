package co.rowm.osrsllm.tilemarker

import co.rowm.osrsllm.pathfinder.TilePoint
import net.runelite.api.Client
import net.runelite.api.Perspective
import net.runelite.api.coords.LocalPoint
import net.runelite.api.coords.WorldPoint
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
 * Draws path tiles + ad-hoc markers on the OSRS scene.
 *
 *  - Path tiles get an outlined polygon (alpha-blended fill is too noisy on long
 *    routes; outlines stay legible at any length).
 *  - Single markers are drawn with a solid fill + optional centred label.
 *  - Tiles outside the loaded scene/plane are skipped silently — happens any
 *    time the player walks far from a previously-set destination.
 */
@Singleton
class TileMarkerOverlay @Inject constructor(
    private val client: Client,
    private val service: TileMarkerService,
    private val persistent: PersistentTileMarkerService,
) : Overlay() {

    init {
        position = OverlayPosition.DYNAMIC
        layer = OverlayLayer.ABOVE_SCENE
        priority = PRIORITY_LOW
    }

    override fun render(graphics: Graphics2D): Dimension? {
        val path = service.path()
        val pathColor = service.pathColor()
        val markers = service.markers()
        val persistentMarkers = persistent.renderable()
        if (path.isEmpty() && markers.isEmpty() && persistentMarkers.isEmpty()) return null

        if (path.isNotEmpty()) drawPath(graphics, path, pathColor)
        for (m in persistentMarkers) drawMarker(graphics, m)
        for (m in markers) drawMarker(graphics, m)
        return null
    }

    private fun drawPath(g: Graphics2D, path: List<TilePoint>, color: Color) {
        val plane = client.plane
        val stroke = BasicStroke(2f)
        g.stroke = stroke
        for ((i, t) in path.withIndex()) {
            if (t.plane != plane) continue
            val poly = tilePoly(t) ?: continue
            // Endpoints get a brighter outline; intermediate steps a dimmer one.
            val emphasised = i == 0 || i == path.lastIndex
            g.color = if (emphasised) brighter(color) else color
            g.drawPolygon(poly)
            if (i == path.lastIndex) {
                // Fill the destination so it pops.
                g.color = Color(color.red, color.green, color.blue, 90)
                g.fillPolygon(poly)
            }
        }
    }

    private fun drawMarker(g: Graphics2D, m: TileMarker) {
        if (m.tile.plane != client.plane) return
        val poly = tilePoly(m.tile) ?: return
        g.color = Color(m.color.red, m.color.green, m.color.blue, 110)
        g.fillPolygon(poly)
        g.stroke = BasicStroke(2f)
        g.color = m.color
        g.drawPolygon(poly)
        m.label?.let { drawLabel(g, poly, it, m.color) }
    }

    private fun drawLabel(g: Graphics2D, poly: Polygon, text: String, color: Color) {
        val bounds = poly.bounds
        val cx = bounds.centerX.toInt()
        val cy = bounds.centerY.toInt()
        val fm = g.fontMetrics
        val tx = cx - fm.stringWidth(text) / 2
        val ty = cy + fm.ascent / 2
        // Shadow first for legibility against any tile texture.
        g.color = Color.BLACK
        g.drawString(text, tx + 1, ty + 1)
        g.color = color
        g.drawString(text, tx, ty)
    }

    private fun tilePoly(t: TilePoint): Polygon? {
        val wp = WorldPoint(t.x, t.y, t.plane)
        val lp: LocalPoint = LocalPoint.fromWorld(client, wp) ?: return null
        return Perspective.getCanvasTilePoly(client, lp)
    }

    private fun brighter(c: Color): Color =
        Color(
            (c.red + 60).coerceAtMost(255),
            (c.green + 60).coerceAtMost(255),
            (c.blue + 60).coerceAtMost(255),
            c.alpha,
        )
}
