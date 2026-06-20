package co.rowm.osrsllm.overlay

import net.runelite.client.game.ItemManager
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D

/**
 * Minimal layout primitive contract. Three responsibilities:
 *
 *  1. [measure] — given a max width, return the size you'd actually use. Width <= max.
 *  2. [render]  — draw yourself at (x, y) into the bounds the parent allotted.
 *  3. [onClick] — optionally handle a click at local (x, y); return true if state changed.
 *
 * Primitives are re-created cheaply every frame so they should be stateless EXCEPT for
 * truly interactive ones (Collapsible, ToolCallBox) which hold their own expanded flag.
 */
interface Primitive {
    fun measure(maxWidth: Int, ctx: RenderContext): Size
    fun render(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, ctx: RenderContext)
    fun onClick(localX: Int, localY: Int, ctx: RenderContext): Boolean = false
}

data class Size(val width: Int, val height: Int) {
    companion object { val ZERO = Size(0, 0) }
}

/**
 * Per-render context passed down the primitive tree. Read-only from a primitive's POV
 * except for [invalidate], which schedules a re-measure (e.g. when a collapsible toggles).
 */
class RenderContext(
    val itemManager: ItemManager,
    val fonts: FontSet = FontSet.DEFAULT,
    val palette: Palette = Palette.DEFAULT,
    val invalidate: () -> Unit = {},
)

class FontSet(
    val regular: Font,
    val bold: Font,
    val italic: Font,
    val mono: Font,
    val heading: Font,
    val small: Font,
) {
    companion object {
        val DEFAULT = FontSet(
            regular = Font("SansSerif", Font.PLAIN, 12),
            bold = Font("SansSerif", Font.BOLD, 12),
            italic = Font("SansSerif", Font.ITALIC, 12),
            mono = Font("Monospaced", Font.PLAIN, 11),
            heading = Font("SansSerif", Font.BOLD, 14),
            small = Font("SansSerif", Font.PLAIN, 10),
        )
    }
}

/** Reusable colors so primitives don't hardcode them. */
class Palette(
    val background: Color,
    val backgroundAlt: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val accent: Color,
    val accentBg: Color,
    val warning: Color,
    val warningBg: Color,
    val success: Color,
    val codeBg: Color,
    val divider: Color,
    val tableHeaderBg: Color,
) {
    companion object {
        val DEFAULT = Palette(
            background = Color(20, 20, 22, 235),
            backgroundAlt = Color(34, 34, 38, 235),
            border = Color(95, 95, 100, 220),
            text = Color(232, 232, 232),
            textDim = Color(180, 180, 180),
            textMuted = Color(140, 140, 140),
            accent = Color(140, 214, 168),
            accentBg = Color(40, 80, 60, 200),
            warning = Color(232, 174, 78),
            warningBg = Color(80, 60, 30, 200),
            success = Color(120, 200, 120),
            codeBg = Color(26, 26, 26),
            divider = Color(70, 70, 75),
            tableHeaderBg = Color(45, 45, 50, 220),
        )
    }
}
