package co.rowm.osrsllm.transport

import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Parses shortest-path TSV files under `src/main/resources/transports` and
 * writes the canonical DB to `src/main/resources/transports.json`.
 *
 * Run via Gradle task `refreshTransportDb` (defined in build.gradle.kts).
 * The output JSON is the single source of truth at runtime.
 */
object TransportImporter {

    private val outputJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = false
        explicitNulls = false
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val projectRoot = File(args.firstOrNull() ?: ".").absoluteFile
        val tsvDir = File(projectRoot, "src/main/resources/transports")
        val outFile = File(projectRoot, "src/main/resources/transports.json")
        require(tsvDir.isDirectory) { "No TSV directory at $tsvDir" }

        val records = mutableListOf<TransportRecord>()
        val sources = mutableListOf<String>()
        tsvDir.listFiles { f -> f.extension == "tsv" }
            ?.sortedBy { it.name }
            ?.forEach { tsv ->
                val before = records.size
                records += parseFile(tsv)
                val added = records.size - before
                sources += "${tsv.name} (+$added)"
                println("Parsed ${tsv.name}: $added records")
            }

        val db = TransportDb(
            schema = "1.0",
            sources = sources,
            generatedAt = Instant.now().toString(),
            transports = records,
        )
        outFile.parentFile.mkdirs()
        outFile.writeText(outputJson.encodeToString(TransportDb.serializer(), db))
        println("\nWrote ${records.size} records to $outFile")
    }

    fun parseFile(file: File): List<TransportRecord> {
        val category = file.nameWithoutExtension
        val lines = file.readLines()
        // First non-blank line starting with `#` and containing "Destination" is the header.
        val header = lines.firstOrNull { it.startsWith("#") && it.contains("Destination") }
            ?: return emptyList()
        val columns = header.removePrefix("#").trim().split('\t').map { it.trim() }
        val colIdx = columns.withIndex().associate { (i, name) -> name to i }

        val records = mutableListOf<TransportRecord>()
        var rowIdx = 0
        for (raw in lines) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val cells = raw.split('\t')
            val destStr = cells.getOrNull(colIdx["Destination"] ?: -1)?.trim().orEmpty()
            if (destStr.isBlank()) continue
            val dest = parseWorldPoint(destStr) ?: continue
            val origin = colIdx["Origin"]?.let { idx -> parseWorldPoint(cells.getOrNull(idx)?.trim().orEmpty()) }
            val items = parseItemReqs(cells.cell(colIdx["Items"]))
            val quests = parseQuests(cells.cell(colIdx["Quests"]))
            val skills = parseSkills(cells.cell(colIdx["Skills"]))
            val duration = cells.cell(colIdx["Duration"]).toIntOrNull() ?: 0
            val displayInfo = cells.cell(colIdx["Display info"]).ifBlank {
                // Fall back to "menuOption menuTarget" if no display info
                cells.cell(colIdx["menuOption menuTarget objectID"]).ifBlank {
                    "${category.replace('_', ' ')} ${rowIdx + 1}"
                }
            }
            val consumable = cells.cell(colIdx["Consumable"]).equals("T", ignoreCase = true)
            val wildernessLimit = cells.cell(colIdx["Wilderness level"]).toIntOrNull()

            records += TransportRecord(
                id = "$category:$rowIdx",
                category = category,
                name = displayInfo,
                origin = origin,
                destination = dest,
                durationTicks = duration,
                itemReqs = items,
                questReqs = quests,
                skillReqs = skills,
                consumable = consumable,
                wildernessLimit = wildernessLimit,
                source = "shortest-path/${file.name}",
            )
            rowIdx++
        }
        return records
    }

    private fun List<String>.cell(idx: Int?): String =
        if (idx == null || idx !in indices) "" else this[idx].trim()

    private fun parseWorldPoint(s: String): WorldPointXYZ? {
        if (s.isBlank()) return null
        val parts = s.split(' ', '\t').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val plane = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return WorldPointXYZ(x, y, plane)
    }

    /**
     * Parse a requirement string like "1=1;2=3||4=5" into ANDed groups,
     * each containing OR alternatives. Empty input → empty list.
     */
    private fun parseItemReqs(s: String): List<List<ItemNeed>> {
        if (s.isBlank()) return emptyList()
        return s.split(';').mapNotNull { group ->
            val alternatives = group.split("||").mapNotNull { it.parseItemNeed() }
            alternatives.takeIf { it.isNotEmpty() }
        }
    }

    private fun String.parseItemNeed(): ItemNeed? {
        val s = trim().takeIf { it.isNotBlank() } ?: return null
        val parts = s.split('=', limit = 2)
        val id = parts[0].trim().toIntOrNull() ?: return null
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        return ItemNeed(id, count)
    }

    private fun parseQuests(s: String): List<String> {
        if (s.isBlank()) return emptyList()
        return s.split(';').mapNotNull { q -> q.trim().takeIf { it.isNotBlank() } }
    }

    private fun parseSkills(s: String): Map<String, Int> {
        if (s.isBlank()) return emptyMap()
        // Format examples: "agility>=70", "agility>=70;magic>=80"
        return s.split(';').mapNotNull { req ->
            val r = req.trim()
            val match = Regex("([A-Za-z]+)\\s*>=\\s*(\\d+)").find(r) ?: return@mapNotNull null
            match.groupValues[1].lowercase() to match.groupValues[2].toInt()
        }.toMap()
    }
}
