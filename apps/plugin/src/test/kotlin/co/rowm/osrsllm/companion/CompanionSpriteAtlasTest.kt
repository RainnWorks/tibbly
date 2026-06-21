package co.rowm.osrsllm.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CompanionSpriteAtlas] and its [PlaceholderAtlas] fallback.
 *
 * The placeholder path is the load-bearing one for the test suite: the
 * baked PNG resources may not be present in CI yet (the Quaternius
 * source is vendored manually and the bake runs offline on Tom's box).
 * These tests prove that load() degrades to the placeholder cleanly,
 * that every pose name in the catalog renders without crashing, and
 * that the pose catalog spec stays aligned with the 58-frame Probe
 * atlas defined in docs/product/COMPANION_VISUAL_BIBLE.md section 3.
 */
class CompanionSpriteAtlasTest {

    @Test
    fun `pose catalog covers exactly 58 frames`() {
        val total = CompanionSpriteAtlas.POSE_CATALOG.sumOf { it.second }
        assertEquals(CompanionSpriteAtlas.TOTAL_FRAMES, total)
        assertEquals(58, total)
    }

    @Test
    fun `pose catalog matches the documented atlas layout`() {
        val byName = CompanionSpriteAtlas.POSE_CATALOG.toMap()

        // 8 hover-move directions, 3 frames each
        for (suffix in listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")) {
            assertEquals(3, byName["hover_move_$suffix"])
        }
        // 8 idle-hover directions, 2 frames each
        for (suffix in listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")) {
            assertEquals(2, byName["idle_hover_$suffix"])
        }
        // single-pose entries
        assertEquals(8, byName["scan"])
        assertEquals(2, byName["display_on"])
        assertEquals(2, byName["power_down"])
        assertEquals(2, byName["power_down_extended"])
        assertEquals(2, byName["reaction_rise"])
        assertEquals(2, byName["speak"])
    }

    @Test
    fun `load falls back to placeholder when baked assets are missing`() {
        // The repo ships the placeholder code path active by default
        // until Tom vendors probe.glb and runs the bake. The atlas
        // must still load.
        val atlas = CompanionSpriteAtlas.load(
            CompanionSpriteAtlas.Variant.DEFAULT,
            cellSize = 32,
        )
        assertTrue(
            "atlas should fall back to placeholder when bake output is absent",
            atlas.isPlaceholder,
        )
    }

    @Test
    fun `placeholder renders every pose at 32px`() {
        val atlas = PlaceholderAtlas.build(
            CompanionSpriteAtlas.Variant.DEFAULT,
            cellSize = 32,
        )
        assertTrue(atlas.isPlaceholder)
        for ((poseName, expectedFrames) in CompanionSpriteAtlas.POSE_CATALOG) {
            assertEquals(expectedFrames, atlas.frameCount(poseName))
            for (i in 0 until expectedFrames) {
                val image = atlas.frame(poseName, i)
                assertNotNull("frame missing for $poseName[$i]", image)
                assertEquals(32, image!!.width)
                assertEquals(32, image.height)
            }
        }
    }

    @Test
    fun `placeholder renders every variant at every cell size`() {
        for (variant in CompanionSpriteAtlas.Variant.entries) {
            for (cellSize in listOf(32, 64, 96)) {
                val atlas = PlaceholderAtlas.build(variant, cellSize)
                assertEquals(variant, atlas.variant)
                assertEquals(cellSize, atlas.cellSize)
                for ((poseName, expectedFrames) in CompanionSpriteAtlas.POSE_CATALOG) {
                    assertEquals(
                        "frame count drift for ${variant.resourceKey}@${cellSize}px/$poseName",
                        expectedFrames,
                        atlas.frameCount(poseName),
                    )
                }
            }
        }
    }

    @Test
    fun `frame returns null for unknown pose names`() {
        val atlas = PlaceholderAtlas.build(
            CompanionSpriteAtlas.Variant.DEFAULT,
            cellSize = 32,
        )
        assertNull(atlas.frame("not_a_pose", 0))
        assertNull(atlas.frame("hover_move_n", 99))
        assertNull(atlas.frame("hover_move_n", -1))
    }

    @Test
    fun `placeholder bobs the sprite between adjacent idle frames`() {
        // The placeholder is deterministic enough that two adjacent
        // idle-hover frames produce different pixel buffers. This is
        // the smoke test that the animator's tick loop will actually
        // see motion, not a still PNG, even before the real bake.
        val atlas = PlaceholderAtlas.build(
            CompanionSpriteAtlas.Variant.DEFAULT,
            cellSize = 64,
        )
        val a = atlas.frame("idle_hover_s", 0)
        val b = atlas.frame("idle_hover_s", 1)
        assertNotNull(a)
        assertNotNull(b)
        val aPixels = (a!!).getRGB(0, 0, a.width, a.height, null, 0, a.width)
        val bPixels = (b!!).getRGB(0, 0, b.width, b.height, null, 0, b.width)
        assertFalse(
            "adjacent placeholder idle frames should differ so the animator can tell them apart",
            aPixels.contentEquals(bPixels),
        )
    }

    @Test
    fun `every variant exposes a distinct displayName`() {
        val names = CompanionSpriteAtlas.Variant.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.contains("Probe"))
    }
}
