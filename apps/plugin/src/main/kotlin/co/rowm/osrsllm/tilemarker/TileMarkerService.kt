package co.rowm.osrsllm.tilemarker

import co.rowm.osrsllm.pathfinder.TilePoint
import net.runelite.api.Client
import java.awt.Color
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the AI most recently asked us to point at. Stored so we can verify on
 * each frame that the OSRS hint arrow is still pointing where we set it (vs.
 * the game replacing it with a quest arrow, another plugin setting one, etc).
 *
 * Ownership semantics: if [TileMarkerService.aiHintArrowActive] returns false
 * the AI never set an arrow OR the game has since replaced ours. In either
 * case we DON'T touch [Client.clearHintArrow] — that'd nuke a game-set arrow
 * the user actually wants to follow.
 */
sealed class AiHintArrowRef {
    data class Tile(val x: Int, val y: Int, val plane: Int) : AiHintArrowRef()
    data class Npc(val index: Int) : AiHintArrowRef()
}

/**
 * One marked tile. Drawn as an outlined polygon on the ground by [TileMarkerOverlay].
 */
data class TileMarker(
    val tile: TilePoint,
    val color: Color,
    /** Optional short label drawn at the tile's centre. */
    val label: String? = null,
)

/**
 * Thread-safe holder for "where the AI wants to highlight things on the ground".
 *
 * Two slots:
 *   - `path`: a sequence of tiles (typically from [PathfinderService.walkPath])
 *     rendered as a connected route with the endpoints emphasized.
 *   - `markers`: ad-hoc individual tiles (single destinations, NPC locations, etc.)
 *
 * Both slots are atomic so the agent thread can update from any background
 * worker while the overlay renders on the client thread.
 */
@Singleton
class TileMarkerService @Inject constructor() {

    private val pathRef = AtomicReference<List<TilePoint>>(emptyList())
    private val pathColorRef = AtomicReference<Color>(DEFAULT_PATH_COLOR)
    private val markersRef = AtomicReference<List<TileMarker>>(emptyList())
    private val hintArrowRef = AtomicReference<AiHintArrowRef?>(null)

    fun setPath(tiles: List<TilePoint>, color: Color = DEFAULT_PATH_COLOR) {
        pathRef.set(tiles)
        pathColorRef.set(color)
    }

    fun clearPath() {
        pathRef.set(emptyList())
    }

    fun path(): List<TilePoint> = pathRef.get()
    fun pathColor(): Color = pathColorRef.get()

    fun addMarker(marker: TileMarker) {
        markersRef.updateAndGet { it + marker }
    }

    fun setMarkers(markers: List<TileMarker>) {
        markersRef.set(markers)
    }

    fun clearMarkers() {
        markersRef.set(emptyList())
    }

    fun markers(): List<TileMarker> = markersRef.get()

    /**
     * Record the hint arrow we just told the game to display. Pair with an
     * actual `Client.setHintArrow(...)` call on the client thread — this is
     * only the bookkeeping side.
     */
    fun recordHintArrow(ref: AiHintArrowRef) { hintArrowRef.set(ref) }

    fun forgetHintArrow() { hintArrowRef.set(null) }

    /**
     * True iff the AI most recently set the hint arrow AND the game's current
     * arrow still matches that reference (i.e., we still "own" it). If the
     * game has replaced our arrow we release ownership silently so we don't
     * later clear an arrow we didn't set.
     */
    fun aiHintArrowActive(client: Client): Boolean {
        val ref = hintArrowRef.get() ?: return false
        val matches = when (ref) {
            is AiHintArrowRef.Tile -> {
                val p = client.hintArrowPoint
                p != null && p.x == ref.x && p.y == ref.y && p.plane == ref.plane
            }
            is AiHintArrowRef.Npc -> client.hintArrowNpc?.index == ref.index
        }
        if (!matches) hintArrowRef.set(null)
        return matches
    }

    fun clearAll() {
        clearPath()
        clearMarkers()
        forgetHintArrow()
    }

    companion object {
        val DEFAULT_PATH_COLOR: Color = Color(140, 214, 168, 200)
        val DEFAULT_MARKER_COLOR: Color = Color(232, 174, 78, 220)
    }
}
