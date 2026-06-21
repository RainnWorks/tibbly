package co.rowm.osrsllm.cloud

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Companion ring buffer to [AuditLog], scoped to transport-layer egress events.
 *
 * REVIEWER NOTE — RAI-36:
 * Where [AuditLog] records every successful payload-level egress through
 * [EgressGate], this class records the transport-level facts that an
 * auditor or the player might want to see:
 *
 *   - the HTTP-style verb of the underlying call (`WSS_SEND`, `WSS_CONNECT`,
 *     `WSS_CLOSE`, …),
 *   - the host the call was made to (must be the single configured
 *     [BackendUrl] host — anything else is a regression),
 *   - the path (`/v1/chat`, `/v1/ws`, …),
 *   - a short status string (`OK`, `4xx`, `5xx`, `THROTTLED`, `REFUSED`, …).
 *
 * The buffer is bounded to [CAPACITY] entries and is thread-safe. Older
 * entries fall off the back. Like [AuditLog], the entries live ONLY in
 * memory — they are never written to SLF4J or to disk.
 *
 * Style mirrors [AuditLog]: synchronized methods, `ArrayDeque` ring,
 * companion `CAPACITY` constant, single dependency-injected constructor.
 */
@Singleton
class NetworkAuditLogger @Inject constructor() {

    data class Entry(
        val timestampMillis: Long,
        val method: String,
        val host: String,
        val path: String,
        val status: String,
    )

    private val buffer = ArrayDeque<Entry>(CAPACITY)

    @Synchronized
    fun log(
        method: String,
        host: String,
        path: String,
        status: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast(
            Entry(
                timestampMillis = nowMillis,
                method = method,
                host = host,
                path = path,
                status = status,
            ),
        )
    }

    @Synchronized
    fun entries(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    companion object {
        /**
         * Maximum number of egress events held in memory. Sized so that a
         * very chatty session (one event per second for >2 minutes) is fully
         * representable in the panel without ever growing unbounded.
         */
        const val CAPACITY: Int = 200
    }
}
