package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import net.runelite.api.Skill
import net.runelite.client.plugins.xptracker.XpTrackerService
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps RuneLite's [XpTrackerService] so the agent can read per-skill rates and
 * goal estimates. Only returns entries with non-zero progress — keeps the
 * preamble compact.
 */
@Singleton
class XpTrackerIntegration @Inject constructor(
    private val xp: XpTrackerService,
) {

    private val log = LoggerFactory.getLogger(XpTrackerIntegration::class.java)

    @Serializable
    data class SkillRate(
        val skill: String,
        val xpPerHour: Int,
        val actionsPerHour: Int,
        val actionsDone: Int,
        val actionsLeft: Int,
        /** XP remaining until the configured goal level. 0 when no goal or goal hit. */
        val xpToGoal: Int,
        /** Human-formatted "1h 23m" / "5d 12h" / etc. Null when no goal. */
        val timeToGoal: String?,
    )

    /** All skills that currently have non-zero xp/hr. */
    fun activeRates(): List<SkillRate> = Skill.values().toList()
        .mapNotNull { skill -> rate(skill)?.takeIf { it.xpPerHour > 0 || it.actionsPerHour > 0 } }

    fun rate(skill: Skill): SkillRate? = runCatching {
        SkillRate(
            skill = skill.name,
            xpPerHour = xp.getXpHr(skill),
            actionsPerHour = xp.getActionsHr(skill),
            actionsDone = xp.getActions(skill),
            actionsLeft = xp.getActionsLeft(skill),
            xpToGoal = xp.getEndGoalXp(skill).coerceAtLeast(0),
            timeToGoal = xp.getTimeTilGoal(skill)?.takeIf { it.isNotBlank() && it != "0s" },
        )
    }.onFailure { log.debug("XP rate read for {} failed: {}", skill, it.message) }.getOrNull()
}
