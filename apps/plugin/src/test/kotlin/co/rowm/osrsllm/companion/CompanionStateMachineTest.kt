package co.rowm.osrsllm.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the companion through a sequence of events and asserts the
 * spec transitions. The companion lore says dead air is sacred; we keep
 * it that way by making the state machine deterministic.
 */
class CompanionStateMachineTest {

    private class VirtualClock(var nowMs: Long = 0L) : CompanionStateMachine.Clock {
        override fun nowMs(): Long = nowMs
        fun advance(delta: Long) {
            nowMs += delta
        }
    }

    @Test
    fun `starts idle`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        assertEquals(AnimationState.Idle, sm.current())
    }

    @Test
    fun `PlayerMoved transitions to walking`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        sm.on(
            CompanionEvent.PlayerMoved(
                direction = Direction.NORTH,
                fromTile = CompanionTile(0, 0, 0),
                toTile = CompanionTile(0, 1, 0),
            ),
        )
        assertEquals(AnimationState.Walking, sm.current())
        assertEquals(Direction.NORTH, sm.direction())
    }

    @Test
    fun `PlayerStopped from Walking goes Idle`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        sm.on(
            CompanionEvent.PlayerMoved(
                Direction.NORTH,
                CompanionTile(0, 0, 0),
                CompanionTile(0, 1, 0),
            ),
        )
        sm.on(CompanionEvent.PlayerStopped)
        assertEquals(AnimationState.Idle, sm.current())
    }

    @Test
    fun `NpcDialogOpened transitions to LookAt`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        sm.on(CompanionEvent.NpcDialogOpened)
        assertEquals(AnimationState.LookAt, sm.current())
    }

    @Test
    fun `LookAt times out back to Idle on tick`() {
        val clock = VirtualClock()
        val sm = CompanionStateMachine(clock = clock)
        sm.on(CompanionEvent.NpcDialogOpened)
        assertEquals(AnimationState.LookAt, sm.current())
        clock.advance(CompanionStateMachine.LOOK_AT_DURATION_MS + 50)
        sm.tick()
        assertEquals(AnimationState.Idle, sm.current())
    }

    @Test
    fun `PetDrop fires Surprise`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        sm.on(CompanionEvent.PetDrop)
        assertEquals(AnimationState.Surprise, sm.current())
    }

    @Test
    fun `Surprise locks out other events during its window`() {
        val clock = VirtualClock()
        val sm = CompanionStateMachine(clock = clock)
        sm.on(CompanionEvent.LevelUp)
        assertEquals(AnimationState.Surprise, sm.current())
        // PlayerMoved before the surprise finishes is dropped.
        clock.advance(200)
        sm.on(
            CompanionEvent.PlayerMoved(
                Direction.EAST,
                CompanionTile(0, 0, 0),
                CompanionTile(1, 0, 0),
            ),
        )
        assertEquals(AnimationState.Surprise, sm.current())
        clock.advance(CompanionStateMachine.SURPRISE_DURATION_MS + 50)
        sm.tick()
        assertEquals(AnimationState.Idle, sm.current())
    }

    @Test
    fun `Idle descends through Yawn Read Sit on AFK`() {
        val clock = VirtualClock()
        val sm = CompanionStateMachine(clock = clock)
        clock.advance(CompanionStateMachine.AFK_YAWN_MS + 100)
        sm.tick()
        assertEquals(AnimationState.Yawn, sm.current())
        clock.advance(CompanionStateMachine.YAWN_DURATION_MS + 100)
        sm.tick()
        assertEquals(AnimationState.Read, sm.current())
        clock.advance(CompanionStateMachine.AFK_SIT_MS)
        sm.tick()
        assertEquals(AnimationState.Sit, sm.current())
    }

    @Test
    fun `player activity resets AFK chain`() {
        val clock = VirtualClock()
        val sm = CompanionStateMachine(clock = clock)
        clock.advance(CompanionStateMachine.AFK_YAWN_MS + 100)
        sm.tick()
        assertEquals(AnimationState.Yawn, sm.current())
        // Movement should snap back to Walking and reset the AFK timer.
        sm.on(
            CompanionEvent.PlayerMoved(
                Direction.EAST,
                CompanionTile(0, 0, 0),
                CompanionTile(1, 0, 0),
            ),
        )
        assertEquals(AnimationState.Walking, sm.current())
        clock.advance(1000)
        sm.tick()
        // Should not jump back into Yawn since we just had activity.
        assertEquals(AnimationState.Walking, sm.current())
    }

    @Test
    fun `SpeechBubble shown transitions to Speak`() {
        val sm = CompanionStateMachine(clock = VirtualClock())
        sm.on(CompanionEvent.SpeechBubbleShown(charCount = 24))
        assertEquals(AnimationState.Speak, sm.current())
        assertTrue(sm.isSpeaking())
        sm.on(CompanionEvent.SpeechBubbleDismissed)
        assertEquals(AnimationState.Idle, sm.current())
    }

    @Test
    fun `transition sink fires once per transition`() {
        val transitions = mutableListOf<Pair<AnimationState, AnimationState>>()
        val sink = CompanionStateMachine.TransitionSink { from, to ->
            transitions.add(from to to)
        }
        val sm = CompanionStateMachine(clock = VirtualClock(), sink = sink)
        sm.on(
            CompanionEvent.PlayerMoved(
                Direction.NORTH,
                CompanionTile(0, 0, 0),
                CompanionTile(0, 1, 0),
            ),
        )
        sm.on(CompanionEvent.PlayerStopped)
        assertEquals(
            listOf(
                AnimationState.Idle to AnimationState.Walking,
                AnimationState.Walking to AnimationState.Idle,
            ),
            transitions,
        )
    }
}
