package co.rowm.osrsllm.cloud

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, in-memory ring buffer of every payload that left this plugin.
 *
 * REVIEWER NOTE — RAI-38:
 * Every successful return from `EgressGate.egress(...)` writes a row here.
 * The plugin panel renders this list verbatim so the player can SEE what
 * has been transmitted, in order, with a timestamp and the payload's
 * sealed-subtype tag. The payloads themselves are NOT logged via SLF4J —
 * they only ever live in this in-memory buffer, scoped to the running
 * RuneLite process.
 *
 * Bounded to [CAPACITY] entries; older entries fall off the back.
 */
@Singleton
class AuditLog @Inject constructor() {

    data class Entry(
        val timestampMillis: Long,
        val payloadKind: String,
        val sizeBytes: Int,
    )

    private val buffer = ArrayDeque<Entry>(CAPACITY)

    @Synchronized
    fun record(payloadKind: String, sizeBytes: Int, nowMillis: Long = System.currentTimeMillis()) {
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast(Entry(timestampMillis = nowMillis, payloadKind = payloadKind, sizeBytes = sizeBytes))
    }

    @Synchronized
    fun entries(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    companion object {
        const val CAPACITY: Int = 200
    }
}
