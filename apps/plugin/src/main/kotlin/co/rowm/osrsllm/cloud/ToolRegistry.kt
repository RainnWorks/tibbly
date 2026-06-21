package co.rowm.osrsllm.cloud

/**
 * Declarative `tool-name → family` table that drives cloud-side tool gating (RAI-25).
 *
 * Sourced 1:1 from `local/McpServerService.kt`'s registered tools — the names
 * here MUST mirror the names there, because the backend will use these
 * families to decide which Anthropic tool definitions to ship in the prompt
 * for a given turn. The cloud tool surface for any turn is:
 *
 *     core + matched-family-set-from-router + families-the-LLM-has-enabled-mid-chat
 *
 * # Invariants
 *
 *  - Every tool appears EXACTLY ONCE. The mapping is asserted by
 *    [ToolRegistryTest].
 *  - The set of tool names here must equal the set registered in
 *    `local/McpServerService.kt`. Tools added to MCP without a family
 *    assignment fail [ToolRegistryTest] (the test cross-references the
 *    MCP tool list via [ALL_MCP_TOOLS] below).
 *  - The meta-tool `enable_tools` lives in [ToolFamily.CORE] so the LLM can
 *    always reach it to widen the surface mid-turn.
 *
 * # Why this is a flat Map and not e.g. annotations on the MCP register calls
 *
 *  - `local/McpServerService.kt` is the developer-only debug path and ships
 *    with `developerMode = false` by default (see `apps/plugin/SECURITY_DESIGN.md`).
 *    Mixing cloud routing concerns into that file would couple the audited
 *    legibility surface to growth-feature work. Keeping a separate
 *    declarative table in `cloud/` is the simplest way to stay legible.
 */
object ToolRegistry {

    /**
     * The single source of truth: tool-name → owning family.
     *
     * Ordering: alphabetical within each family block, families grouped to make
     * code review easy.
     */
    val FAMILY: Map<String, ToolFamily> = linkedMapOf(
        // ── CORE (always-on) ──────────────────────────────────────────────
        "chat_message" to ToolFamily.CORE,
        "enable_tools" to ToolFamily.CORE,
        "find_item" to ToolFamily.CORE,
        "ge_price" to ToolFamily.CORE,
        "get_account_identity" to ToolFamily.CORE,
        "get_active_clue" to ToolFamily.CORE,
        "get_equipment" to ToolFamily.CORE,
        "get_event_log" to ToolFamily.CORE,
        "get_inventory" to ToolFamily.CORE,
        "get_player_state" to ToolFamily.CORE,
        "get_stats" to ToolFamily.CORE,
        "list_open_interfaces" to ToolFamily.CORE,
        "notify_player" to ToolFamily.CORE,
        "read_interface" to ToolFamily.CORE,
        "wiki_page" to ToolFamily.CORE,
        "wiki_search" to ToolFamily.CORE,

        // ── BANKING ───────────────────────────────────────────────────────
        "create_bank_tab" to ToolFamily.BANKING,
        "create_grouped_bank_tab" to ToolFamily.BANKING,
        "get_bank" to ToolFamily.BANKING,
        "get_bank_tab" to ToolFamily.BANKING,
        "list_bank_tabs" to ToolFamily.BANKING,
        "list_bank_tag_backups" to ToolFamily.BANKING,
        "list_item_tags" to ToolFamily.BANKING,
        "open_bank_tab" to ToolFamily.BANKING,
        "remove_bank_tab" to ToolFamily.BANKING,
        "restore_bank_tag_backup" to ToolFamily.BANKING,

        // ── QUEST ─────────────────────────────────────────────────────────
        "get_diary_progress" to ToolFamily.QUEST,
        "get_quest_data" to ToolFamily.QUEST,
        "get_quest_guide" to ToolFamily.QUEST,
        "get_quests" to ToolFamily.QUEST,

        // ── QUEST_ITEMS ───────────────────────────────────────────────────
        "get_item_mapping" to ToolFamily.QUEST_ITEMS,

        // ── NAV ───────────────────────────────────────────────────────────
        "find_agility_shortcut" to ToolFamily.NAV,
        "find_location" to ToolFamily.NAV,
        "find_nearest_pois" to ToolFamily.NAV,
        "find_transport" to ToolFamily.NAV,
        "show_path_to" to ToolFamily.NAV,

        // ── COMBAT ────────────────────────────────────────────────────────
        "get_active_prayers" to ToolFamily.COMBAT,
        "get_attack_style" to ToolFamily.COMBAT,
        "get_buffs" to ToolFamily.COMBAT,
        "get_combat_achievements" to ToolFamily.COMBAT,
        "get_combat_info" to ToolFamily.COMBAT,
        "get_hitsplat_history" to ToolFamily.COMBAT,
        "get_npc_max_hp" to ToolFamily.COMBAT,
        "get_poh" to ToolFamily.COMBAT,
        "get_spellbook" to ToolFamily.COMBAT,
        "get_target_projectiles" to ToolFamily.COMBAT,

        // ── GE ────────────────────────────────────────────────────────────
        "get_ge_offers" to ToolFamily.GE,

        // ── SKILLS ────────────────────────────────────────────────────────
        "get_session_loot" to ToolFamily.SKILLS,
        "get_xp_rates" to ToolFamily.SKILLS,

        // ── HIGHLIGHTS ────────────────────────────────────────────────────
        "add_ground_item_highlights" to ToolFamily.HIGHLIGHTS,
        "add_inventory_highlights" to ToolFamily.HIGHLIGHTS,
        "add_npc_highlights" to ToolFamily.HIGHLIGHTS,
        "add_object_highlights" to ToolFamily.HIGHLIGHTS,
        "add_persistent_markers" to ToolFamily.HIGHLIGHTS,
        "clear_visuals" to ToolFamily.HIGHLIGHTS,
        "list_ground_item_highlights" to ToolFamily.HIGHLIGHTS,
        "list_inventory_highlights" to ToolFamily.HIGHLIGHTS,
        "list_npc_highlights" to ToolFamily.HIGHLIGHTS,
        "list_object_highlights" to ToolFamily.HIGHLIGHTS,
        "list_persistent_markers" to ToolFamily.HIGHLIGHTS,
        "list_user_markers" to ToolFamily.HIGHLIGHTS,
        "mark_tile" to ToolFamily.HIGHLIGHTS,
        "point_at" to ToolFamily.HIGHLIGHTS,
        "remove_ground_item_highlight" to ToolFamily.HIGHLIGHTS,
        "remove_inventory_highlight" to ToolFamily.HIGHLIGHTS,
        "remove_npc_highlight" to ToolFamily.HIGHLIGHTS,
        "remove_object_highlight" to ToolFamily.HIGHLIGHTS,
        "remove_persistent_markers" to ToolFamily.HIGHLIGHTS,

        // ── LOADOUTS ──────────────────────────────────────────────────────
        "best_in_slot_from_bank" to ToolFamily.LOADOUTS,
        "save_equipment_loadout" to ToolFamily.LOADOUTS,

        // ── GROUNDSTATE ───────────────────────────────────────────────────
        "get_ground_items" to ToolFamily.GROUNDSTATE,
        "get_nearby_npcs" to ToolFamily.GROUNDSTATE,
        "get_nearby_objects" to ToolFamily.GROUNDSTATE,

        // ── FISHING ───────────────────────────────────────────────────────
        "find_fishing_spot" to ToolFamily.FISHING,

        // ── PARTY ─────────────────────────────────────────────────────────
        "get_party" to ToolFamily.PARTY,

        // ── SLAYER ────────────────────────────────────────────────────────
        "get_slayer_task" to ToolFamily.SLAYER,

        // ── RAIDS (RAI-5 Tier 0) ──────────────────────────────────────────
        "get_raid_layout" to ToolFamily.RAIDS,

        // ── FARMING (RAI-5 Tier 0 — split from get_farming_state) ─────────
        "get_farming_summary" to ToolFamily.FARMING,
        "get_farming_patches" to ToolFamily.FARMING,
    )

    /**
     * Mirror of every tool name registered in `local/McpServerService.kt`.
     *
     * REGRESSION GUARD: the cross-check in [ToolRegistryTest] proves that
     * [FAMILY].keys == this set. If you add a new MCP tool, add it here AND
     * to [FAMILY] in the same PR. The lists going out of sync is the failure
     * mode this exists to catch.
     *
     * Source of truth: scan `name = "..."` and `addLoggedTool(server, "..."`
     * in `local/McpServerService.kt`, plus the 12 names produced by
     * `registerHighlightTools` (4 families × {add, remove, list}).
     */
    val ALL_MCP_TOOLS: Set<String> = setOf(
        // toolNames.addAll(...) block
        "get_bank", "get_quests", "get_nearby_npcs", "get_event_log",
        "get_ground_items", "get_nearby_objects",
        "find_location", "find_nearest_pois", "find_transport", "ge_price",
        "list_bank_tabs", "get_bank_tab", "create_bank_tab", "remove_bank_tab", "open_bank_tab",
        "create_grouped_bank_tab",
        "list_bank_tag_backups", "restore_bank_tag_backup",
        "wiki_search", "wiki_page", "get_quest_guide", "get_quest_data",
        "list_open_interfaces", "read_interface",
        "save_equipment_loadout",
        "find_item",
        "list_item_tags",
        "show_path_to", "mark_tile", "point_at", "clear_visuals",
        "best_in_slot_from_bank",
        "get_slayer_task", "get_xp_rates", "get_active_clue", "get_party",
        "notify_player", "chat_message",
        "find_agility_shortcut", "find_fishing_spot", "get_item_mapping",
        "list_user_markers",
        "add_persistent_markers", "remove_persistent_markers", "list_persistent_markers",
        "get_npc_max_hp",
        // addLoggedTool registrations
        "get_inventory", "get_equipment", "get_stats",
        "get_ge_offers",
        "get_combat_info", "get_diary_progress", "get_spellbook", "get_attack_style",
        "get_session_loot", "get_hitsplat_history", "get_combat_achievements",
        "get_poh", "get_buffs", "get_player_state",
        // RAI-5 Tier 0 state-probes (see cloud/tools/StateProbes.kt)
        "get_account_identity", "get_raid_layout", "get_target_projectiles", "get_active_prayers",
        // RAI-5 Tier 0 (5/5) — farming split (see cloud/tools/FarmingTables.kt)
        "get_farming_summary", "get_farming_patches",
        // registerHighlightTools — 4 families × 3 verbs
        "add_npc_highlights", "remove_npc_highlight", "list_npc_highlights",
        "add_object_highlights", "remove_object_highlight", "list_object_highlights",
        "add_ground_item_highlights", "remove_ground_item_highlight", "list_ground_item_highlights",
        "add_inventory_highlights", "remove_inventory_highlight", "list_inventory_highlights",
        // Cloud-only meta-tool — handled here, not by MCP
        "enable_tools",
    )

    /** Family of a tool name, or null if unknown (caller decides whether to log). */
    fun familyOf(toolName: String): ToolFamily? = FAMILY[toolName]

    /** All tool names belonging to [family], in registration order. */
    fun toolsIn(family: ToolFamily): List<String> =
        FAMILY.entries.asSequence().filter { it.value == family }.map { it.key }.toList()

    /**
     * Compute the allowed-tool set for a turn given the families the router
     * picked. CORE is always included. Unknown families are dropped silently
     * — the router never invents families.
     */
    fun allowedTools(families: Set<ToolFamily>): List<String> {
        val effective = families + ToolFamily.CORE
        return FAMILY.entries.asSequence()
            .filter { it.value in effective }
            .map { it.key }
            .toList()
    }
}
