package co.rowm.osrsllm.highlights

import net.runelite.api.Client
import net.runelite.api.NPC
import net.runelite.api.Perspective
import net.runelite.client.ui.overlay.Overlay
import net.runelite.client.ui.overlay.OverlayLayer
import net.runelite.client.ui.overlay.OverlayPosition
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Shape
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders agent-managed NPC highlights. Outlines each matching NPC's convex
 * hull (the same shape NpcIndicators uses) plus a centred label when one is
 * configured. NPCs that aren't currently in-view get skipped silently.
 */
@Singleton
class NpcHighlightOverlay @Inject constructor(
    private val client: Client,
    private val service: NpcHighlightService,
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
        @Suppress("DEPRECATION")
        val npcs: List<NPC> = client.npcs.toList()
        if (npcs.isEmpty()) return null

        graphics.stroke = BasicStroke(2f)
        for (npc in npcs) {
            val entry = byTargetId[npc.id] ?: continue
            val color = service.colorFor(entry)
            val hull: Shape? = runCatching { npc.convexHull }.getOrNull()
            if (hull != null) {
                graphics.color = Color(color.red, color.green, color.blue, 60)
                graphics.fill(hull)
                graphics.color = color
                graphics.draw(hull)
            } else {
                // Fallback to the tile poly so we still mark the NPC even if its hull is unavailable.
                val lp = npc.localLocation ?: continue
                val poly = Perspective.getCanvasTilePoly(client, lp) ?: continue
                graphics.color = color
                graphics.drawPolygon(poly)
            }
            entry.label?.takeIf { it.isNotBlank() }?.let { label ->
                drawCenteredLabel(graphics, npc, label, color)
            }
        }
        return null
    }

    private fun drawCenteredLabel(g: Graphics2D, npc: NPC, text: String, color: Color) {
        val point = npc.getCanvasTextLocation(g, text, npc.logicalHeight + 40) ?: return
        g.color = Color.BLACK
        g.drawString(text, point.x + 1, point.y + 1)
        g.color = color
        g.drawString(text, point.x, point.y)
    }
}
