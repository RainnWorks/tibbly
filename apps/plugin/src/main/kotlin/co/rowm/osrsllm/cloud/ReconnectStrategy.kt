package co.rowm.osrsllm.cloud

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with jitter for the WSS reconnect loop.
 *
 * Doubles the base delay on each successive failure, capped at [capMillis].
 * Each emitted delay is jittered by ±[jitterPercent] so a brief backend
 * blip doesn't synchronise a thundering herd of plugins reconnecting at
 * the same instant.
 *
 * Backoff resets to [baseMillis] after every successful connection
 * ([reset]). The class is single-threaded — callers reconnect from a
 * single coroutine.
 *
 * Defaults (RAI-22):
 *   - base 500 ms, cap 30 s, jitter ±10%
 *   - exponent 2x per failure (500 → 1000 → 2000 → 4000 → … → 30000)
 */
class ReconnectStrategy(
    private val baseMillis: Long = 500L,
    private val capMillis: Long = 30_000L,
    private val jitterPercent: Double = 0.10,
    private val random: Random = Random.Default,
) {
    private var attempt: Int = 0

    init {
        require(baseMillis > 0) { "baseMillis must be > 0 (got $baseMillis)" }
        require(capMillis >= baseMillis) { "capMillis must be >= baseMillis (got cap=$capMillis base=$baseMillis)" }
        require(jitterPercent in 0.0..0.5) { "jitterPercent must be in [0, 0.5] (got $jitterPercent)" }
    }

    /** Next backoff delay in milliseconds. Advances the internal attempt counter. */
    fun nextDelayMillis(): Long {
        // Cap the exponent so 1L shl 63 doesn't overflow when something pathological happens.
        val cappedShift = min(attempt, 30)
        val raw = baseMillis * (1L shl cappedShift)
        val capped = min(raw, capMillis)
        val jitterSpan = (capped * jitterPercent).toLong()
        val jitter = if (jitterSpan == 0L) 0L else random.nextLong(-jitterSpan, jitterSpan + 1)
        val delay = (capped + jitter).coerceAtLeast(0L)
        attempt++
        return delay
    }

    /** Reset on successful connection. */
    fun reset() {
        attempt = 0
    }

    /** Test-only: which attempt are we on (0-indexed before the first call to [nextDelayMillis]). */
    internal fun currentAttempt(): Int = attempt
}
