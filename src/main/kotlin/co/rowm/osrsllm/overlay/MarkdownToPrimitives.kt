package co.rowm.osrsllm.overlay

import co.rowm.osrsllm.overlay.primitives.Box
import co.rowm.osrsllm.overlay.primitives.Divider
import co.rowm.osrsllm.overlay.primitives.FlowText
import co.rowm.osrsllm.overlay.primitives.Spacer
import co.rowm.osrsllm.overlay.primitives.Table
import co.rowm.osrsllm.overlay.primitives.TextBlock
import co.rowm.osrsllm.overlay.primitives.VStack

/**
 * Lightweight markdown → primitive-tree renderer.
 *
 * Supports the shapes claude's responses actually use:
 *   - Headings: `# H1`, `## H2`, `### H3`
 *   - Paragraphs (blank-line separated)
 *   - Bulleted lists: `- item`, `* item` (single level)
 *   - Numbered lists: `1.`, `2.` etc.
 *   - Code blocks: ``` fenced ``` and inline `code` (very basic — inline is rendered as plain text)
 *   - GFM-ish tables (header row, separator, body rows)
 *   - Horizontal rules: `---`
 *
 * Not aiming for a complete markdown spec — just the chunks we see in real chat output.
 */
object MarkdownToPrimitives {

    fun parse(markdown: String): Primitive {
        val lines = markdown.lines()
        val out = mutableListOf<Primitive>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> { i++ }

                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines += lines[i]; i++
                    }
                    if (i < lines.size) i++
                    out += Box(
                        TextBlock(codeLines.joinToString("\n"), TextBlock.Style.Mono),
                        background = Palette.DEFAULT.codeBg,
                        padding = Box.Padding.uniform(6),
                    )
                }

                trimmed.startsWith("# ") -> {
                    out += TextBlock(trimmed.removePrefix("# ").trim(), TextBlock.Style.Heading)
                    i++
                }
                trimmed.startsWith("## ") -> {
                    out += TextBlock(trimmed.removePrefix("## ").trim(), TextBlock.Style.Subheading)
                    i++
                }
                trimmed.startsWith("### ") -> {
                    out += TextBlock(trimmed.removePrefix("### ").trim(), TextBlock.Style.Bold)
                    i++
                }

                trimmed == "---" || trimmed == "***" -> {
                    out += Divider()
                    i++
                }

                isBullet(trimmed) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && isBullet(lines[i].trim())) {
                        items += stripBullet(lines[i].trim())
                        i++
                    }
                    out += VStack(items.map { bulletRow(it) }, gap = 2)
                }

                isNumbered(trimmed) -> {
                    val items = mutableListOf<Pair<String, String>>()
                    while (i < lines.size && isNumbered(lines[i].trim())) {
                        val match = numberedRegex.find(lines[i].trim())!!
                        items += match.groupValues[1] to match.groupValues[2]
                        i++
                    }
                    out += VStack(items.map { (n, txt) -> numberedRow(n, txt) }, gap = 2)
                }

                isTableHeader(trimmed, lines.getOrNull(i + 1)?.trim()) -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines += lines[i].trim(); i++
                    }
                    out += parseTable(tableLines)
                }

                else -> {
                    val paragraphLines = mutableListOf(line)
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty() &&
                        !lines[i].trim().startsWith("#") && !lines[i].trim().startsWith("```") &&
                        !isBullet(lines[i].trim()) && !isNumbered(lines[i].trim()) &&
                        !lines[i].trim().startsWith("|")) {
                        paragraphLines += lines[i]; i++
                    }
                    out += inlineRichText(paragraphLines.joinToString(" ") { it.trim() })
                }
            }
        }
        return VStack(out.withSeparators(), gap = 4)
    }

    // ---- helpers ----

    private fun isBullet(s: String) = s.startsWith("- ") || s.startsWith("* ") || s.startsWith("• ")
    private fun stripBullet(s: String): String = s.removePrefix("-").removePrefix("*").removePrefix("•").trim()
    private val numberedRegex = Regex("""^(\d+)\.\s+(.*)""")
    private fun isNumbered(s: String) = numberedRegex.matches(s)

    private fun bulletRow(text: String): Primitive {
        val runs = mutableListOf<FlowText.FlowRun>(
            FlowText.FlowRun.Text("• ", TextBlock.Style.BodyDim),
        )
        runs += parseInlineRuns(text)
        return FlowText(runs)
    }

    private fun numberedRow(num: String, text: String): Primitive {
        val runs = mutableListOf<FlowText.FlowRun>(
            FlowText.FlowRun.Text("$num. ", TextBlock.Style.BodyDim),
        )
        runs += parseInlineRuns(text)
        return FlowText(runs)
    }

    private fun isTableHeader(line: String, next: String?): Boolean {
        if (!line.startsWith("|")) return false
        if (next == null || !next.startsWith("|")) return false
        // separator row looks like |---|---| or |:--|:--:|
        return next.replace("|", "").trim().matches(Regex("""[-:\s]+"""))
    }

    private fun parseTable(lines: List<String>): Primitive {
        if (lines.size < 2) return TextBlock(lines.joinToString("\n"))
        fun cells(line: String) = line.trim().trim('|').split('|').map { it.trim() }
        val header = cells(lines[0]).map { TextBlock(it, TextBlock.Style.Bold) }
        val rows = lines.drop(2).map { row -> cells(row).map { TextBlock(it) as Primitive } }
        return Table(header, rows)
    }

    /**
     * Parse a paragraph into a [FlowText] honoring inline markdown + item refs:
     *   - `[item:NNN]` → inline icon + auto-pulled name
     *   - `**bold**`   → bold span
     *   - `*italic*`   → italic span
     *   - `` `code` `` → mono span
     * Other text becomes a plain Body run.
     */
    private fun inlineRichText(text: String): Primitive = FlowText(parseInlineRuns(text))

    private val inlinePattern: Regex = Regex(
        """\[item:(\d+)(?:\|(\d+))?]|\*\*([^*]+)\*\*|(?<!\w)\*([^*\n]+?)\*(?!\w)|`([^`]+)`""",
    )

    /**
     * Walk [text] and produce a sequence of [FlowText.FlowRun]s. The regex matches
     * one of: [item:N], [item:N|qty], **bold**, *italic*, `code` — and the bits in
     * between become plain Body text runs. Non-matching content preserves spaces so
     * the FlowText tokenizer can handle wrapping.
     */
    fun parseInlineRuns(text: String): List<FlowText.FlowRun> {
        val out = mutableListOf<FlowText.FlowRun>()
        var lastEnd = 0
        for (m in inlinePattern.findAll(text)) {
            if (m.range.first > lastEnd) {
                val plain = text.substring(lastEnd, m.range.first)
                if (plain.isNotEmpty()) out += FlowText.FlowRun.Text(plain)
            }
            val groups = m.groupValues
            when {
                groups[1].isNotEmpty() -> {
                    val id = groups[1].toInt()
                    val qty = groups.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                    out += FlowText.FlowRun.Icon(itemId = id, qty = qty)
                }
                groups[3].isNotEmpty() -> out += FlowText.FlowRun.Text(groups[3], TextBlock.Style.Bold)
                groups[4].isNotEmpty() -> out += FlowText.FlowRun.Text(groups[4], TextBlock.Style.Italic)
                groups[5].isNotEmpty() -> out += FlowText.FlowRun.Text(groups[5], TextBlock.Style.Mono)
            }
            lastEnd = m.range.last + 1
        }
        if (lastEnd < text.length) {
            val plain = text.substring(lastEnd)
            if (plain.isNotEmpty()) out += FlowText.FlowRun.Text(plain)
        }
        if (out.isEmpty()) out += FlowText.FlowRun.Text(text)
        return out
    }

    /** Inserts a Spacer between block-level entries so they don't crowd. */
    private fun List<Primitive>.withSeparators(): List<Primitive> {
        if (isEmpty()) return this
        val out = mutableListOf<Primitive>()
        for ((i, p) in withIndex()) {
            if (i > 0) out += Spacer(2)
            out += p
        }
        return out
    }
}
