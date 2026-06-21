package co.rowm.osrsllm.cloud

/**
 * Tool family tags used by the cloud-chat tool-gating router (RAI-25).
 *
 * Every MCP tool exposed by the plugin belongs to EXACTLY ONE family. The
 * router (see [ContextRouter]) expands the first-turn tool surface from
 * just [CORE] to `core + matched-family-set` based on keyword intent in the
 * user's message. Mid-conversation the LLM can call the meta-tool
 * `enable_tools(family)` (see [EnableToolsTool]) to widen the surface.
 *
 * Why this exists — token economy: the MCP catalog is ~72 tools at ~120 tokens
 * each (~8K input tokens before the user has typed a word). Gating drops the
 * turn-1 surface to ≤1.5K, which is the single largest margin lever in the
 * system (see docs/research/llm-providers/_SUMMARY.md and TOOL_ECONOMY.md).
 *
 * IMPORTANT: this enum is part of the wire contract — backend (RAI-2) maps
 * family names from `user_message.allowedTools[]` to the same buckets to
 * decide which Anthropic tool definitions to inline in the prompt. Renaming
 * a family is a breaking change.
 */
enum class ToolFamily {
    /**
     * Always-on tools. Things every chat benefits from regardless of intent:
     *   - identity / location / event log / inventory / equipment / stats
     *   - the wiki + ge price lookups
     *   - chat / notify channels back to the player
     *   - the meta-tool `enable_tools` itself, so the LLM can widen later
     *   - `get_active_clue` is core because clue-step queries arrive without
     *     a "clue" keyword (riddles, coordinates, anagrams).
     */
    CORE,

    /** Bank tabs, bank tag backups, bank state, withdrawal/deposit guidance. */
    BANKING,

    /** Quest tracking, quest data + walkthrough guides, diary progress. */
    QUEST,

    /** Quest-related items, loadouts pre-built for quest fights / item requirements. */
    QUEST_ITEMS,

    /** World navigation: pathfinding, transports, agility shortcuts, POIs. */
    NAV,

    /** Combat info, attack style, spellbook, buffs, hitsplats, achievements. */
    COMBAT,

    /** Grand Exchange offers + market info. */
    GE,

    /** Skill XP, training planners, level lookups. */
    SKILLS,

    /** In-game highlights: NPCs, objects, ground items, inventory items, tile markers. */
    HIGHLIGHTS,

    /** Saved equipment loadouts. */
    LOADOUTS,

    /** Ground items / scene loot / nearby objects / nearby npcs (world-around-me snapshot). */
    GROUNDSTATE,

    /** Fishing-spot lookup; placeholder for future fishing-specific tools. */
    FISHING,

    /** Group-mate state: party composition, group iron man membership, etc. */
    PARTY,

    /** Slayer task lookup, slayer master data, slayer-only tools. */
    SLAYER,
    ;

    /** Wire-form (`SLAYER` → `"slayer"`). Used on the backend payload. */
    fun wireName(): String = name.lowercase()

    companion object {
        /** Parses a wire-form family name; returns null on miss (caller decides whether to log). */
        fun fromWire(name: String): ToolFamily? = entries.firstOrNull { it.wireName() == name.lowercase() }
    }
}
