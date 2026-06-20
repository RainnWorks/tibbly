package co.rowm.osrsllm.poi

import co.rowm.osrsllm.GameStateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class Poi(
    val type: String,
    val name: String,
    val x: Int,
    val y: Int,
    val plane: Int = 0,
    val notes: String? = null,
)

@Serializable
data class PoiDb(val schema: String = "1.0", val pois: List<Poi> = emptyList())

@Serializable
data class PoiSearchHit(
    val name: String,
    val type: String,
    val x: Int,
    val y: Int,
    val plane: Int,
    /** Chebyshev (max-axis) tile distance from the player. Null if player has no known location. */
    val tileDistance: Int? = null,
    /** Same plane as the player? Cross-plane lookups still report tileDistance but flag this. */
    val samePlane: Boolean = true,
    val notes: String? = null,
)

@Serializable
data class PoiSearchResponse(
    val type: String,
    val fromX: Int? = null,
    val fromY: Int? = null,
    val plane: Int? = null,
    val results: List<PoiSearchHit>,
    val note: String? = null,
)

@Singleton
class PoiService @Inject constructor(
    private val gameStateStore: GameStateStore,
) {

    private val log = LoggerFactory.getLogger(PoiService::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pois: List<Poi> = loadDb()

    init {
        log.info("Loaded {} POIs (banks: {})", pois.size, pois.count { it.type == "bank" })
    }

    fun findNearest(type: String, limit: Int = 5): PoiSearchResponse {
        val snap = gameStateStore.snapshot()
        val player = snap.player
        val fromX = player?.locationX
        val fromY = player?.locationY
        val plane = player?.plane
        val ofType = pois.filter { it.type.equals(type, ignoreCase = true) }
        if (ofType.isEmpty()) {
            return PoiSearchResponse(
                type = type,
                results = emptyList(),
                note = "No POIs of type '$type' in the database. Known types: ${pois.map { it.type }.distinct().sorted()}",
            )
        }
        if (fromX == null || fromY == null) {
            // No player position — return the list without distance ranking
            return PoiSearchResponse(
                type = type,
                fromX = null,
                fromY = null,
                results = ofType.take(limit).map { it.toHitNoDistance() },
                note = "Player position unknown — returning POIs without distance ranking.",
            )
        }
        val ranked = ofType
            .map { poi ->
                val dist = max(abs(poi.x - fromX), abs(poi.y - fromY))
                val samePlane = plane == null || poi.plane == plane
                poi to (dist to samePlane)
            }
            // Same plane first, then by distance
            .sortedWith(compareBy({ !it.second.second }, { it.second.first }))
            .take(limit)
            .map { (poi, distInfo) ->
                PoiSearchHit(
                    name = poi.name,
                    type = poi.type,
                    x = poi.x,
                    y = poi.y,
                    plane = poi.plane,
                    tileDistance = distInfo.first,
                    samePlane = distInfo.second,
                    notes = poi.notes,
                )
            }
        return PoiSearchResponse(
            type = type,
            fromX = fromX,
            fromY = fromY,
            plane = plane,
            results = ranked,
            note = "Distances are straight-line tile distances (Chebyshev). Combine with find_transport(destination=poi.name) to plan the actual route — a far bank with a teleport is usually quicker than a close one on foot.",
        )
    }

    fun knownTypes(): List<String> = pois.map { it.type }.distinct().sorted()

    /** All known POIs (read-only view). */
    fun all(): List<Poi> = pois

    fun searchByName(query: String, limit: Int = 10): List<Poi> {
        val q = query.trim().lowercase().takeIf { it.isNotBlank() } ?: return emptyList()
        return pois.filter { it.name.lowercase().contains(q) }.take(limit)
    }

    private fun Poi.toHitNoDistance() = PoiSearchHit(name, type, x, y, plane, null, true, notes)

    private fun loadDb(): List<Poi> {
        val stream = javaClass.classLoader.getResourceAsStream("pois.json")
            ?: return emptyList()
        return try {
            json.decodeFromString(PoiDb.serializer(), stream.bufferedReader().use { it.readText() })
                .pois
        } catch (t: Throwable) {
            log.error("Failed to parse pois.json", t)
            emptyList()
        }
    }
}
