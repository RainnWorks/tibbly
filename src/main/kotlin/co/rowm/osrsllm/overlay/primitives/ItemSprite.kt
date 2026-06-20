package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Renders a single OSRS item icon at fixed 36×32 (the standard ItemManager.getImage
 * sprite size). Optional quantity badge in the top-left when > 1, drawn yellow like the
 * native inventory.
 *
 * Uses [ItemManager.getImage] which caches and is safe to call on the render thread.
 * If the image is null (unknown id), draws a debug rectangle so the layout doesn't shift.
 */
class ItemSprite(
    private val itemId: Int,
    private val quantity: Int = 1,
    private val scale: Float = 0.75f,
) : Primitive {

    private val width: Int = (ICON_W * scale).toInt()
    private val height: Int = (ICON_H * scale).toInt()

    override fun measure(maxWidth: Int, ctx: RenderContext): Size = Size(width.coerceAtMost(maxWidth), height)

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        val image: BufferedImage? = runCatching { ctx.itemManager.getImage(itemId, quantity, false) }.getOrNull()
        if (image != null) {
            g.drawImage(image, x, y, width, height, null)
        } else {
            g.color = Color(80, 80, 80)
            g.drawRect(x, y, width - 1, height - 1)
            g.font = ctx.fonts.small
            g.color = ctx.palette.textDim
            g.drawString("?$itemId", x + 2, y + 12)
        }
        if (quantity > 1) {
            g.font = Font("SansSerif", Font.PLAIN, 10)
            val qty = compactQty(quantity)
            g.color = Color.BLACK
            g.drawString(qty, x + 2, y + 11)
            g.color = QTY_COLOR
            g.drawString(qty, x + 1, y + 10)
        }
    }

    private fun compactQty(n: Int): String = when {
        n < 100_000 -> n.toString()
        n < 10_000_000 -> "${n / 1000}K"
        else -> "${n / 1_000_000}M"
    }

    companion object {
        private const val ICON_W = 36
        private const val ICON_H = 32
        private val QTY_COLOR = Color(255, 255, 0)
    }
}
