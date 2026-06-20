package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import net.runelite.client.plugins.slayer.SlayerPluginService
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over RuneLite's [SlayerPluginService] giving the agent a snapshot
 * of the player's current slayer task. Surfaced both in the harness preamble
 * (`Slayer task: ...`) and via the `get_slayer_task` MCP tool.
 */
@Singleton
class SlayerIntegration @Inject constructor(
    private val slayer: SlayerPluginService,
) {

    private val log = LoggerFactory.getLogger(SlayerIntegration::class.java)

    @Serializable
    data class SlayerTask(
        val task: String,
        val location: String?,
        val initialAmount: Int,
        val remainingAmount: Int,
        val killed: Int,
        /** NPC ids on-screen that count toward this task (from SlayerPlugin.targets). */
        val visibleTargetNpcIds: List<Int>,
    )

    fun current(): SlayerTask? = runCatching {
        val task = slayer.task?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val initial = slayer.initialAmount
        val remaining = slayer.remainingAmount
        SlayerTask(
            task = task,
            location = slayer.taskLocation?.takeIf { it.isNotBlank() },
            initialAmount = initial,
            remainingAmount = remaining,
            killed = (initial - remaining).coerceAtLeast(0),
            visibleTargetNpcIds = slayer.targets?.mapNotNull { runCatching { it.id }.getOrNull() }
                ?.distinct().orEmpty(),
        )
    }.onFailure { log.debug("Slayer task read failed: {}", it.message) }.getOrNull()
}
