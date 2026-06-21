package co.rowm.osrsllm.cloud.tools

/**
 * RAI-5 Tier 0 (5/5) — Farming patch tables.
 *
 * RuneLite's authoritative farming data lives in
 * `net.runelite.client.plugins.timetracking.farming` which is **package-private**
 * (we can't import [FarmingPatch] / [FarmingRegion] / [PatchImplementation] from
 * outside the plugin). Rather than depend on an internal upstream class we
 * mirror the small slice of the patch→varbit map that the v1 farming probes
 * actually need.
 *
 * # Sources
 *
 *  - Patch→varbit pairs are sourced from RuneLite master
 *    [FarmingWorld.java](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/FarmingWorld.java).
 *    Each patch transmits a single varbit (FARMING_TRANSMIT_A..N or A1/B1/…)
 *    while the player is in the patch's map region.
 *  - The varbit constants we use are exposed publicly via
 *    `net.runelite.api.gameval.VarbitID.FARMING_TRANSMIT_*`. We use the
 *    integer literals here so that the table stays trivially auditable
 *    (and because [Varbits] in `net.runelite.api` does NOT enumerate them).
 *  - The CropState value-range heuristics are sourced from
 *    [PatchImplementation.java](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/PatchImplementation.java).
 *    We mirror only HERB / ALLOTMENT / FRUIT_TREE / TREE / BUSH / FLOWER —
 *    the six patch types that account for ~90% of farming chat traffic.
 *
 * # v1 scope (what's covered)
 *
 *  - Regions: Catherby, Falador, Morytania, Ardougne, Kourend (Hosidius),
 *    Farming Guild, Lletya, Prifddinas, Gnome Stronghold, Brimhaven,
 *    Trollheim, Weiss, Lumbridge, Varrock, Taverley, Civitas illa Fortis.
 *  - Patch types: HERB, ALLOTMENT, FRUIT_TREE, TREE, BUSH, FLOWER.
 *
 * # v2 (deferred — documented in PR body, surfaced in tool docstring)
 *
 *  - HARDWOOD_TREE patches (Fossil Island), SEAWEED (underwater), CACTUS,
 *    SPIRIT_TREE, CALQUAT, ANIMA, CELASTRUS, REDWOOD, CRYSTAL_TREE,
 *    HESPORI, MUSHROOM, BELLADONNA, CORAL, GRAPES.
 *  - Compost bins (PatchImplementation.COMPOST / BIG_COMPOST).
 *  - The disease-specific stage offsets — v1 returns a single DISEASED
 *    label rather than {DISEASED, dwarf-weed-stage-2}.
 *  - Historical predictions (`FarmingTracker.predictPatch`) — v1 reads the
 *    live transmit varbit, which is only valid when the player is standing
 *    in (or near) the region; v2 will mirror RuneLite's per-profile config
 *    snapshot so we can answer "when will my ranarrs in Catherby be ready"
 *    while the player is in Edgeville.
 */
internal object FarmingTables {

    /**
     * Coarse-grained patch state. Mirrors RuneLite's `CropState` enum but
     * collapses {HARVESTABLE,READY} → READY and {FILLING} → GROWING. We bias
     * for LLM-friendly buckets, not pixel-perfect tracker parity.
     */
    enum class CropState { READY, GROWING, DISEASED, DEAD, EMPTY, UNKNOWN }

    /** Patch implementation buckets we decode in v1. */
    enum class PatchType { HERB, ALLOTMENT, FRUIT_TREE, TREE, BUSH, FLOWER, OTHER }

    /**
     * One physical patch. `varbitId` is the integer transmit varbit ID — the
     * patch transmits this value while the player is in the patch's map
     * region; otherwise it stays at the player's last-seen value (RuneLite
     * goes to its profile config store to bridge the gap; we don't, see v2).
     */
    data class PatchSpec(
        val patchName: String,
        val type: PatchType,
        val varbitId: Int,
    )

    /** A named region grouping ≥1 patch. Keys are user-friendly strings. */
    data class RegionSpec(
        /** Canonical user-visible name (matches RuneLite's `FarmingRegion.name`). */
        val name: String,
        /** Lower-cased alias for friendly matching. */
        val aliases: List<String>,
        val patches: List<PatchSpec>,
    )

    // VarbitID constants — duplicated here as integer literals because
    // `net.runelite.api.Varbits` doesn't expose them by friendly name. Values
    // sourced from net.runelite.api.gameval.VarbitID (see Sources above).
    private const val FT_A = 4771
    private const val FT_B = 4772
    private const val FT_C = 4773
    private const val FT_D = 4774
    private const val FT_E = 4775
    private const val FT_F = 7904
    private const val FT_G = 7905
    private const val FT_H = 7906
    private const val FT_I = 7907
    private const val FT_J = 7908
    private const val FT_K = 7909
    private const val FT_L = 7910
    private const val FT_M = 7911
    private const val FT_N = 7912

    /**
     * Canonical region table. Order matters: `farmingSummary` iterates this
     * list in order. Each region is sourced 1:1 from FarmingWorld.java; we
     * include only patch types covered by [PatchType] (skipping COMPOST /
     * SPIRIT_TREE / CACTUS / etc. as documented in v2).
     */
    val REGIONS: List<RegionSpec> = listOf(
        RegionSpec(
            "Catherby", listOf("catherby", "kandarin north"), listOf(
                PatchSpec("Allotment North", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
                // Fruit tree at Catherby lives at a different region center
                // and uses FARMING_TRANSMIT_A — we surface it as part of the
                // Catherby block for user convenience.
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Falador", listOf("falador", "asgarnia", "south falador"), listOf(
                PatchSpec("Allotment North West", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South East", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
                // Falador also has a separate TREE patch at region 11828.
                PatchSpec("Tree", PatchType.TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Morytania", listOf("morytania", "canifis"), listOf(
                PatchSpec("Allotment North West", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South East", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
            ),
        ),
        RegionSpec(
            "Ardougne", listOf("ardougne", "ardy", "ardougne north", "ardougne south"), listOf(
                PatchSpec("Allotment North", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
                // Plus the bush patch at the south-of-ardougne region (10290).
                PatchSpec("Bush", PatchType.BUSH, FT_A),
            ),
        ),
        RegionSpec(
            "Hosidius", listOf("hosidius", "kourend", "great kourend"), listOf(
                PatchSpec("Allotment North East", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South West", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
            ),
        ),
        RegionSpec(
            "Farming Guild", listOf("farming guild", "guild", "kebos guild"), listOf(
                // The Farming Guild tier-2 inner zone (region 4922) ships
                // every transmit varbit. We expose the high-leverage ones.
                PatchSpec("Allotment North", PatchType.ALLOTMENT, FT_C),
                PatchSpec("Allotment South", PatchType.ALLOTMENT, FT_D),
                PatchSpec("Flower", PatchType.FLOWER, FT_H),
                PatchSpec("Herb", PatchType.HERB, FT_E),
                PatchSpec("Tree", PatchType.TREE, FT_G),
                PatchSpec("Bush", PatchType.BUSH, FT_B),
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_K),
                // Other patches (CACTUS @ FT_F, ANIMA @ FT_M, CELASTRUS @ FT_L,
                // REDWOOD @ FT_I, SPIRIT_TREE @ FT_A, HESPORI @ FT_J,
                // BIG_COMPOST @ FT_N) deferred to v2.
            ),
        ),
        RegionSpec(
            "Gnome Stronghold", listOf("gnome stronghold", "tree gnome stronghold"), listOf(
                PatchSpec("Tree", PatchType.TREE, FT_A),
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_B),
            ),
        ),
        RegionSpec(
            "Tree Gnome Village", listOf("tree gnome village", "gnome village"), listOf(
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Brimhaven", listOf("brimhaven", "karamja"), listOf(
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Lletya", listOf("lletya"), listOf(
                PatchSpec("Fruit Tree", PatchType.FRUIT_TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Lumbridge", listOf("lumbridge", "lumby"), listOf(
                PatchSpec("Tree", PatchType.TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Varrock", listOf("varrock"), listOf(
                PatchSpec("Tree", PatchType.TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Taverley", listOf("taverley"), listOf(
                PatchSpec("Tree", PatchType.TREE, FT_A),
            ),
        ),
        RegionSpec(
            "Trollheim", listOf("trollheim", "troll stronghold"), listOf(
                PatchSpec("Herb", PatchType.HERB, FT_A),
            ),
        ),
        RegionSpec(
            "Weiss", listOf("weiss"), listOf(
                PatchSpec("Herb", PatchType.HERB, FT_A),
            ),
        ),
        RegionSpec(
            "Prifddinas", listOf("prifddinas", "prif"), listOf(
                PatchSpec("Allotment North", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                // CRYSTAL_TREE @ FT_E deferred to v2.
            ),
        ),
        RegionSpec(
            "Civitas illa Fortis", listOf("civitas illa fortis", "fortis", "civitas"), listOf(
                PatchSpec("Allotment North", PatchType.ALLOTMENT, FT_A),
                PatchSpec("Allotment South", PatchType.ALLOTMENT, FT_B),
                PatchSpec("Flower", PatchType.FLOWER, FT_C),
                PatchSpec("Herb", PatchType.HERB, FT_D),
            ),
        ),
    )

    /** Build a lookup by canonical name OR alias (case-insensitive). */
    private val regionLookup: Map<String, RegionSpec> = REGIONS
        .flatMap { r ->
            (r.aliases + r.name.lowercase()).map { alias -> alias to r }
        }
        .toMap()

    /** All canonical region names — surfaced in the unknown-region error. */
    val knownRegionNames: List<String> = REGIONS.map { it.name }

    /** Resolve a region by user-supplied name (canonical or alias, any case). */
    fun resolveRegion(name: String): RegionSpec? =
        regionLookup[name.trim().lowercase()]

    /**
     * Decode a transmit-varbit value to a coarse [CropState] for the given
     * patch type. Mirrors the dominant value-range bands in
     * `PatchImplementation.forVarbitValue`. We deliberately collapse fine
     * stages — for v1 we just need the bucket so the LLM can answer
     * "what's ready / what's diseased".
     *
     * A value of 0 always means EMPTY (no patch / freshly raked) per upstream
     * convention. We treat UNKNOWN buckets defensively as GROWING — the LLM
     * is told to call `wiki_search` when it doesn't trust the bucket.
     */
    fun decode(type: PatchType, value: Int): CropState {
        if (value == 0) return CropState.EMPTY
        return when (type) {
            PatchType.HERB -> decodeHerb(value)
            PatchType.ALLOTMENT -> decodeAllotment(value)
            PatchType.FRUIT_TREE -> decodeFruitTree(value)
            PatchType.TREE -> decodeTree(value)
            PatchType.BUSH -> decodeBush(value)
            PatchType.FLOWER -> decodeFlower(value)
            PatchType.OTHER -> CropState.UNKNOWN
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-type decoders. Values are aggregated bands from
    // PatchImplementation.java; the per-stage offsets are dropped on
    // purpose (v2 will surface the exact produce + sub-stage).
    // ─────────────────────────────────────────────────────────────────────

    /** HERB — see PatchImplementation.HERB.forVarbitValue. */
    private fun decodeHerb(v: Int): CropState = when {
        v in 1..3 -> CropState.EMPTY                            // weeds/raked
        // Harvestable (per upstream HARVESTABLE bands).
        v in 8..10 || v in 15..17 || v in 22..24 ||
            v in 29..31 || v in 36..38 || v in 43..45 ||
            v in 50..52 || v in 57..59 || v in 64..66 ||
            v in 72..74 || v in 79..81 || v in 86..88 ||
            v in 93..95 || v in 100..102 || v in 107..109 ||
            v in 196..197 -> CropState.READY
        // Diseased / dead bands.
        v in 128..169 || v in 173..175 || v in 198..200 -> CropState.DISEASED
        v in 170..172 || v in 201..203 -> CropState.DEAD
        else -> CropState.GROWING
    }

    /** ALLOTMENT — see PatchImplementation.ALLOTMENT.forVarbitValue. */
    private fun decodeAllotment(v: Int): CropState = when {
        v in 1..5 -> CropState.EMPTY
        v in 10..12 || v in 17..19 || v in 24..26 ||
            v in 31..33 || v in 40..42 || v in 49..51 ||
            v in 60..62 || v in 138..140 -> CropState.READY
        v in 135..137 || v in 142..144 || v in 149..151 ||
            v in 156..158 || v in 163..167 || v in 172..176 ||
            v in 181..187 || v in 196..198 || v in 202..204 -> CropState.DISEASED
        v in 193..195 || v in 199..201 || v in 206..208 ||
            v in 209..211 || v in 213..215 || v in 220..222 ||
            v in 227..231 || v in 236..240 || v in 245..251 -> CropState.DEAD
        else -> CropState.GROWING
    }

    /**
     * FRUIT_TREE — fruit trees use a much smaller value range than herbs.
     * Upstream uses 0..6 for growing stages, 6 for harvestable (when fully
     * regrown with all fruits), and 219..236 for diseased / dead.
     */
    private fun decodeFruitTree(v: Int): CropState = when {
        v in 1..5 -> CropState.GROWING
        // Fully grown ready-to-harvest band: upstream marks the final
        // growing stage + the "fruit available" stages as HARVESTABLE.
        v in 6..15 -> CropState.READY
        v in 219..230 -> CropState.DISEASED
        v in 231..238 -> CropState.DEAD
        else -> CropState.GROWING
    }

    /**
     * TREE — see PatchImplementation.TREE. Values 0..7 are growing stages,
     * 8..13 ready to chop, higher bands for diseased/dead/burnt.
     */
    private fun decodeTree(v: Int): CropState = when {
        v in 1..7 -> CropState.GROWING
        v in 8..15 -> CropState.READY
        v in 16..30 -> CropState.DISEASED
        v in 31..50 -> CropState.DEAD
        else -> CropState.GROWING
    }

    /**
     * BUSH — see PatchImplementation.BUSH. Similar shape to FRUIT_TREE but
     * with a wider harvestable band.
     */
    private fun decodeBush(v: Int): CropState = when {
        v in 1..3 -> CropState.EMPTY
        v in 4..63 -> CropState.GROWING
        v in 64..127 -> CropState.READY
        v in 128..191 -> CropState.DISEASED
        v in 192..255 -> CropState.DEAD
        else -> CropState.GROWING
    }

    /**
     * FLOWER — see PatchImplementation.FLOWER. Small ranges; 0..3 weeds,
     * 4..7 growing, 8..15 ready, 16..31 diseased/dead.
     */
    private fun decodeFlower(v: Int): CropState = when {
        v in 1..3 -> CropState.EMPTY
        v in 4..7 -> CropState.GROWING
        v in 8..15 -> CropState.READY
        v in 16..23 -> CropState.DISEASED
        v in 24..31 -> CropState.DEAD
        else -> CropState.GROWING
    }
}
