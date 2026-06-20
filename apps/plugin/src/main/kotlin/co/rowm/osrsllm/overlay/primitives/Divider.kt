package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/** Full-width horizontal rule using the palette's divider color. */
class Divider(private val paddingY: Int = 3) : Primitive {
    override fun measure(maxWidth: Int, ctx: RenderContext) = Size(maxWidth, paddingY * 2 + 1)

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        g.color = ctx.palette.divider
        g.drawLine(x, y + paddingY, x + width - 1, y + paddingY)
    }
}
