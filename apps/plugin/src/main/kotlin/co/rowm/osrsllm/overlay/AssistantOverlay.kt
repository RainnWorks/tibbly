package co.rowm.osrsllm.overlay

import co.rowm.osrsllm.chat.ToolCall
import co.rowm.osrsllm.overlay.primitives.Box
import co.rowm.osrsllm.overlay.primitives.Collapsible
import co.rowm.osrsllm.overlay.primitives.Divider
import co.rowm.osrsllm.overlay.primitives.HStack
import co.rowm.osrsllm.overlay.primitives.Spacer
import co.rowm.osrsllm.overlay.primitives.TextBlock
import co.rowm.osrsllm.overlay.primitives.VStack
import net.runelite.api.Client
import net.runelite.api.gameval.InterfaceID
import net.runelite.api.gameval.VarClientID
import net.runelite.api.widgets.Widget
import net.runelite.client.game.ItemManager
import net.runelite.client.input.KeyListener
import net.runelite.client.input.MouseAdapter
import net.runelite.client.input.MouseListener
import net.runelite.client.input.MouseWheelListener
import java.awt.event.KeyEvent
import net.runelite.client.ui.overlay.Overlay
import net.runelite.client.ui.overlay.OverlayLayer
import net.runelite.client.ui.overlay.OverlayPosition
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the assistant UI as a docked overlay over the OSRS chatbox area.
 *
 * Anchors to the chatbox widget bounds each frame (via [findChatboxBounds]); ignores
 * the chatbox completely when the state is [AssistantOverlayState.Hidden].
 *
 * Mouse handling:
 *   - wheel events scroll the primitive list
 *   - left clicks within the overlay area dispatch to the primitive tree (Collapsible
 *     headers, etc.)
 *   - clicks on the dismiss "✕" hide the overlay
 *
 * Rendering pipeline:
 *   - background panel + border
 *   - title bar ("You: <query>" + close button + thinking pulse)
 *   - scrollable body region: rendered primitive tree built from the active state
 *   - tool-call list at the bottom (collapsible per call)
 *   - scrollbar on the right when content overflows
 */
@Singleton
class AssistantOverlay @Inject constructor(
    private val client: Client,
    private val itemManager: ItemManager,
    private val state: AssistantOverlayStateHolder,
) : Overlay() {

    init {
        // DYNAMIC lets us anchor the overlay to the chatbox message area each frame
        // via setPreferredLocation/Size. We only render when the AI tab is selected,
        // so the overlay literally replaces the native chat rendering with our rich
        // primitive tree (tables, inline icons, expandable tool calls, scrolling).
        position = OverlayPosition.DYNAMIC
        layer = OverlayLayer.ABOVE_WIDGETS
        priority = PRIORITY_HIGHEST
    }

    /** Triggered by interactive primitives so we re-measure on next frame. */
    private var dirty: Boolean = true
    private val invalidate: () -> Unit = { dirty = true; cachedBodyTree = null }
    private var cachedBodyTree: Primitive? = null
    private var lastStateRendered: AssistantOverlayState? = null

    // Layout bookkeeping (set per render so click handlers can hit-test).
    private var renderedBounds: Rectangle = Rectangle(0, 0, 0, 0)
    private var bodyClipRect: Rectangle = Rectangle(0, 0, 0, 0)
    private var bodyContentHeight: Int = 0
    private var bodyTreeForClick: Primitive? = null
    private var scrollY: Int = 0
    private var dismissBtnBounds: Rectangle = Rectangle(0, 0, 0, 0)
    private var minimizedBadgeBounds: Rectangle = Rectangle(0, 0, 0, 0)
    /** Updated on any user interaction (click, scroll). Used for auto-dismiss timer. */
    @Volatile private var lastInteractionMs: Long = 0L

    private val ctx: RenderContext by lazy {
        RenderContext(itemManager = itemManager, invalidate = invalidate)
    }

    override fun render(graphics: Graphics2D): Dimension? {
        // Gate 1: AI tab must be the active chat view (Trade slot = 6).
        val activeTab = runCatching { client.getVarcIntValue(VarClientID.CHAT_VIEW) }.getOrDefault(0)
        if (activeTab != CHAT_VIEW_TRADE) {
            renderedBounds = Rectangle(0, 0, 0, 0)
            return null
        }

        // Auto-dismiss after AUTO_DISMISS_MS of no interaction post-response.
        (state.get() as? AssistantOverlayState.Response)?.let { resp ->
            val ref = maxOf(resp.finishedAtMs, lastInteractionMs)
            if (System.currentTimeMillis() - ref > AUTO_DISMISS_MS) {
                state.set(AssistantOverlayState.Hidden)
            }
        }

        val current = state.get()
        if (current is AssistantOverlayState.Hidden) {
            // Nothing to show. Let the native AI tab chat lines remain visible.
            renderedBounds = Rectangle(0, 0, 0, 0)
            return null
        }

        // Re-measure when state changes.
        if (current !== lastStateRendered) {
            lastStateRendered = current
            cachedBodyTree = null
            scrollY = 0
        }

        // Anchor the overlay to the OSRS chat message area each frame.
        val chatBounds = findChatAreaBounds() ?: return null
        preferredLocation = chatBounds.location
        preferredSize = Dimension(chatBounds.width, chatBounds.height)
        renderedBounds = Rectangle(chatBounds)

        val w = chatBounds.width
        val h = chatBounds.height

        // 1. Background + border.
        graphics.color = ctx.palette.background
        graphics.fillRect(0, 0, w, h)
        graphics.color = ctx.palette.border
        graphics.drawRect(0, 0, w - 1, h - 1)

        // 2. Title bar.
        val titleH = drawTitleBar(graphics, w, current)

        // 3. Body area (everything below the title bar).
        val bodyX = PAD
        val bodyY = titleH + 4
        val bodyW = w - PAD * 2 - SCROLLBAR_W
        val bodyH = h - titleH - PAD * 2
        // Hit-test rect stored in screen coords (relative to overlay origin).
        bodyClipRect = Rectangle(
            renderedBounds.x + bodyX,
            renderedBounds.y + bodyY,
            bodyW,
            bodyH,
        )

        // 4. Build the primitive tree.
        val tree = cachedBodyTree ?: buildTree(current).also { cachedBodyTree = it }
        bodyTreeForClick = tree
        val treeSize = tree.measure(bodyW, ctx)
        bodyContentHeight = treeSize.height

        // 5. Clip + render with scroll offset (local coords).
        val origClip = graphics.clip
        graphics.setClip(bodyX, bodyY, bodyW, bodyH)
        tree.render(graphics, bodyX, bodyY - scrollY, bodyW, treeSize.height, ctx)
        graphics.clip = origClip

        // 6. Scrollbar.
        drawScrollbar(graphics, w, h, titleH)

        return Dimension(w, h)
    }

    private fun drawTitleBar(g: Graphics2D, w: Int, state: AssistantOverlayState): Int {
        val titleH = 20
        g.color = ctx.palette.backgroundAlt
        g.fillRect(0, 0, w, titleH)
        g.color = ctx.palette.divider
        g.drawLine(0, titleH, w, titleH)

        val query = when (state) {
            is AssistantOverlayState.Thinking -> state.query
            is AssistantOverlayState.Response -> state.query
            else -> ""
        }
        val prefix = when (state) {
            is AssistantOverlayState.Thinking -> "thinking ${dots(state.startedAtMs)} "
            is AssistantOverlayState.Response -> if (state.errored) "error " else ""
            else -> ""
        }
        g.font = ctx.fonts.regular
        g.color = ctx.palette.accent
        g.drawString(prefix, PAD, 14)
        val prefixW = g.fontMetrics.stringWidth(prefix)
        g.color = ctx.palette.text
        val maxQueryW = w - PAD * 2 - prefixW - 28
        val displayQuery = truncate(query, g.fontMetrics, maxQueryW)
        g.drawString(displayQuery, PAD + prefixW, 14)

        // Dismiss button — bounds stored in screen coords for hit testing.
        val dismissLocalX = w - 20
        val dismissLocalY = 3
        dismissBtnBounds = Rectangle(
            renderedBounds.x + dismissLocalX,
            renderedBounds.y + dismissLocalY,
            16,
            14,
        )
        g.color = ctx.palette.textDim
        g.drawString("✕", dismissLocalX + 3, dismissLocalY + 12)

        return titleH
    }

    private fun drawScrollbar(g: Graphics2D, w: Int, h: Int, titleH: Int) {
        if (bodyContentHeight <= bodyClipRect.height) return
        val trackX = w - SCROLLBAR_W - 2
        val trackY = titleH + 4
        val trackH = bodyClipRect.height
        g.color = ctx.palette.backgroundAlt
        g.fillRect(trackX, trackY, SCROLLBAR_W, trackH)
        val thumbH = (trackH.toDouble() * trackH / bodyContentHeight).toInt().coerceAtLeast(12)
        val maxScroll = bodyContentHeight - trackH
        val thumbY = trackY + (trackH - thumbH) * scrollY / maxScroll.coerceAtLeast(1)
        g.color = ctx.palette.border
        g.fillRect(trackX, thumbY, SCROLLBAR_W, thumbH)
    }

    /**
     * Build the primitive tree for the given state. Cached and rebuilt only when the
     * state object identity changes or a child primitive invalidates.
     */
    private fun buildTree(state: AssistantOverlayState): Primitive {
        val parts = mutableListOf<Primitive>()
        when (state) {
            is AssistantOverlayState.Hidden -> { /* unreachable */ }
            is AssistantOverlayState.Thinking -> {
                if (state.toolCalls.isNotEmpty()) {
                    parts += TextBlock("Tool calls so far:", TextBlock.Style.BodyDim)
                    parts += toolCallList(state.toolCalls)
                } else {
                    parts += TextBlock("Working on it…", TextBlock.Style.Italic)
                }
            }
            is AssistantOverlayState.Response -> {
                if (state.body !is VStack) parts += state.body else parts += state.body
                if (state.toolCalls.isNotEmpty()) {
                    parts += Spacer(6)
                    parts += Divider()
                    parts += Spacer(4)
                    parts += TextBlock("Tool calls (${state.toolCalls.size})", TextBlock.Style.BodyDim)
                    parts += toolCallList(state.toolCalls)
                }
            }
        }
        return VStack(parts, gap = 4)
    }

    private fun toolCallList(calls: List<ToolCall>): Primitive {
        return VStack(
            calls.map { call ->
                Collapsible(
                    header = HStack(
                        listOf(
                            TextBlock("▸", TextBlock.Style.BodyDim),
                            TextBlock(call.name, TextBlock.Style.Italic, color = ctx.palette.accent),
                        ),
                        gap = 4,
                    ),
                    detail = Box(
                        VStack(
                            listOf(
                                TextBlock("input", TextBlock.Style.BodyMuted),
                                TextBlock(call.input.take(800), TextBlock.Style.Mono),
                                Spacer(2),
                                TextBlock("result", TextBlock.Style.BodyMuted),
                                TextBlock(call.result.take(1200), TextBlock.Style.Mono),
                            ),
                            gap = 2,
                        ),
                        background = ctx.palette.codeBg,
                        padding = Box.Padding.uniform(5),
                    ),
                )
            },
            gap = 2,
        )
    }

    /**
     * Bounds of the OSRS chat message area in canvas coords. We anchor to
     * [InterfaceID.Chatbox.CHATAREA] so we cover the messages but not the tab strip
     * or input bar — switching tabs and typing still work normally.
     */
    private fun findChatAreaBounds(): Rectangle? {
        val w: Widget? = runCatching { client.getWidget(InterfaceID.Chatbox.CHATAREA) }.getOrNull()
        if (w != null && !w.isHidden) {
            val loc = w.canvasLocation ?: return null
            return Rectangle(loc.x, loc.y, w.width, w.height)
        }
        return null
    }

    // ----------------- input listeners -----------------

    val mouseListener: MouseListener = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent): MouseEvent {
            if (state.get() is AssistantOverlayState.Hidden) return e
            if (!renderedBounds.contains(e.point)) return e
            // Dismiss button (screen coords)?
            if (dismissBtnBounds.contains(e.point)) {
                state.set(AssistantOverlayState.Hidden)
                invalidate()
                e.consume()
                return e
            }
            // Body area (screen coords)?
            val tree = bodyTreeForClick
            if (tree != null && bodyClipRect.contains(e.point)) {
                val localX = e.point.x - bodyClipRect.x
                val localY = e.point.y - bodyClipRect.y + scrollY
                if (tree.onClick(localX, localY, ctx)) e.consume()
            }
            lastInteractionMs = System.currentTimeMillis()
            return e
        }
    }

    val mouseWheelListener: MouseWheelListener = MouseWheelListener { e: MouseWheelEvent ->
        if (state.get() is AssistantOverlayState.Hidden) return@MouseWheelListener e
        if (!renderedBounds.contains(e.point)) return@MouseWheelListener e
        if (bodyContentHeight <= bodyClipRect.height) return@MouseWheelListener e
        val step = if (e.scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL) e.unitsToScroll * 12 else e.wheelRotation * 36
        val maxScroll = (bodyContentHeight - bodyClipRect.height).coerceAtLeast(0)
        scrollY = (scrollY + step).coerceIn(0, maxScroll)
        lastInteractionMs = System.currentTimeMillis()
        e.consume()
        e
    }

    /**
     * Esc dismisses the overlay when it's visible. We only react when the chatbox text
     * input ISN'T open — otherwise Esc has its own meaning there (cancel input).
     */
    val keyListener: KeyListener = object : KeyListener {
        override fun keyTyped(e: KeyEvent) {}
        override fun keyReleased(e: KeyEvent) {}
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode != KeyEvent.VK_ESCAPE) return
            if (state.get() is AssistantOverlayState.Hidden) return
            state.set(AssistantOverlayState.Hidden)
            invalidate()
        }
        override fun isEnabledOnLoginScreen(): Boolean = false
    }

    // ----------------- utilities -----------------

    private fun truncate(s: String, fm: java.awt.FontMetrics, maxW: Int): String {
        if (fm.stringWidth(s) <= maxW) return s
        var lo = 0
        var hi = s.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fm.stringWidth(s.take(mid) + "…") <= maxW) lo = mid else hi = mid - 1
        }
        return s.take(lo) + "…"
    }

    private fun dots(startedAtMs: Long): String {
        val cycle = ((System.currentTimeMillis() - startedAtMs) / 400L % 4L).toInt()
        return ".".repeat(cycle).padEnd(3)
    }

    companion object {
        private const val PAD = 8
        private const val SCROLLBAR_W = 4
        /** Auto-hide a finished response after this much idle time. */
        private const val AUTO_DISMISS_MS = 120_000L
        /** VarClientID.CHAT_VIEW value when the AI (Trade) tab is the active view. */
        private const val CHAT_VIEW_TRADE = 6
    }
}
