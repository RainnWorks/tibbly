package co.rowm.osrsllm

import kotlinx.serialization.Serializable

@Serializable
data class GameSnapshot(
    val timestamp: Long,
    val loggedIn: Boolean,
    val player: PlayerSnapshot?,
    val skills: List<SkillSnapshot>,
    val inventory: ContainerSnapshot,
    val equipment: List<EquipmentSlotSnapshot>,
    val bank: BankSnapshot,
    val quests: List<QuestSnapshot>,
    val nearbyNpcs: List<NearbyNpcSnapshot>,
    val slayer: SlayerSnapshot? = null,
    val geOffers: List<GeOfferSnapshot> = emptyList(),
    val buffs: BuffsSnapshot? = null,
    val groundItems: List<GroundItemSnapshot> = emptyList(),
    val nearbyObjects: List<NearbyObjectSnapshot> = emptyList(),
    val combat: CombatInfoSnapshot? = null,
    val diary: DiaryProgressSnapshot? = null,
    val spellbook: SpellbookSnapshot? = null,
    val attackStyle: AttackStyleSnapshot? = null,
    val combatAchievements: CombatAchievementsSnapshot? = null,
    val poh: PohSnapshot? = null,
)

@Serializable
data class SlayerSnapshot(
    val masterId: Int,
    val masterName: String?,
    val creatureId: Int,
    val taskRemaining: Int,
    val points: Int,
    val streak: Int,
    val isBoss: Boolean,
)

@Serializable
data class GeOfferSnapshot(
    val slot: Int,
    val state: String,
    val itemId: Int,
    val itemName: String?,
    val quantityFilled: Int,
    val totalQuantity: Int,
    val pricePerItem: Int,
    val totalSpent: Int,
)

/** Buff timers expressed in ticks remaining (1 tick = 0.6s). Null means no active value. */
@Serializable
data class BuffsSnapshot(
    val staminaTicks: Int? = null,
    val antifireTicks: Int? = null,
    val superAntifireTicks: Int? = null,
    val vengeanceActive: Boolean = false,
    val vengeanceCooldownTicks: Int? = null,
    val imbuedHeartCooldownTicks: Int? = null,
    val deathChargeCooldownTicks: Int? = null,
    val divineSuperCombatTicks: Int? = null,
    val divineRangingTicks: Int? = null,
    val divineMagicTicks: Int? = null,
    val nmzOverloadRefreshes: Int? = null,
    val coxOverloadRefreshes: Int? = null,
    val poisonTicks: Int? = null,
)

@Serializable
data class PlayerSnapshot(
    val name: String?,
    val combatLevel: Int,
    val totalLevel: Int,
    val world: Int,
    val accountType: String,
    val locationX: Int?,
    val locationY: Int?,
    val plane: Int?,
    val regionId: Int?,
    val hitpoints: Int,
    val hitpointsMax: Int,
    val prayer: Int,
    val prayerMax: Int,
    val runEnergyPercent: Int,
    val specialAttackPercent: Int,
    val activePrayers: List<String>,
    val inCombat: Boolean,
    val targetName: String?,
)

@Serializable
data class NearbyNpcSnapshot(
    val name: String,
    val combatLevel: Int,
    val distance: Int,
    val locationX: Int,
    val locationY: Int,
)

@Serializable
data class GroundItemSnapshot(
    val itemId: Int,
    val name: String,
    val quantity: Int,
    val distance: Int,
    val locationX: Int,
    val locationY: Int,
)

@Serializable
data class NearbyObjectSnapshot(
    val objectId: Int,
    val name: String,
    val actions: List<String>,
    val distance: Int,
    val locationX: Int,
    val locationY: Int,
)

@Serializable
data class CombatInfoSnapshot(
    val playerAnimationId: Int,
    val target: TargetSnapshot?,
)

@Serializable
data class DiaryRegionProgress(
    val easy: Boolean,
    val medium: Boolean,
    val hard: Boolean,
    val elite: Boolean,
) {
    val tiersComplete: Int get() = listOf(easy, medium, hard, elite).count { it }
}

@Serializable
data class DiaryProgressSnapshot(
    val regions: Map<String, DiaryRegionProgress>,
    val totalTiersComplete: Int,
    val totalTiers: Int = 48, // 12 regions × 4 tiers
)

@Serializable
data class SpellbookSnapshot(
    val spellbookId: Int,
    val spellbookName: String,
)

@Serializable
data class AttackStyleSnapshot(
    val styleIndex: Int,
    val weaponTypeId: Int,
    val description: String,
    val autocastSpellId: Int? = null,
)

@Serializable
data class CombatAchievementsSnapshot(
    val easyComplete: Boolean,
    val mediumComplete: Boolean,
    val hardComplete: Boolean,
    val eliteComplete: Boolean,
    val masterComplete: Boolean,
    val grandmasterComplete: Boolean,
    val tiersComplete: Int,
)

@Serializable
data class PohSnapshot(
    val houseLocationId: Int,
    val houseLocationName: String,
)

@Serializable
data class HitsplatEvent(
    val timestamp: Long,
    val direction: String,        // "in" | "out"
    val amount: Int,
    val typeId: Int,              // raw HitsplatID; agent can wiki it
    val target: String?,          // attacker (if in) / target (if out)
)

@Serializable
data class HitsplatHistorySnapshot(
    val sessionElapsedSeconds: Long,
    val totalDamageDealt: Long,
    val totalDamageTaken: Long,
    val events: List<HitsplatEvent>,
)

@Serializable
data class TargetSnapshot(
    val name: String?,
    val combatLevel: Int,
    /** Quantized HP (0..healthScale). Not raw HP. */
    val healthRatio: Int,
    val healthScale: Int,
    /** Computed `ratio/scale * 100` rounded down; null if scale ≤ 0. */
    val healthPercent: Int?,
    val animationId: Int,
    val locationX: Int?,
    val locationY: Int?,
    val distance: Int?,
)

@Serializable
data class SkillSnapshot(
    val skill: String,
    val level: Int,
    val boostedLevel: Int,
    val experience: Int,
)

@Serializable
data class ItemSnapshot(
    val id: Int,
    val name: String,
    val quantity: Int,
)

@Serializable
data class ContainerSnapshot(
    val items: List<ItemSnapshot>,
    val updatedAt: Long?,
)

@Serializable
data class EquipmentSlotSnapshot(
    val slot: String,
    val item: ItemSnapshot?,
)

@Serializable
data class BankSnapshot(
    val items: List<ItemSnapshot>,
    val lastSeenAt: Long?,
    val totalQuantity: Long,
)

@Serializable
data class QuestSnapshot(
    val name: String,
    val state: String,
)
