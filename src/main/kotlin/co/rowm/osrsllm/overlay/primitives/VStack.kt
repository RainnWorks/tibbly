package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/**
 * Vertical stack with a fixed gap between children. Width is the widest child's
 * width (capped at maxWidth); height is sum of children + (n-1)*gap.
 *
 * Click events are dispatched to the child whose vertical band contains the click.
 */
class VStack(
    private val children: List<Primitive>,
    private val gap: Int = 4,
) : Primitive {

    // Bookkeeping per measure() so render + onClick can reuse the same layout.
    private var laidOutMaxWidth: Int = -1
    private val laidOutSizes: MutableList<Size> = mutableListOf()
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (laidOutMaxWidth == maxWidth) return totalSize
        laidOutSizes.clear()
        var widest = 0
        var height = 0
        var visible = 0
        for (child in children) {
            val s = child.measure(maxWidth, ctx)
            laidOutSizes += s
            if (s.height == 0 && s.width == 0) continue
            widest = maxOf(widest, s.width)
            height += s.height
            visible++
        }
        if (visible > 1) height += gap * (visible - 1)
        totalSize = Size(widest.coerceAtMost(maxWidth), height)
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (laidOutMaxWidth != width) measure(width, ctx)
        var cy = y
        for ((i, child) in children.withIndex()) {
            val s = laidOutSizes.getOrNull(i) ?: continue
            if (s.height == 0 && s.width == 0) continue
            child.render(g, x, cy, width, s.height, ctx)
            cy += s.height + gap
        }
    }

    override fun onClick(localX: Int, localY: Int, ctx: RenderContext): Boolean {
        if (laidOutMaxWidth < 0) return false
        var cy = 0
        for ((i, child) in children.withIndex()) {
            val s = laidOutSizes.getOrNull(i) ?: continue
            if (s.height == 0 && s.width == 0) continue
            if (localY in cy until (cy + s.height)) {
                return child.onClick(localX, localY - cy, ctx)
            }
            cy += s.height + gap
        }
        return false
    }
}
