package co.rowm.osrsllm

/**
 * Three-tier value model for the chat surface. See
 * `docs/architecture/HUB_RELEASE_STRATEGY.md` for the full rationale.
 *
 * Each variant describes WHERE the chat request goes, NOT what tools are
 * available — tool surfaces stay live in every mode so other in-RuneLite
 * consumers (overlays, structured panels) keep working.
 *
 * Sealed (rather than enum) so future BYO providers can carry per-variant
 * data without breaking call sites, and so each variant can be referenced
 * as a concrete type at the egress allow-list level.
 */
public sealed class ChatMode {

    /** Stable wire/config key. Kept narrow + lowercase so the RuneLite
     * config value (a String) round-trips cleanly. */
    public abstract val configValue: String

    /**
     * Tibbly cloud (current production path). Plugin opens a single WSS
     * link to the Tibbly backend; the backend drives the LLM and bills
     * the player against their subscription. All data egress is funnelled
     * through `EgressGate.egress()`. Default for existing installs.
     */
    public object Cloud : ChatMode() {
        public override val configValue: String = "cloud"
    }

    /**
     * BYO Anthropic. Plugin talks DIRECTLY to `api.anthropic.com` using
     * the player-supplied API key. No request ever touches our backend.
     * The key is stored in RuneLite config with `secret = true`.
     */
    public object ByoAnthropic : ChatMode() {
        public override val configValue: String = "byo-anthropic"
    }

    /**
     * BYO OpenAI. Plugin talks DIRECTLY to `api.openai.com`. Same shape
     * as [ByoAnthropic] — the player owns the key, we never see it.
     */
    public object ByoOpenAi : ChatMode() {
        public override val configValue: String = "byo-openai"
    }

    /**
     * BYO OpenRouter. Plugin talks DIRECTLY to `openrouter.ai`. Gives
     * the player a single key + their pick of any model OpenRouter
     * routes.
     */
    public object ByoOpenRouter : ChatMode() {
        public override val configValue: String = "byo-openrouter"
    }

    /**
     * Tools-only. Chat panel is disabled but the in-RuneLite tool surface
     * (structured panels, overlays, slash commands) stays available. This
     * is the value-floor mode the hub reviewer can install with no API
     * key and no Tibbly account.
     */
    public object ToolsOnly : ChatMode() {
        public override val configValue: String = "tools-only"
    }

    /**
     * Convenience predicate — true iff this mode talks DIRECTLY to a
     * third-party LLM provider (i.e. any of the BYO variants). Useful
     * for the egress allow-list switch.
     */
    public val isByo: Boolean
        get() = this is ByoAnthropic || this is ByoOpenAi || this is ByoOpenRouter

    public companion object {

        /**
         * Parse the persisted RuneLite config string back into a [ChatMode].
         * Returns [Cloud] for an unknown value rather than throwing — the
         * config is user-edited and we must never crash the plugin on a
         * typo. The unknown-value case is logged at the call site.
         */
        @JvmStatic
        public fun fromConfigValue(raw: String?): ChatMode = when (raw?.trim()?.lowercase()) {
            Cloud.configValue -> Cloud
            ByoAnthropic.configValue -> ByoAnthropic
            ByoOpenAi.configValue -> ByoOpenAi
            ByoOpenRouter.configValue -> ByoOpenRouter
            ToolsOnly.configValue -> ToolsOnly
            else -> Cloud
        }

        /** Stable ordering for the RuneLite config dropdown. */
        @JvmStatic
        public val all: List<ChatMode> = listOf(Cloud, ByoAnthropic, ByoOpenAi, ByoOpenRouter, ToolsOnly)
    }
}
