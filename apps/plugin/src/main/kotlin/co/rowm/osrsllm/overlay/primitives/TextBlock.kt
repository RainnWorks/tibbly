package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D

/**
 * Word-wrapped multi-line text block.
 *
 * Style choices (font + color) are picked via [style]; you can pass an explicit
 * [color] to override the style's default. Wraps at word boundaries; long words
 * overflow rather than break mid-word.
 */
class TextBlock(
    private val text: String,
    private val style: Style = Style.Body,
    private val color: Color? = null,
    private val lineSpacing: Int = 1,
) : Primitive {

    enum class Style { Body, BodyDim, BodyMuted, Bold, Italic, Heading, Subheading, Mono }

    private var cached: Cached? = null

    private data class Cached(val maxWidth: Int, val lines: List<String>, val size: Size, val lineHeight: Int)

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        val c = cached
        if (c != null && c.maxWidth == maxWidth) return c.size

        val font = pickFont(ctx)
        val fm = dummyFontMetrics(font)
        val lines = wrap(text, fm, maxWidth)
        val lineHeight = fm.height + lineSpacing
        val height = (lines.size * lineHeight - lineSpacing).coerceAtLeast(fm.height)
        val width = lines.maxOf { fm.stringWidth(it) }.coerceAtMost(maxWidth)
        val size = Size(width, height)
        cached = Cached(maxWidth, lines, size, lineHeight)
        return size
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        val c = cached ?: run {
            measure(width, ctx)
            cached ?: return
        }
        g.font = pickFont(ctx)
        g.color = color ?: pickColor(ctx)
        val fm = g.fontMetrics
        var cy = y + fm.ascent
        for (line in c.lines) {
            g.drawString(line, x, cy)
            cy += c.lineHeight
        }
    }

    private fun pickFont(ctx: RenderContext): Font = when (style) {
        Style.Body, Style.BodyDim, Style.BodyMuted -> ctx.fonts.regular
        Style.Bold -> ctx.fonts.bold
        Style.Italic -> ctx.fonts.italic
        Style.Heading -> ctx.fonts.heading
        Style.Subheading -> ctx.fonts.bold
        Style.Mono -> ctx.fonts.mono
    }

    private fun pickColor(ctx: RenderContext): Color = when (style) {
        Style.Body, Style.Bold, Style.Italic, Style.Mono -> ctx.palette.text
        Style.BodyDim -> ctx.palette.textDim
        Style.BodyMuted -> ctx.palette.textMuted
        Style.Heading, Style.Subheading -> ctx.palette.text
    }

    companion object {
        /**
         * Word-wrap [text] to at most [maxWidth] pixels per line according to [fm].
         * Preserves explicit \n line breaks. Empty input returns one empty line.
         */
        fun wrap(text: String, fm: FontMetrics, maxWidth: Int): List<String> {
            if (text.isEmpty()) return listOf("")
            val out = mutableListOf<String>()
            for (rawLine in text.split('\n')) {
                if (rawLine.isEmpty()) { out += ""; continue }
                if (fm.stringWidth(rawLine) <= maxWidth) { out += rawLine; continue }
                var current = StringBuilder()
                for (word in rawLine.split(' ')) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (fm.stringWidth(candidate) <= maxWidth || current.isEmpty()) {
                        current = StringBuilder(candidate)
                    } else {
                        out += current.toString()
                        current = StringBuilder(word)
                    }
                }
                if (current.isNotEmpty()) out += current.toString()
            }
            return out
        }

        /**
         * FontMetrics without needing a Graphics. Reuses a hidden 1x1 image.
         * Safe to call off the render thread (which we don't — measure is called from render
         * anyway — but it's nice for tests).
         */
        private val measureImage = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        fun dummyFontMetrics(font: Font): FontMetrics {
            val g = measureImage.graphics
            return try { g.getFontMetrics(font) } finally { g.dispose() }
        }
    }
}
