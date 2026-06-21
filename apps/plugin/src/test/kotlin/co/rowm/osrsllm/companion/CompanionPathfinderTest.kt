package co.rowm.osrsllm.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A* sanity. The pathfinder is the floor for "the companion catches up";
 * if these fail the renderer will see stale tile snapshots.
 */
class CompanionPathfinderTest {

    /** Open 11x11 grid: every tile walkable, plane 0. */
    private val open: WalkableTest = WalkableTest { _, _, _ -> true }

    @Test
    fun `degenerate same source and target returns singleton path`() {
        val pathfinder = CompanionPathfinder(walkable = open)
        val from = CompanionTile(0, 0, 0)
        val path = pathfinder.findPath(from, from)
        assertNotNull("expected non-null path", path)
        assertEquals(listOf(from), path)
    }

    @Test
    fun `shortest path on open grid is chebyshev distance plus one`() {
        val pathfinder = CompanionPathfinder(walkable = open)
        val from = CompanionTile(0, 0, 0)
        val to = CompanionTile(4, 3, 0)
        val path = pathfinder.findPath(from, to)
        assertNotNull(path)
        // Diagonal allowed so chebyshev = max(4, 3) = 4 steps, 5 tiles inclusive.
        assertEquals(5, path!!.size)
        assertEquals(from, path.first())
        assertEquals(to, path.last())
    }

    @Test
    fun `different plane returns null`() {
        val pathfinder = CompanionPathfinder(walkable = open)
        val path = pathfinder.findPath(
            CompanionTile(0, 0, 0),
            CompanionTile(0, 0, 1),
        )
        assertNull(path)
    }

    @Test
    fun `target outside chase radius returns null`() {
        val pathfinder = CompanionPathfinder(walkable = open, maxChaseTiles = 4)
        val path = pathfinder.findPath(
            CompanionTile(0, 0, 0),
            CompanionTile(10, 0, 0),
        )
        assertNull(path)
    }

    @Test
    fun `blocked target returns null`() {
        val blocker = WalkableTest { x, y, _ -> !(x == 3 && y == 3) }
        val pathfinder = CompanionPathfinder(walkable = blocker)
        val path = pathfinder.findPath(
            CompanionTile(0, 0, 0),
            CompanionTile(3, 3, 0),
        )
        assertNull(path)
    }

    @Test
    fun `wall forces detour`() {
        // Vertical wall on x=2 between y=0..5; target is past the wall.
        val walled = WalkableTest { x, y, _ ->
            !(x == 2 && y in 0..5)
        }
        val pathfinder = CompanionPathfinder(walkable = walled)
        val from = CompanionTile(0, 2, 0)
        val to = CompanionTile(4, 2, 0)
        val path = pathfinder.findPath(from, to)
        assertNotNull("expected a detour path", path)
        // Cannot cross x=2 directly at y=2 - the path must dip around.
        assertTrue(
            "path must avoid (2, 0..5)",
            path!!.none { it.x == 2 && it.y in 0..5 },
        )
    }

    @Test
    fun `nextStep returns second tile of path`() {
        val pathfinder = CompanionPathfinder(walkable = open)
        val from = CompanionTile(0, 0, 0)
        val to = CompanionTile(3, 0, 0)
        val next = pathfinder.nextStep(from, to)
        assertNotNull(next)
        assertEquals(CompanionTile(1, 0, 0), next)
    }

    @Test
    fun `direction fromDelta classifies eight buckets`() {
        assertEquals(Direction.EAST, Direction.fromDelta(1, 0))
        assertEquals(Direction.WEST, Direction.fromDelta(-1, 0))
        assertEquals(Direction.NORTH, Direction.fromDelta(0, 1))
        assertEquals(Direction.SOUTH, Direction.fromDelta(0, -1))
        assertEquals(Direction.NORTH_EAST, Direction.fromDelta(1, 1))
        assertEquals(Direction.SOUTH_WEST, Direction.fromDelta(-1, -1))
    }

    @Test
    fun `followOffset prefers tile behind player along facing`() {
        // Player facing NORTH at (5, 5). Follow tile should be south of player.
        val tile = CompanionFollowOffset.followTile(
            playerTile = CompanionTile(5, 5, 0),
            facing = Direction.NORTH,
            walkable = open,
            followTiles = 3,
        )
        assertEquals(CompanionTile(5, 2, 0), tile)
    }

    @Test
    fun `followOffset falls back to closer tile when preferred is blocked`() {
        // Player facing NORTH; tile (5, 2) blocked but (5, 3) ok.
        val walkable = WalkableTest { x, y, _ -> !(x == 5 && y == 2) }
        val tile = CompanionFollowOffset.followTile(
            playerTile = CompanionTile(5, 5, 0),
            facing = Direction.NORTH,
            walkable = walkable,
            followTiles = 3,
        )
        assertEquals(CompanionTile(5, 3, 0), tile)
    }
}
