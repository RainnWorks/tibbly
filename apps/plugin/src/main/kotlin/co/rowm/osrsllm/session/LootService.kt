package co.rowm.osrsllm.session

import kotlinx.serialization.Serializable
import net.runelite.client.events.NpcLootReceived
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LootedItem(val id: Int, val name: String, val quantity: Int)

@Serializable
data class NpcLootSummary(
    val npc: String,
    val kills: Int,
    val items: List<LootedItem>,
)

@Serializable
data class LootSnapshot(
    val sessionElapsedSeconds: Long,
    val totalKills: Int,
    val byNpc: List<NpcLootSummary>,
)

/**
 * Tracks NPC loot received during the session. Subscribes to
 * `NpcLootReceived` (RuneLite-client event). Aggregates kills + items per NPC name.
 *
 * Items are looked up via ItemManager for human-readable names. We don't price them
 * here — `ge_price` is a separate tool the LLM can call if it needs values.
 */
@Singleton
class LootService @Inject constructor(
    private val itemManager: ItemManager,
) {

    private val log = LoggerFactory.getLogger(LootService::class.java)
    private val sessionStart = AtomicLong(System.currentTimeMillis())
    private val perNpcKills = ConcurrentHashMap<String, Int>()
    private val perNpcItems = ConcurrentHashMap<String, ConcurrentHashMap<Int, Int>>()

    fun resetSession() {
        sessionStart.set(System.currentTimeMillis())
        perNpcKills.clear()
        perNpcItems.clear()
        log.info("Loot session reset")
    }

    fun clear() = resetSession()

    @Subscribe
    fun onNpcLootReceived(event: NpcLootReceived) {
        val name = event.npc?.name ?: "Unknown NPC"
        perNpcKills.merge(name, 1) { a, _ -> a + 1 }
        val bucket = perNpcItems.computeIfAbsent(name) { ConcurrentHashMap() }
        for (stack in event.items) {
            bucket.merge(stack.id, stack.quantity) { a, b -> a + b }
        }
    }

    fun snapshot(): LootSnapshot {
        val elapsedSec = (System.currentTimeMillis() - sessionStart.get()) / 1000
        val byNpc = perNpcKills.entries
            .map { (npc, kills) ->
                val itemMap = perNpcItems[npc].orEmpty()
                val items = itemMap.entries.map { (id, qty) ->
                    val itemName = runCatching { itemManager.getItemComposition(id).name }
                        .getOrDefault("Unknown")
                    LootedItem(id, itemName, qty)
                }.sortedByDescending { it.quantity }
                NpcLootSummary(npc, kills, items)
            }
            .sortedByDescending { it.kills }
        return LootSnapshot(
            sessionElapsedSeconds = elapsedSec,
            totalKills = byNpc.sumOf { it.kills },
            byNpc = byNpc,
        )
    }
}
