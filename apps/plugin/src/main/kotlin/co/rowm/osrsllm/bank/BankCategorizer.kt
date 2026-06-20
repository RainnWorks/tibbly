package co.rowm.osrsllm.bank

import co.rowm.osrsllm.ItemSnapshot

/**
 * Maps each bank item to zero or more **named buckets** ("food", "potions", "gear",
 * etc.) using only the snapshot fields we already have (id + name). No
 * `ItemComposition` lookups, so this is safe to call from any thread.
 *
 * Predicates are intentionally lenient — an item can match multiple categories
 * (e.g. *Saradomin brew(4)* is both a potion and food). Callers asking for
 * `category="food"` get every match; callers iterating items can see all the
 * categories an item belongs to via [categoriesFor].
 *
 * The category list is fixed and small — easier to maintain than a per-id table
 * of 30k items, and it covers the realistic vocabulary a player or agent uses
 * when asking "what food do I have", "show me my gear", etc.
 */
object BankCategorizer {

    /** Public list of category keys. Stable; the agent references these by name. */
    val ALL: List<String> = listOf(
        "food", "potions", "runes", "gear", "weapons", "ammo",
        "tools", "logs", "ores", "bars", "herbs", "seeds",
        "teleports", "currency",
    )

    /** Return every category an item belongs to (lowercase). May be empty. */
    fun categoriesFor(item: ItemSnapshot): List<String> {
        val out = ArrayList<String>(2)
        for (cat in ALL) if (predicate(cat)(item)) out += cat
        return out
    }

    /** Items in [items] that belong to [category]. */
    fun filter(category: String, items: List<ItemSnapshot>): List<ItemSnapshot> {
        val pred = predicate(category.lowercase()) { false }
        return items.filter(pred)
    }

    // ----- predicate definitions -----

    private fun predicate(category: String, default: (ItemSnapshot) -> Boolean = { false }):
        (ItemSnapshot) -> Boolean = when (category) {
        "food"      -> ::isFood
        "potions"   -> ::isPotion
        "runes"     -> ::isRune
        "gear"      -> ::isGear
        "weapons"   -> ::isWeapon
        "ammo"      -> ::isAmmo
        "tools"     -> ::isTool
        "logs"      -> ::isLog
        "ores"      -> ::isOre
        "bars"      -> ::isBar
        "herbs"     -> ::isHerb
        "seeds"     -> ::isSeed
        "teleports" -> ::isTeleport
        "currency"  -> ::isCurrency
        else        -> default
    }

    private val POTION_SUFFIX = Regex(""".*\(\d\)$""")

    /**
     * Foods. Detected by: known cooked-food names (fish, baked, etc.), name
     * patterns like "Cooked X" / "X pie" / "X cake" / "X stew", and a few special
     * cases like Saradomin brew. Tries to be inclusive — better to include a non-food
     * that happens to match than miss a food the player relies on.
     */
    private fun isFood(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (n in COOKED_FISH || n in OTHER_FOODS) return true
        if (n.endsWith(" pie") || n.endsWith(" cake") || n.endsWith(" stew") ||
            n.endsWith(" pizza") || n.endsWith(" bread") || n.endsWith(" bake")) return true
        if (n.startsWith("cooked ")) return true
        if (n.contains("saradomin brew")) return true // also a potion; intentional double-match
        if (n.contains("tuna potato") || n.contains("rocktail") || n.contains("baron shark")) return true
        return false
    }

    private fun isPotion(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        // (1)/(2)/(3)/(4) suffix is the OSRS potion convention — wide net but reliable.
        if (POTION_SUFFIX.matches(it.name)) return true
        if (n.contains(" potion") || n.contains(" mix")) return true
        if (n.contains("antidote") || n.contains("antifire") || n.contains("antivenom")) return true
        if (n in OTHER_POTIONS) return true
        return false
    }

    private fun isRune(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (n.endsWith(" rune") || n.endsWith(" runes")) return true
        if (n in RUNE_PACKS) return true
        return false
    }

    private fun isGear(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        // Armor parts + accessory slots. Excludes weapons handled separately.
        if (GEAR_NAME_PARTS.any { n.contains(it) }) return true
        // Many gear items end in "(b)", "(t)", "(g)" trim variants — strip and recheck
        if (GEAR_SUFFIXES.any { n.endsWith(it) }) return true
        return false
    }

    private fun isWeapon(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (WEAPON_NAME_PARTS.any { n.endsWith(" $it") || n.contains(" $it") || n.startsWith("$it ") }) return true
        if (n in NAMED_WEAPONS) return true
        return false
    }

    private fun isAmmo(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (n.endsWith(" arrow") || n.endsWith(" arrows")) return true
        if (n.endsWith(" bolt") || n.endsWith(" bolts") || n.contains(" bolts ")) return true
        if (n.endsWith(" dart") || n.endsWith(" darts")) return true
        if (n.endsWith(" javelin") || n.endsWith(" javelins")) return true
        if (n.endsWith(" knife") || n.endsWith(" knives") || n.endsWith(" throwing knives")) return true
        if (n.contains(" chinchompa")) return true
        return false
    }

    private fun isTool(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        return TOOL_SUFFIXES.any { n.endsWith(it) }
    }

    private fun isLog(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        return n.endsWith(" logs") || n == "logs"
    }

    private fun isOre(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        return n.endsWith(" ore")
    }

    private fun isBar(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        return n.endsWith(" bar")
    }

    private fun isHerb(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (n.startsWith("grimy ")) return true
        if (n in CLEAN_HERBS) return true
        return false
    }

    private fun isSeed(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        return n.endsWith(" seed") || n.endsWith(" seeds")
    }

    private fun isTeleport(it: ItemSnapshot): Boolean {
        val n = it.name.lowercase()
        if (n.contains("teleport") || n.endsWith(" tablet")) return true
        // Charged jewelry that teleports
        if (n.startsWith("amulet of glory") || n.startsWith("ring of dueling") ||
            n.startsWith("ring of wealth") || n.startsWith("games necklace") ||
            n.startsWith("combat bracelet") || n.startsWith("skills necklace") ||
            n.startsWith("digsite pendant") || n.startsWith("burning amulet")) return true
        return false
    }

    private fun isCurrency(it: ItemSnapshot): Boolean = when (it.id) {
        995, 13204 -> true // Coins, Platinum token
        else -> {
            val n = it.name.lowercase()
            n in CURRENCY_NAMES
        }
    }

    // ----- name vocabularies -----

    private val COOKED_FISH = setOf(
        "shrimps", "anchovies", "sardine", "herring", "mackerel", "trout", "pike",
        "salmon", "tuna", "lobster", "bass", "swordfish", "monkfish", "shark",
        "sea turtle", "manta ray", "anglerfish", "dark crab", "karambwan",
    )

    private val OTHER_FOODS = setOf(
        "bread", "cake", "chocolate cake", "potato", "baked potato",
        "egg potato", "chilli potato", "mushroom potato", "potato with butter",
        "potato with cheese", "stew", "curry", "fishcake",
    )

    private val OTHER_POTIONS = setOf(
        "imbued heart", "overload", "saradomin brew", "zamorak brew", "salt-water spritzer",
    )

    private val RUNE_PACKS = setOf(
        "rune pouch", "divine rune pouch", "small rune pouch", "rune pack",
    )

    private val GEAR_NAME_PARTS = listOf(
        "helm", "hood", "coif", "hat", "mask", "cowl", " body", "chestplate",
        "platebody", "robetop", "robe top", "robe bottom", "plateskirt",
        "platelegs", "chaps", "leatherskirt", "skirt", "tassets", "trousers",
        "kiteshield", "sq shield", " shield", " buckler", "defender",
        " cape", " cloak", " amulet", " necklace", " ring", " bracelet",
        " gloves", " gauntlets", " mitts", " vambraces", " boots", " sandals",
        " shoes", "blessed d'hide",
    )

    private val GEAR_SUFFIXES = listOf(" (g)", " (t)", " (b)", " (or)", " (i)", " (a)")

    private val WEAPON_NAME_PARTS = listOf(
        "sword", "scimitar", "longsword", "shortsword", "dagger", "rapier",
        "mace", "warhammer", "battleaxe", "halberd", "hasta", "spear",
        "bow", "crossbow", "blowpipe", "wand", "staff", "trident",
        "whip", "claws", "godsword", "axe", "scythe", "maul",
    )

    private val NAMED_WEAPONS = setOf(
        "abyssal whip", "abyssal tentacle", "dragon claws", "armadyl godsword",
        "saradomin godsword", "bandos godsword", "zamorak godsword",
        "elder maul", "voidwaker", "noxious halberd", "ghrazi rapier",
        "scythe of vitur", "tumeken's shadow", "twisted bow", "zaryte crossbow",
        "armadyl crossbow", "toxic blowpipe", "bow of faerdhinen", "blade of saeldor",
    )

    private val TOOL_SUFFIXES = listOf(
        " pickaxe", " hatchet", " axe", " fishing rod", " harpoon",
        " net", " hammer", " chisel", " knife", " tinderbox", " spade",
        " bucket", " rake", " seed dibber", " watering can",
    )

    private val CLEAN_HERBS = setOf(
        "guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed",
        "toadflax", "irit leaf", "avantoe", "kwuarm", "snapdragon",
        "cadantine", "lantadyme", "dwarf weed", "torstol",
    )

    private val CURRENCY_NAMES = setOf(
        "coins", "platinum token", "blood money", "tokkul", "trading sticks",
        "marks of grace", "warriors' guild token",
    )
}
