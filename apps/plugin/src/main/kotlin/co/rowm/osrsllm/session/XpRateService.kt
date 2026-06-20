package co.rowm.osrsllm.session

import kotlinx.serialization.Serializable
import net.runelite.api.Skill
import net.runelite.api.events.StatChanged
import net.runelite.client.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SkillXpRate(
    val skill: String,
    val xpGained: Long,
    val xpPerHour: Long,
)

@Serializable
data class XpRateSnapshot(
    val sessionElapsedSeconds: Long,
    val skills: List<SkillXpRate>,
)

/**
 * Aggregates per-skill XP gained since session start. "Session" starts when the player
 * first logs in (or when the plugin starts up). Cleared on plugin shutdown.
 *
 * Per-skill state: prior XP (for delta calc) + cumulative session gain.
 * Rate is computed lazily at snapshot time so we don't need a rolling window.
 */
@Singleton
class XpRateService @Inject constructor() {

    private val log = LoggerFactory.getLogger(XpRateService::class.java)
    private val sessionStart = AtomicLong(System.currentTimeMillis())
    private val priorXp = ConcurrentHashMap<Skill, Int>()
    private val sessionGains = ConcurrentHashMap<Skill, Long>()

    fun resetSession() {
        sessionStart.set(System.currentTimeMillis())
        priorXp.clear()
        sessionGains.clear()
        log.info("XP session reset")
    }

    fun clear() = resetSession()

    @Subscribe
    fun onStatChanged(event: StatChanged) {
        val skill = event.skill ?: return
        val xp = event.xp
        val prev = priorXp.put(skill, xp)
        if (prev == null) return // seed baseline, don't count
        val gain = xp - prev
        if (gain > 0) {
            sessionGains.compute(skill) { _, v -> (v ?: 0L) + gain }
        }
    }

    fun snapshot(): XpRateSnapshot {
        val elapsedMs = (System.currentTimeMillis() - sessionStart.get()).coerceAtLeast(1)
        val elapsedHours = elapsedMs / 3_600_000.0
        val perSkill = sessionGains.entries
            .filter { it.value > 0 }
            .map { (skill, gained) ->
                SkillXpRate(
                    skill = capitalize(skill.name),
                    xpGained = gained,
                    xpPerHour = if (elapsedHours > 0.0) (gained / elapsedHours).toLong() else 0L,
                )
            }
            .sortedByDescending { it.xpGained }
        return XpRateSnapshot(
            sessionElapsedSeconds = elapsedMs / 1000,
            skills = perSkill,
        )
    }

    private fun capitalize(s: String) =
        if (s.isEmpty()) s else s[0].uppercase() + s.substring(1).lowercase()
}
