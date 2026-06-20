package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Color
import java.awt.Graphics2D

/**
 * Single-child container with padding, background fill, and optional left accent stripe.
 *
 * Use for: info boxes (accent stripe + textDim background), warnings (warningBg),
 * code blocks (codeBg), or any time you want to set a child apart visually.
 */
class Box(
    private val child: Primitive,
    private val background: Color? = null,
    private val accentStripe: Color? = null,
    private val padding: Padding = Padding.uniform(6),
    private val cornerRadius: Int = 0,
) : Primitive {

    data class Padding(val top: Int, val right: Int, val bottom: Int, val left: Int) {
        companion object { fun uniform(v: Int) = Padding(v, v, v, v) }
    }

    private var laidOutMaxWidth: Int = -1
    private var childSize: Size = Size.ZERO
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (laidOutMaxWidth == maxWidth) return totalSize
        val accentExtra = if (accentStripe != null) 4 else 0
        val innerMax = (maxWidth - padding.left - padding.right - accentExtra).coerceAtLeast(0)
        childSize = child.measure(innerMax, ctx)
        totalSize = Size(
            (childSize.width + padding.left + padding.right + accentExtra).coerceAtMost(maxWidth),
            childSize.height + padding.top + padding.bottom,
        )
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (background != null) {
            g.color = background
            if (cornerRadius > 0) g.fillRoundRect(x, y, width, height, cornerRadius, cornerRadius)
            else g.fillRect(x, y, width, height)
        }
        val accentExtra: Int
        if (accentStripe != null) {
            g.color = accentStripe
            g.fillRect(x, y, 3, height)
            accentExtra = 4
        } else accentExtra = 0
        if (laidOutMaxWidth != width) measure(width, ctx)
        child.render(
            g,
            x + padding.left + accentExtra,
            y + padding.top,
            childSize.width,
            childSize.height,
            ctx,
        )
    }

    override fun onClick(localX: Int, localY: Int, ctx: RenderContext): Boolean {
        val accentExtra = if (accentStripe != null) 4 else 0
        return child.onClick(localX - padding.left - accentExtra, localY - padding.top, ctx)
    }
}
