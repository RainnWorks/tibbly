package co.rowm.osrsllm.cloud

import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-turn telemetry for the cloud-chat tool-gating system (RAI-25).
 *
 * Writes ONLY to the local [AuditLog] so the player can see, on their plugin
 * panel, the surface size and tool-use density per turn:
 *
 *   toolsExposed = how many tool definitions we shipped to the LLM this turn
 *                  (cardinality of router-selected families ∪ core)
 *   toolsInvoked = how many of those the LLM actually called this turn
 *
 * The ratio `toolsInvoked / toolsExposed` is the token-economy headline:
 * a low ratio means we over-gated less aggressively than we could have, a
 * high ratio means the router was on-target. Aggregating across users is
 * RAI-37 analytics' job; this class deliberately does NOT egress.
 *
 * # Why this is its own class
 *
 * AuditLog's `record(payloadKind, sizeBytes)` is per-egress. Telemetry events
 * are per-turn, sometimes carry no egress, and have a different vocabulary.
 * Keeping them named-distinct keeps the panel readable and the security-design
 * grep audit narrow.
 */
@Singleton
class ToolGatingTelemetry @Inject constructor(
    private val auditLog: AuditLog,
) {

    private val log = LoggerFactory.getLogger(ToolGatingTelemetry::class.java)

    /**
     * Called once per turn AFTER the router picks the family set, BEFORE the
     * outbound `ChatUserMessage` egress. Records the cardinality so we can
     * audit the surface size offline.
     *
     * @param turnId the unique id of this turn (matches the egress audit row).
     * @param families families enabled this turn (includes CORE).
     * @param toolCount how many tool definitions that resolves to.
     */
    fun recordExposed(turnId: String, families: Set<ToolFamily>, toolCount: Int) {
        val tag = "ToolsExposed(turn=$turnId,families=${families.size},tools=$toolCount)"
        auditLog.record(payloadKind = tag, sizeBytes = toolCount)
        log.debug("ToolsExposed turn={} families={} tools={}", turnId, families, toolCount)
    }

    /**
     * Called when the turn ends (LLM returned its final assistant message, or
     * the user cancelled). Records how many distinct tools the LLM actually
     * invoked during the turn.
     */
    fun recordInvoked(turnId: String, invokedTools: Set<String>) {
        val tag = "ToolsInvoked(turn=$turnId,n=${invokedTools.size})"
        auditLog.record(payloadKind = tag, sizeBytes = invokedTools.size)
        log.debug("ToolsInvoked turn={} tools={}", turnId, invokedTools)
    }
}
