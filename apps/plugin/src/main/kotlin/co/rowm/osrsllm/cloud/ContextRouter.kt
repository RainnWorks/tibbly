package co.rowm.osrsllm.cloud

import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keyword + game-state intent router for cloud tool gating (RAI-25).
 *
 * Given a user's message (plus optional GameStateContext hints), returns the
 * set of [ToolFamily]s the backend should expose on this turn ON TOP OF
 * [ToolFamily.CORE]. The output flows to the backend over the WS payload
 * `user_message.allowedTools[]` (see `OutboundPayload.ChatUserMessage` and
 * the backend's `AllowedTool` resolver).
 *
 * Design notes
 * ─────────────────────────────────────────────────────────────────────────
 *  - Keyword matching is intentionally simple (whole-word match, lowercase).
 *    The router is the cheap-and-recall-biased gate; the LLM has an escape
 *    hatch via `enable_tools(family)` mid-chat.
 *  - We BIAS TOWARD RECALL: matching a synonym for a family pulls in the
 *    related families that almost always co-occur (e.g. `slayer → combat`,
 *    `kill → combat + slayer + nav`, because a slayer monster query and a
 *    boss kill query are almost the same prompt shape).
 *  - `core` itself is implicit — the router returns the *non-core* families
 *    to enable; [ToolRegistry.allowedTools] always unions CORE in.
 *  - No remote logging: keyword matches are written to AuditLog so the user
 *    can see why their surface widened. Telemetry analytics is RAI-37's job.
 */
@Singleton
class ContextRouter @Inject constructor() {

    private val log = LoggerFactory.getLogger(ContextRouter::class.java)

    /**
     * Compact slice of game state available at routing time. The router only
     * uses fields that are cheap to assemble from the existing GameStateStore
     * snapshot — see `Snapshots.kt`. Optional everywhere; the keyword rules
     * are the dominant signal.
     */
    data class GameStateContext(
        /** True if the bank interface is currently open. Pulls BANKING in. */
        val bankOpen: Boolean = false,
        /** True if the GE interface is currently open. Pulls GE in. */
        val geOpen: Boolean = false,
        /** True if combat tracker is showing an active opponent. Pulls COMBAT in. */
        val inCombat: Boolean = false,
        /** True if a slayer task is currently assigned. Pulls SLAYER+COMBAT in. */
        val hasActiveSlayerTask: Boolean = false,
        /** True if the player has an active clue scroll in inventory. (clue is CORE.) */
        val hasActiveClue: Boolean = false,
    ) {
        companion object {
            val EMPTY = GameStateContext()
        }
    }

    /**
     * The rule table. Order is irrelevant; matches accumulate into a set.
     * Each rule is a `Regex` (compiled once) with a target family set.
     *
     * Keywords come from the RAI-25 brief verbatim. Where a brief term has
     * obvious synonyms ("teleport" → "tp"), we widen — the test suite asserts
     * the brief's required matches AND any synonyms we add stay green.
     */
    private val rules: List<Rule> = listOf(
        // slayer / task / assignment → slayer + combat
        rule(setOf(ToolFamily.SLAYER, ToolFamily.COMBAT),
            "slayer", "task", "tasks", "assignment", "assignments"),

        // clue / coordinate / cipher / anagram → core only (clue is core)
        rule(emptySet(), // returns only CORE (handled implicitly downstream)
            "clue", "clues", "coordinate", "coordinates", "cipher", "anagram", "anagrams", "riddle", "riddles"),

        // bank / withdraw / deposit / tag / tab → banking
        rule(setOf(ToolFamily.BANKING),
            "bank", "banking", "withdraw", "withdrawal", "deposit", "tag", "tags", "tab", "tabs"),

        // quest / hard diary → quest + quest_items
        rule(setOf(ToolFamily.QUEST, ToolFamily.QUEST_ITEMS),
            "quest", "quests", "questing", "diary", "diaries"),

        // kill / boss / fight / monster / dps → combat + slayer + nav
        rule(setOf(ToolFamily.COMBAT, ToolFamily.SLAYER, ToolFamily.NAV),
            "kill", "kills", "killing", "boss", "bosses", "fight", "fighting",
            "monster", "monsters", "dps", "pvm", "pvp"),

        // prayer flick / protect / piety / rigour / augury / projectile / tick → combat
        // (RAI-5 catalog §3 — supports get_active_prayers + get_target_projectiles.)
        rule(setOf(ToolFamily.COMBAT),
            "prayer", "prayers", "flick", "flicking", "protect", "piety", "rigour", "rigor",
            "augury", "chivalry", "projectile", "projectiles", "tick", "ticks", "nuke"),

        // raid / cox / tob / toa / boss-name shortcuts → raids + combat
        // (RAI-5 catalog §3 — supports get_raid_layout. Verzik / Olm / etc.
        // already pull COMBAT via the kill/boss rule; we add RAIDS here.)
        rule(setOf(ToolFamily.RAIDS, ToolFamily.COMBAT),
            "raid", "raids", "raiding",
            "cox", "chambers",
            "tob", "theatre", "theater",
            "toa", "tombs", "invocation", "invocations",
            "verzik", "sotetseg", "xarpus", "nylocas", "maiden",
            "olm", "vasa", "vespula", "vanguards", "tekton", "muttadiles",
            "akkha", "baba", "kephri", "zebak", "wardens", "warden"),

        // ge / grand exchange / buy / sell / price → ge
        rule(setOf(ToolFamily.GE),
            "ge", "grand", "exchange", "buy", "buying", "sell", "selling", "price", "prices", "flip"),

        // highlight / mark / flag / tile / outline → highlights
        rule(setOf(ToolFamily.HIGHLIGHTS),
            "highlight", "highlights", "mark", "marker", "markers", "flag", "tile", "tiles", "outline", "outlines"),

        // loadout / gear / equipment → loadouts + banking
        rule(setOf(ToolFamily.LOADOUTS, ToolFamily.BANKING),
            "loadout", "loadouts", "gear", "equipment", "armour", "armor", "kit"),

        // go to / teleport / run to / how do i get → nav
        rule(setOf(ToolFamily.NAV),
            "teleport", "teleports", "teleporting", "tp"),
        phraseRule(setOf(ToolFamily.NAV),
            "go to", "run to", "walk to", "head to", "get to",
            "how do i get", "how do i reach", "where is",
            "directions to", "fastest way", "shortest path"),

        // fish / fishing → fishing
        rule(setOf(ToolFamily.FISHING),
            "fish", "fishing", "fisher"),

        // xp / level / train → skills
        rule(setOf(ToolFamily.SKILLS),
            "xp", "exp", "experience", "level", "levels", "leveling", "train", "training"),

        // loot / drop / stack → groundstate
        rule(setOf(ToolFamily.GROUNDSTATE),
            "loot", "looting", "drop", "drops", "stack", "stacks", "pile"),

        // farm / patch / herb / tree / allotment / compost → farming
        // (RAI-5 catalog §3 — supports get_farming_summary + get_farming_patches.)
        // "tree" overlaps slightly with NAV-talk ("the spirit tree at Falador")
        // but the brief explicitly calls it out — we accept the recall bias.
        rule(setOf(ToolFamily.FARMING),
            "farm", "farms", "farming", "farmer",
            "patch", "patches",
            "herb", "herbs",
            "tree", "trees",
            "allotment", "allotments",
            "compost", "supercompost", "ultracompost",
            "tithe",
            "ranarr", "snapdragon", "torstol", "irit", "kwuarm", "toadflax",
            "harralander", "lantadyme", "avantoe", "dwarf",
            "fruit"),
        phraseRule(setOf(ToolFamily.FARMING),
            "snape grass", "fruit tree", "spirit tree", "magic seed",
            "palm tree", "calquat", "hardwood"),
    )

    /**
     * Returns the non-core families to enable on this turn. CORE is implicit
     * and is unioned in by [ToolRegistry.allowedTools].
     *
     * The returned set may be empty (e.g. a clue-step question) — that means
     * "core only", which is the cheapest possible turn.
     */
    fun route(
        userMessage: String,
        gameState: GameStateContext = GameStateContext.EMPTY,
    ): Set<ToolFamily> {
        val msg = userMessage.lowercase()
        val out = linkedSetOf<ToolFamily>()

        for (rule in rules) {
            if (rule.matches(msg)) out.addAll(rule.families)
        }

        // Game-state nudges — only ADD families. Never remove a keyword-matched one.
        if (gameState.bankOpen) out += ToolFamily.BANKING
        if (gameState.geOpen) out += ToolFamily.GE
        if (gameState.inCombat) out += ToolFamily.COMBAT
        if (gameState.hasActiveSlayerTask) {
            out += ToolFamily.SLAYER
            out += ToolFamily.COMBAT
        }
        // gameState.hasActiveClue is intentionally not nudged here — clue is CORE.

        if (log.isDebugEnabled) {
            log.debug("ContextRouter routed message [{}] → {}", userMessage.take(80), out)
        }
        return out
    }

    /**
     * Convenience pass-through used by `BackendWsClient` when building the
     * `ChatUserMessage` payload — returns the wire-form family names to attach
     * to `allowedTools[]`. CORE is appended last so the backend sees it.
     */
    fun routeWire(userMessage: String, gameState: GameStateContext = GameStateContext.EMPTY): List<String> {
        val matched = route(userMessage, gameState)
        return (matched.map { it.wireName() } + ToolFamily.CORE.wireName()).distinct()
    }

    // ── Rule helpers ─────────────────────────────────────────────────────

    private data class Rule(val families: Set<ToolFamily>, val pattern: Regex) {
        fun matches(text: String): Boolean = pattern.containsMatchIn(text)
    }

    /** Whole-word keyword match (word boundaries). Case-insensitive. */
    private fun rule(families: Set<ToolFamily>, vararg keywords: String): Rule {
        val joined = keywords.joinToString("|") { Regex.escape(it) }
        return Rule(families, Regex("\\b(?:$joined)\\b", RegexOption.IGNORE_CASE))
    }

    /** Phrase-match (allows multi-word triggers like "how do i get"). */
    private fun phraseRule(families: Set<ToolFamily>, vararg phrases: String): Rule {
        val joined = phrases.joinToString("|") { Regex.escape(it) }
        return Rule(families, Regex("(?:$joined)", RegexOption.IGNORE_CASE))
    }
}
