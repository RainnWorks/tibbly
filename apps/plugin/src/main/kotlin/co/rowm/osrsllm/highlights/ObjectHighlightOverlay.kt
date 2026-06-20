package co.rowm.osrsllm.highlights

import net.runelite.api.Client
import net.runelite.api.GameObject
import net.runelite.api.TileObject
import net.runelite.api.coords.WorldPoint
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
 * Renders agent-managed game-object highlights by walking the loaded scene
 * tiles and matching each [TileObject] against the registered entries.
 *
 * The scene grid is typically 104x104; walking it every frame is fine. We
 * pre-bucket the entries by `(targetId)` and `(targetId, x, y, plane)` to
 * keep the per-tile check O(1).
 */
@Singleton
class ObjectHighlightOverlay @Inject constructor(
    private val client: Client,
    private val service: ObjectHighlightService,
) : Overlay() {

    init {
        position = OverlayPosition.DYNAMIC
        layer = OverlayLayer.ABOVE_SCENE
        priority = PRIORITY_LOW
    }

    override fun render(graphics: Graphics2D): Dimension? {
        val entries = service.all()
        if (entries.isEmpty()) return null

        val byId = entries.filter { it.x == null }.groupBy { it.targetId }
        val byInstance = entries.filter { it.x != null }
            .associateBy { InstanceKey(it.targetId, it.x!!, it.y!!, it.plane ?: 0) }

        val scene = client.scene ?: return null
        val planeFloor = client.plane
        val tiles = scene.tiles ?: return null

        graphics.stroke = BasicStroke(2f)

        for (z in tiles.indices) {
            if (z != planeFloor) continue
            val planeArr = tiles[z] ?: continue
            for (xRow in planeArr) {
                for (tile in xRow) {
                    if (tile == null) continue
                    visitObjectsOnTile(tile.gameObjects, byId, byInstance, graphics)
                    listOfNotNull(tile.wallObject, tile.decorativeObject, tile.groundObject)
                        .forEach { obj ->
                            highlightIfMatch(obj, byId, byInstance, graphics)
                        }
                }
            }
        }
        return null
    }

    private fun visitObjectsOnTile(
        gameObjects: Array<GameObject?>?,
        byId: Map<Int, List<HighlightEntry>>,
        byInstance: Map<InstanceKey, HighlightEntry>,
        graphics: Graphics2D,
    ) {
        gameObjects?.forEach { go -> if (go != null) highlightIfMatch(go, byId, byInstance, graphics) }
    }

    private fun highlightIfMatch(
        obj: TileObject,
        byId: Map<Int, List<HighlightEntry>>,
        byInstance: Map<InstanceKey, HighlightEntry>,
        graphics: Graphics2D,
    ) {
        val wp = obj.worldLocation ?: return
        val entry: HighlightEntry =
            byInstance[InstanceKey(obj.id, wp.x, wp.y, wp.plane)]
                ?: byId[obj.id]?.firstOrNull()
                ?: return

        val color = service.colorFor(entry)
        // GameObjects expose a convex hull around the full model; other TileObject
        // kinds only offer a clickbox. Either is a usable outline.
        val hull: Shape? = if (obj is GameObject) runCatching { obj.convexHull }.getOrNull() else null
        val clickbox: Shape? = runCatching { obj.clickbox }.getOrNull()
        val shape = hull ?: clickbox
        if (shape != null) {
            graphics.color = Color(color.red, color.green, color.blue, 50)
            graphics.fill(shape)
            graphics.color = color
            graphics.draw(shape)
        }
        entry.label?.takeIf { it.isNotBlank() }?.let { label ->
            val canvasPoint = obj.getCanvasTextLocation(graphics, label, 40) ?: return@let
            graphics.color = Color.BLACK
            graphics.drawString(label, canvasPoint.x + 1, canvasPoint.y + 1)
            graphics.color = color
            graphics.drawString(label, canvasPoint.x, canvasPoint.y)
        }
    }

    private data class InstanceKey(val id: Int, val x: Int, val y: Int, val plane: Int)
}
