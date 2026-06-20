package co.rowm.osrsllm.overlay.primitives

import co.rowm.osrsllm.overlay.Primitive
import co.rowm.osrsllm.overlay.RenderContext
import co.rowm.osrsllm.overlay.Size
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D

/**
 * Inline flowing text with word-wrap that supports mixed text styles AND embedded
 * OSRS item icons in the same paragraph.
 *
 * Input is a sequence of [FlowRun]s — text spans (with style + color) and icon
 * placeholders. Tokenization splits text into words; each word and each icon becomes
 * an atomic unit that's wrapped onto lines like text in a paragraph.
 *
 * Per-line baseline is computed from the max ascent of tokens on that line so mixed
 * fonts (e.g. body + bold) line up correctly. Icons are vertically centered relative
 * to the line height. Long words that exceed the line width overflow rather than break.
 */
class FlowText(
    private val runs: List<FlowRun>,
    private val lineSpacing: Int = 2,
    private val iconScale: Float = 0.55f,
) : Primitive {

    sealed class FlowRun {
        /** Plain or styled text. */
        data class Text(
            val text: String,
            val style: TextBlock.Style = TextBlock.Style.Body,
            val color: Color? = null,
        ) : FlowRun()

        /**
         * Inline item icon. Auto-pulls the item's display name from ItemManager and
         * renders it as bold-accent text immediately after the sprite, so the agent
         * only has to write `[item:N]` and we render `[icon] Item name` together.
         */
        data class Icon(
            val itemId: Int,
            val qty: Int = 1,
            val showName: Boolean = true,
        ) : FlowRun()
    }

    /** Internal token after splitting text runs into words and resolving icon metrics. */
    private sealed class Token(open val width: Int, open val height: Int, open val ascent: Int) {
        class Word(
            val text: String,
            val style: TextBlock.Style,
            val color: Color?,
            width: Int,
            height: Int,
            ascent: Int,
        ) : Token(width, height, ascent)

        class Space(width: Int, height: Int, ascent: Int) : Token(width, height, ascent)

        class IconToken(
            val itemId: Int,
            val qty: Int,
            val nameText: String,
            val iconW: Int,
            val iconH: Int,
            val nameWidth: Int,
            ascent: Int,
            height: Int,
        ) : Token(iconW + nameWidth, height, ascent)
    }

    private var laidOutMaxWidth: Int = -1
    private var lines: List<List<Token>> = emptyList()
    private var totalSize: Size = Size.ZERO

    override fun measure(maxWidth: Int, ctx: RenderContext): Size {
        if (laidOutMaxWidth == maxWidth) return totalSize
        val tokens = tokenize(ctx)
        val out = wrap(tokens, maxWidth)
        lines = out

        var totalH = 0
        var widest = 0
        for ((i, line) in out.withIndex()) {
            val lineH = line.maxOfOrNull { it.height } ?: 0
            totalH += lineH
            if (i < out.size - 1) totalH += lineSpacing
            widest = maxOf(widest, line.sumOf { it.width })
        }
        totalSize = Size(widest.coerceAtMost(maxWidth), totalH)
        laidOutMaxWidth = maxWidth
        return totalSize
    }

    override fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext) {
        if (laidOutMaxWidth != width) measure(width, ctx)
        var cy = y
        for (line in lines) {
            val lineH = line.maxOfOrNull { it.height } ?: 0
            val baselineY = cy + (line.maxOfOrNull { it.ascent } ?: 0)
            var cx = x
            for (token in line) {
                when (token) {
                    is Token.Word -> {
                        g.font = pickFont(token.style, ctx)
                        g.color = token.color ?: pickColor(token.style, ctx)
                        g.drawString(token.text, cx, baselineY)
                        cx += token.width
                    }
                    is Token.Space -> { cx += token.width }
                    is Token.IconToken -> {
                        val img = runCatching { ctx.itemManager.getImage(token.itemId, token.qty, false) }.getOrNull()
                        val iconTopY = cy + (lineH - token.iconH) / 2
                        if (img != null) {
                            g.drawImage(img, cx, iconTopY, token.iconW, token.iconH, null)
                        } else {
                            g.color = Color(80, 80, 80)
                            g.drawRect(cx, iconTopY, token.iconW - 1, token.iconH - 1)
                        }
                        if (token.qty > 1) {
                            val qtyStr = compactQty(token.qty)
                            g.font = Font("SansSerif", Font.PLAIN, 9)
                            g.color = Color.BLACK
                            g.drawString(qtyStr, cx + 2, iconTopY + 10)
                            g.color = QTY_COLOR
                            g.drawString(qtyStr, cx + 1, iconTopY + 9)
                        }
                        if (token.nameText.isNotEmpty()) {
                            g.font = ctx.fonts.bold
                            g.color = ctx.palette.accent
                            g.drawString(token.nameText, cx + token.iconW, baselineY)
                        }
                        cx += token.width
                    }
                }
            }
            cy += lineH + lineSpacing
        }
    }

    // --- tokenization / wrapping ---

    private fun tokenize(ctx: RenderContext): List<Token> {
        val out = mutableListOf<Token>()
        for (run in runs) {
            when (run) {
                is FlowRun.Text -> {
                    val font = pickFont(run.style, ctx)
                    val fm = TextBlock.dummyFontMetrics(font)
                    val spaceW = fm.charWidth(' ')
                    // Split keeping whitespace as separators.
                    var i = 0
                    val text = run.text
                    while (i < text.length) {
                        val c = text[i]
                        if (c.isWhitespace()) {
                            out += Token.Space(spaceW, fm.height, fm.ascent)
                            i++
                        } else {
                            var j = i
                            while (j < text.length && !text[j].isWhitespace()) j++
                            val word = text.substring(i, j)
                            out += Token.Word(
                                text = word,
                                style = run.style,
                                color = run.color,
                                width = fm.stringWidth(word),
                                height = fm.height,
                                ascent = fm.ascent,
                            )
                            i = j
                        }
                    }
                }
                is FlowRun.Icon -> {
                    val iconW = (BASE_ICON_W * iconScale).toInt()
                    val iconH = (BASE_ICON_H * iconScale).toInt()
                    val name: String = if (run.showName)
                        runCatching { ctx.itemManager.getItemComposition(run.itemId).name }.getOrDefault("item ${run.itemId}")
                    else ""
                    val nameDisplay = if (name.isNotEmpty()) " $name" else ""
                    val font = ctx.fonts.bold
                    val fm = TextBlock.dummyFontMetrics(font)
                    val nameW = if (nameDisplay.isNotEmpty()) fm.stringWidth(nameDisplay) else 0
                    val tokH = maxOf(iconH, fm.height)
                    out += Token.IconToken(
                        itemId = run.itemId,
                        qty = run.qty,
                        nameText = nameDisplay,
                        iconW = iconW,
                        iconH = iconH,
                        nameWidth = nameW,
                        ascent = fm.ascent.coerceAtLeast((iconH * 0.85).toInt()),
                        height = tokH,
                    )
                }
            }
        }
        return out
    }

    private fun wrap(tokens: List<Token>, maxWidth: Int): List<List<Token>> {
        val out = mutableListOf<MutableList<Token>>()
        var current = mutableListOf<Token>()
        var currentWidth = 0
        for (token in tokens) {
            if (token is Token.Space) {
                // Drop spaces at start of a new line.
                if (currentWidth == 0) continue
                current += token
                currentWidth += token.width
                continue
            }
            if (currentWidth + token.width > maxWidth && current.isNotEmpty()) {
                // Trim trailing space from previous line for cleanliness.
                while (current.isNotEmpty() && current.last() is Token.Space) {
                    current.removeAt(current.size - 1)
                }
                out += current
                current = mutableListOf()
                currentWidth = 0
            }
            current += token
            currentWidth += token.width
        }
        if (current.isNotEmpty()) {
            while (current.isNotEmpty() && current.last() is Token.Space) {
                current.removeAt(current.size - 1)
            }
            if (current.isNotEmpty()) out += current
        }
        return out
    }

    private fun pickFont(style: TextBlock.Style, ctx: RenderContext): Font = when (style) {
        TextBlock.Style.Body, TextBlock.Style.BodyDim, TextBlock.Style.BodyMuted -> ctx.fonts.regular
        TextBlock.Style.Bold -> ctx.fonts.bold
        TextBlock.Style.Italic -> ctx.fonts.italic
        TextBlock.Style.Mono -> ctx.fonts.mono
        TextBlock.Style.Heading -> ctx.fonts.heading
        TextBlock.Style.Subheading -> ctx.fonts.bold
    }

    private fun pickColor(style: TextBlock.Style, ctx: RenderContext): Color = when (style) {
        TextBlock.Style.BodyDim -> ctx.palette.textDim
        TextBlock.Style.BodyMuted -> ctx.palette.textMuted
        else -> ctx.palette.text
    }

    private fun compactQty(n: Int): String = when {
        n < 100_000 -> n.toString()
        n < 10_000_000 -> "${n / 1000}K"
        else -> "${n / 1_000_000}M"
    }

    companion object {
        private const val BASE_ICON_W = 36
        private const val BASE_ICON_H = 32
        private val QTY_COLOR = Color(255, 255, 0)
    }
}
