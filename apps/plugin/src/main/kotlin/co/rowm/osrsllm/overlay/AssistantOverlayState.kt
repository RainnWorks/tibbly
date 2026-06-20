package co.rowm.osrsllm.overlay

import co.rowm.osrsllm.chat.ToolCall
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe state for the on-chat overlay. Three modes:
 *
 * - [Hidden]   nothing rendered, overlay is invisible
 * - [Thinking] claude is running; show the query + a "thinking…" indicator + live tool calls
 * - [Response] claude finished; show the rendered primitive tree + the tool calls
 *
 * The slash-command handler writes; the overlay reads on the render thread.
 */
sealed class AssistantOverlayState {

    object Hidden : AssistantOverlayState()

    data class Thinking(
        val query: String,
        val toolCalls: List<ToolCall>,
        /** Monotonic timestamp so we can animate the dots. */
        val startedAtMs: Long,
    ) : AssistantOverlayState()

    data class Response(
        val query: String,
        val body: Primitive,
        val toolCalls: List<ToolCall>,
        val finishedAtMs: Long,
        val errored: Boolean = false,
    ) : AssistantOverlayState()
}

/** Wraps an AtomicReference for thread-safe state hand-off. */
@Singleton
class AssistantOverlayStateHolder @Inject constructor() {
    private val ref = AtomicReference<AssistantOverlayState>(AssistantOverlayState.Hidden)

    /**
     * Most-recent Response so we can show a "minimized" badge after dismiss and
     * let the user reopen without re-asking.
     */
    private val lastResponse = AtomicReference<AssistantOverlayState.Response?>(null)

    fun get(): AssistantOverlayState = ref.get()

    fun set(state: AssistantOverlayState) {
        ref.set(state)
        if (state is AssistantOverlayState.Response) lastResponse.set(state)
    }

    fun mutate(transform: (AssistantOverlayState) -> AssistantOverlayState): AssistantOverlayState {
        var prev: AssistantOverlayState
        var next: AssistantOverlayState
        do {
            prev = ref.get()
            next = transform(prev)
        } while (!ref.compareAndSet(prev, next))
        if (next is AssistantOverlayState.Response) lastResponse.set(next)
        return next
    }

    fun lastResponse(): AssistantOverlayState.Response? = lastResponse.get()

    /** Restore the most-recent Response if any; no-op otherwise. */
    fun reopenLast() {
        val last = lastResponse.get() ?: return
        ref.set(last)
    }

    /** Clear minimized history too (e.g. on shutdown). */
    fun clear() {
        ref.set(AssistantOverlayState.Hidden)
        lastResponse.set(null)
    }
}
