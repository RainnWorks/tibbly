package co.rowm.osrsllm.chat

import co.rowm.osrsllm.BuffsSnapshot
import co.rowm.osrsllm.GameStateStore
import co.rowm.osrsllm.banktags.BankTagService
import co.rowm.osrsllm.events.EventLogService
import co.rowm.osrsllm.widgets.WidgetTracker

/**
 * Builds the "Current state" preamble that's injected at the top of every claude turn.
 *
 * The agent doesn't need to burn a tool call asking for the player's location, HP,
 * inventory count, current slayer task, or what just happened — that information is
 * already on the atomic snapshot and the bounded event log. We render it as compact
 * markdown so the agent can read it before deciding whether a tool call is even needed.
 *
 * Kept tight: only the fields the agent will most often want. Heavy data (full bank,
 * full quest list, hitsplat-by-hitsplat history) stays behind tool calls.
 */
object HarnessContext {

    fun build(
        store: GameStateStore,
        eventLog: EventLogService,
        widgets: WidgetTracker? = null,
        bankTags: BankTagService? = null,
        slayer: co.rowm.osrsllm.integrations.SlayerIntegration? = null,
        xpTracker: co.rowm.osrsllm.integrations.XpTrackerIntegration? = null,
        clueScroll: co.rowm.osrsllm.integrations.ClueScrollIntegration? = null,
        party: co.rowm.osrsllm.integrations.PartyIntegration? = null,
    ): String {
        val snap = store.snapshot()
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        sb.appendLine("# Current state")

        if (!snap.loggedIn || snap.player == null) {
            sb.append(
                "Logged out. Live player tools will return not-ready. " +
                    "Wiki, ge_price, find_location, find_transport, find_nearest_pois still work.",
            )
            return sb.toString()
        }

        val p = snap.player
        sb.append("Player: ").append(p.name ?: "?")
            .append(" — ").append(p.accountType)
            .append(", combat ").append(p.combatLevel)
            .append(", world ").append(p.world).appendLine()
        ironmanNote(p.accountType)?.let { sb.appendLine(it) }
        if (p.locationX != null && p.locationY != null) {
            sb.append("Location: x=").append(p.locationX)
                .append(" y=").append(p.locationY)
                .append(" plane=").append(p.plane ?: 0)
            if (p.regionId != null) sb.append(" region=").append(p.regionId)
            sb.appendLine()
        }
        sb.append("HP ").append(p.hitpoints).append("/").append(p.hitpointsMax)
            .append("  Prayer ").append(p.prayer).append("/").append(p.prayerMax)
            .append("  Run ").append(p.runEnergyPercent).append('%')
            .append("  Spec ").append(p.specialAttackPercent).append('%')
            .appendLine()
        if (p.activePrayers.isNotEmpty()) {
            sb.append("Active prayers: ").appendLine(p.activePrayers.joinToString(", "))
        }
        if (p.inCombat) {
            sb.append("In combat")
            if (p.targetName != null) sb.append(" with ").append(p.targetName)
            snap.combat?.target?.healthPercent?.let { sb.append(" (~").append(it).append("% HP)") }
            sb.appendLine()
        }
        snap.spellbook?.let { sb.append("Spellbook: ").appendLine(it.spellbookName) }
        snap.attackStyle?.let { sb.append("Attack style: ").appendLine(it.description) }
        snap.slayer?.let { s ->
            sb.append("Slayer task: ")
                .append(s.taskRemaining).append(" remaining")
            if (s.masterName != null) sb.append(" (").append(s.masterName).append(')')
            if (s.streak > 0) sb.append(", streak ").append(s.streak)
            sb.appendLine()
        }
        formatBuffs(snap.buffs)?.let { sb.append("Buffs: ").appendLine(it) }

        formatStats(snap.skills)?.let { sb.append("Stats: ").appendLine(it) }
        formatEquipment(snap.equipment)?.let { sb.append("Worn: ").appendLine(it) }

        val invUsed = snap.inventory.items.count { it.id > 0 && it.quantity > 0 }
        formatInventory(snap.inventory.items)?.let { items ->
            sb.append("Inventory (").append(invUsed).append("/28): ").appendLine(items)
        } ?: sb.append("Inventory: ").append(invUsed).appendLine("/28 slots used")

        val bankLast = snap.bank.lastSeenAt
        if (bankLast != null) {
            sb.append("Bank: last seen ").append(relativeTime(now - bankLast))
                .append(" — ").append(snap.bank.items.size).append(" stacks, ")
                .append(snap.bank.totalQuantity).appendLine(" items total")
        } else {
            sb.appendLine("Bank: not seen this session — get_bank will return not-ready until player opens bank")
        }

        if (snap.quests.isNotEmpty()) {
            val ip = snap.quests.count { it.state == "IN_PROGRESS" }
            val done = snap.quests.count { it.state == "FINISHED" }
            sb.append("Quests: ").append(done).append(" complete, ")
                .append(ip).appendLine(" in progress")
        }
        if (snap.geOffers.isNotEmpty()) {
            sb.append("GE: ").append(snap.geOffers.size).appendLine(" active offer(s)")
        }

        runCatching { slayer?.current() }.getOrNull()?.let { task ->
            sb.append("Slayer task: ").append(task.task)
            task.location?.let { sb.append(" @ ").append(it) }
            sb.append(" — ").append(task.remainingAmount).append("/").append(task.initialAmount)
                .append(" remaining (killed ").append(task.killed).append(')')
            if (task.visibleTargetNpcIds.isNotEmpty()) {
                sb.append(", ").append(task.visibleTargetNpcIds.size).append(" target(s) on screen")
            }
            sb.appendLine()
        }

        runCatching { clueScroll?.current() }.getOrNull()?.let { clue ->
            sb.append("Active clue: ").append(clue.type)
            clue.text?.takeIf { it.isNotBlank() }?.let {
                val excerpt = it.replace(Regex("<[^>]+>"), "").take(120)
                sb.append(" — \"").append(excerpt).append('"')
            }
            clue.location?.let { sb.append(" (solved → ").append(it.x).append(',').append(it.y).append(')') }
            if (clue.npcs.isNotEmpty()) sb.append(" NPCs: ").append(clue.npcs.joinToString(", "))
            sb.appendLine(". get_active_clue for full details.")
        }

        runCatching { xpTracker?.activeRates() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { rates ->
            val top = rates.sortedByDescending { it.xpPerHour }.take(3)
            sb.append("XP rates: ")
            sb.append(top.joinToString(", ") { r ->
                buildString {
                    append(r.skill.lowercase().replaceFirstChar { it.uppercase() })
                    append(' ').append(formatRate(r.xpPerHour)).append("/h")
                    r.timeToGoal?.let { append(" (goal in ").append(it).append(')') }
                }
            })
            if (rates.size > top.size) sb.append(" (").append(rates.size - top.size).append(" more — see get_xp_rates)")
            sb.appendLine()
        }

        runCatching { party?.snapshot() }.getOrNull()?.takeIf { it.inParty }?.let { p ->
            sb.append("Party: ").append(p.memberCount).append(" member(s)")
            val named = p.members.mapNotNull { it.displayName.takeIf { n -> n != "<unknown>" } }
            if (named.isNotEmpty()) sb.append(" — ").append(named.joinToString(", "))
            sb.appendLine()
        }

        val activeLayout = runCatching { bankTags?.activeLayout() }.getOrNull()
        if (activeLayout != null) {
            sb.append("Active bank tag: ").append(activeLayout.tag)
                .append(" — ").append(activeLayout.itemCount).append(" item(s) tagged")
            if (activeLayout.totalSlots > 0) {
                sb.append("; custom layout: ")
                    .append(activeLayout.laidOutItems).append(" placed across ")
                    .append(activeLayout.groupRows).append(" rows")
            }
            sb.appendLine(". get_bank_tab(name) for contents + notes.")
        }

        val openInterfaces = runCatching { widgets?.open() }.getOrNull().orEmpty()
        if (openInterfaces.isNotEmpty()) {
            sb.append("Open interfaces: ")
            sb.append(openInterfaces.joinToString(", ") { oi ->
                oi.name?.let { "$it (${oi.id})" } ?: "id=${oi.id}"
            })
            sb.appendLine()
            sb.appendLine("  → use read_interface(groupId) to see what's on screen")
        }

        val recent = eventLog.queryText(now - RECENT_WINDOW_MS, null, RECENT_LIMIT)
        if (recent.isNotBlank() && recent != "(no matching events)") {
            sb.appendLine()
            sb.appendLine("Recent events:")
            sb.append(recent)
        }
        return sb.toString().trimEnd()
    }

    /**
     * One-line caveat appended to the account-type field so the agent doesn't
     * suggest things ironmen can't do (GE buying, trading, scavenged drops, etc.).
     */
    private fun formatRate(perHour: Int): String = when {
        perHour >= 1_000_000 -> "%.1fM".format(perHour / 1_000_000.0)
        perHour >= 1_000 -> "${perHour / 1_000}K"
        else -> perHour.toString()
    }

    private fun ironmanNote(accountType: String): String? = when (accountType) {
        "Normal" -> null
        "Ironman" -> "Ironman: no trading, no GE buying, no player drops. Self-sufficient training/loot only. ge_price still useful for selling on alt or wiki reference."
        "Ultimate Ironman" -> "Ultimate Ironman: no trading, no GE, NO BANK USE. Recommend strategies that minimise banking. Look for item-storage workarounds (Looting bag, Rune pouch, deposit-box services)."
        "Hardcore Ironman" -> "Hardcore Ironman: one life — if HP gets low, prioritise survival/escape (teleport, eat, run). Lose HCIM status on death."
        "Group Ironman", "Unranked Group Ironman" -> "Group Ironman: can only trade with group members. No GE buying, no public trades. Note items the group could share."
        "Hardcore Group Ironman" -> "Hardcore Group Ironman: group-only trades AND one life — prioritise survival. Lose HC status on death."
        else -> null
    }

    /** One-line compact stats: `Atk 99 Str 99 Def 99 … Total 2200`. */
    private fun formatStats(skills: List<co.rowm.osrsllm.SkillSnapshot>): String? {
        if (skills.isEmpty()) return null
        var total = 0
        val parts = skills.asSequence()
            .filter { it.skill != "OVERALL" }
            .map { s ->
                total += s.level
                val short = SKILL_SHORT_NAMES[s.skill] ?: s.skill.lowercase().replaceFirstChar { it.uppercase() }.take(4)
                val lvlStr = if (s.boostedLevel != s.level) "${s.level}(${s.boostedLevel})" else s.level.toString()
                "$short $lvlStr"
            }.toList()
        return parts.joinToString(" ") + "  Total $total"
    }

    /** `HEAD Karil's coif, BODY Ahrim's robetop, …` — only non-empty slots. */
    private fun formatEquipment(equipment: List<co.rowm.osrsllm.EquipmentSlotSnapshot>): String? {
        val worn = equipment.filter { it.item != null }
        if (worn.isEmpty()) return null
        return worn.joinToString(", ") { es ->
            val item = es.item!!
            val qty = if (item.quantity > 1) " ×${item.quantity}" else ""
            "${es.slot} ${item.name}$qty"
        }
    }

    /**
     * Inventory summary: groups stackable duplicates, caps line length. Returns null if
     * the inventory is empty so the caller can fall back to "X/28 slots used".
     */
    private fun formatInventory(items: List<co.rowm.osrsllm.ItemSnapshot>): String? {
        val real = items.filter { it.id > 0 && it.quantity > 0 }
        if (real.isEmpty()) return null
        // Sum quantities by name (handles split stacks + lookups that distinguish noted/normal)
        val grouped = real.groupBy { it.name }
            .map { (name, list) -> name to list.sumOf { it.quantity.toLong() } }
            .sortedByDescending { it.second }
        return grouped.joinToString(", ") { (name, qty) ->
            if (qty > 1) "$name ×$qty" else name
        }
    }

    private fun formatBuffs(b: BuffsSnapshot?): String? {
        if (b == null) return null
        val parts = mutableListOf<String>()
        b.staminaTicks?.takeIf { it > 0 }?.let { parts += "stamina ${ticksHuman(it)}" }
        b.antifireTicks?.takeIf { it > 0 }?.let { parts += "antifire ${ticksHuman(it)}" }
        b.superAntifireTicks?.takeIf { it > 0 }?.let { parts += "super-antifire ${ticksHuman(it)}" }
        if (b.vengeanceActive) parts += "vengeance ready"
        b.divineSuperCombatTicks?.takeIf { it > 0 }?.let { parts += "divine-combat ${ticksHuman(it)}" }
        b.divineRangingTicks?.takeIf { it > 0 }?.let { parts += "divine-ranging ${ticksHuman(it)}" }
        b.divineMagicTicks?.takeIf { it > 0 }?.let { parts += "divine-magic ${ticksHuman(it)}" }
        b.poisonTicks?.takeIf { it > 0 }?.let { parts += "POISONED ${ticksHuman(it)}" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun ticksHuman(ticks: Int): String {
        val seconds = (ticks * 0.6).toInt()
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
    }

    private fun relativeTime(ageMs: Long): String = when {
        ageMs < 10_000 -> "just now"
        ageMs < 60_000 -> "${ageMs / 1_000}s ago"
        ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
        ageMs < 86_400_000 -> "${ageMs / 3_600_000}h ago"
        else -> "${ageMs / 86_400_000}d ago"
    }

    private const val RECENT_WINDOW_MS = 5 * 60_000L
    private const val RECENT_LIMIT = 6

    /** Abbreviations matching how OSRS players actually refer to skills. */
    private val SKILL_SHORT_NAMES: Map<String, String> = mapOf(
        "ATTACK" to "Atk",
        "STRENGTH" to "Str",
        "DEFENCE" to "Def",
        "HITPOINTS" to "HP",
        "RANGED" to "Rng",
        "PRAYER" to "Pry",
        "MAGIC" to "Mag",
        "COOKING" to "Cook",
        "WOODCUTTING" to "WC",
        "FLETCHING" to "Fle",
        "FISHING" to "Fish",
        "FIREMAKING" to "FM",
        "CRAFTING" to "Cra",
        "SMITHING" to "Smi",
        "MINING" to "Min",
        "HERBLORE" to "Herb",
        "AGILITY" to "Agi",
        "THIEVING" to "Thi",
        "SLAYER" to "Sla",
        "FARMING" to "Far",
        "RUNECRAFT" to "RC",
        "HUNTER" to "Hun",
        "CONSTRUCTION" to "Con",
    )
}
