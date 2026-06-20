package co.rowm.osrsllm.integrations

import kotlinx.serialization.Serializable
import net.runelite.client.party.PartyService
import net.runelite.client.plugins.party.PartyPluginService
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surfaces RuneLite party membership. Combines the low-level [PartyService]
 * (party members, display names, login state) with the higher-level
 * [PartyPluginService] (per-member world-map position).
 */
@Singleton
class PartyIntegration @Inject constructor(
    private val party: PartyService,
    private val partyPlugin: PartyPluginService,
) {

    private val log = LoggerFactory.getLogger(PartyIntegration::class.java)

    @Serializable
    data class PartySnapshot(
        val inParty: Boolean,
        val memberCount: Int,
        val members: List<PartyMemberInfo>,
    )

    @Serializable
    data class PartyMemberInfo(
        val displayName: String,
        val loggedIn: Boolean,
        val location: WorldPoint? = null,
    )

    @Serializable
    data class WorldPoint(val x: Int, val y: Int, val plane: Int)

    fun snapshot(): PartySnapshot = runCatching {
        if (!party.isInParty) {
            return@runCatching PartySnapshot(inParty = false, memberCount = 0, members = emptyList())
        }
        val members = party.members.orEmpty().map { m ->
            val pd = runCatching { partyPlugin.getPartyData(m.memberId) }.getOrNull()
            val wp = pd?.worldMapPoint
            PartyMemberInfo(
                displayName = m.displayName ?: "<unknown>",
                loggedIn = m.isLoggedIn,
                location = wp?.let { WorldPoint(it.worldPoint?.x ?: 0,
                    it.worldPoint?.y ?: 0, it.worldPoint?.plane ?: 0) }
                    ?.takeIf { it.x > 0 || it.y > 0 },
            )
        }
        PartySnapshot(inParty = true, memberCount = members.size, members = members)
    }.onFailure { log.debug("Party snapshot failed: {}", it.message) }
        .getOrDefault(PartySnapshot(inParty = false, memberCount = 0, members = emptyList()))
}
