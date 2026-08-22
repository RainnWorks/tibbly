package co.rowm.osrsllm

import co.rowm.osrsllm.companion.CompanionConfig
import co.rowm.osrsllm.companion.PersonalityArchetype
import co.rowm.osrsllm.companion.Starter
import net.runelite.client.config.Config
import net.runelite.client.config.ConfigGroup
import net.runelite.client.config.ConfigItem

/**
 * Backing store for every Tibbly setting.
 *
 * Historical context: this used to be the rendered config form in the
 * RuneLite plugin sidebar — ~17 `@ConfigItem` rows split across two
 * `@ConfigSection`s. We pulled the whole UI inside the plugin panel
 * itself (cog button → settings view) so chat + config live in a
 * single pane. See `co.rowm.osrsllm.ui.TibblyPanel` for the new
 * surface and `docs/agents/DECISION_LOG.md` for the rationale.
 *
 * The methods below stay because RuneLite's `ConfigManager` mints a
 * proxy from this interface that everyone in the plugin reads through —
 * removing them would mean rewriting every call site to talk to
 * `ConfigManager` directly. Keeping the interface as a typed read view
 * (defaults baked in via Kotlin's default methods) is the cheap move.
 *
 * The only `@ConfigItem` left is a single pointer the player sees in
 * the standard RuneLite plugin settings tab — its description tells
 * them to use the cog button inside the panel instead. Hub reviewers
 * expect *something* in this tab, and an empty form looks broken.
 */
@ConfigGroup("osrsllm")
interface OsrsLlmHelperConfig : Config {

    /**
     * Single pointer item. Always reads false in code; it exists purely
     * so the RuneLite plugin settings tab has a row whose `description`
     * tells the player where the real settings live.
     */
    @ConfigItem(
        keyName = "_settingsLiveInPanel",
        name = "Settings",
        description = "Tibbly settings live inside the Tibbly side panel. " +
            "Click the cog (⚙) at the top of the panel to open them.",
        position = 1,
    )
    fun settingsLiveInPanel(): Boolean = false

    /* -------------------------------------------------------------------------
     * Cloud chat / consent
     * --------------------------------------------------------------------- */

    fun consentAccepted(): Boolean = false

    fun cloudChatEnabled(): Boolean = false

    fun backendUrl(): String = "wss://api.tibbly.io/plugin"

    /* -------------------------------------------------------------------------
     * Chat mode + BYOK
     * --------------------------------------------------------------------- */

    fun chatModeChoice(): ChatModeChoice = ChatModeChoice.CLOUD

    fun byoApiKey(): String = ""

    fun byoModel(): String = ""

    fun byoTelemetryOptIn(): Boolean = false

    /* -------------------------------------------------------------------------
     * Companion
     *
     * The companion is gated on `consentAccepted` AND `companionEnabled`.
     * With either off the renderer / state machine / orchestrator are
     * never wired up; the `:checkCompanionConsentGated` Gradle task scans
     * the companion/ package and fails on any path that bypasses
     * `consentAccepted()`. See `docs/product/EMBODIED_COMPANION.md`.
     * --------------------------------------------------------------------- */

    fun companionEnabled(): Boolean = true

    // RAI-73: keyName stays `companionStarter` to preserve persisted player
    // configs. Player-facing copy is reframed from "Companion form" (which
    // implied four different creatures) to "Companion personality". The
    // visual is always the same Probe; the four values pick the voice.
    // The row itself lives in the panel — see
    // `co.rowm.osrsllm.ui.sections.CompanionSection`, CompanionConfig.kt
    // KDoc and apps/plugin/docs/CONFIG.md for the post-pivot framing.
    fun companionStarter(): Starter = Starter.VETERAN

    fun companionName(): String = ""

    fun companionArchetype(): PersonalityArchetype = PersonalityArchetype.DRY_WIKI_VETERAN

    fun companionSpeechVerbosity(): Int = CompanionConfig.DEFAULT_VERBOSITY

    fun companionProactiveTriggersEnabled(): Boolean = true

    /**
     * Snapshot the player's companion preferences. Wire-in reads this
     * on startup so internal companion state always reflects the
     * persisted config without scattering reads across files.
     */
    fun companionConfig(): CompanionConfig = CompanionConfig(
        companionEnabled = companionEnabled(),
        starter = companionStarter(),
        companionName = companionName(),
        archetype = companionArchetype(),
        speechVerbosity = companionSpeechVerbosity().coerceIn(
            CompanionConfig.MIN_VERBOSITY,
            CompanionConfig.MAX_VERBOSITY,
        ),
        proactiveTriggersEnabled = companionProactiveTriggersEnabled(),
    )

    /* -------------------------------------------------------------------------
     * Developer-only knobs
     * --------------------------------------------------------------------- */

    fun developerMode(): Boolean = false

    fun localMcpEnabled(): Boolean = false

    fun localMcpHost(): String = "127.0.0.1"

    fun localMcpPort(): Int = 51823

    /** Resolve the typed [ChatMode] from the persisted UI choice. */
    fun chatMode(): ChatMode = chatModeChoice().toChatMode()
}
