package co.rowm.osrsllm.companion

import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point as AwtPoint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import net.runelite.api.Client
import net.runelite.api.Perspective
import net.runelite.api.coords.LocalPoint
import net.runelite.api.coords.WorldPoint
import net.runelite.client.ui.overlay.Overlay
import net.runelite.client.ui.overlay.OverlayLayer
import net.runelite.client.ui.overlay.OverlayPosition

/**
 * Renders the companion sprite onto the OSRS scene.
 *
 * The renderer is deliberately dumb: it asks [CompanionRuntimeState] for
 * the current tile, direction, animation, and elapsed-time, projects the
 * tile to a canvas point, and draws the sprite. State transitions are
 * owned by [CompanionStateMachine]; path interpolation by
 * [CompanionPathfinder] and its caller. This file only paints.
 *
 * Z-order: `ABOVE_SCENE` keeps the sprite above world geometry but below
 * RuneLite's regular widget overlays so the chatbox + minimap still draw
 * cleanly on top. Priority is low so the visuals-control overlays and
 * AssistantOverlay sit on top when both are active.
 *
 * The renderer never touches `EgressGate`; it cannot exfiltrate state.
 * The `:checkCompanionConsentGated` Gradle task verifies this by greping
 * the `companion/` package for any call to `EgressGate.egress` or
 * `egressHttp` that isn't preceded by a `consentAccepted()` check.
 */
class CompanionRenderer(
    private val client: Client,
    private val atlasProvider: () -> CompanionSpriteAtlas,
    private val runtimeState: CompanionRuntimeState,
    private val canvasResolver: CanvasResolver = DefaultCanvasResolver(client),
) : Overlay() {

    init {
        position = OverlayPosition.DYNAMIC
        layer = OverlayLayer.ABOVE_SCENE
        priority = PRIORITY_LOW
    }

    /**
     * Last-resolved canvas anchor for the companion sprite. The speech
     * bubble reads this so it can float above the sprite. Updated on
     * each successful render; null when the companion isn't visible.
     */
    @Volatile var lastCanvasAnchor: AwtPoint? = null
        private set

    /** Last-resolved click target for the sprite. Used by the chat shortcut. */
    @Volatile var lastClickBounds: Rectangle? = null
        private set

    override fun render(graphics: Graphics2D): Dimension? {
        val snapshot = runtimeState.snapshot()
        if (!snapshot.visible) {
            lastCanvasAnchor = null
            lastClickBounds = null
            return null
        }
        val anchor = canvasResolver.resolve(snapshot.tile, snapshot.subTileOffset)
        if (anchor == null) {
            lastCanvasAnchor = null
            lastClickBounds = null
            return null
        }

        val atlas = atlasProvider()
        val direction = snapshot.direction
        val animation = atlas.animation(snapshot.state, direction)
        val frame = animation.frameAt(snapshot.animationElapsedMs)
        val sprite = atlas.sprite(snapshot.state, direction, frame.index)

        val originalComposite = graphics.composite
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            val opacity = snapshot.opacity.coerceIn(0f, 1f)
            if (opacity < 1f) {
                graphics.composite =
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
            }
            drawSprite(graphics, sprite, anchor, atlas.tileSize)
            lastCanvasAnchor = AwtPoint(anchor.x, anchor.y - atlas.tileSize)
            lastClickBounds = Rectangle(
                anchor.x - atlas.tileSize / 2,
                anchor.y - atlas.tileSize,
                atlas.tileSize,
                atlas.tileSize,
            )
        } finally {
            graphics.composite = originalComposite
        }
        return null
    }

    /** Hit-test for the sprite. Used by `CompanionInteractionMouseListener`. */
    fun spriteContains(canvasPoint: AwtPoint): Boolean {
        val bounds = lastClickBounds ?: return false
        return bounds.contains(canvasPoint)
    }

    private fun drawSprite(g: Graphics2D, sprite: BufferedImage, anchor: RenderPoint, tileSize: Int) {
        // The anchor is the tile's centre on the canvas. The sprite's
        // bottom-middle should land at the anchor so it sits "on" the
        // tile instead of floating above it.
        val drawX = anchor.x - tileSize / 2
        val drawY = anchor.y - tileSize
        g.drawImage(sprite, drawX, drawY, null)
    }

    /**
     * Snapshot the renderer reads each frame. Immutable so the producing
     * side (state machine + path follower) can publish atomically and the
     * renderer never sees a half-updated tile.
     */
    data class Snapshot(
        val visible: Boolean,
        val tile: CompanionTile,
        val subTileOffset: SubTileOffset,
        val direction: Direction,
        val state: AnimationState,
        val animationElapsedMs: Long,
        val opacity: Float,
    ) {
        companion object {
            val HIDDEN: Snapshot = Snapshot(
                visible = false,
                tile = CompanionTile(0, 0, 0),
                subTileOffset = SubTileOffset.ZERO,
                direction = Direction.SOUTH,
                state = AnimationState.Idle,
                animationElapsedMs = 0L,
                opacity = 1f,
            )
        }
    }

    /**
     * Sub-tile offset for smooth interpolation between tiles. The caller
     * (path follower) updates this each tick so the companion glides
     * between integer tile coordinates rather than snapping.
     */
    data class SubTileOffset(val dx: Float, val dy: Float) {
        companion object {
            val ZERO: SubTileOffset = SubTileOffset(0f, 0f)
        }
    }

    /**
     * Indirection that lets the renderer be unit-tested without a Client.
     * Default impl uses [Perspective.localToCanvas]; tests inject a
     * deterministic stub.
     */
    fun interface CanvasResolver {
        fun resolve(tile: CompanionTile, subTile: SubTileOffset): RenderPoint?
    }

    /**
     * Plain integer canvas coordinate. Wraps an `x`/`y` so the resolver
     * abstraction doesn't have to leak `net.runelite.api.Point` or
     * `java.awt.Point` types into tests.
     */
    data class RenderPoint(val x: Int, val y: Int)
}

/**
 * Mutable handle the state machine + path follower publish into. The
 * renderer reads [snapshot] each frame; `Volatile` keeps the read cheap.
 * Updates from non-EDT threads (game tick callbacks, animation timers)
 * land here without locking.
 */
class CompanionRuntimeState {
    @Volatile private var current: CompanionRenderer.Snapshot = CompanionRenderer.Snapshot.HIDDEN
    fun snapshot(): CompanionRenderer.Snapshot = current
    fun update(snapshot: CompanionRenderer.Snapshot) {
        current = snapshot
    }
    fun hide() {
        current = CompanionRenderer.Snapshot.HIDDEN
    }
}

/**
 * Default canvas resolver that talks to a live `Client`. Kept as a
 * separate top-level class so the test suite can construct a
 * [CompanionRenderer] with a stub resolver without instantiating one
 * of these.
 */
class DefaultCanvasResolver(private val client: Client) : CompanionRenderer.CanvasResolver {
    override fun resolve(
        tile: CompanionTile,
        subTile: CompanionRenderer.SubTileOffset,
    ): CompanionRenderer.RenderPoint? {
        val world = WorldPoint(tile.x, tile.y, tile.plane)
        val local = LocalPoint.fromWorld(client, world) ?: return null
        // Apply sub-tile interpolation in local-space units. Each local
        // unit is 1/128 of a tile, so a sub-tile offset of [0..1] tiles
        // becomes 0..128 local units. The 4-arg [localToCanvas] overload
        // takes raw (x, y, plane) so we don't have to construct a
        // worldView-aware LocalPoint just to pass it back in.
        val nudgedX = local.x + (subTile.dx * Perspective.LOCAL_TILE_SIZE).toInt()
        val nudgedY = local.y + (subTile.dy * Perspective.LOCAL_TILE_SIZE).toInt()
        val canvas = Perspective.localToCanvas(client, nudgedX, nudgedY, tile.plane) ?: return null
        return CompanionRenderer.RenderPoint(canvas.x, canvas.y)
    }
}
