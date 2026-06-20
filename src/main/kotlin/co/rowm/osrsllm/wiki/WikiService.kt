package co.rowm.osrsllm.wiki

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Serializable
data class WikiSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
)

@Serializable
data class WikiSearchResponse(
    val query: String,
    val totalHits: Int,
    val hits: List<WikiSearchHit>,
)

@Serializable
data class WikiSection(val index: String, val title: String, val level: Int)

@Serializable
data class WikiPageResponse(
    val title: String,
    val url: String,
    val sections: List<WikiSection>,
    val section: String? = null,
    val content: String,
    val truncated: Boolean,
)

class WikiService {

    private val log = LoggerFactory.getLogger(WikiService::class.java)
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val userAgent = "osrs-llm-helper/0.1 (RuneLite plugin; thomas@rowm.co)"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Search the wiki with snippets. The snippet field often contains enough context
     * to answer common questions without a follow-up page fetch.
     */
    fun search(query: String, limit: Int = 5): WikiSearchResponse {
        val capped = limit.coerceIn(1, 20)
        val url = "$BASE?action=query&list=search&srsearch=${enc(query)}" +
            "&srlimit=$capped&srprop=snippet&format=json"
        log.info("wiki_search: query='{}' limit={}", query, capped)
        val body = httpGet(url, Duration.ofSeconds(8))
        val root = json.parseToJsonElement(body).jsonObject
        val q = root["query"]?.jsonObject
            ?: return WikiSearchResponse(query, 0, emptyList())
        val totalHits = q["searchinfo"]?.jsonObject?.get("totalhits")?.jsonPrimitive?.intOrNull ?: 0
        val hits = q["search"]?.jsonArray?.map { entry ->
            val obj = entry.jsonObject
            val title = obj["title"]?.jsonPrimitive?.content.orEmpty()
            val snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty()
            WikiSearchHit(
                title = title,
                url = pageUrl(title),
                snippet = stripHtml(snippet),
            )
        } ?: emptyList()
        return WikiSearchResponse(query, totalHits, hits)
    }

    /**
     * Fetch a page or a specific section. Always returns the section TOC so the
     * caller can iterate into specific parts via subsequent calls with `section`.
     */
    fun page(title: String, section: String? = null, maxChars: Int = 4000): WikiPageResponse {
        log.info("wiki_page: title='{}' section={}", title, section)
        val sections = fetchSections(title)
        val (resolvedTitle, content) = if (section != null) {
            fetchSection(title, section)
        } else {
            fetchFullExtract(title)
        }
        val truncated = content.length > maxChars
        val trimmed = if (truncated) content.take(maxChars) + "…" else content
        return WikiPageResponse(
            title = resolvedTitle,
            url = pageUrl(resolvedTitle),
            sections = sections,
            section = section,
            content = trimmed,
            truncated = truncated,
        )
    }

    private fun fetchSections(title: String): List<WikiSection> {
        val url = "$BASE?action=parse&prop=sections&page=${enc(title)}" +
            "&format=json&redirects=true"
        return try {
            val body = httpGet(url, Duration.ofSeconds(8))
            val root = json.parseToJsonElement(body).jsonObject
            root["parse"]?.jsonObject?.get("sections")?.jsonArray?.map { s ->
                val o = s.jsonObject
                WikiSection(
                    index = o["index"]?.jsonPrimitive?.content.orEmpty(),
                    title = o["line"]?.jsonPrimitive?.content.orEmpty(),
                    level = o["level"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2,
                )
            } ?: emptyList()
        } catch (t: Throwable) {
            log.warn("fetchSections('{}') failed: {}", title, t.message)
            emptyList()
        }
    }

    private fun fetchFullExtract(title: String): Pair<String, String> {
        val url = "$BASE?action=query&prop=extracts&explaintext=true" +
            "&redirects=1&titles=${enc(title)}&format=json"
        val body = httpGet(url, Duration.ofSeconds(12))
        val root = json.parseToJsonElement(body).jsonObject
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
            ?: return title to "(unexpected response)"
        val firstPage = pages.entries
            .map { it.value.jsonObject }
            .firstOrNull { it["missing"] == null }
            ?: return title to "(no such page)"
        val resolved = firstPage["title"]?.jsonPrimitive?.content ?: title
        val extract = firstPage["extract"]?.jsonPrimitive?.content.orEmpty()
        return resolved to extract.trim()
    }

    private fun fetchSection(title: String, section: String): Pair<String, String> {
        val url = "$BASE?action=parse&prop=text&page=${enc(title)}" +
            "&section=${enc(section)}&format=json&redirects=true&disabletoc=true"
        val body = httpGet(url, Duration.ofSeconds(12))
        val root = json.parseToJsonElement(body).jsonObject
        val parse = root["parse"]?.jsonObject ?: return title to "(unexpected response)"
        val resolved = parse["title"]?.jsonPrimitive?.content ?: title
        val html = parse["text"]?.let { textNode ->
            // text is usually { "*": "..." } but can also be a primitive
            (textNode as? kotlinx.serialization.json.JsonObject)?.get("*")?.jsonPrimitive?.content
                ?: textNode.jsonPrimitive.content
        }.orEmpty()
        return resolved to stripHtml(html)
    }

    private fun pageUrl(title: String): String =
        "https://oldschool.runescape.wiki/w/" + title.replace(' ', '_')

    private fun httpGet(url: String, timeout: Duration): String {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .timeout(timeout)
            .GET()
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            error("Wiki HTTP ${resp.statusCode()}: ${resp.body().take(200)}")
        }
        return resp.body()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    /**
     * Strip HTML tags and decode common entities. Good enough for snippets and
     * section HTML from MediaWiki — we don't need full DOM parsing.
     */
    private fun stripHtml(html: String): String {
        if (html.isEmpty()) return ""
        val noScripts = html.replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        val noStyles = noScripts.replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        // Convert block-level tags to newlines so plain text retains structure.
        val withBreaks = noStyles
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        val noTags = withBreaks.replace(Regex("<[^>]+>"), "")
        val entitiesDecoded = noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) {
                runCatching { String(Character.toChars(it.groupValues[1].toInt())) }
                    .getOrDefault(it.value)
            }
        // Normalise whitespace per line, preserve paragraph breaks.
        return entitiesDecoded
            .split('\n')
            .joinToString("\n") { it.replace(Regex("[ \\t]+"), " ").trim() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    companion object {
        private const val BASE = "https://oldschool.runescape.wiki/api.php"

        // Compact output — token efficiency over human readability for tool responses.
        val outputJson: Json = Json {
            prettyPrint = false
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
