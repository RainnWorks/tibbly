package co.rowm.osrsllm.pathfinder

import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public entry point for tile-distance pathfinding using shortest-path's collision
 * data.
 *
 * The collision map (`/collision-map.zip` on the classpath, ~1.16 MB) is loaded
 * lazily on first call to avoid a startup hit when the player isn't using the
 * transport tool. Subsequent calls are fast — the map is held in memory.
 *
 * Returns null when:
 *  - The collision map is missing (run `./gradlew refreshCollisionMap` to fetch)
 *  - Source/target are on different planes (no walk path without stairs/ladders)
 *  - BFS exceeds its node budget (likely unreachable or too far)
 */
@Singleton
class PathfinderService @Inject constructor() {

    private val log = LoggerFactory.getLogger(PathfinderService::class.java)

    private val pathfinder: WalkPathfinder? by lazy { loadPathfinder() }

    private fun loadPathfinder(): WalkPathfinder? {
        val stream = javaClass.classLoader.getResourceAsStream("collision-map.zip")
            ?: run {
                log.warn("collision-map.zip missing from classpath — walk pathfinding disabled. " +
                    "Run `./gradlew refreshCollisionMap` to fetch it.")
                return null
            }
        return try {
            val start = System.currentTimeMillis()
            val map = SplitFlagMap.fromZip(stream)
            val pf = WalkPathfinder(CollisionMap(map))
            log.info("Pathfinder ready ({} ms)", System.currentTimeMillis() - start)
            pf
        } catch (t: Throwable) {
            log.error("Failed to load collision map; walk pathfinding disabled", t)
            null
        }
    }

    /**
     * Tile-step walk distance between two world points. 8-connected, obstacles
     * respected. Returns null when the source/target are on different planes,
     * pathfinding is disabled, or the BFS budget was exhausted.
     */
    fun walkTiles(fromX: Int, fromY: Int, fromPlane: Int, toX: Int, toY: Int, toPlane: Int): Int? {
        if (fromPlane != toPlane) return null
        val pf = pathfinder ?: return null
        return pf.walk(fromX, fromY, toX, toY, fromPlane)
    }

    /**
     * Approximate run-ticks to walk the path: distance ÷ 2 (player runs 2 tiles per
     * tick with energy). Ceiling, so a 3-tile path is 2 ticks. Returns null on
     * pathfinding failure — caller can fall back to a cruder estimate.
     */
    fun runTicks(fromX: Int, fromY: Int, fromPlane: Int, toX: Int, toY: Int, toPlane: Int): Int? {
        val tiles = walkTiles(fromX, fromY, fromPlane, toX, toY, toPlane) ?: return null
        return (tiles + 1) / 2
    }

    /**
     * Reconstruct the actual tile sequence the player would walk through to reach
     * the destination. Used by the tile-marker overlay to draw the route on the
     * ground. Returns null on the same conditions as [walkTiles].
     */
    fun walkPath(fromX: Int, fromY: Int, fromPlane: Int,
                 toX: Int, toY: Int, toPlane: Int): List<TilePoint>? {
        if (fromPlane != toPlane) return null
        val pf = pathfinder ?: return null
        return pf.walkPath(fromX, fromY, toX, toY, fromPlane)
    }
}
