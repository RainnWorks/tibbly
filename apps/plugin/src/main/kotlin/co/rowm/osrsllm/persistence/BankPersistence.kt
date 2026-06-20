package co.rowm.osrsllm.persistence

import co.rowm.osrsllm.BankSnapshot
import kotlinx.serialization.json.Json
import net.runelite.client.RuneLite
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the player's last-seen bank to disk per account hash, so the snapshot
 * survives RuneLite restarts. Until they actually open the bank in-game, the
 * loaded data is the most recent persisted view from the last session.
 */
@Singleton
class BankPersistence @Inject constructor() {

    private val log = LoggerFactory.getLogger(BankPersistence::class.java)
    private val dir: File = File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/state").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun fileFor(accountHash: Long): File = File(dir, "bank-$accountHash.json")

    fun save(accountHash: Long, snapshot: BankSnapshot) {
        if (accountHash <= 0) return
        try {
            fileFor(accountHash).writeText(json.encodeToString(BankSnapshot.serializer(), snapshot))
        } catch (t: Throwable) {
            log.warn("BankPersistence.save failed for account {}: {}", accountHash, t.message)
        }
    }

    fun load(accountHash: Long): BankSnapshot? {
        if (accountHash <= 0) return null
        val f = fileFor(accountHash)
        if (!f.exists()) return null
        return try {
            json.decodeFromString(BankSnapshot.serializer(), f.readText())
        } catch (t: Throwable) {
            log.warn("BankPersistence.load failed for account {}: {}", accountHash, t.message)
            null
        }
    }
}
