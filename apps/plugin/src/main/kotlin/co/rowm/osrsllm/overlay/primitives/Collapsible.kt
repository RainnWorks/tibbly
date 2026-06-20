package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/**
 * Header + hideable detail. Click the header to toggle [expanded].
 *
 * The header should be self-contained (e.g. an HStack with "▸" + label). When
 * expanded=true, the detail primitive is also measured + rendered below.
 *
 * Click hit-testing covers only the header area, not the detail.
 */
class Collapsible(
    private val header: Primitive,
    private val detail: Primitive,
    private var expanded: Boolean = false,
    private val gap: Int = 3,
) : Primitive {

    private var laidOutMaxWidth: Int = -1
    private var headerSize: Size = Size.ZERO
    private var detailSize: Size = Size.ZERO
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (laidOutMaxWidth == maxWidth) return totalSize
        headerSize = header.measure(maxWidth, ctx)
        if (expanded) {
            detailSize = detail.measure(maxWidth, ctx)
            totalSize = Size(
                maxOf(headerSize.width, detailSize.width).coerceAtMost(maxWidth),
                headerSize.height + gap + detailSize.height,
            )
        } else {
            detailSize = Size.ZERO
            totalSize = headerSize
        }
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (laidOutMaxWidth != width) measure(width, ctx)
        header.render(g, x, y, width, headerSize.height, ctx)
        if (expanded) {
            detail.render(g, x, y + headerSize.height + gap, width, detailSize.height, ctx)
        }
    }

    override fun onClick(localX: Int, localY: Int, ctx: RenderContext): Boolean {
        // Only the header is clickable. Detail content can have its own children
        // handle clicks via a future overload, but for now Collapsible owns the toggle.
        if (localY < headerSize.height) {
            expanded = !expanded
            laidOutMaxWidth = -1
            ctx.invalidate()
            return true
        }
        return false
    }
}
