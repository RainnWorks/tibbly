package co.rowm.osrsllm.companion

/**
 * Player-tunable companion behaviour. Modelled as a value class plus a
 * pair of enums so the existing [co.rowm.osrsllm.OsrsLlmHelperConfig]
 * surface can expose the knobs and the rest of the companion code
 * doesn't have to care about RuneLite's `@ConfigItem` machinery.
 *
 * The companion is gated by `consentAccepted` (existing item) AND
 * `companionEnabled` (added below). Both default to off after consent,
 * and the wire-in refuses to instantiate the renderer until both are on.
 *
 * Knobs:
 *  - [companionEnabled] - master switch.
 *  - [starter] - visual form. `Veteran` (default hooded humanoid),
 *    `Fox`, `Wisp`, `Golem`.
 *  - [companionName] - player-chosen nickname; passed to backend
 *    personality preamble.
 *  - [archetype] - voice archetype. Pre-set per starter but overridable.
 *  - [speechVerbosity] - 1 (mostly silent) .. 5 (chatty).
 *  - [proactiveTriggersEnabled] - when off, the companion only speaks
 *    when spoken to.
 */
data class CompanionConfig(
    val companionEnabled: Boolean = true,
    val starter: Starter = Starter.VETERAN,
    val companionName: String = "",
    val archetype: PersonalityArchetype = PersonalityArchetype.DRY_WIKI_VETERAN,
    val speechVerbosity: Int = DEFAULT_VERBOSITY,
    val proactiveTriggersEnabled: Boolean = true,
) {
    init {
        require(speechVerbosity in MIN_VERBOSITY..MAX_VERBOSITY) {
            "speechVerbosity must be in [$MIN_VERBOSITY, $MAX_VERBOSITY] (got $speechVerbosity)"
        }
    }

    /**
     * Default archetype derived from the starter when the player hasn't
     * overridden. Used by the in-game first-run picker so the dropdown
     * shows a sensible match.
     */
    companion object {
        const val MIN_VERBOSITY: Int = 1
        const val MAX_VERBOSITY: Int = 5
        const val DEFAULT_VERBOSITY: Int = 3
        /** Section key used by the RuneLite @ConfigSection annotation on the config interface. */
        const val SECTION_KEY: String = "tibblyCompanion"
        /** Stable section title - referenced by docs and the wire-in. */
        const val SECTION_TITLE: String = "Tibbly Companion"

        fun defaultArchetypeFor(starter: Starter): PersonalityArchetype = when (starter) {
            Starter.VETERAN -> PersonalityArchetype.DRY_WIKI_VETERAN
            Starter.FOX -> PersonalityArchetype.SARDONIC_VETERAN
            Starter.WISP -> PersonalityArchetype.SOFT_CONFUSED_FRIEND
            Starter.GOLEM -> PersonalityArchetype.EARNEST_HELPER
        }
    }
}

/**
 * Authored personality archetypes. Stored as enums on the plugin side so
 * the dropdown looks the same across sessions; the actual voice prefix
 * lives on the backend (a few hundred tokens per archetype, swapped in
 * before each turn).
 *
 * Names taken verbatim from `docs/product/EMBODIED_COMPANION.md` §5.
 */
enum class PersonalityArchetype(val id: String, val label: String, val description: String) {
    DRY_WIKI_VETERAN(
        id = "dry_wiki_veteran",
        label = "Dry wiki veteran",
        description = "Sparse, accurate, occasionally biting. Reads the wiki at you.",
    ),
    SOFT_CONFUSED_FRIEND(
        id = "soft_confused_friend",
        label = "Soft, confused friend",
        description = "Sweet, a little lost, cheers you on when you do well.",
    ),
    SARDONIC_VETERAN(
        id = "sardonic_veteran",
        label = "Sardonic veteran",
        description = "Been there, killed that. Says less than they know.",
    ),
    EARNEST_HELPER(
        id = "earnest_helper",
        label = "Earnest helper",
        description = "Genuinely wants to help. Always polite. Mostly upbeat.",
    );

    companion object {
        fun fromIdOrDefault(id: String?): PersonalityArchetype =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DRY_WIKI_VETERAN
    }
}
