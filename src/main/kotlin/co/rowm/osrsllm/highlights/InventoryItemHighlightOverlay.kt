package co.rowm.osrsllm.highlights

import net.runelite.api.widgets.WidgetItem
import net.runelite.client.ui.overlay.WidgetItemOverlay
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders agent-managed inventory item highlights using RuneLite's
 * [WidgetItemOverlay] helper, which already handles iterating items across the
 * inventory pane, the bank pane, and the equipment screen.
 *
 * For each item the helper hands us, we look up its id in the service. Matches
 * get an outlined rectangle around their slot plus an optional label drawn
 * underneath. Non-matches are skipped.
 */
@Singleton
class InventoryItemHighlightOverlay @Inject constructor(
    private val service: InventoryItemHighlightService,
) : WidgetItemOverlay() {

    init {
        showOnInventory()
        showOnBank()
        showOnEquipment()
    }

    override fun renderItemOverlay(graphics: Graphics2D, itemId: Int, widgetItem: WidgetItem) {
        val entry = service.all().firstOrNull { it.targetId == itemId } ?: return
        val color = service.colorFor(entry)
        val bounds = widgetItem.canvasBounds
        graphics.color = Color(color.red, color.green, color.blue, 60)
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        graphics.color = color
        graphics.stroke = BasicStroke(2f)
        graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
        entry.label?.takeIf { it.isNotBlank() }?.let { label ->
            val fm = graphics.fontMetrics
            val tx = bounds.x + (bounds.width - fm.stringWidth(label)) / 2
            val ty = bounds.y + bounds.height + fm.ascent
            graphics.color = Color.BLACK
            graphics.drawString(label, tx + 1, ty + 1)
            graphics.color = color
            graphics.drawString(label, tx, ty)
        }
    }
}
