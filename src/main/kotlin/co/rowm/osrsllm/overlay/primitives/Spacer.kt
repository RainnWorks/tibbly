package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/** Vertical empty space. Used as a fixed gap between stacked primitives. */
class Spacer(private val height: Int) : Primitive {
    override fun measure(maxWidth: Int, ctx: RenderContext) = Size(0, height)
    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) { /* no-op */ }
}
