/*
 * BFS that returns the minimum number of tile steps between two points on the
 * same plane, walking only — no transports, fairy rings, etc. Diagonal moves
 * count as one step (OSRS's run movement is 8-connected).
 *
 * Algorithmic shape mirrors shortest-path's `Pathfinder` but stripped to pure
 * tile traversal — no transport graph, no wilderness/league checks, no async
 * cancellation. See BSD-2-Clause notice in CollisionMap.kt.
 */
package co.rowm.osrsllm.pathfinder

import org.slf4j.LoggerFactory

/** Tile coord on a specific plane. Public so callers (overlays, MCP tools) can use it. */
public data class TilePoint(val x: Int, val y: Int, val plane: Int)

internal class WalkPathfinder(private val map: CollisionMap) {

    private val log = LoggerFactory.getLogger(WalkPathfinder::class.java)

    /**
     * Reconstruct the actual tile sequence from start to target. Returns null
     * under the same conditions as [walk]. List includes both endpoints; size-1
     * equals the tile distance.
     *
     * Same BFS as [walk] but tracks each tile's predecessor so we can rebuild
     * the path on hit. Slightly more memory than [walk] (the parent map) but
     * still cheap — only matters if you actually need the sequence.
     */
    fun walkPath(
        fromX: Int, fromY: Int, toX: Int, toY: Int, plane: Int,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): List<TilePoint>? {
        if (fromX == toX && fromY == toY) return listOf(TilePoint(fromX, fromY, plane))
        if (map.isBlocked(fromX, fromY, plane)) return null
        if (map.isBlocked(toX, toY, plane)) return null

        val queueX = ArrayDeque<Int>(1024)
        val queueY = ArrayDeque<Int>(1024)
        val visited = HashSet<Long>(2048)
        val parent = HashMap<Long, Long>(2048)

        fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

        queueX.addLast(fromX); queueY.addLast(fromY)
        visited.add(pack(fromX, fromY))

        var explored = 0
        while (queueX.isNotEmpty()) {
            val x = queueX.removeFirst()
            val y = queueY.removeFirst()
            if (++explored > maxNodes) return null

            for (dir in 0 until 8) {
                val (dx, dy, walkable) = neighborOffset(dir, x, y, plane)
                if (!walkable) continue
                val nx = x + dx
                val ny = y + dy
                val key = pack(nx, ny)
                if (!visited.add(key)) continue
                parent[key] = pack(x, y)
                if (nx == toX && ny == toY) {
                    return reconstruct(key, parent, plane)
                }
                queueX.addLast(nx); queueY.addLast(ny)
            }
        }
        return null
    }

    private fun reconstruct(endKey: Long, parent: Map<Long, Long>, plane: Int): List<TilePoint> {
        val path = ArrayList<TilePoint>(64)
        var cur = endKey
        while (true) {
            val cx = (cur shr 32).toInt()
            val cy = cur.toInt()
            path += TilePoint(cx, cy, plane)
            val par = parent[cur] ?: break
            cur = par
        }
        path.reverse()
        return path
    }

    private fun neighborOffset(dir: Int, x: Int, y: Int, plane: Int): Triple<Int, Int, Boolean> =
        when (dir) {
            0 -> Triple(0,  1, map.n(x, y, plane))
            1 -> Triple(0, -1, map.s(x, y, plane))
            2 -> Triple(1,  0, map.e(x, y, plane))
            3 -> Triple(-1, 0, map.w(x, y, plane))
            4 -> Triple(1,  1, map.ne(x, y, plane))
            5 -> Triple(-1, 1, map.nw(x, y, plane))
            6 -> Triple(1, -1, map.se(x, y, plane))
            7 -> Triple(-1,-1, map.sw(x, y, plane))
            else -> Triple(0, 0, false)
        }

    /**
     * Tile-step distance from (fromX,fromY) to (toX,toY) on [plane]. 8-connected.
     *
     * Returns null when:
     *  - source or target tile is fully blocked (no edges)
     *  - the BFS explores [maxNodes] tiles without finding the target (likely
     *    unreachable or too far to be worth knowing exactly)
     */
    fun walk(
        fromX: Int, fromY: Int, toX: Int, toY: Int, plane: Int,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): Int? {
        if (fromX == toX && fromY == toY) return 0
        if (map.isBlocked(fromX, fromY, plane)) return null
        if (map.isBlocked(toX, toY, plane)) return null

        // Parallel ArrayDeques for (x, y) of the frontier + a step-count for each.
        // Using primitive int ops avoids per-node autoboxing on a hot path.
        val queueX = ArrayDeque<Int>(1024)
        val queueY = ArrayDeque<Int>(1024)
        val queueDist = ArrayDeque<Int>(1024)
        val visited = HashSet<Long>(2048)

        fun pack(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

        queueX.addLast(fromX); queueY.addLast(fromY); queueDist.addLast(0)
        visited.add(pack(fromX, fromY))

        var explored = 0
        while (queueX.isNotEmpty()) {
            val x = queueX.removeFirst()
            val y = queueY.removeFirst()
            val d = queueDist.removeFirst()
            explored++
            if (explored > maxNodes) {
                log.debug("BFS gave up after {} nodes, ({},{})→({},{}) p={}",
                    explored, fromX, fromY, toX, toY, plane)
                return null
            }

            // Check each of the 8 neighbors. Each direction has its own walkability
            // predicate from CollisionMap so we respect walls + corner clips.
            for (dir in 0 until 8) {
                val (dx, dy, walkable) = when (dir) {
                    0 -> Triple(0,  1, map.n(x, y, plane))
                    1 -> Triple(0, -1, map.s(x, y, plane))
                    2 -> Triple(1,  0, map.e(x, y, plane))
                    3 -> Triple(-1, 0, map.w(x, y, plane))
                    4 -> Triple(1,  1, map.ne(x, y, plane))
                    5 -> Triple(-1, 1, map.nw(x, y, plane))
                    6 -> Triple(1, -1, map.se(x, y, plane))
                    7 -> Triple(-1,-1, map.sw(x, y, plane))
                    else -> Triple(0, 0, false)
                }
                if (!walkable) continue
                val nx = x + dx
                val ny = y + dy
                if (nx == toX && ny == toY) return d + 1
                val key = pack(nx, ny)
                if (!visited.add(key)) continue
                queueX.addLast(nx); queueY.addLast(ny); queueDist.addLast(d + 1)
            }
        }
        return null
    }

    companion object {
        /**
         * Hard cap on BFS exploration. ~5000 tiles ≈ a 70-tile-radius search area which
         * covers any reasonable "should I walk?" decision. Beyond that the teleport is
         * always faster anyway.
         */
        const val DEFAULT_MAX_NODES = 5000
    }
}
