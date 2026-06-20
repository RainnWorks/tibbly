package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/**
 * Horizontal layout. Children are sized in order until the row budget is consumed;
 * anything beyond gets dropped (use it for short inline runs like "Use [icon] X").
 *
 * Vertical alignment: children's y is offset so they centre on the row height.
 */
class HStack(
    private val children: List<Primitive>,
    private val gap: Int = 4,
) : Primitive {

    private var laidOutMaxWidth: Int = -1
    private val laidOutSizes: MutableList<Size> = mutableListOf()
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (laidOutMaxWidth == maxWidth) return totalSize
        laidOutSizes.clear()
        var usedWidth = 0
        var tallest = 0
        for (child in children) {
            val budget = (maxWidth - usedWidth - (if (laidOutSizes.isNotEmpty()) gap else 0))
                .coerceAtLeast(0)
            if (budget <= 0) { laidOutSizes += Size.ZERO; continue }
            val s = child.measure(budget, ctx)
            laidOutSizes += s
            if (s.width == 0 && s.height == 0) continue
            usedWidth += s.width + (if (laidOutSizes.size > 1) gap else 0)
            tallest = maxOf(tallest, s.height)
        }
        totalSize = Size(usedWidth.coerceAtMost(maxWidth), tallest)
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (laidOutMaxWidth != width) measure(width, ctx)
        var cx = x
        for ((i, child) in children.withIndex()) {
            val s = laidOutSizes.getOrNull(i) ?: continue
            if (s.width == 0 && s.height == 0) continue
            val yOffset = (height - s.height) / 2
            child.render(g, cx, y + yOffset, s.width, s.height, ctx)
            cx += s.width + gap
        }
    }

    override fun onClick(localX: Int, localY: Int, ctx: RenderContext): Boolean {
        if (laidOutMaxWidth < 0) return false
        var cx = 0
        for ((i, child) in children.withIndex()) {
            val s = laidOutSizes.getOrNull(i) ?: continue
            if (s.width == 0 && s.height == 0) continue
            if (localX in cx until (cx + s.width)) {
                val yOffset = (totalSize.height - s.height) / 2
                return child.onClick(localX - cx, localY - yOffset, ctx)
            }
            cx += s.width + gap
        }
        return false
    }
}
