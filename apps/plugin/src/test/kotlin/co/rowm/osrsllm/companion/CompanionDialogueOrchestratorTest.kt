package co.rowm.osrsllm.companion

import co.rowm.osrsllm.cloud.AuditLog
import co.rowm.osrsllm.cloud.BackendWsClient
import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.EgressGate
import co.rowm.osrsllm.cloud.OutboundPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cooldown discipline under load. The product spec calls dead air
 * sacred; if these tests fail we are about to ship a Clippy.
 */
class CompanionDialogueOrchestratorTest {

    /**
     * A capturing EgressGate stub. Overrides only [egress] so the
     * orchestrator's call site is exercised end-to-end without needing
     * a real WSS transport.
     */
    private class CapturingGate(
        transport: BackendWsClient,
        auditLog: AuditLog,
    ) : EgressGate(transport, auditLog) {
        val sent = mutableListOf<OutboundPayload>()
        override fun egress(
            payload: OutboundPayload,
            consent: ConsentState,
            cloudChatEnabled: Boolean,
        ) {
            sent += payload
        }
    }

    private class VirtualClock(var nowMs: Long = 0L) : CompanionDialogueOrchestrator.Clock {
        override fun nowMs(): Long = nowMs
        fun advance(delta: Long) { nowMs += delta }
    }

    private lateinit var clock: VirtualClock
    private lateinit var gate: CapturingGate
    private var consent = ConsentState.freeze(accepted = true, nowMillis = 0)
    private var cloudChat = true
    private var triggersOn = true

    @Before
    fun setUp() {
        ConsentState.reset()
        consent = ConsentState.freeze(accepted = true, nowMillis = 0)
        cloudChat = true
        triggersOn = true
        clock = VirtualClock()
        gate = CapturingGate(BackendWsClient(), AuditLog())
    }

    @After
    fun tearDown() {
        ConsentState.reset()
    }

    private fun orchestrator(
        config: CompanionDialogueOrchestrator.CooldownConfig =
            CompanionDialogueOrchestrator.CooldownConfig(),
        randomGate: (Double) -> Boolean = { true },
    ): CompanionDialogueOrchestrator =
        CompanionDialogueOrchestrator(
            egressGate = gate,
            consentSupplier = { consent },
            cloudChatEnabledSupplier = { cloudChat },
            triggersEnabledSupplier = { triggersOn },
            clock = clock,
            cooldownConfig = config,
            randomGate = randomGate,
        )

    @Test
    fun `first trigger fires`() {
        val o = orchestrator()
        val fired = o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("level_up"))
        assertTrue(fired)
        assertEquals(1, gate.sent.size)
        val sent = gate.sent.single()
        assertTrue(sent is OutboundPayload.CompanionTrigger)
        assertEquals("level_up", (sent as OutboundPayload.CompanionTrigger).triggerType)
    }

    @Test
    fun `consent off suppresses everything`() {
        ConsentState.reset()
        consent = ConsentState.freeze(accepted = false, nowMillis = 0)
        val o = orchestrator()
        assertFalse(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("level_up")))
        assertEquals(0, gate.sent.size)
    }

    @Test
    fun `cloudChat off suppresses everything`() {
        cloudChat = false
        val o = orchestrator()
        assertFalse(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("level_up")))
        assertEquals(0, gate.sent.size)
    }

    @Test
    fun `proactive off suppresses ProactiveTrigger`() {
        triggersOn = false
        val o = orchestrator()
        assertFalse(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("level_up")))
        assertEquals(0, gate.sent.size)
    }

    @Test
    fun `min gap suppresses second trigger within 30s`() {
        val o = orchestrator(
            config = CompanionDialogueOrchestrator.CooldownConfig(
                minGapMs = 30_000L,
                hourlyBudget = 8,
                windowMs = 60 * 60 * 1000L,
                densityWindowMs = 10 * 60 * 1000L,
                sessionDecayAfterMs = Long.MAX_VALUE,
                sessionDecayFloor = 1.0,
            ),
        )
        assertTrue(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("a")))
        clock.advance(5_000L)
        assertFalse(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("b")))
        clock.advance(30_000L)
        assertTrue(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("c")))
        assertEquals(2, gate.sent.size)
    }

    @Test
    fun `hourly budget caps fires under load`() {
        // 4 fires, each spaced 31s apart, density doubles to 8, equal to budget.
        val o = orchestrator(
            config = CompanionDialogueOrchestrator.CooldownConfig(
                minGapMs = 30_000L,
                hourlyBudget = 8,
                windowMs = 60 * 60 * 1000L,
                densityWindowMs = 10 * 60 * 1000L,
                sessionDecayAfterMs = Long.MAX_VALUE,
                sessionDecayFloor = 1.0,
            ),
        )
        var fired = 0
        repeat(10) {
            if (o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("t$it"))) fired++
            clock.advance(31_000L)
        }
        // Density doubling each recent fire means budget halves: 4 fires.
        assertEquals(4, fired)
    }

    @Test
    fun `session decay suppresses after threshold when random gate returns false`() {
        val o = orchestrator(
            config = CompanionDialogueOrchestrator.CooldownConfig(
                minGapMs = 1L,
                hourlyBudget = 100,
                windowMs = 60 * 60 * 1000L,
                densityWindowMs = 60 * 60 * 1000L,
                sessionDecayAfterMs = 1_000L,
                sessionDecayFloor = 0.0,
            ),
            randomGate = { false },
        )
        // Inside decay window - random gate at 0.0 vetoes.
        clock.advance(2_000L)
        assertFalse(o.consider(CompanionDialogueOrchestrator.ProactiveTrigger("late")))
    }

    @Test
    fun `interaction events bypass cooldown but still respect consent`() {
        val o = orchestrator()
        repeat(5) {
            o.recordInteraction(
                CompanionDialogueOrchestrator.InteractionEvent(
                    id = "click_sprite",
                    payload = mapOf("x" to "100", "y" to "100"),
                ),
            )
        }
        assertEquals(5, gate.sent.size)
        assertTrue(gate.sent.all { it is OutboundPayload.CompanionInteractionEvent })

        ConsentState.reset()
        consent = ConsentState.freeze(accepted = false, nowMillis = 0)
        o.recordInteraction(CompanionDialogueOrchestrator.InteractionEvent("noop"))
        assertEquals(5, gate.sent.size)
    }

    @Test
    fun `memory hints carry summary plus evidence`() {
        val o = orchestrator()
        o.submitMemoryHint(
            CompanionDialogueOrchestrator.MemoryHint(
                summary = "player killed 113 Vorkath this week",
                evidenceProbeIds = listOf("loot_log", "session_xp"),
            ),
        )
        assertEquals(1, gate.sent.size)
        val sent = gate.sent.single() as OutboundPayload.CompanionMemoryHint
        assertEquals("player killed 113 Vorkath this week", sent.memorableEvent)
        assertEquals(listOf("loot_log", "session_xp"), sent.evidenceProbeIds)
    }
}
