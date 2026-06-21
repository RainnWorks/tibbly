package co.rowm.osrsllm.companion

import co.rowm.osrsllm.cloud.AuditLog
import co.rowm.osrsllm.cloud.BackendWsClient
import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.EgressGate
import co.rowm.osrsllm.cloud.OutboundPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * End-to-end consent gate. Mirrors the test the plugin Hub reviewer will
 * run mentally: "with consent off, can the companion send anything?" The
 * answer is no - every egress path inside the orchestrator routes
 * through the consent supplier and aborts.
 *
 * The Gradle task `:checkCompanionConsentGated` provides the compile-
 * time half of the contract (no egress call without a consent reference
 * in the same file); this test provides the runtime half (the gate
 * actually behaves).
 */
class CompanionConsentGateTest {

    private class CountingGate(
        transport: BackendWsClient,
        auditLog: AuditLog,
    ) : EgressGate(transport, auditLog) {
        var count: Int = 0
        override fun egress(
            payload: OutboundPayload,
            consent: ConsentState,
            cloudChatEnabled: Boolean,
        ) {
            count++
        }
    }

    @After
    fun tearDown() {
        ConsentState.reset()
    }

    @Test
    fun `companion does not egress when consent is off`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = false)
        val gate = CountingGate(BackendWsClient(), AuditLog())
        val orchestrator = CompanionDialogueOrchestrator(
            egressGate = gate,
            consentSupplier = { consent },
            cloudChatEnabledSupplier = { true },
            triggersEnabledSupplier = { true },
        )
        repeat(10) {
            orchestrator.consider(CompanionDialogueOrchestrator.ProactiveTrigger("t$it"))
            orchestrator.recordInteraction(CompanionDialogueOrchestrator.InteractionEvent("click"))
            orchestrator.submitMemoryHint(CompanionDialogueOrchestrator.MemoryHint("memo"))
        }
        assertEquals("no egress should fire with consent off", 0, gate.count)
    }

    @Test
    fun `companion does not egress when cloud chat is off`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = true)
        val gate = CountingGate(BackendWsClient(), AuditLog())
        val orchestrator = CompanionDialogueOrchestrator(
            egressGate = gate,
            consentSupplier = { consent },
            cloudChatEnabledSupplier = { false },
            triggersEnabledSupplier = { true },
        )
        orchestrator.consider(CompanionDialogueOrchestrator.ProactiveTrigger("t"))
        orchestrator.recordInteraction(CompanionDialogueOrchestrator.InteractionEvent("c"))
        orchestrator.submitMemoryHint(CompanionDialogueOrchestrator.MemoryHint("m"))
        assertEquals("no egress should fire with cloudChat off", 0, gate.count)
    }
}
