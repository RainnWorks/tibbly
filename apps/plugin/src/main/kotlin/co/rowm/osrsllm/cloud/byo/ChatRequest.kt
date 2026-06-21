package co.rowm.osrsllm.cloud.byo

/**
 * Normalized chat-turn request the BYO runner builds once and hands to a
 * [ByoProvider] for provider-specific encoding.
 *
 * The runner owns the prompt-build step — it pulls the player message,
 * compresses the `HarnessContext` preamble into [systemPrompt], and picks
 * the model id. The provider then translates this shape into Anthropic /
 * OpenAI / OpenRouter wire JSON.
 *
 * The API key is NOT carried here — it lives on the runner's call site
 * and is attached at the HTTP-header layer by the provider's
 * [ByoProvider.authHeader] helper. Keeping the key off this object means
 * its `toString()` is safe to log.
 */
public data class ChatRequest(
    /** Provider-specific model id, already resolved (config or default). */
    val model: String,
    /** The compact game-state preamble + system instructions. */
    val systemPrompt: String?,
    /** The literal text the player typed. */
    val userMessage: String,
    /** Optional cap on output tokens. Provider default is used if null. */
    val maxTokens: Int? = null,
)
