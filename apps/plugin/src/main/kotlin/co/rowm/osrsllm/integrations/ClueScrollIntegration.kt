package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import net.runelite.api.coords.WorldPoint
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin
import net.runelite.client.plugins.cluescrolls.ClueScrollService
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue
import net.runelite.client.plugins.cluescrolls.clues.CipherClue
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue
import net.runelite.client.plugins.cluescrolls.clues.FairyRingClue
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue
import net.runelite.client.plugins.cluescrolls.clues.MapClue
import net.runelite.client.plugins.cluescrolls.clues.MusicClue
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue
import net.runelite.client.plugins.cluescrolls.clues.ThreeStepCrypticClue
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surfaces the player's active clue scroll. Reads via [ClueScrollService.getClue]
 * and pulls type-specific details (text, hint NPCs, solved coordinates) by
 * instance-checking known clue subclasses. The clue objects ship with public
 * getters — we just unify them into one shape.
 */
@Singleton
class ClueScrollIntegration @Inject constructor(
    private val cluePlugin: ClueScrollPlugin,
    private val clueService: ClueScrollService,
) {

    private val log = LoggerFactory.getLogger(ClueScrollIntegration::class.java)

    @Serializable
    data class ActiveClue(
        val type: String,
        /** Raw clue text shown to the player (where applicable). */
        val text: String?,
        /** Resolved solution NPCs, when the plugin can figure them out. */
        val npcs: List<String>,
        /** Primary solved coordinate, if any. */
        val location: Coord?,
        /** All solved coordinates (some clues have multiple candidate spots). */
        val locations: List<Coord>,
        val requiresSpade: Boolean,
        val requiresLight: Boolean,
        /** Free-text hint that the plugin author set on the clue (e.g. song name, area). */
        val hint: String? = null,
    )

    @Serializable
    data class Coord(val x: Int, val y: Int, val plane: Int)

    fun current(): ActiveClue? {
        val clue = runCatching { clueService.clue }.getOrNull() ?: return null
        return try {
            when (clue) {
                is CrypticClue -> ActiveClue(
                    type = "cryptic",
                    text = clue.text,
                    npcs = listOfNotNull(clue.getNpc(cluePlugin)?.takeIf { it.isNotBlank() }),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is AnagramClue -> ActiveClue(
                    // AnagramClue's anagram text field is not Lombok-exposed —
                    // we surface area/question instead so the agent has *something*
                    // to work with. The displayed anagram letters are only
                    // resolvable through the plugin's internal text provider.
                    type = "anagram",
                    text = listOfNotNull(clue.area, clue.question)
                        .filter { it.isNotBlank() }
                        .takeIf { it.isNotEmpty() }?.joinToString(" — "),
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is CipherClue -> ActiveClue(
                    type = "cipher",
                    text = clue.text,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is CoordinateClue -> ActiveClue(
                    type = "coordinate",
                    text = null,
                    npcs = emptyList(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = clue.getLocations(cluePlugin)?.mapNotNull { it?.toCoord() }.orEmpty(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is EmoteClue -> ActiveClue(
                    type = "emote",
                    text = clue.text,
                    npcs = emptyList(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is HotColdClue -> ActiveClue(
                    type = "hot-cold",
                    text = clue.text,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = clue.getLocations(cluePlugin)?.mapNotNull { it?.toCoord() }.orEmpty(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is FairyRingClue -> ActiveClue(
                    type = "fairy-ring",
                    text = clue.text,
                    npcs = emptyList(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is MapClue -> ActiveClue(
                    type = "map",
                    text = null,
                    npcs = emptyList(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is MusicClue -> ActiveClue(
                    type = "music",
                    text = null,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                    hint = clue.song,
                )
                is ThreeStepCrypticClue -> ActiveClue(
                    type = "three-step-cryptic",
                    text = clue.text,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = clue.getLocation(cluePlugin)?.toCoord(),
                    locations = clue.getLocations(cluePlugin)?.mapNotNull { it?.toCoord() }.orEmpty(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is FaloTheBardClue -> ActiveClue(
                    type = "falo-the-bard",
                    text = clue.text,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = null,
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                is SkillChallengeClue -> ActiveClue(
                    type = "skill-challenge",
                    text = null,
                    npcs = clue.getNpcs(cluePlugin)?.toList().orEmpty(),
                    location = null,
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
                else -> ActiveClue(
                    type = clue.javaClass.simpleName.removeSuffix("Clue").lowercase(),
                    text = null,
                    npcs = emptyList(),
                    location = null,
                    locations = emptyList(),
                    requiresSpade = clue.isRequiresSpade,
                    requiresLight = clue.isRequiresLight,
                )
            }
        } catch (t: Throwable) {
            log.debug("Clue read failed: {}", t.message)
            null
        }
    }

    private fun WorldPoint.toCoord() = Coord(x, y, plane)
}
