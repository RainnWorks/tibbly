package co.rowm.osrsllm.companion

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renderer + atlas smoke. We don't load a Client; the renderer exposes a
 * [CompanionRenderer.CanvasResolver] seam so we can drive it
 * deterministically. Validates:
 *
 *  - placeholder atlas serves a sprite for every (state, direction).
 *  - renderer publishes the canvas anchor + click bounds after a paint.
 *  - hidden snapshot draws nothing and clears the anchor.
 */
class CompanionRendererTest {

    private fun buildGraphics(): Pair<BufferedImage, Graphics2D> {
        val img = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        return img to g
    }

    @Test
    fun `placeholder atlas serves every state and direction`() {
        val atlas = PlaceholderAtlas(Starter.VETERAN)
        for (state in AnimationState.ALL) {
            for (dir in Direction.values()) {
                val animation = atlas.animation(state, dir)
                assertTrue("animation must have at least one frame", animation.frames.isNotEmpty())
                val sprite = atlas.sprite(state, dir, 0)
                assertEquals(PlaceholderAtlas.TILE_PX, sprite.width)
                assertEquals(PlaceholderAtlas.TILE_PX, sprite.height)
            }
        }
    }

    @Test
    fun `loader falls back to placeholder when no resources present`() {
        CompanionAtlasLoader.reset()
        val atlas = CompanionAtlasLoader.load(Starter.VETERAN)
        assertTrue("expected placeholder atlas; got ${atlas::class.simpleName}",
            atlas is PlaceholderAtlas)
    }

    @Test
    fun `visible snapshot publishes canvas anchor + click bounds`() {
        val state = CompanionRuntimeState()
        state.update(
            CompanionRenderer.Snapshot(
                visible = true,
                tile = CompanionTile(5, 5, 0),
                subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                direction = Direction.SOUTH,
                state = AnimationState.Idle,
                animationElapsedMs = 0L,
                opacity = 1f,
            ),
        )
        val resolver = CompanionRenderer.CanvasResolver { _, _ ->
            CompanionRenderer.RenderPoint(100, 150)
        }
        val renderer = CompanionRenderer(
            client = stubClient(),
            atlasProvider = { PlaceholderAtlas(Starter.FOX) },
            runtimeState = state,
            canvasResolver = resolver,
        )
        val (_, g) = buildGraphics()
        renderer.render(g)
        g.dispose()
        val anchor = renderer.lastCanvasAnchor
        assertNotNull("anchor must be published", anchor)
        // Speech bubble anchors above the sprite, so y = canvas - tile size.
        assertEquals(150 - PlaceholderAtlas.TILE_PX, anchor!!.y)
        assertEquals(100, anchor.x)
        val bounds = renderer.lastClickBounds
        assertNotNull(bounds)
        assertEquals(PlaceholderAtlas.TILE_PX, bounds!!.width)
        assertEquals(PlaceholderAtlas.TILE_PX, bounds.height)
        // Hit-test inside the bounds.
        assertTrue(renderer.spriteContains(java.awt.Point(100, 150 - PlaceholderAtlas.TILE_PX / 2)))
        assertTrue(!renderer.spriteContains(java.awt.Point(0, 0)))
    }

    @Test
    fun `hidden snapshot clears anchor`() {
        val state = CompanionRuntimeState()
        // Default snapshot is HIDDEN.
        val resolver = CompanionRenderer.CanvasResolver { _, _ ->
            CompanionRenderer.RenderPoint(50, 50)
        }
        val renderer = CompanionRenderer(
            client = stubClient(),
            atlasProvider = { PlaceholderAtlas(Starter.WISP) },
            runtimeState = state,
            canvasResolver = resolver,
        )
        val (_, g) = buildGraphics()
        renderer.render(g)
        g.dispose()
        assertNull(renderer.lastCanvasAnchor)
        assertNull(renderer.lastClickBounds)
    }

    @Test
    fun `null resolver result clears anchor`() {
        val state = CompanionRuntimeState()
        state.update(
            CompanionRenderer.Snapshot(
                visible = true,
                tile = CompanionTile(0, 0, 0),
                subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                direction = Direction.NORTH,
                state = AnimationState.Walking,
                animationElapsedMs = 0L,
                opacity = 1f,
            ),
        )
        val resolver = CompanionRenderer.CanvasResolver { _, _ -> null }
        val renderer = CompanionRenderer(
            client = stubClient(),
            atlasProvider = { PlaceholderAtlas(Starter.GOLEM) },
            runtimeState = state,
            canvasResolver = resolver,
        )
        val (_, g) = buildGraphics()
        renderer.render(g)
        g.dispose()
        assertNull(renderer.lastCanvasAnchor)
    }

    /**
     * Tests don't need a real Client - the renderer only touches it via
     * [CompanionRenderer.CanvasResolver], and we inject our own. The
     * field is non-null so we need a placeholder, but it's never
     * dereferenced inside the test code path.
     */
    private fun stubClient(): net.runelite.api.Client =
        java.lang.reflect.Proxy.newProxyInstance(
            net.runelite.api.Client::class.java.classLoader,
            arrayOf(net.runelite.api.Client::class.java),
        ) { _, _, _ -> null } as net.runelite.api.Client
}
