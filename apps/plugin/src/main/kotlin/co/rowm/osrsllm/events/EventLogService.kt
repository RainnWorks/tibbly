package co.rowm.osrsllm.events

import kotlinx.serialization.Serializable
import net.runelite.api.Client
import net.runelite.api.GameState
import net.runelite.api.Skill
import net.runelite.api.events.ActorDeath
import net.runelite.api.events.ChatMessage
import net.runelite.api.events.GameStateChanged
import net.runelite.api.events.HitsplatApplied
import net.runelite.api.events.StatChanged
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.events.NpcLootReceived
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GameEvent(
    val timestamp: Long,
    val type: String,
    val summary: String,
)

/**
 * Bounded rolling log of high-signal in-game events so the LLM can answer
 * "what just happened" without us pre-computing aggregates.
 *
 * Capped at MAX_EVENTS. Newest first. Thread-safe via ConcurrentLinkedDeque
 * (writes from client thread, reads from MCP tool's Ktor coroutine).
 */
@Singleton
class EventLogService @Inject constructor(
    private val client: Client,
    private val itemManager: ItemManager,
) {

    private val log = LoggerFactory.getLogger(EventLogService::class.java)
    private val events = ConcurrentLinkedDeque<GameEvent>()

    // Track prior XP/level per skill so StatChanged → delta works.
    // Null entry means "haven't seen this skill yet"; the first StatChanged seeds
    // the baseline without emitting a spurious "gain" on login.
    private val priorXp = ConcurrentHashMap<Skill, Int>()
    private val priorLevel = ConcurrentHashMap<Skill, Int>()

    // Accumulated XP per skill since the user last asked
    fun snapshot(): List<GameEvent> = events.toList()

    fun clear() {
        events.clear()
        priorXp.clear()
        priorLevel.clear()
    }

    /**
     * Returns a compact plain-text feed: one event per line, newest first,
     * `[Xm ago] summary`. Optional substring match on summary (case-insensitive).
     * Tiny on tokens — no JSON envelopes.
     */
    fun queryText(sinceMs: Long?, query: String?, limit: Int): String {
        val cap = limit.coerceIn(1, 200)
        val now = System.currentTimeMillis()
        val matches = events.asSequence()
            .filter { sinceMs == null || it.timestamp >= sinceMs }
            .filter { query.isNullOrBlank() || it.summary.contains(query, ignoreCase = true) }
            .take(cap)
            .toList()
        if (matches.isEmpty()) return "(no matching events)"
        return matches.joinToString("\n") { ev ->
            "[${relativeTime(now - ev.timestamp)}] ${ev.summary}"
        }
    }

    private fun relativeTime(ageMs: Long): String = when {
        ageMs < 10_000 -> "just now"
        ageMs < 60_000 -> "${ageMs / 1_000}s ago"
        ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
        ageMs < 86_400_000 -> "${ageMs / 3_600_000}h ago"
        else -> "${ageMs / 86_400_000}d ago"
    }

    @Subscribe
    fun onChatMessage(event: ChatMessage) {
        val type = event.type ?: return
        val typeName = type.name.lowercase()
        val keep = typeName in CHAT_KEEP
        if (!keep) return
        val msg = (event.message ?: return).take(200)
        val name = event.name?.takeIf { it.isNotBlank() }
        val summary = if (name != null) "$name: $msg" else msg
        push("chat.$typeName", summary)
    }

    @Subscribe
    fun onHitsplatApplied(event: HitsplatApplied) {
        val actor = event.actor ?: return
        val hit = event.hitsplat ?: return
        if (hit.amount <= 0) return // skip block/zero/heal markers
        val local = client.localPlayer ?: return
        // Summaries both contain "damage" so a single substring query catches both.
        when {
            actor == local -> push("damage.in", "Took ${hit.amount} damage")
            actor == local.interacting ->
                push("damage.out", "Dealt ${hit.amount} damage to ${actor.name ?: "target"}")
            // Damage between unrelated actors — skip
        }
    }

    @Subscribe
    fun onStatChanged(event: StatChanged) {
        val skill = event.skill ?: return
        val xp = event.xp
        val level = event.level
        val prevXp = priorXp.put(skill, xp)
        val prevLevel = priorLevel.put(skill, level)
        // First time we see this skill: seed baselines, emit nothing.
        if (prevXp == null || prevLevel == null) return
        val gain = xp - prevXp
        if (gain > 0) {
            push("xp.${skill.name.lowercase()}", "+$gain ${capitalize(skill.name)} xp gained")
        }
        if (level > prevLevel) {
            push("level.up", "Level up: ${capitalize(skill.name)} $prevLevel → $level")
        }
    }

    @Subscribe
    fun onActorDeath(event: ActorDeath) {
        val actor = event.actor ?: return
        val local = client.localPlayer ?: return
        when {
            actor == local -> push("death", "You died")
            actor == local.interacting -> push("kill", "Killed ${actor.name ?: "target"}")
        }
    }

    @Subscribe
    fun onGameStateChanged(event: GameStateChanged) {
        when (event.gameState) {
            GameState.LOGGED_IN -> push("session.login", "Logged in")
            GameState.LOGIN_SCREEN, GameState.HOPPING -> push("session.logout", "Logged out / hopping")
            else -> {}
        }
    }

    @Subscribe
    fun onNpcLootReceived(event: NpcLootReceived) {
        val npcName = event.npc?.name ?: "Unknown NPC"
        val items = event.items.joinToString(", ") { stack ->
            val itemName = runCatching { itemManager.getItemComposition(stack.id).name }
                .getOrDefault("Unknown")
            if (stack.quantity > 1) "$itemName × ${stack.quantity}" else itemName
        }
        push("loot.$npcName", "Looted from $npcName: $items".take(220))
    }

    private fun push(type: String, summary: String) {
        events.addFirst(GameEvent(System.currentTimeMillis(), type, summary))
        while (events.size > MAX_EVENTS) events.pollLast()
    }

    private fun capitalize(s: String): String =
        if (s.isEmpty()) s else s[0].uppercase() + s.substring(1).lowercase()

    companion object {
        private const val MAX_EVENTS = 300
        private val CHAT_KEEP = setOf(
            "publicchat",
            "privatechat",
            "privatechatout",
            "gamemessage",
            "broadcast",
            "spam",
            "modchat",
            "tradereq_in",
            "tradereq_out",
            "trade",
            "trade_sent",
            "clan_message",
            "clan_chat",
            "clan_guest_chat",
            "friendscs",
            "item_examine",
            "npc_examine",
            "objectexamine",
        )
    }
}
