package co.rowm.osrsllm.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The follower drives interpolation between integer tiles. Renderer
 * correctness depends on it advancing smoothly across the 600 ms tick
 * window and falling back to fade-respawn on out-of-range targets.
 */
class CompanionPathFollowerTest {

    private val walkable: WalkableTest = WalkableTest { _, _, _ -> true }
    private val pathfinder = CompanionPathfinder(walkable = walkable)

    @Test
    fun `teleport initialises companion in place`() {
        val follower = CompanionPathFollower(pathfinder, walkable)
        follower.teleport(CompanionTile(3, 3, 0), Direction.NORTH)
        val snapshot = follower.advance(0L, AnimationState.Idle)
        assertTrue(snapshot.visible)
        assertEquals(CompanionTile(3, 3, 0), snapshot.tile)
    }

    @Test
    fun `retarget to adjacent tile interpolates over tick window`() {
        val follower = CompanionPathFollower(
            pathfinder = pathfinder,
            walkable = walkable,
            tickDurationMs = 600L,
        )
        follower.teleport(CompanionTile(0, 0, 0))
        follower.retarget(CompanionTile(1, 0, 0), 0L)
        // Halfway through the tick the subTileOffset should be ~0.5.
        val mid = follower.advance(300L, AnimationState.Walking)
        assertTrue(mid.subTileOffset.dx in 0.4f..0.6f)
        // End of tick - companion is now on the target tile.
        val end = follower.advance(600L, AnimationState.Walking)
        assertEquals(CompanionTile(1, 0, 0), end.tile)
    }

    @Test
    fun `retarget to distant tile triggers fade out`() {
        val follower = CompanionPathFollower(
            pathfinder = pathfinder,
            walkable = walkable,
            teleportThreshold = 4,
            fadeDurationMs = 200L,
        )
        follower.teleport(CompanionTile(0, 0, 0))
        follower.retarget(CompanionTile(50, 50, 0), 0L)
        val midFade = follower.advance(100L, AnimationState.Walking)
        assertTrue("opacity must dip below 1 during fade-out", midFade.opacity < 1f)
        // After fade-out completes companion jumps to target and starts fade-in.
        val afterFadeOut = follower.advance(300L, AnimationState.Walking)
        assertEquals(CompanionTile(50, 50, 0), afterFadeOut.tile)
    }

    @Test
    fun `retarget to same tile is a noop`() {
        val follower = CompanionPathFollower(pathfinder, walkable)
        follower.teleport(CompanionTile(5, 5, 0))
        val first = follower.retarget(CompanionTile(5, 5, 0), 0L)
        assertTrue(
            "first retarget after teleport reuses the seed target, so no change is needed",
            !first,
        )
    }
}
