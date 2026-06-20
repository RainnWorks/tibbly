/*
 * Adapted from Skretzo's shortest-path RuneLite plugin.
 * Original source: https://github.com/Skretzo/shortest-path
 * License: BSD-2-Clause. See NOTICE.md for the full attribution.
 *
 * We use the collision-map.zip resource and a minimal subset of their
 * data-structure code (SplitFlagMap, FlagMap, walkability checks). Their full
 * pathfinder includes transport-graph traversal which we don't need — we only
 * want straight-line walk distance between two tiles.
 */
package co.rowm.osrsllm.pathfinder

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.BitSet
import java.util.zip.ZipInputStream

/**
 * Compact bit-grid over a 64×64 region for one or more vertical planes.
 *
 * Two flags per tile:
 *   - flag 0: can move NORTH (i.e. wall on the north side is absent)
 *   - flag 1: can move EAST
 * SOUTH/WEST are derived by sampling the adjacent tile's N/E bits.
 *
 * Storage: 2 bits/tile × 64×64 = 1024 bytes per plane.
 */
internal class FlagMap(
    private val minX: Int,
    private val minY: Int,
    bytes: ByteArray,
) {
    private val flags: BitSet = BitSet.valueOf(bytes)
    val planeCount: Byte = ((flags.size() + PLANE_BITS - 1) / PLANE_BITS).toByte()

    fun get(x: Int, y: Int, z: Int, flag: Int): Boolean {
        if (x < minX || x >= minX + REGION_SIZE) return false
        if (y < minY || y >= minY + REGION_SIZE) return false
        if (z < 0 || z >= planeCount) return false
        return flags.get(index(x, y, z, flag))
    }

    private fun index(x: Int, y: Int, z: Int, flag: Int): Int {
        if (x < minX || x >= minX + REGION_SIZE || y < minY || y >= minY + REGION_SIZE
            || z < 0 || z >= planeCount || flag < 0 || flag >= FLAG_COUNT) {
            throw IndexOutOfBoundsException("[$x,$y,$z,$flag] outside region [$minX..${minX + REGION_SIZE - 1}, $minY..${minY + REGION_SIZE - 1}]")
        }
        return (z * REGION_SIZE * REGION_SIZE + (y - minY) * REGION_SIZE + (x - minX)) * FLAG_COUNT + flag
    }

    companion object {
        const val REGION_SIZE = 64
        const val FLAG_COUNT = 2
        private const val PLANE_BITS = FLAG_COUNT * REGION_SIZE * REGION_SIZE
    }
}

/**
 * Sparse grid of [FlagMap]s, keyed by region coords (x/64, y/64). Loaded from a zip
 * where each entry is named `<regionX>_<regionY>` and contains the packed bits.
 */
internal class SplitFlagMap(
    private val regionMaps: Map<Int, FlagMap>,
    private val extent: RegionExtent,
) {
    data class RegionExtent(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
        val widthInclusive: Int get() = maxX - minX + 1
        val heightInclusive: Int get() = maxY - minY + 1
    }

    fun get(x: Int, y: Int, z: Int, flag: Int): Boolean {
        val regionX = x / FlagMap.REGION_SIZE
        val regionY = y / FlagMap.REGION_SIZE
        if (regionX < extent.minX || regionX > extent.maxX) return false
        if (regionY < extent.minY || regionY > extent.maxY) return false
        val region = regionMaps[packRegion(regionX, regionY)] ?: return false
        return region.get(x, y, z, flag)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SplitFlagMap::class.java)

        fun packRegion(x: Int, y: Int): Int = (x and 0xFFFF) or ((y and 0xFFFF) shl 16)

        fun fromZip(stream: InputStream): SplitFlagMap {
            val regions = HashMap<Int, FlagMap>()
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = 0
            var maxY = 0
            ZipInputStream(stream).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val parts = entry.name.split('_')
                    val rx = parts[0].toInt()
                    val ry = parts[1].toInt()
                    if (rx < minX) minX = rx
                    if (ry < minY) minY = ry
                    if (rx > maxX) maxX = rx
                    if (ry > maxY) maxY = ry
                    regions[packRegion(rx, ry)] =
                        FlagMap(rx * FlagMap.REGION_SIZE, ry * FlagMap.REGION_SIZE, zis.readAllBytes())
                }
            }
            log.info("Loaded collision map: {} regions, extent x=[{}..{}], y=[{}..{}]",
                regions.size, minX, maxX, minY, maxY)
            return SplitFlagMap(regions, RegionExtent(minX, minY, maxX, maxY))
        }
    }
}

/**
 * 8-direction walkability lookups on top of the [SplitFlagMap].
 *
 * `n/s/e/w` are direct flag reads. Diagonals require corner-clipping checks — you
 * can only step NE if you can also step N then E (no clipping a wall corner).
 */
internal class CollisionMap(private val data: SplitFlagMap) {
    fun n(x: Int, y: Int, z: Int): Boolean = data.get(x, y, z, 0)
    fun s(x: Int, y: Int, z: Int): Boolean = n(x, y - 1, z)
    fun e(x: Int, y: Int, z: Int): Boolean = data.get(x, y, z, 1)
    fun w(x: Int, y: Int, z: Int): Boolean = e(x - 1, y, z)

    fun ne(x: Int, y: Int, z: Int): Boolean =
        n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z)

    fun nw(x: Int, y: Int, z: Int): Boolean =
        n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z)

    fun se(x: Int, y: Int, z: Int): Boolean =
        s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z)

    fun sw(x: Int, y: Int, z: Int): Boolean =
        s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z)

    /**
     * A tile is "blocked" if no edges leave it. Useful to skip nodes during BFS:
     * a fully-blocked tile is unreachable, so don't even enqueue it.
     */
    fun isBlocked(x: Int, y: Int, z: Int): Boolean =
        !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z)
}
