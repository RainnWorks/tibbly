package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Graphics2D

/**
 * Simple table laid out as fixed columns. Column widths are chosen to fit the widest
 * cell, capped at maxWidth/cols if everything is too wide.
 *
 * Rows are stacked vertically; the header row gets a subtle background and bold style
 * is applied by the caller via the cell primitives (TextBlock(..., Style.Bold)).
 *
 * Cells are arbitrary primitives — text, item sprites, mixed HStacks, etc.
 */
class Table(
    private val header: List<Primitive>?,
    private val rows: List<List<Primitive>>,
    private val cellPaddingX: Int = 6,
    private val cellPaddingY: Int = 3,
) : Primitive {

    private val cols: Int = (header?.size ?: rows.firstOrNull()?.size ?: 0)

    private var laidOutMaxWidth: Int = -1
    private lateinit var colWidths: IntArray
    private val rowHeights = mutableListOf<Int>()
    private var headerHeight: Int = 0
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (cols == 0) return Size.ZERO
        if (laidOutMaxWidth == maxWidth) return totalSize

        // First pass: ask each cell what it WANTS in the budget split N ways.
        val budget = ((maxWidth - cellPaddingX * 2 * cols) / cols).coerceAtLeast(20)
        colWidths = IntArray(cols)
        val perCellMeasure = Array(rows.size + (if (header != null) 1 else 0)) { rowIdx ->
            val cells = if (header != null && rowIdx == 0) header
                else rows[rowIdx - (if (header != null) 1 else 0)]
            Array(cols) { colIdx ->
                cells.getOrNull(colIdx)?.measure(budget, ctx) ?: Size.ZERO
            }
        }
        for (col in 0 until cols) {
            val widest = perCellMeasure.maxOf { it[col].width }
            colWidths[col] = widest
        }

        // Second pass: scale down if total exceeds maxWidth.
        val totalCellWidth = colWidths.sum() + cellPaddingX * 2 * cols
        if (totalCellWidth > maxWidth) {
            val scale = (maxWidth - cellPaddingX * 2 * cols).toDouble() / colWidths.sum().toDouble()
            for (i in colWidths.indices) colWidths[i] = (colWidths[i] * scale).toInt().coerceAtLeast(10)
        }

        // Row heights = max cell height in row.
        rowHeights.clear()
        var totalHeight = 0
        if (header != null) {
            val h = perCellMeasure[0].maxOf { it.height } + cellPaddingY * 2
            headerHeight = h
            totalHeight += h
        }
        val rowOffset = if (header != null) 1 else 0
        for (r in rows.indices) {
            val h = perCellMeasure[r + rowOffset].maxOf { it.height } + cellPaddingY * 2
            rowHeights += h
            totalHeight += h
        }
        val totalWidth = colWidths.sum() + cellPaddingX * 2 * cols
        totalSize = Size(totalWidth.coerceAtMost(maxWidth), totalHeight)
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (laidOutMaxWidth != width) measure(width, ctx)
        var cy = y
        if (header != null) {
            g.color = ctx.palette.tableHeaderBg
            g.fillRect(x, cy, totalSize.width, headerHeight)
            drawRow(g, x, cy, headerHeight, header, ctx)
            cy += headerHeight
            g.color = ctx.palette.divider
            g.drawLine(x, cy, x + totalSize.width - 1, cy)
        }
        for ((i, row) in rows.withIndex()) {
            val h = rowHeights[i]
            if (i % 2 == 1) {
                g.color = ctx.palette.backgroundAlt
                g.fillRect(x, cy, totalSize.width, h)
            }
            drawRow(g, x, cy, h, row, ctx)
            cy += h
        }
    }

    private fun drawRow(g: Graphics2D, x: Int, y: Int, h: Int, cells: List<Primitive>, ctx: RenderContext) {
        var cx = x
        for ((col, cell) in cells.withIndex()) {
            if (col >= cols) break
            val cw = colWidths[col]
            val measured = cell.measure(cw, ctx)
            val cellY = y + cellPaddingY
            cell.render(g, cx + cellPaddingX, cellY, cw, measured.height, ctx)
            cx += cw + cellPaddingX * 2
        }
    }
}
