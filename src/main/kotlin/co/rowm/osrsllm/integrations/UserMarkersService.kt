package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads user-flagged in-game state stashed by sibling RuneLite plugins:
 *
 *  - **Ground Marker** (`groundMarker.region_<id>`) — tiles the user has flagged.
 *  - **Object Indicators** (`objectindicators.region_<id>`) — objects the user
 *    has highlighted (resource nodes, doors, doors with traps, etc.).
 *  - **NPC Indicators** (`npcindicators.*`) — NPC names/ids the user always tags.
 *  - **Notes** (`notes.notesData`) — free-text notes the user wrote.
 *
 * Surfaces this state read-only so the agent knows what the user already cares
 * about and can answer "show me my flagged spots", "what did I write down
 * about prep for X", etc.
 *
 * Decode happens lazily on each call — these configs are rarely large and
 * change infrequently. Region IDs decode to absolute world coordinates using
 * RuneLite's standard packing: `regionId = (worldX>>6)<<8 | (worldY>>6)`.
 */
@Singleton
class UserMarkersService @Inject constructor(
    private val configManager: ConfigManager,
) {

    private val log = LoggerFactory.getLogger(UserMarkersService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TileMarker(
        val x: Int, val y: Int, val plane: Int,
        val color: String? = null,
        val label: String? = null,
    )

    @Serializable
    data class ObjectMarker(
        val id: Int,
        val name: String?,
        val x: Int, val y: Int, val plane: Int,
        val color: String? = null,
    )

    @Serializable
    data class HighlightedNpcs(
        val byName: List<String>,
        val byId: List<Int>,
    )

    /** Free-text notes from RuneLite's Notes plugin. Empty when nothing written. */
    fun userNotes(): String? = configManager.getConfiguration(NOTES_GROUP, "notesData")
        ?.takeIf { it.isNotBlank() }

    /** Every ground marker the user has placed, across all regions they've visited. */
    fun groundMarkers(): List<TileMarker> = scanRegionConfigs(GROUND_MARKER_GROUP).flatMap { (regionId, body) ->
        decodePoints(body) { obj ->
            val rx = obj["regionX"]?.jsonPrimitive?.intOrNull ?: return@decodePoints null
            val ry = obj["regionY"]?.jsonPrimitive?.intOrNull ?: return@decodePoints null
            val z = obj["z"]?.jsonPrimitive?.intOrNull ?: 0
            val (wx, wy) = regionLocalToWorld(regionId, rx, ry)
            TileMarker(
                x = wx, y = wy, plane = z,
                color = obj["color"]?.jsonPrimitive?.content,
                label = obj["label"]?.jsonPrimitive?.content,
            )
        }
    }

    /** Every object the user has highlighted via the Object Markers plugin. */
    fun objectMarkers(): List<ObjectMarker> = scanRegionConfigs(OBJECT_INDICATORS_GROUP).flatMap { (regionId, body) ->
        decodePoints(body) { obj ->
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@decodePoints null
            val rx = obj["regionX"]?.jsonPrimitive?.intOrNull ?: return@decodePoints null
            val ry = obj["regionY"]?.jsonPrimitive?.intOrNull ?: return@decodePoints null
            val z = obj["z"]?.jsonPrimitive?.intOrNull ?: 0
            val (wx, wy) = regionLocalToWorld(regionId, rx, ry)
            ObjectMarker(
                id = id,
                name = obj["name"]?.jsonPrimitive?.content,
                x = wx, y = wy, plane = z,
                color = obj["color"]?.jsonPrimitive?.content,
            )
        }
    }

    /** NPCs the user has flagged. The plugin keeps name-based and id-based lists separately. */
    fun highlightedNpcs(): HighlightedNpcs {
        val names = configManager.getConfiguration(NPC_INDICATORS_GROUP, "npcToHighlight")
            ?.let { it.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() } }
            ?: emptyList()
        // ID-based tags exist as `tagstyle_<npcId>` keys
        val ids = runCatching {
            configManager.getConfigurationKeys("$NPC_INDICATORS_GROUP.tagstyle_")
                .mapNotNull { it.substringAfterLast("tagstyle_").toIntOrNull() }
        }.getOrDefault(emptyList())
        return HighlightedNpcs(byName = names, byId = ids)
    }

    private fun scanRegionConfigs(group: String): List<Pair<Int, String>> = runCatching {
        configManager.getConfigurationKeys("$group.region_")
            .mapNotNull { fullKey ->
                val regionId = fullKey.substringAfterLast("region_").toIntOrNull()
                    ?: return@mapNotNull null
                val body = configManager.getConfiguration(group, "region_$regionId")
                    ?: return@mapNotNull null
                regionId to body
            }
    }.onFailure { log.debug("scan {} failed: {}", group, it.message) }
        .getOrDefault(emptyList())

    private inline fun <T : Any> decodePoints(
        body: String, mapper: (JsonObject) -> T?,
    ): List<T> = runCatching {
        val arr = json.parseToJsonElement(body) as? JsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { (it as? JsonObject)?.let(mapper) }
    }.onFailure { log.debug("decode failed: {}", it.message) }.getOrDefault(emptyList())

    /** Region (id, regionX, regionY) → absolute world (x, y). 64x64 regions. */
    private fun regionLocalToWorld(regionId: Int, rx: Int, ry: Int): Pair<Int, Int> {
        val baseX = (regionId ushr 8) shl 6
        val baseY = (regionId and 0xFF) shl 6
        return (baseX + rx) to (baseY + ry)
    }

    companion object {
        private const val GROUND_MARKER_GROUP = "groundMarker"
        private const val OBJECT_INDICATORS_GROUP = "objectindicators"
        private const val NPC_INDICATORS_GROUP = "npcindicators"
        private const val NOTES_GROUP = "notes"
    }
}
