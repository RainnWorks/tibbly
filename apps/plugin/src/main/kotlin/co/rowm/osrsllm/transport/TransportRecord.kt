package co.rowm.osrsllm.transport

import kotlinx.serialization.Serializable

/**
 * Canonical schema for the transport DB. Imported initially from the
 * shortest-path plugin's TSVs (BSD-2-Clause); extensible with our own
 * augmentations on top.
 */
@Serializable
data class TransportRecord(
    /** Stable slug: "<category>:<idx>". */
    val id: String,
    /** Source bucket: teleportation_items, teleportation_spells, fairy_rings, … */
    val category: String,
    /** Human-readable name from the source data ("Display info"). */
    val name: String,
    /** Where you can be when invoking this. null = anywhere (carryable). */
    val origin: WorldPointXYZ? = null,
    /** Where you end up. */
    val destination: WorldPointXYZ,
    /** Optional human area name for the destination (derived from coords). */
    val destinationArea: String? = null,
    /** Cost in game ticks (0.6s each). */
    val durationTicks: Int = 0,
    /** ANDed groups, each = OR alternatives of items. Empty = no item req. */
    val itemReqs: List<List<ItemNeed>> = emptyList(),
    /** Quest names (FINISHED required). Use display names matching our QuestSnapshot. */
    val questReqs: List<String> = emptyList(),
    /** Skill name → required level. */
    val skillReqs: Map<String, Int> = emptyMap(),
    /** True if the item/spell is consumed per use (charges, runes, runed tabs). */
    val consumable: Boolean = false,
    /** Max wilderness level where this works. 20 = up to standard wildy line; 0 = unrestricted. */
    val wildernessLimit: Int? = null,
    /** Free-text notes (we layer our own metadata on top of the imported data). */
    val notes: String? = null,
    /** Origin: which TSV this row came from (or "manual" for hand-added). */
    val source: String = "shortest-path",
)

@Serializable
data class WorldPointXYZ(val x: Int, val y: Int, val plane: Int = 0)

@Serializable
data class ItemNeed(val itemId: Int, val count: Int = 1)

@Serializable
data class TransportDb(
    /** Schema version — bump when canonical shape changes. */
    val schema: String = "1.0",
    /** Where the data came from. */
    val sources: List<String> = emptyList(),
    /** Generation timestamp (ISO). */
    val generatedAt: String = "",
    /** The records themselves. */
    val transports: List<TransportRecord> = emptyList(),
)
