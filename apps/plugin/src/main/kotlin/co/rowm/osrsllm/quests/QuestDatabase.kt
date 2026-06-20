package co.rowm.osrsllm.quests

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local quest walkthrough database, sourced from
 * `scripts/build_quest_data.py` → `src/main/resources/quest-data.json`.
 *
 * Replaces the 3-4 wiki API roundtrips the agent used to make per quest
 * question (wiki_search → wiki_page("X/Quick guide") → section drill-in)
 * with one in-memory lookup. ~150-200 quests, ~1-2 MB JSON.
 *
 * Loads lazily — first call pays the parse cost (~50ms); after that lookups
 * are ConcurrentHashMap fast.
 */
@Serializable
data class QuestItem(
    val name: String,
    val qty: Int = 1,
    val optional: Boolean = false,
    val note: String? = null,
)

@Serializable
data class QuestStep(
    val section: String,
    val title: String,
    val content: String,
)

@Serializable
data class QuestRecord(
    val name: String,
    val url: String,
    val quickGuideUrl: String,
    val members: Boolean? = null,
    val questPoints: Int? = null,
    val difficulty: String = "",
    val length: String = "",
    val series: String = "",
    val released: String = "",
    val developer: String = "",
    val requirements: String = "",
    val items: List<QuestItem> = emptyList(),
    val itemsRaw: String = "",
    val recommendedRaw: String = "",
    val enemies: String = "",
    val start: String = "",
    val steps: List<QuestStep> = emptyList(),
)

@Singleton
class QuestDatabase @Inject constructor() {

    private val log = LoggerFactory.getLogger(QuestDatabase::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** canonical name (case-preserved) → record */
    private val byName: Map<String, QuestRecord> by lazy { load() }
    /** lowercase index for fuzzy lookup */
    private val byLowerName: Map<String, QuestRecord> by lazy {
        byName.mapKeys { it.key.lowercase() }
    }

    private fun load(): Map<String, QuestRecord> {
        val stream = javaClass.classLoader.getResourceAsStream("quest-data.json")
            ?: run {
                log.warn("quest-data.json missing from classpath — get_quest_data disabled. " +
                    "Run `./gradlew refreshQuestData` (or `python3 scripts/build_quest_data.py`).")
                return emptyMap()
            }
        return try {
            val text = stream.bufferedReader().use { it.readText() }
            val raw: Map<String, QuestRecord> = json.decodeFromString(
                kotlinx.serialization.serializer(), text,
            )
            log.info("Loaded {} quests from quest-data.json", raw.size)
            raw
        } catch (t: Throwable) {
            log.error("Failed to parse quest-data.json", t)
            emptyMap()
        }
    }

    /** Exact name lookup (case-insensitive). */
    fun get(name: String): QuestRecord? {
        val key = name.trim().lowercase()
        byLowerName[key]?.let { return it }
        // Try fuzzy substring match — useful when the agent gets the name slightly wrong.
        return byLowerName.entries.firstOrNull { (k, _) -> k.contains(key) }?.value
    }

    fun list(): List<String> = byName.keys.toList().sorted()

    fun size(): Int = byName.size
}
