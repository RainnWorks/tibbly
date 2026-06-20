package co.rowm.osrsllm.items

import kotlinx.serialization.Serializable
import net.runelite.client.callback.ClientThread
import net.runelite.client.game.ItemManager
import net.runelite.client.game.ItemStats
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Equipment-stats snapshot we surface to the agent. Mirrors RuneLite's
 * [ItemStats] / `ItemEquipmentStats` but with names instead of int slot ids and
 * grouped attack/defence bonuses for readability.
 *
 * Non-equipable items resolve to null in the resolver cache.
 */
@Serializable
data class EquipmentStatsSnapshot(
    /** "head"|"cape"|"amulet"|"weapon"|"body"|"shield"|"legs"|"gloves"|"boots"|"ring"|"ammo"|"unknown" */
    val slot: String,
    val twoHanded: Boolean = false,
    val atk: AttackBonuses,
    val def: DefenceBonuses,
    val str: Int = 0,
    val rstr: Int = 0,
    val mdmg: Float = 0f,
    val prayer: Int = 0,
    /** Attack speed in ticks (lower = faster). Only meaningful for weapons. */
    val aspeed: Int = 0,
    /** kg, can be negative for some items (anti-gravity boots, etc.). */
    val weight: Float = 0f,
)

@Serializable
data class AttackBonuses(
    val stab: Int = 0, val slash: Int = 0, val crush: Int = 0,
    val magic: Int = 0, val ranged: Int = 0,
)

@Serializable
data class DefenceBonuses(
    val stab: Int = 0, val slash: Int = 0, val crush: Int = 0,
    val magic: Int = 0, val ranged: Int = 0,
)

/**
 * Thread-safe resolver for equipment stats. Mirrors [ItemNameResolver] — handles
 * the off-client-thread → client-thread hand-off via [ClientThread.invoke] +
 * [CompletableFuture], caches results forever (item stats don't change).
 *
 * Non-equipable items (food, runes, currency, etc.) cache as a sentinel so we
 * don't keep retrying them every call.
 */
@Singleton
class ItemStatsResolver @Inject constructor(
    private val itemManager: ItemManager,
    private val clientThread: ClientThread,
) {

    private val log = LoggerFactory.getLogger(ItemStatsResolver::class.java)

    /** Wrapper distinguishes "resolved to nothing" (key present, value=null) from "not resolved yet". */
    private class Holder(val stats: EquipmentStatsSnapshot?)
    private val cache = ConcurrentHashMap<Int, Holder>()

    fun resolve(id: Int): EquipmentStatsSnapshot? = resolveAll(listOf(id))[id]

    fun resolveAll(ids: Collection<Int>): Map<Int, EquipmentStatsSnapshot?> {
        if (ids.isEmpty()) return emptyMap()
        val unique = ids.toSet().filter { it > 0 }
        if (unique.isEmpty()) return emptyMap()

        val out = HashMap<Int, EquipmentStatsSnapshot?>(unique.size)
        val missing = mutableListOf<Int>()
        for (id in unique) {
            val h = cache[id]
            if (h != null) out[id] = h.stats else missing += id
        }
        if (missing.isEmpty()) return out

        val future = CompletableFuture<Map<Int, EquipmentStatsSnapshot?>>()
        clientThread.invoke(Runnable {
            try {
                val resolved = HashMap<Int, EquipmentStatsSnapshot?>(missing.size)
                for (id in missing) {
                    resolved[id] = runCatching { mapStats(itemManager.getItemStats(id)) }
                        .getOrNull()
                }
                future.complete(resolved)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })

        val fresh = try {
            future.get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.warn("Stat resolve failed for {} ids: {}", missing.size, t.message)
            missing.associateWith { null as EquipmentStatsSnapshot? }
        }
        for ((id, snapshot) in fresh) cache[id] = Holder(snapshot)
        out.putAll(fresh)
        return out
    }

    private fun mapStats(stats: ItemStats?): EquipmentStatsSnapshot? {
        if (stats == null || !stats.isEquipable) return null
        val eq = stats.equipment ?: return null
        return EquipmentStatsSnapshot(
            slot = slotName(eq.slot),
            twoHanded = eq.isTwoHanded,
            atk = AttackBonuses(eq.astab, eq.aslash, eq.acrush, eq.amagic, eq.arange),
            def = DefenceBonuses(eq.dstab, eq.dslash, eq.dcrush, eq.dmagic, eq.drange),
            str = eq.str,
            rstr = eq.rstr,
            mdmg = eq.mdmg,
            prayer = eq.prayer,
            aspeed = eq.aspeed,
            weight = stats.weight.toFloat(),
        )
    }

    private fun slotName(slot: Int): String = when (slot) {
        0 -> "head"
        1 -> "cape"
        2 -> "amulet"
        3 -> "weapon"
        4 -> "body"
        5 -> "shield"
        7 -> "legs"
        9 -> "gloves"
        10 -> "boots"
        12 -> "ring"
        13 -> "ammo"
        else -> "unknown($slot)"
    }
}
