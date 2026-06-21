package co.rowm.osrsllm.chat

/**
 * Listener for live tool-call events from a chat backend. Each backend
 * (cloud WSS, BYO direct) dispatches here as the LLM emits tool_use blocks
 * and as the corresponding tool_result blocks arrive, so the chat UI can
 * show tool activity in real time instead of staring at a "thinking..."
 * bubble while the model silently fires many tool calls in the background.
 *
 * Callbacks fire on whatever thread the backend reader uses; implementations
 * are responsible for marshalling to the EDT before touching Swing state.
 */
interface ToolCallListener {
    fun onToolCallStarted(id: String, name: String, input: String) {}
    fun onToolCallCompleted(id: String, result: String) {}
}
