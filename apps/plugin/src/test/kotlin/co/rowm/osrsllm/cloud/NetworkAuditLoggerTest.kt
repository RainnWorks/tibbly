package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RAI-36 — exercises the [NetworkAuditLogger] ring-buffer contract.
 *
 *   1. A fresh logger is empty.
 *   2. A single `log(...)` call produces a single readable entry whose
 *      fields are the ones we passed.
 *   3. Once we've written more than [NetworkAuditLogger.CAPACITY] entries,
 *      the buffer holds exactly the LAST [NetworkAuditLogger.CAPACITY]
 *      entries — older ones fall off the front, newest entries are kept.
 *   4. `clear()` empties the buffer.
 *   5. The logger is safe to share across threads (basic concurrent
 *      `log()` from multiple threads must not corrupt the buffer).
 */
class NetworkAuditLoggerTest {

    private lateinit var logger: NetworkAuditLogger

    @Before
    fun setUp() {
        logger = NetworkAuditLogger()
    }

    @Test
    fun `fresh logger is empty`() {
        assertEquals(0, logger.entries().size)
    }

    @Test
    fun `single log call records all fields verbatim`() {
        logger.log(
            method = "WSS_SEND",
            host = "api.osrsllm.app",
            path = "/v1/ws",
            status = "OK",
            nowMillis = 1_700_000_000_000L,
        )
        val entries = logger.entries()
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals("WSS_SEND", e.method)
        assertEquals("api.osrsllm.app", e.host)
        assertEquals("/v1/ws", e.path)
        assertEquals("OK", e.status)
        assertEquals(1_700_000_000_000L, e.timestampMillis)
    }

    @Test
    fun `ring buffer is bounded to CAPACITY and drops oldest entries`() {
        val overshoot = 50
        for (i in 0 until NetworkAuditLogger.CAPACITY + overshoot) {
            logger.log(
                method = "WSS_SEND",
                host = "api.osrsllm.app",
                path = "/v1/ws",
                status = "seq=$i",
            )
        }
        val entries = logger.entries()
        assertEquals(
            "ring buffer must cap at CAPACITY",
            NetworkAuditLogger.CAPACITY,
            entries.size,
        )
        // The oldest surviving entry should be the one at index `overshoot`
        // (zero-based), so its status field should contain that sequence.
        val firstStatus = entries.first().status
        assertEquals(
            "oldest CAPACITY entries should have been dropped",
            "seq=$overshoot",
            firstStatus,
        )
        // The newest entry should be the last sequence we wrote.
        val lastStatus = entries.last().status
        val lastSeq = NetworkAuditLogger.CAPACITY + overshoot - 1
        assertEquals(
            "newest entry should be the last one we logged",
            "seq=$lastSeq",
            lastStatus,
        )
    }

    @Test
    fun `entries returns a snapshot that does not reflect later writes`() {
        logger.log(method = "WSS_SEND", host = "h", path = "/a", status = "OK")
        val snapshot = logger.entries()
        logger.log(method = "WSS_SEND", host = "h", path = "/b", status = "OK")
        // Snapshot is what entries() returned at the time of the call.
        assertEquals(1, snapshot.size)
        // The logger itself now holds two.
        assertEquals(2, logger.entries().size)
    }

    @Test
    fun `clear empties the buffer`() {
        repeat(10) {
            logger.log(method = "WSS_SEND", host = "h", path = "/p", status = "OK")
        }
        assertEquals(10, logger.entries().size)
        logger.clear()
        assertEquals(0, logger.entries().size)
    }

    @Test
    fun `concurrent log calls from many threads do not corrupt the buffer`() {
        val threads = 8
        val perThread = 200
        val workers = (0 until threads).map { tIdx ->
            Thread {
                for (i in 0 until perThread) {
                    logger.log(
                        method = "WSS_SEND",
                        host = "api.osrsllm.app",
                        path = "/v1/ws",
                        status = "t=$tIdx i=$i",
                    )
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        // We wrote threads * perThread = 1600 entries, capped at CAPACITY.
        // The exact identity of surviving entries is not deterministic across
        // schedulers but the buffer must hold exactly CAPACITY entries with
        // no corruption (every entry's fields must be readable strings).
        val entries = logger.entries()
        assertEquals(NetworkAuditLogger.CAPACITY, entries.size)
        for (e in entries) {
            assertEquals("WSS_SEND", e.method)
            assertEquals("api.osrsllm.app", e.host)
            assertEquals("/v1/ws", e.path)
            assertTrue("status field should match `t=N i=N` shape: ${e.status}",
                e.status.matches(Regex("""t=\d+ i=\d+""")))
        }
    }
}
