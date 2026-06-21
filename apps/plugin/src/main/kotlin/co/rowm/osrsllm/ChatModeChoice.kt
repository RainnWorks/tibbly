package co.rowm.osrsllm

/**
 * RuneLite config dropdowns are bound to a Java/Kotlin enum's `toString()`
 * output. We expose this thin enum as the storage shape and convert to the
 * richer [ChatMode] sealed hierarchy in code.
 *
 * The display strings here are what the player sees in the RuneLite config
 * UI. Keep them human-readable and consistent with the marketing copy.
 *
 * See `docs/architecture/HUB_RELEASE_STRATEGY.md` for the three-tier model.
 */
public enum class ChatModeChoice(
    /** Persisted config value (also surfaced to ChatMode.fromConfigValue). */
    public val configValue: String,
    private val display: String,
) {
    /** Tibbly cloud — current production path. Default to preserve behaviour. */
    CLOUD(ChatMode.Cloud.configValue, "Tibbly cloud (managed)"),

    /** Bring-your-own Anthropic API key. */
    BYO_ANTHROPIC(ChatMode.ByoAnthropic.configValue, "Direct: Anthropic (BYO key)"),

    /** Bring-your-own OpenAI API key. */
    BYO_OPENAI(ChatMode.ByoOpenAi.configValue, "Direct: OpenAI (BYO key)"),

    /** Bring-your-own OpenRouter API key. */
    BYO_OPENROUTER(ChatMode.ByoOpenRouter.configValue, "Direct: OpenRouter (BYO key)"),

    /** Chat disabled; tool surfaces still available. */
    TOOLS_ONLY(ChatMode.ToolsOnly.configValue, "Tools only (no chat)"),
    ;

    override fun toString(): String = display

    /** Lift this UI choice into the typed [ChatMode] sealed hierarchy. */
    public fun toChatMode(): ChatMode = ChatMode.fromConfigValue(configValue)
}
