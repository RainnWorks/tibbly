package co.rowm.osrsllm.integrations

import net.runelite.client.game.NPCManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves NPC max HP via RuneLite's NPC-info backend. Cached upstream by
 * [NPCManager] — call as often as you like. Returns null for NPCs the dataset
 * doesn't know about (custom event NPCs, certain bosses with variable HP).
 */
@Singleton
class NpcHpService @Inject constructor(
    private val npcManager: NPCManager,
) {

    /** Max HP for an NPC id, or null if not in the dataset. */
    fun maxHp(npcId: Int): Int? = runCatching { npcManager.getHealth(npcId) }.getOrNull()
}
