package co.rowm.osrsllm.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PricedItem(
    val id: Int,
    val name: String,
    val highPrice: Long?,
    val highMinutesAgo: Long?,
    val lowPrice: Long?,
    val lowMinutesAgo: Long?,
    val avgPrice: Long?,
)

@Serializable
data class PriceLookupResponse(
    val query: String,
    val source: String,
    val items: List<PricedItem>,
    val note: String? = null,
)

/**
 * Looks up live OSRS Grand Exchange prices using the wiki realtime API
 * (https://prices.runescape.wiki). Item names → IDs are resolved via the
 * already-loaded ItemManager, then we fan out per-ID HTTPS calls in parallel.
 */
@Singleton
class PriceService @Inject constructor(private val itemManager: ItemManager) {

    private val log = LoggerFactory.getLogger(PriceService::class.java)
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val userAgent = "osrs-llm-helper/0.1 (RuneLite plugin; thomas@rowm.co)"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun lookup(query: String, limit: Int = 5): PriceLookupResponse {
        val capped = limit.coerceIn(1, 15)
        log.info("ge_price: query='{}' limit={}", query, capped)

        val matches = runCatching { itemManager.search(query) }
            .getOrElse {
                log.warn("ItemManager.search failed: {}", it.message)
                emptyList()
            }
            .take(capped)

        if (matches.isEmpty()) {
            return PriceLookupResponse(
                query = query,
                source = "wiki-realtime",
                items = emptyList(),
                note = "No tradeable items match '$query'.",
            )
        }

        val now = System.currentTimeMillis()
        // Parallel fan-out per id. The wiki API is fast (~50-200ms) so this stays
        // well under a second for a typical handful of matches.
        val futures = matches.map { ip ->
            CompletableFuture.supplyAsync { fetchOne(ip.id) }
        }
        val priced = matches.mapIndexed { idx, ip ->
            val raw = runCatching { futures[idx].get(8, TimeUnit.SECONDS) }
                .getOrElse {
                    log.warn("fetchOne({}) failed: {}", ip.id, it.message)
                    null
                }
            val avg = if (raw?.high != null && raw.low != null) (raw.high + raw.low) / 2 else null
            PricedItem(
                id = ip.id,
                name = ip.name,
                highPrice = raw?.high,
                highMinutesAgo = raw?.highTime?.let { (now / 1000L - it) / 60 },
                lowPrice = raw?.low,
                lowMinutesAgo = raw?.lowTime?.let { (now / 1000L - it) / 60 },
                avgPrice = avg,
            )
        }

        return PriceLookupResponse(
            query = query,
            source = "wiki-realtime (oldschool.runescape.wiki)",
            items = priced,
        )
    }

    private fun fetchOne(id: Int): RawPrice? {
        val url = "https://prices.runescape.wiki/api/v1/osrs/latest?id=$id"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            log.warn("Wiki price HTTP {} for id={}: {}", resp.statusCode(), id, resp.body().take(120))
            return null
        }
        val root = json.parseToJsonElement(resp.body()).jsonObject
        val data = root["data"]?.jsonObject ?: return null
        val entry = data[id.toString()]?.jsonObject ?: return null
        return RawPrice(
            high = entry["high"]?.jsonPrimitive?.longOrNull,
            highTime = entry["highTime"]?.jsonPrimitive?.longOrNull,
            low = entry["low"]?.jsonPrimitive?.longOrNull,
            lowTime = entry["lowTime"]?.jsonPrimitive?.longOrNull,
        )
    }

    private data class RawPrice(
        val high: Long?,
        val highTime: Long?,
        val low: Long?,
        val lowTime: Long?,
    )
}
