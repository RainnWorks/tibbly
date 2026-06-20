package co.rowm.osrsllm.session

import co.rowm.osrsllm.HitsplatEvent
import co.rowm.osrsllm.HitsplatHistorySnapshot
import net.runelite.api.Client
import net.runelite.api.events.HitsplatApplied
import net.runelite.client.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rolling history of hitsplats involving the local player. Bounded to MAX_EVENTS
 * (most recent first). Maintains lifetime damage-dealt / damage-taken totals
 * separate from the deque so they survive the trim.
 *
 * Why a separate service from EventLogService: the event log is plain text for
 * "what just happened" queries; this gives structured (timestamp, direction, amount,
 * type, target) tuples that are easier to analyse for combat metrics.
 */
@Singleton
class HitsplatHistoryService @Inject constructor(private val client: Client) {

    private val log = LoggerFactory.getLogger(HitsplatHistoryService::class.java)
    private val sessionStart = AtomicLong(System.currentTimeMillis())
    private val events = ConcurrentLinkedDeque<HitsplatEvent>()
    private val damageDealt = AtomicLong(0)
    private val damageTaken = AtomicLong(0)

    fun resetSession() {
        sessionStart.set(System.currentTimeMillis())
        events.clear()
        damageDealt.set(0)
        damageTaken.set(0)
        log.info("Hitsplat history reset")
    }

    fun clear() = resetSession()

    @Subscribe
    fun onHitsplatApplied(event: HitsplatApplied) {
        val actor = event.actor ?: return
        val hit = event.hitsplat ?: return
        val local = client.localPlayer ?: return
        val now = System.currentTimeMillis()
        when {
            actor == local -> {
                if (hit.amount > 0) damageTaken.addAndGet(hit.amount.toLong())
                push(
                    HitsplatEvent(
                        timestamp = now,
                        direction = "in",
                        amount = hit.amount,
                        typeId = hit.hitsplatType,
                        target = (local.interacting?.name) ?: actor.name,
                    ),
                )
            }
            actor == local.interacting -> {
                if (hit.amount > 0) damageDealt.addAndGet(hit.amount.toLong())
                push(
                    HitsplatEvent(
                        timestamp = now,
                        direction = "out",
                        amount = hit.amount,
                        typeId = hit.hitsplatType,
                        target = actor.name,
                    ),
                )
            }
        }
    }

    private fun push(e: HitsplatEvent) {
        events.addFirst(e)
        while (events.size > MAX_EVENTS) events.pollLast()
    }

    fun snapshot(limit: Int = MAX_EVENTS): HitsplatHistorySnapshot {
        val elapsedSec = (System.currentTimeMillis() - sessionStart.get()) / 1000
        return HitsplatHistorySnapshot(
            sessionElapsedSeconds = elapsedSec,
            totalDamageDealt = damageDealt.get(),
            totalDamageTaken = damageTaken.get(),
            events = events.asSequence().take(limit).toList(),
        )
    }

    companion object {
        private const val MAX_EVENTS = 200
    }
}
