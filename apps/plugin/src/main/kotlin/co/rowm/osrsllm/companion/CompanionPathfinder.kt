package co.rowm.osrsllm.companion

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max

/**
 * Coordinates the companion paths against. Mirrors
 * [co.rowm.osrsllm.pathfinder.TilePoint] but kept private to the
 * companion package so the renderer + tests don't drag the
 * shortest-path collision map into scope.
 */
data class CompanionTile(val x: Int, val y: Int, val plane: Int)

/**
 * Abstraction over "can the companion stand on this tile". The renderer
 * supplies a real implementation backed by RuneLite's
 * `CollisionData` (via `Client.getCollisionMaps()`); tests supply a
 * grid bitmap so the pathfinder can be exercised deterministically.
 */
fun interface WalkableTest {
    fun isWalkable(x: Int, y: Int, plane: Int): Boolean
}

/**
 * A* search from a companion tile to a target tile. 8-connected. Pure
 * data: no Client, no graphics, no game-tick coupling. The companion
 * state machine drives recomputation; this class only finds paths.
 *
 * The search is intentionally capped at a small node budget so a
 * fairy-ring or teleport (which would put the target hundreds of tiles
 * away) returns null fast and the renderer falls back to the
 * fade-and-respawn path instead of chewing CPU on an unreachable goal.
 *
 * The cap also matters during tests: if a future refactor accidentally
 * makes "every tile walkable" we still terminate.
 */
class CompanionPathfinder(
    private val walkable: WalkableTest,
    private val maxNodes: Int = DEFAULT_MAX_NODES,
    /** Max chase distance before the renderer should fade instead. */
    private val maxChaseTiles: Int = DEFAULT_MAX_CHASE_TILES,
) {

    /**
     * Compute a path from [from] to [to] inclusive of both endpoints, or
     * null when:
     *  - the two tiles are on different planes (no walk path)
     *  - the target is more than [maxChaseTiles] tiles away (caller should
     *    fade-and-respawn instead)
     *  - the target is not walkable
     *  - the node budget is exhausted
     *
     * The returned list always starts at [from] and ends at [to] when
     * non-null. Length is at most [maxChaseTiles] + 1.
     */
    fun findPath(from: CompanionTile, to: CompanionTile): List<CompanionTile>? {
        if (from.plane != to.plane) return null
        if (from == to) return listOf(from)
        val chebyshev = max(abs(from.x - to.x), abs(from.y - to.y))
        if (chebyshev > maxChaseTiles) return null
        if (!walkable.isWalkable(to.x, to.y, to.plane)) return null

        // Encode (x, y) as a packed Long to keep the maps tight.
        val startKey = packKey(from.x, from.y)
        val targetKey = packKey(to.x, to.y)

        val gScore = HashMap<Long, Int>()
        val cameFrom = HashMap<Long, Long>()
        val open = PriorityQueue<Node>(compareBy { it.fScore })
        gScore[startKey] = 0
        open += Node(startKey, from.x, from.y, 0, heuristic(from.x, from.y, to.x, to.y))

        var visited = 0
        while (open.isNotEmpty()) {
            val current = open.poll()
            if (current.key == targetKey) {
                return reconstruct(cameFrom, startKey, targetKey, from.plane)
            }
            visited++
            if (visited > maxNodes) return null
            for (step in NEIGHBOURS) {
                val nx = current.x + step.first
                val ny = current.y + step.second
                if (!walkable.isWalkable(nx, ny, from.plane)) continue
                val tentative = (gScore[current.key] ?: continue) + costOf(step)
                val nKey = packKey(nx, ny)
                val existing = gScore[nKey]
                if (existing == null || tentative < existing) {
                    gScore[nKey] = tentative
                    cameFrom[nKey] = current.key
                    val f = tentative + heuristic(nx, ny, to.x, to.y)
                    open += Node(nKey, nx, ny, tentative, f)
                }
            }
        }
        return null
    }

    /**
     * Pick the next tile the companion should step into to follow the
     * player. Returns null when no path exists; the renderer treats that
     * as a fade-and-respawn signal.
     *
     * The returned tile is the second element of the path (the first is
     * `from` itself); when the companion is already on `target` we return
     * `target` so the caller can short-circuit.
     */
    fun nextStep(from: CompanionTile, target: CompanionTile): CompanionTile? {
        if (from == target) return target
        val path = findPath(from, target) ?: return null
        return path.getOrNull(1) ?: target
    }

    /** Reconstruct the path from the cameFrom map. */
    private fun reconstruct(
        cameFrom: Map<Long, Long>,
        startKey: Long,
        targetKey: Long,
        plane: Int,
    ): List<CompanionTile> {
        val reverse = ArrayList<CompanionTile>(32)
        var cursor = targetKey
        while (true) {
            val (x, y) = unpackKey(cursor)
            reverse += CompanionTile(x, y, plane)
            if (cursor == startKey) break
            cursor = cameFrom[cursor] ?: break
        }
        reverse.reverse()
        return reverse
    }

    /**
     * Chebyshev distance, scaled to 10/14 so diagonal steps are penalised
     * to match an 8-connected grid where diagonals cost sqrt(2). Keeps the
     * search monotone in path length.
     */
    private fun heuristic(ax: Int, ay: Int, bx: Int, by: Int): Int {
        val dx = abs(ax - bx)
        val dy = abs(ay - by)
        return COST_STRAIGHT * (dx + dy) + (COST_DIAG - 2 * COST_STRAIGHT) * kotlin.math.min(dx, dy)
    }

    private fun costOf(step: Pair<Int, Int>): Int =
        if (step.first == 0 || step.second == 0) COST_STRAIGHT else COST_DIAG

    private data class Node(val key: Long, val x: Int, val y: Int, val gScore: Int, val fScore: Int)

    companion object {
        private const val DEFAULT_MAX_NODES = 4_000
        private const val DEFAULT_MAX_CHASE_TILES = 16
        private const val COST_STRAIGHT = 10
        private const val COST_DIAG = 14

        private val NEIGHBOURS: List<Pair<Int, Int>> = listOf(
            0 to 1, 1 to 1, 1 to 0, 1 to -1,
            0 to -1, -1 to -1, -1 to 0, -1 to 1,
        )

        /** Pack two ints into a Long, keeping negatives safe. */
        internal fun packKey(x: Int, y: Int): Long =
            (x.toLong() and 0xFFFFFFFFL) shl 32 or (y.toLong() and 0xFFFFFFFFL)

        internal fun unpackKey(key: Long): Pair<Int, Int> {
            val x = (key ushr 32).toInt()
            val y = (key and 0xFFFFFFFFL).toInt()
            return x to y
        }
    }
}

/**
 * Default offset behind the player where the companion likes to stand.
 *
 * The companion tries to keep three tiles between itself and the player
 * along the player's most recent direction of travel. When the player
 * is standing still the offset stays "behind" relative to the direction
 * the player most recently moved. Computed pure-function so the
 * renderer + state machine can both call it without a Client.
 */
object CompanionFollowOffset {
    const val DEFAULT_FOLLOW_TILES: Int = 3

    /**
     * Compute the follow tile the companion would prefer to stand on,
     * given the player's tile, the direction the player last moved, and
     * a [walkable] predicate to fall back to adjacent tiles when the
     * preferred slot is blocked.
     */
    fun followTile(
        playerTile: CompanionTile,
        facing: Direction,
        walkable: WalkableTest,
        followTiles: Int = DEFAULT_FOLLOW_TILES,
    ): CompanionTile {
        val behind = Direction.fromDelta(-facing.dx, -facing.dy)
        val preferred = CompanionTile(
            x = playerTile.x + behind.dx * followTiles,
            y = playerTile.y + behind.dy * followTiles,
            plane = playerTile.plane,
        )
        if (walkable.isWalkable(preferred.x, preferred.y, preferred.plane)) return preferred
        // Walk inward one tile at a time so we never land further from the
        // player than the spec says. Stops at distance 1 because zero would
        // land the companion ON the player's tile.
        for (d in (followTiles - 1) downTo 1) {
            val candidate = CompanionTile(
                x = playerTile.x + behind.dx * d,
                y = playerTile.y + behind.dy * d,
                plane = playerTile.plane,
            )
            if (walkable.isWalkable(candidate.x, candidate.y, candidate.plane)) return candidate
        }
        // Last resort: stand on the player's tile. Renderer reads this as a
        // "snap to player" signal.
        return playerTile
    }
}
