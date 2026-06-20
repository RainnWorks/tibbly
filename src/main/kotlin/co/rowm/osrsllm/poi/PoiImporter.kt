package co.rowm.osrsllm.poi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Merges multiple POI data sources into the canonical `src/main/resources/pois.json`.
 *
 * Inputs read from `src/main/resources/poi-data/`:
 *   - TSV files (shortest-path format)
 *   - JSON files in our schema (hand-curated or agent-mined)
 *
 * Dedupe rule: same `type` + same lowercased `name` collapse to one entry,
 * taking the first coord encountered (typically the most "obvious" booth/altar).
 *
 * Run with `./gradlew refreshPoiDb`.
 */
object PoiImporter {

    private val outputJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = false
        explicitNulls = false
    }

    private val inputJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class RawPoiFile(val pois: List<Poi> = emptyList())

    @JvmStatic
    fun main(args: Array<String>) {
        val projectRoot = File(args.firstOrNull() ?: ".").absoluteFile
        val dataDir = File(projectRoot, "src/main/resources/poi-data")
        val outFile = File(projectRoot, "src/main/resources/pois.json")
        require(dataDir.isDirectory) { "No POI data directory at $dataDir" }

        val collected = mutableListOf<Poi>()
        val sourceCounts = mutableMapOf<String, Int>()

        dataDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val before = collected.size
            when {
                f.name.endsWith(".tsv") -> collected += parseTsv(f)
                f.name.endsWith(".json") -> collected += parseJson(f)
            }
            sourceCounts[f.name] = collected.size - before
        }

        val deduped = dedupe(collected)
        val sortedByType = deduped.sortedWith(compareBy({ it.type }, { it.name }))

        outFile.writeText(
            outputJson.encodeToString(PoiDb.serializer(), PoiDb(pois = sortedByType))
        )

        println("=== POI import ===")
        sourceCounts.forEach { (file, n) -> println("  $file: +$n") }
        println("  raw total: ${collected.size}")
        println("  after dedupe: ${deduped.size}")
        println()
        deduped.groupBy { it.type }.toSortedMap().forEach { (t, list) ->
            println("  $t: ${list.size}")
        }
        println()
        println("Wrote ${sortedByType.size} POIs to $outFile")
    }

    private fun parseTsv(file: File): List<Poi> {
        val type = file.nameWithoutExtension // "bank" → type bank, "altar" → type altar
        val lines = file.readLines()
        val header = lines.firstOrNull { it.startsWith("#") && it.contains("Destination") }
            ?: return emptyList()
        val cols = header.removePrefix("#").trim().split('\t').map { it.trim() }
        val idx = cols.withIndex().associate { (i, name) -> name to i }
        val results = mutableListOf<Poi>()
        for (raw in lines) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val cells = raw.split('\t')
            val dest = cells.getOrNull(idx["Destination"] ?: -1)?.trim().orEmpty()
            if (dest.isBlank()) continue
            val parts = dest.split(' ').filter { it.isNotBlank() }
            if (parts.size < 2) continue
            val x = parts[0].toIntOrNull() ?: continue
            val y = parts[1].toIntOrNull() ?: continue
            val plane = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val info = cells.getOrNull(idx["Info"] ?: -1)?.trim().orEmpty()
            val baseName = info.ifBlank { "$type ${results.size + 1}" }
            // Tag the type into the name so a "X bank" / "X altar" substring search works naturally.
            val name = if (baseName.lowercase().contains(type)) baseName
                else "$baseName $type"
            results += Poi(type = type, name = name, x = x, y = y, plane = plane)
        }
        return results
    }

    private fun parseJson(file: File): List<Poi> {
        return try {
            inputJson.decodeFromString(RawPoiFile.serializer(), file.readText()).pois
        } catch (t: Throwable) {
            println("WARN: ${file.name} parse failed: ${t.message}")
            emptyList()
        }
    }

    private fun dedupe(pois: List<Poi>): List<Poi> {
        // Same (type, lowercased name) → keep first. Coord differences for the same name
        // (e.g. multiple bank booths around one bank) collapse to a single representative.
        val seen = HashSet<String>()
        return pois.filter { p ->
            val key = "${p.type.lowercase()}|${p.name.lowercase()}"
            seen.add(key)
        }
    }
}
