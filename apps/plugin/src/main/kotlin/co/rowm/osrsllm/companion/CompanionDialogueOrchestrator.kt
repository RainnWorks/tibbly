package co.rowm.osrsllm.companion

import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.EgressGate
import co.rowm.osrsllm.cloud.OutboundPayload
import java.util.ArrayDeque
import org.slf4j.LoggerFactory

/**
 * The "alive" brain. Watches state transitions and ambient game events,
 * decides whether to fire a proactive line, and (when it decides yes)
 * emits a [OutboundPayload.CompanionTrigger] via [EgressGate].
 *
 * Cooldown discipline is the whole point of this class. The product
 * spec (`docs/product/EMBODIED_COMPANION.md` §2 Clippy lesson and §5
 * personality discipline) calls dead air sacred. The defaults below
 * codify it:
 *
 *  - At most one proactive line every [DEFAULT_MIN_GAP_MS] (30 s).
 *  - At most [DEFAULT_HOURLY_BUDGET] proactive lines per rolling hour.
 *  - Each proactive line counts double for the next 10 minutes
 *    (decay-on-density), so a 4-line burst spends almost the entire
 *    hourly budget rather than the literal 4.
 *  - The longer a session runs without intervention, the lower the
 *    odds the next eligible moment fires. Concretely, after the
 *    [SESSION_DECAY_AFTER_MS] mark, each new trigger candidate has its
 *    "fire probability" multiplied by [SESSION_DECAY_FLOOR].
 *
 * The orchestrator NEVER directly contacts an LLM; it only declares an
 * event. The backend brain decides whether to generate a line at all.
 * This keeps the plugin's egress surface as small as the existing
 * cloud chat path and is enforced by `:checkCompanionConsentGated`.
 */
class CompanionDialogueOrchestrator(
    private val egressGate: EgressGate,
    private val consentSupplier: () -> ConsentState,
    private val cloudChatEnabledSupplier: () -> Boolean,
    private val triggersEnabledSupplier: () -> Boolean,
    private val clock: Clock = SystemClock,
    private val cooldownConfig: CooldownConfig = CooldownConfig(),
    private val randomGate: (Double) -> Boolean = { p -> Math.random() < p },
) {

    private val log = LoggerFactory.getLogger(CompanionDialogueOrchestrator::class.java)

    /** Timestamps of recent fires, newest-first. Used for hourly budget. */
    private val fireTimestamps = ArrayDeque<Long>()
    @Volatile private var sessionStartedAtMs: Long = clock.nowMs()
    @Volatile private var lastFireAtMs: Long = NEVER

    /** Reset internal counters; called from plugin shutdown. */
    @Synchronized
    fun reset() {
        fireTimestamps.clear()
        sessionStartedAtMs = clock.nowMs()
        lastFireAtMs = NEVER
    }

    /**
     * Consider firing a proactive trigger. Returns `true` if the trigger
     * was emitted, `false` if it was suppressed by cooldown / consent /
     * configuration.
     *
     * The orchestrator's contract: callers should call this freely; the
     * filtering happens inside.
     */
    @Synchronized
    fun consider(trigger: ProactiveTrigger): Boolean {
        val now = clock.nowMs()
        // Gate 1 - consent. The companion must NEVER egress without it.
        // The `:checkCompanionConsentGated` Gradle task enforces this.
        val consent = consentSupplier()
        if (!consent.accepted) {
            log.debug("Companion trigger suppressed: consent off")
            return false
        }
        // Gate 2 - cloud chat must be on (egress can't fire otherwise).
        if (!cloudChatEnabledSupplier()) {
            log.debug("Companion trigger suppressed: cloudChat off")
            return false
        }
        // Gate 3 - player has opted out of proactive lines.
        if (!triggersEnabledSupplier()) {
            log.debug("Companion trigger suppressed: triggers disabled by player")
            return false
        }
        // Gate 4 - minimum gap. Sentinel [NEVER] means we have never
        // fired; the first eligible candidate of the session is always
        // allowed through.
        if (lastFireAtMs != NEVER && now - lastFireAtMs < cooldownConfig.minGapMs) {
            log.debug("Companion trigger suppressed: min-gap")
            return false
        }
        // Gate 5 - hourly budget (with density penalty for recent fires).
        purgeOlderThan(now - cooldownConfig.windowMs)
        val recentLast10m = fireTimestamps.count { now - it < cooldownConfig.densityWindowMs }
        val effectiveCount = fireTimestamps.size + recentLast10m // doubles recent fires
        if (effectiveCount >= cooldownConfig.hourlyBudget) {
            log.debug("Companion trigger suppressed: hourly budget exhausted ({} effective)", effectiveCount)
            return false
        }
        // Gate 6 - session decay. Once we're past the threshold, only
        // fire with probability [SESSION_DECAY_FLOOR] so a long session
        // tails off rather than chattering forever.
        val sessionAgeMs = now - sessionStartedAtMs
        if (sessionAgeMs > cooldownConfig.sessionDecayAfterMs &&
            !randomGate(cooldownConfig.sessionDecayFloor)
        ) {
            log.debug("Companion trigger suppressed: session decay")
            return false
        }
        // All gates passed: fire.
        fireTimestamps.addFirst(now)
        lastFireAtMs = now
        val payload = OutboundPayload.CompanionTrigger(
            triggerType = trigger.id,
            contextSnapshot = trigger.contextSnapshot,
        )
        runCatching { egressGate.egress(payload, consent, cloudChatEnabledSupplier()) }
            .onFailure { log.warn("Companion trigger egress failed: {}", it.message) }
        return true
    }

    /**
     * Emit a click / drag / dismiss event. These are not cooldown-gated -
     * the player initiated them, so they always egress (subject to
     * consent + cloud chat).
     */
    @Synchronized
    fun recordInteraction(event: InteractionEvent) {
        val consent = consentSupplier()
        if (!consent.accepted) return
        if (!cloudChatEnabledSupplier()) return
        val payload = OutboundPayload.CompanionInteractionEvent(
            eventType = event.id,
            payload = event.payload,
        )
        runCatching { egressGate.egress(payload, consent, cloudChatEnabledSupplier()) }
            .onFailure { log.warn("Companion interaction egress failed: {}", it.message) }
    }

    /**
     * Send a memory-consolidation hint to the backend at end of session.
     * Same consent + cloud-chat gates apply.
     */
    @Synchronized
    fun submitMemoryHint(event: MemoryHint) {
        val consent = consentSupplier()
        if (!consent.accepted) return
        if (!cloudChatEnabledSupplier()) return
        val payload = OutboundPayload.CompanionMemoryHint(
            memorableEvent = event.summary,
            evidenceProbeIds = event.evidenceProbeIds,
        )
        runCatching { egressGate.egress(payload, consent, cloudChatEnabledSupplier()) }
            .onFailure { log.warn("Companion memory hint egress failed: {}", it.message) }
    }

    /** Visible for tests - current count of recent fires inside the rolling window. */
    fun firedInWindow(): Int = synchronized(this) { fireTimestamps.size }

    private fun purgeOlderThan(cutoffMs: Long) {
        val it = fireTimestamps.iterator()
        while (it.hasNext()) {
            if (it.next() < cutoffMs) it.remove()
        }
    }

    /**
     * Cooldown knobs. Defaults match the spec; tests override for short
     * deterministic windows.
     */
    data class CooldownConfig(
        val minGapMs: Long = DEFAULT_MIN_GAP_MS,
        val hourlyBudget: Int = DEFAULT_HOURLY_BUDGET,
        val windowMs: Long = DEFAULT_WINDOW_MS,
        val densityWindowMs: Long = DEFAULT_DENSITY_WINDOW_MS,
        val sessionDecayAfterMs: Long = SESSION_DECAY_AFTER_MS,
        val sessionDecayFloor: Double = SESSION_DECAY_FLOOR,
    )

    /** Time abstraction so tests can run virtual clocks. */
    fun interface Clock {
        fun nowMs(): Long
    }

    private object SystemClock : Clock {
        override fun nowMs(): Long = System.currentTimeMillis()
    }

    /**
     * Trigger nominated by the state machine / event subscribers. The
     * orchestrator decides whether it actually fires.
     */
    data class ProactiveTrigger(
        val id: String,
        val contextSnapshot: Map<String, String> = emptyMap(),
    )

    /** Player-initiated interaction with the sprite. */
    data class InteractionEvent(
        val id: String,
        val payload: Map<String, String> = emptyMap(),
    )

    /** Candidate fact the backend should consider remembering. */
    data class MemoryHint(
        val summary: String,
        val evidenceProbeIds: List<String> = emptyList(),
    )

    companion object {
        /** Sentinel for "no fire has happened yet this session". */
        private const val NEVER: Long = Long.MIN_VALUE

        const val DEFAULT_MIN_GAP_MS: Long = 30_000L
        const val DEFAULT_HOURLY_BUDGET: Int = 8
        const val DEFAULT_WINDOW_MS: Long = 60 * 60 * 1000L
        const val DEFAULT_DENSITY_WINDOW_MS: Long = 10 * 60 * 1000L
        const val SESSION_DECAY_AFTER_MS: Long = 90 * 60 * 1000L
        const val SESSION_DECAY_FLOOR: Double = 0.25
    }
}
