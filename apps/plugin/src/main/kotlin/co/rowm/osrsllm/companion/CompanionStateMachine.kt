package co.rowm.osrsllm.companion

import org.slf4j.LoggerFactory

/**
 * Pure state machine for the companion. Driven by abstract
 * [CompanionEvent]s so it can be unit-tested without a Client and
 * without RuneLite's EventBus.
 *
 * The plugin wire-in layer (see `CompanionEventAdapter`) subscribes to
 * `GameTick`, `PlayerSpawned`, `ChatMessage`, `NpcDespawned`,
 * `HitsplatApplied`, `WidgetLoaded` and translates them into
 * [CompanionEvent] values; this file deliberately knows nothing about
 * those RuneLite types so the gradle gates that scan `companion/` for
 * raw RuneLite couplings stay clean.
 *
 * # Transition table (matches EMBODIED_COMPANION.md §4)
 *
 * | From          | Trigger                                          | To       |
 * |---------------|--------------------------------------------------|----------|
 * | Idle          | PlayerMoved                                      | Walking  |
 * | Walking       | PlayerStopped                                    | Idle     |
 * | Idle/Walking  | NpcDialogOpened, BankOpened, ExamineFired        | LookAt   |
 * | LookAt        | (timeout, default 1500ms)                        | Idle     |
 * | Idle          | NoInputFor(60s)                                  | Yawn     |
 * | Yawn          | (animation completes)                            | Read     |
 * | Read          | NoInputFor(120s)                                 | Sit      |
 * | Sit/Read/Yawn | PlayerMoved                                      | Walking  |
 * | any           | PetDrop, LevelUp, CollectionLogNotification      | Surprise |
 * | Surprise      | (animation completes)                            | Idle     |
 * | any           | SpeechBubbleShown                                | Speak    |
 * | Speak         | SpeechBubbleDismissed                            | Idle     |
 */
class CompanionStateMachine(
    private val clock: Clock = SystemClock,
    private val sink: TransitionSink = TransitionSink { _, _ -> },
) {

    private val log = LoggerFactory.getLogger(CompanionStateMachine::class.java)

    @Volatile private var current: AnimationState = AnimationState.Idle
    @Volatile private var stateEnteredAtMs: Long = clock.nowMs()
    @Volatile private var lastInputAtMs: Long = clock.nowMs()
    @Volatile private var lastDirection: Direction = Direction.SOUTH
    @Volatile private var speechActive: Boolean = false

    /** Snapshot the renderer reads. Cheap, thread-safe. */
    fun current(): AnimationState = current

    /** Last-known facing direction. */
    fun direction(): Direction = lastDirection

    /** Push an event in. Returns the new state. */
    @Synchronized
    fun on(event: CompanionEvent): AnimationState {
        val previous = current
        val now = clock.nowMs()
        val next = transition(event, now)
        if (next != previous) {
            current = next
            stateEnteredAtMs = now
            log.debug("Companion state: {} -> {} on {}", previous.id, next.id, event::class.simpleName)
            sink.onTransition(previous, next)
        }
        return next
    }

    /** Periodic tick the wire-in fires from `GameTick` to drive timeouts. */
    @Synchronized
    fun tick(now: Long = clock.nowMs()): AnimationState =
        on(CompanionEvent.Tick(now))

    /**
     * Whether the speech bubble subsystem currently has the floor. Public
     * so the orchestrator can synchronise its cooldowns with the
     * "did we just say something" signal.
     */
    fun isSpeaking(): Boolean = speechActive

    /** Milliseconds since the current state was entered. */
    fun elapsedInStateMs(): Long = clock.nowMs() - stateEnteredAtMs

    private fun transition(event: CompanionEvent, now: Long): AnimationState {
        // Always remember the player's facing direction; the renderer
        // reads it independently of the animation state.
        if (event is CompanionEvent.PlayerMoved) {
            lastDirection = event.direction
            lastInputAtMs = now
        }
        // Treat "user is doing something" inputs as a wake signal so the
        // yawn / read / sit chain restarts cleanly.
        if (event.isUserActivity()) {
            lastInputAtMs = now
        }

        // Surprise always wins until its short animation completes.
        if (current == AnimationState.Surprise &&
            now - stateEnteredAtMs < SURPRISE_DURATION_MS
        ) {
            // Drop the event; the player will see the surprise animation play out.
            return current
        }

        return when (event) {
            is CompanionEvent.PlayerMoved -> AnimationState.Walking
            CompanionEvent.PlayerStopped -> AnimationState.Idle
            CompanionEvent.NpcDialogOpened,
            CompanionEvent.BankOpened,
            CompanionEvent.ExamineFired,
                -> AnimationState.LookAt
            CompanionEvent.PetDrop,
            CompanionEvent.LevelUp,
            CompanionEvent.CollectionLogNotification,
                -> AnimationState.Surprise
            is CompanionEvent.SpeechBubbleShown -> {
                speechActive = true
                AnimationState.Speak
            }
            CompanionEvent.SpeechBubbleDismissed -> {
                speechActive = false
                AnimationState.Idle
            }
            is CompanionEvent.Tick -> tickTransition(now)
        }
    }

    private fun tickTransition(now: Long): AnimationState {
        val sinceState = now - stateEnteredAtMs
        // LookAt and Surprise self-time-out so the state machine doesn't
        // get stuck.
        when (current) {
            AnimationState.LookAt -> if (sinceState >= LOOK_AT_DURATION_MS) return AnimationState.Idle
            AnimationState.Surprise -> if (sinceState >= SURPRISE_DURATION_MS) return AnimationState.Idle
            else -> { /* fall through */ }
        }
        // Yawn auto-advances into Read after its animation plays out.
        if (current == AnimationState.Yawn && sinceState >= YAWN_DURATION_MS) {
            return AnimationState.Read
        }
        // Idle/Walking - check whether to descend into the AFK ladder.
        val sinceInput = now - lastInputAtMs
        return when (current) {
            AnimationState.Idle -> when {
                sinceInput >= AFK_SIT_MS -> AnimationState.Sit
                sinceInput >= AFK_READ_MS -> AnimationState.Read
                sinceInput >= AFK_YAWN_MS -> AnimationState.Yawn
                else -> current
            }
            AnimationState.Read -> when {
                sinceInput >= AFK_SIT_MS -> AnimationState.Sit
                else -> current
            }
            else -> current
        }
    }

    /**
     * Interface for time so tests can run virtual clocks. The default
     * uses the JVM clock.
     */
    fun interface Clock {
        fun nowMs(): Long
    }

    /** JVM-clock implementation. */
    private object SystemClock : Clock {
        override fun nowMs(): Long = System.currentTimeMillis()
    }

    /**
     * Sink for transition notifications. The dialogue orchestrator
     * subscribes so it can fire proactive lines on state changes (e.g.
     * `Idle -> Surprise` is a candidate moment to say "nice level up").
     */
    fun interface TransitionSink {
        fun onTransition(from: AnimationState, to: AnimationState)
    }

    companion object {
        const val LOOK_AT_DURATION_MS: Long = 1_500L
        const val SURPRISE_DURATION_MS: Long = 1_200L
        const val YAWN_DURATION_MS: Long = 1_500L
        const val AFK_YAWN_MS: Long = 60_000L
        const val AFK_READ_MS: Long = 90_000L
        const val AFK_SIT_MS: Long = 120_000L
    }
}

/**
 * Events the state machine reacts to. Modelled as a sealed class so the
 * `when` exhaustivity check catches missing transitions in code review.
 */
sealed class CompanionEvent {
    /** Player walked from one tile to another. Direction encodes the heading. */
    data class PlayerMoved(val direction: Direction, val fromTile: CompanionTile, val toTile: CompanionTile) : CompanionEvent()

    /** Player stopped walking. */
    object PlayerStopped : CompanionEvent()

    /** An NPC dialog opened, or the player engaged a multi-line conversation. */
    object NpcDialogOpened : CompanionEvent()

    /** The bank interface opened. */
    object BankOpened : CompanionEvent()

    /** The player examined something. */
    object ExamineFired : CompanionEvent()

    /** A pet dropped. */
    object PetDrop : CompanionEvent()

    /** The player levelled up. */
    object LevelUp : CompanionEvent()

    /** A collection-log entry was added. */
    object CollectionLogNotification : CompanionEvent()

    /** The speech bubble started showing. Carries the text length so the renderer can size the bubble. */
    data class SpeechBubbleShown(val charCount: Int) : CompanionEvent()

    /** The speech bubble was dismissed. */
    object SpeechBubbleDismissed : CompanionEvent()

    /** Wall-clock pulse from the wire-in. Drives idle / AFK transitions. */
    data class Tick(val nowMs: Long) : CompanionEvent()

    /** Whether this event counts as the player doing something. */
    fun isUserActivity(): Boolean = when (this) {
        is PlayerMoved,
        NpcDialogOpened,
        BankOpened,
        ExamineFired,
        PetDrop,
        LevelUp,
        CollectionLogNotification,
            -> true
        is SpeechBubbleShown,
        SpeechBubbleDismissed,
        is Tick,
        PlayerStopped,
            -> false
    }
}
