package co.rowm.osrsllm

import co.rowm.osrsllm.persistence.BankPersistence
import net.runelite.api.Actor
import net.runelite.api.Client
import net.runelite.api.EquipmentInventorySlot
import net.runelite.api.GameState
import net.runelite.api.Item
import net.runelite.api.ItemContainer
import net.runelite.api.GrandExchangeOffer
import net.runelite.api.NPC
import net.runelite.api.Prayer
import net.runelite.api.Quest
import net.runelite.api.Skill
import net.runelite.api.VarPlayer
import net.runelite.api.Varbits
import net.runelite.api.gameval.InventoryID
import net.runelite.api.gameval.VarbitID
import kotlin.math.abs
import kotlin.math.max
import net.runelite.api.coords.WorldPoint
import net.runelite.api.events.GameStateChanged
import net.runelite.api.events.GameTick
import net.runelite.api.events.ItemContainerChanged
import net.runelite.api.events.StatChanged
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.game.ItemManager
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameStateStore @Inject constructor(
    private val client: Client,
    private val itemManager: ItemManager,
    private val bankPersistence: BankPersistence,
) {

    private val current = AtomicReference(empty())
    private var loadedAccountHash: Long = 0L

    fun snapshot(): GameSnapshot = current.get()

    fun clear() {
        current.set(empty())
        loadedAccountHash = 0L
    }

    @Subscribe
    fun onGameStateChanged(event: GameStateChanged) {
        if (event.gameState == GameState.LOGIN_SCREEN || event.gameState == GameState.HOPPING) {
            current.updateAndGet { it.copy(loggedIn = false, player = null) }
        } else if (event.gameState == GameState.LOGGED_IN) {
            current.updateAndGet { it.copy(loggedIn = true) }
            maybeLoadPersistedBank()
        }
    }

    private fun maybeLoadPersistedBank() {
        val hash = runCatching { client.accountHash }.getOrDefault(-1L)
        if (hash <= 0 || hash == loadedAccountHash) return
        val loaded = bankPersistence.load(hash) ?: run {
            loadedAccountHash = hash
            return
        }
        loadedAccountHash = hash
        current.updateAndGet { current ->
            // Only restore if we haven't already seen the bank this session (lastSeenAt
            // from the loaded file is from the prior session — we want to keep current
            // session data if any).
            if (current.bank.lastSeenAt != null && current.bank.lastSeenAt >= loaded.lastSeenAt!!) {
                current
            } else {
                current.copy(bank = loaded)
            }
        }
    }

    @Subscribe
    fun onItemContainerChanged(event: ItemContainerChanged) {
        val container = event.itemContainer ?: return
        val now = System.currentTimeMillis()
        when (event.containerId) {
            InventoryID.INV -> current.updateAndGet {
                it.copy(inventory = ContainerSnapshot(container.toItems(), now))
            }
            InventoryID.BANK -> {
                val items = container.toItems()
                val newBank = BankSnapshot(items, now, items.sumOf { i -> i.quantity.toLong() })
                current.updateAndGet { it.copy(bank = newBank) }
                // Persist for next session.
                val hash = runCatching { client.accountHash }.getOrDefault(-1L)
                if (hash > 0) bankPersistence.save(hash, newBank)
            }
            InventoryID.WORN -> current.updateAndGet {
                it.copy(equipment = container.toEquipment())
            }
        }
    }

    @Subscribe
    fun onStatChanged(event: StatChanged) {
        current.updateAndGet { snap ->
            val updated = snap.skills.toMutableList()
            val name = event.skill.getName()
            val replacement = SkillSnapshot(
                skill = name,
                level = event.level,
                boostedLevel = event.boostedLevel,
                experience = event.xp,
            )
            val idx = updated.indexOfFirst { it.skill == name }
            if (idx >= 0) updated[idx] = replacement else updated.add(replacement)
            snap.copy(skills = updated.toList())
        }
    }

    @Subscribe
    fun onGameTick(@Suppress("UNUSED_PARAMETER") event: GameTick) {
        val now = System.currentTimeMillis()
        val player = readPlayer()
        val quests = readQuests()
        val nearby = readNearbyNpcs()
        val slayer = readSlayer()
        val ge = readGe()
        val buffs = readBuffs()
        val ground = readGroundItems()
        val objects = readNearbyObjects()
        val combat = readCombatInfo()
        val diary = readDiaryProgress()
        val spellbook = readSpellbook()
        val attackStyle = readAttackStyle()
        val ca = readCombatAchievements()
        val poh = readPoh()
        current.updateAndGet {
            it.copy(
                timestamp = now,
                loggedIn = client.gameState == GameState.LOGGED_IN,
                player = player,
                quests = quests,
                nearbyNpcs = nearby,
                slayer = slayer,
                geOffers = ge,
                buffs = buffs,
                groundItems = ground,
                nearbyObjects = objects,
                combat = combat,
                diary = diary,
                spellbook = spellbook,
                attackStyle = attackStyle,
                combatAchievements = ca,
                poh = poh,
            )
        }
    }

    private fun readCombatAchievements(): CombatAchievementsSnapshot {
        fun done(id: Int): Boolean =
            runCatching { client.getVarbitValue(id) }.getOrDefault(0) == 1
        val easy = done(Varbits.COMBAT_ACHIEVEMENT_TIER_EASY)
        val medium = done(Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM)
        val hard = done(Varbits.COMBAT_ACHIEVEMENT_TIER_HARD)
        val elite = done(Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE)
        val master = done(Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER)
        val gm = done(Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER)
        return CombatAchievementsSnapshot(
            easy, medium, hard, elite, master, gm,
            listOf(easy, medium, hard, elite, master, gm).count { it },
        )
    }

    private fun readPoh(): PohSnapshot? {
        val id = runCatching { client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) }.getOrDefault(-1)
        if (id < 0) return null
        val name = POH_LOCATIONS[id] ?: "Unknown ($id)"
        return PohSnapshot(id, name)
    }

    private fun readDiaryProgress(): DiaryProgressSnapshot? {
        // Each tier varbit is 1 if the player has fully completed that tier of that region.
        fun done(id: Int): Boolean =
            runCatching { client.getVarbitValue(id) }.getOrDefault(0) == 1

        val regions = mapOf(
            "Ardougne" to DiaryRegionProgress(
                done(Varbits.DIARY_ARDOUGNE_EASY),
                done(Varbits.DIARY_ARDOUGNE_MEDIUM),
                done(Varbits.DIARY_ARDOUGNE_HARD),
                done(Varbits.DIARY_ARDOUGNE_ELITE),
            ),
            "Desert" to DiaryRegionProgress(
                done(Varbits.DIARY_DESERT_EASY),
                done(Varbits.DIARY_DESERT_MEDIUM),
                done(Varbits.DIARY_DESERT_HARD),
                done(Varbits.DIARY_DESERT_ELITE),
            ),
            "Falador" to DiaryRegionProgress(
                done(Varbits.DIARY_FALADOR_EASY),
                done(Varbits.DIARY_FALADOR_MEDIUM),
                done(Varbits.DIARY_FALADOR_HARD),
                done(Varbits.DIARY_FALADOR_ELITE),
            ),
            "Fremennik" to DiaryRegionProgress(
                done(Varbits.DIARY_FREMENNIK_EASY),
                done(Varbits.DIARY_FREMENNIK_MEDIUM),
                done(Varbits.DIARY_FREMENNIK_HARD),
                done(Varbits.DIARY_FREMENNIK_ELITE),
            ),
            "Kandarin" to DiaryRegionProgress(
                done(Varbits.DIARY_KANDARIN_EASY),
                done(Varbits.DIARY_KANDARIN_MEDIUM),
                done(Varbits.DIARY_KANDARIN_HARD),
                done(Varbits.DIARY_KANDARIN_ELITE),
            ),
            "Karamja" to DiaryRegionProgress(
                done(Varbits.DIARY_KARAMJA_EASY),
                done(Varbits.DIARY_KARAMJA_MEDIUM),
                done(Varbits.DIARY_KARAMJA_HARD),
                done(Varbits.DIARY_KARAMJA_ELITE),
            ),
            "Kourend & Kebos" to DiaryRegionProgress(
                done(Varbits.DIARY_KOUREND_EASY),
                done(Varbits.DIARY_KOUREND_MEDIUM),
                done(Varbits.DIARY_KOUREND_HARD),
                done(Varbits.DIARY_KOUREND_ELITE),
            ),
            "Lumbridge & Draynor" to DiaryRegionProgress(
                done(Varbits.DIARY_LUMBRIDGE_EASY),
                done(Varbits.DIARY_LUMBRIDGE_MEDIUM),
                done(Varbits.DIARY_LUMBRIDGE_HARD),
                done(Varbits.DIARY_LUMBRIDGE_ELITE),
            ),
            "Morytania" to DiaryRegionProgress(
                done(Varbits.DIARY_MORYTANIA_EASY),
                done(Varbits.DIARY_MORYTANIA_MEDIUM),
                done(Varbits.DIARY_MORYTANIA_HARD),
                done(Varbits.DIARY_MORYTANIA_ELITE),
            ),
            "Varrock" to DiaryRegionProgress(
                done(Varbits.DIARY_VARROCK_EASY),
                done(Varbits.DIARY_VARROCK_MEDIUM),
                done(Varbits.DIARY_VARROCK_HARD),
                done(Varbits.DIARY_VARROCK_ELITE),
            ),
            "Western Provinces" to DiaryRegionProgress(
                done(Varbits.DIARY_WESTERN_EASY),
                done(Varbits.DIARY_WESTERN_MEDIUM),
                done(Varbits.DIARY_WESTERN_HARD),
                done(Varbits.DIARY_WESTERN_ELITE),
            ),
            "Wilderness" to DiaryRegionProgress(
                done(Varbits.DIARY_WILDERNESS_EASY),
                done(Varbits.DIARY_WILDERNESS_MEDIUM),
                done(Varbits.DIARY_WILDERNESS_HARD),
                done(Varbits.DIARY_WILDERNESS_ELITE),
            ),
        )
        val total = regions.values.sumOf { it.tiersComplete }
        return DiaryProgressSnapshot(regions = regions, totalTiersComplete = total)
    }

    private fun readSpellbook(): SpellbookSnapshot? {
        val id = runCatching { client.getVarbitValue(Varbits.SPELLBOOK) }.getOrDefault(-1)
        if (id < 0) return null
        val name = when (id) {
            0 -> "Standard"
            1 -> "Ancient"
            2 -> "Lunar"
            3 -> "Arceuus"
            else -> "Unknown ($id)"
        }
        return SpellbookSnapshot(id, name)
    }

    private fun readAttackStyle(): AttackStyleSnapshot? {
        val style = runCatching { client.getVarpValue(VarPlayer.ATTACK_STYLE) }.getOrDefault(-1)
        val weaponType = runCatching { client.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE) }
            .getOrDefault(-1)
        if (style < 0 && weaponType < 0) return null
        val autocast = runCatching { client.getVarbitValue(VarbitID.AUTOCAST_SPELL) }
            .getOrDefault(0).takeIf { it > 0 }
        val description = when (style) {
            0 -> "Slot 0 (often accurate / accurate slash / accurate bash / accurate stab)"
            1 -> "Slot 1 (often aggressive / aggressive crush / rapid)"
            2 -> "Slot 2 (often controlled / longrange)"
            3 -> "Slot 3 (often defensive / longrange magic / autocast defensive)"
            else -> "Unknown style $style"
        }
        return AttackStyleSnapshot(
            styleIndex = style,
            weaponTypeId = weaponType,
            description = description,
            autocastSpellId = autocast,
        )
    }

    private fun readGroundItems(): List<GroundItemSnapshot> {
        val local = client.localPlayer ?: return emptyList()
        val origin = local.worldLocation ?: return emptyList()
        val scene = runCatching { client.scene }.getOrNull() ?: return emptyList()
        val tiles = runCatching { scene.tiles }.getOrNull() ?: return emptyList()
        val plane = origin.plane
        if (plane !in tiles.indices) return emptyList()
        val results = mutableListOf<GroundItemSnapshot>()
        val planeTiles = tiles[plane]
        for (x in planeTiles.indices) {
            for (y in planeTiles[x].indices) {
                val tile = planeTiles[x][y] ?: continue
                val items = runCatching { tile.groundItems }.getOrNull() ?: continue
                val wp = tile.worldLocation
                val dist = max(abs(wp.x - origin.x), abs(wp.y - origin.y))
                if (dist > GROUND_RADIUS) continue
                for (gi in items) {
                    val name = runCatching { itemManager.getItemComposition(gi.id).name }
                        .getOrDefault("Unknown")
                    results.add(
                        GroundItemSnapshot(
                            itemId = gi.id,
                            name = name,
                            quantity = gi.quantity,
                            distance = dist,
                            locationX = wp.x,
                            locationY = wp.y,
                        ),
                    )
                }
            }
        }
        return results.sortedBy { it.distance }.take(MAX_GROUND_ITEMS)
    }

    private fun readNearbyObjects(): List<NearbyObjectSnapshot> {
        val local = client.localPlayer ?: return emptyList()
        val origin = local.worldLocation ?: return emptyList()
        val scene = runCatching { client.scene }.getOrNull() ?: return emptyList()
        val tiles = runCatching { scene.tiles }.getOrNull() ?: return emptyList()
        val plane = origin.plane
        if (plane !in tiles.indices) return emptyList()
        val results = mutableListOf<NearbyObjectSnapshot>()
        val planeTiles = tiles[plane]
        // Restrict radius for objects — scenes can be dense
        for (x in planeTiles.indices) {
            for (y in planeTiles[x].indices) {
                val tile = planeTiles[x][y] ?: continue
                val wp = tile.worldLocation
                val dist = max(abs(wp.x - origin.x), abs(wp.y - origin.y))
                if (dist > OBJECT_RADIUS) continue

                val candidates = mutableListOf<Int>()
                runCatching { tile.gameObjects }.getOrNull()?.forEach {
                    if (it != null) candidates.add(it.id)
                }
                runCatching { tile.wallObject?.id }.getOrNull()?.let { candidates.add(it) }
                runCatching { tile.decorativeObject?.id }.getOrNull()?.let { candidates.add(it) }
                runCatching { tile.groundObject?.id }.getOrNull()?.let { candidates.add(it) }
                for (id in candidates) {
                    if (id <= 0) continue
                    val comp = runCatching { client.getObjectDefinition(id) }.getOrNull() ?: continue
                    val name = comp.name?.takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val acts = runCatching { comp.actions }.getOrNull()
                        ?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
                    if (acts.isEmpty()) continue // skip purely-decorative
                    results.add(
                        NearbyObjectSnapshot(
                            objectId = id,
                            name = name,
                            actions = acts,
                            distance = dist,
                            locationX = wp.x,
                            locationY = wp.y,
                        ),
                    )
                }
            }
        }
        // Dedupe by (name, x, y) so multi-tile objects only appear once
        return results
            .distinctBy { Triple(it.name, it.locationX, it.locationY) }
            .sortedBy { it.distance }
            .take(MAX_NEARBY_OBJECTS)
    }

    private fun readCombatInfo(): CombatInfoSnapshot? {
        val local = client.localPlayer ?: return null
        val origin = local.worldLocation
        val target: Actor? = local.interacting
        val targetSnap = target?.let { t ->
            val tWp = t.worldLocation
            val ratio = runCatching { t.healthRatio }.getOrDefault(-1)
            val scale = runCatching { t.healthScale }.getOrDefault(-1)
            val percent = if (scale > 0 && ratio >= 0) (ratio * 100) / scale else null
            val combatLvl = (t as? NPC)?.combatLevel
                ?: runCatching { t.combatLevel }.getOrDefault(0)
            val dist = if (tWp != null && origin != null) {
                max(abs(tWp.x - origin.x), abs(tWp.y - origin.y))
            } else null
            TargetSnapshot(
                name = t.name,
                combatLevel = combatLvl,
                healthRatio = ratio,
                healthScale = scale,
                healthPercent = percent,
                animationId = runCatching { t.animation }.getOrDefault(-1),
                locationX = tWp?.x,
                locationY = tWp?.y,
                distance = dist,
            )
        }
        return CombatInfoSnapshot(
            playerAnimationId = runCatching { local.animation }.getOrDefault(-1),
            target = targetSnap,
        )
    }

    private fun readSlayer(): SlayerSnapshot? {
        val taskRemaining = runCatching { client.getVarpValue(VarPlayer.SLAYER_TASK_SIZE) }
            .getOrDefault(0)
        val creatureId = runCatching { client.getVarpValue(VarPlayer.SLAYER_TASK_CREATURE) }
            .getOrDefault(0)
        if (taskRemaining == 0 && creatureId == 0) return null
        val masterId = runCatching { client.getVarbitValue(VarbitID.SLAYER_MASTER) }.getOrDefault(0)
        val points = runCatching { client.getVarbitValue(Varbits.SLAYER_POINTS) }.getOrDefault(0)
        val streak = runCatching { client.getVarbitValue(Varbits.SLAYER_TASK_STREAK) }.getOrDefault(0)
        val isBoss = runCatching { client.getVarbitValue(Varbits.SLAYER_TASK_BOSS) }.getOrDefault(0) != 0
        return SlayerSnapshot(
            masterId = masterId,
            masterName = SLAYER_MASTER_NAMES[masterId],
            creatureId = creatureId,
            taskRemaining = taskRemaining,
            points = points,
            streak = streak,
            isBoss = isBoss,
        )
    }

    private fun readGe(): List<GeOfferSnapshot> {
        val offers: Array<GrandExchangeOffer> =
            runCatching { client.grandExchangeOffers }.getOrNull() ?: return emptyList()
        return offers.asSequence().withIndex().mapNotNull { (idx, offer) ->
            val state = offer.state ?: return@mapNotNull null
            if (state.name == "EMPTY") return@mapNotNull null
            val itemId = offer.itemId
            val name = if (itemId > 0) runCatching { itemManager.getItemComposition(itemId).name }
                .getOrDefault("Unknown") else null
            GeOfferSnapshot(
                slot = idx,
                state = state.name,
                itemId = itemId,
                itemName = name,
                quantityFilled = offer.quantitySold,
                totalQuantity = offer.totalQuantity,
                pricePerItem = offer.price,
                totalSpent = offer.spent,
            )
        }.toList()
    }

    private fun readBuffs(): BuffsSnapshot {
        fun v(id: Int): Int? = runCatching { client.getVarbitValue(id).takeIf { it > 0 } }.getOrNull()
        val stamina = v(Varbits.STAMINA_EFFECT)?.let { it * 10 } // stored in 10-tick units
        val antifire = v(Varbits.ANTIFIRE)?.let { it * 30 }       // each unit = 30 ticks
        val superAntifire = v(Varbits.SUPER_ANTIFIRE)
        val vengActive = v(Varbits.VENGEANCE_ACTIVE) != null
        val vengCd = v(Varbits.VENGEANCE_COOLDOWN)
        val imbued = v(Varbits.IMBUED_HEART_COOLDOWN)?.let { it * 10 }
        val deathCharge = v(Varbits.DEATH_CHARGE_COOLDOWN)
        val divCombat = v(Varbits.DIVINE_SUPER_COMBAT)
        val divRange = v(Varbits.DIVINE_RANGING)
        val divMagic = v(Varbits.DIVINE_MAGIC)
        val nmzOver = v(Varbits.NMZ_OVERLOAD_REFRESHES_REMAINING)
        val coxOver = v(Varbits.COX_OVERLOAD_REFRESHES_REMAINING)
        val poison = runCatching { client.getVarpValue(VarPlayer.POISON).takeIf { it > 0 } }.getOrNull()
        return BuffsSnapshot(
            staminaTicks = stamina,
            antifireTicks = antifire,
            superAntifireTicks = superAntifire,
            vengeanceActive = vengActive,
            vengeanceCooldownTicks = vengCd,
            imbuedHeartCooldownTicks = imbued,
            deathChargeCooldownTicks = deathCharge,
            divineSuperCombatTicks = divCombat,
            divineRangingTicks = divRange,
            divineMagicTicks = divMagic,
            nmzOverloadRefreshes = nmzOver,
            coxOverloadRefreshes = coxOver,
            poisonTicks = poison,
        )
    }

    private fun readAccountType(): String {
        val v = runCatching { client.getVarbitValue(Varbits.ACCOUNT_TYPE) }.getOrDefault(0)
        return when (v) {
            1 -> "Ironman"
            2 -> "Ultimate Ironman"
            3 -> "Hardcore Ironman"
            4 -> "Group Ironman"
            5 -> "Hardcore Group Ironman"
            6 -> "Unranked Group Ironman"
            else -> "Normal"
        }
    }

    private fun readPlayer(): PlayerSnapshot? {
        val local = client.localPlayer ?: return null
        val loc: WorldPoint? = local.worldLocation
        val target: Actor? = local.interacting
        val activePrayers = Prayer.values()
            .filter { runCatching { client.isPrayerActive(it) }.getOrDefault(false) }
            .map { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } }
        val totalLevel = Skill.values()
            .filter { it != Skill.OVERALL }
            .sumOf { runCatching { client.getRealSkillLevel(it) }.getOrDefault(1) }
        val accountType = readAccountType()
        return PlayerSnapshot(
            name = local.name,
            combatLevel = local.combatLevel,
            totalLevel = totalLevel,
            world = client.world,
            accountType = accountType,
            locationX = loc?.x,
            locationY = loc?.y,
            plane = loc?.plane,
            regionId = loc?.regionID,
            hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS),
            hitpointsMax = client.getRealSkillLevel(Skill.HITPOINTS),
            prayer = client.getBoostedSkillLevel(Skill.PRAYER),
            prayerMax = client.getRealSkillLevel(Skill.PRAYER),
            runEnergyPercent = (client.energy / 100).coerceIn(0, 100),
            specialAttackPercent = (client.getVarpValue(VARP_SPECIAL) / 10).coerceIn(0, 100),
            activePrayers = activePrayers,
            inCombat = target != null,
            targetName = target?.name,
        )
    }

    private fun readNearbyNpcs(): List<NearbyNpcSnapshot> {
        val local = client.localPlayer ?: return emptyList()
        val origin = local.worldLocation ?: return emptyList()
        return client.npcs.asSequence()
            .filter { it != null }
            .mapNotNull { npc ->
                val name = npc.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val wp = npc.worldLocation ?: return@mapNotNull null
                if (wp.plane != origin.plane) return@mapNotNull null
                val dist = max(abs(wp.x - origin.x), abs(wp.y - origin.y))
                if (dist > MAX_NEARBY_DISTANCE) return@mapNotNull null
                NearbyNpcSnapshot(
                    name = name,
                    combatLevel = npc.combatLevel,
                    distance = dist,
                    locationX = wp.x,
                    locationY = wp.y,
                )
            }
            .sortedBy { it.distance }
            .take(MAX_NEARBY_NPCS)
            .toList()
    }

    private fun readQuests(): List<QuestSnapshot> =
        Quest.values().map { q ->
            QuestSnapshot(
                name = q.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                state = runCatching { q.getState(client).name }.getOrDefault("UNKNOWN"),
            )
        }

    private fun ItemContainer.toItems(): List<ItemSnapshot> =
        items.asSequence()
            .filter { it.id > 0 && it.quantity > 0 }
            .map { it.toSnapshot() }
            .toList()

    private fun ItemContainer.toEquipment(): List<EquipmentSlotSnapshot> =
        EquipmentInventorySlot.values().map { slot ->
            val item: Item? = runCatching { getItem(slot.slotIdx) }.getOrNull()
            EquipmentSlotSnapshot(
                slot = slot.name,
                item = item?.takeIf { it.id > 0 && it.quantity > 0 }?.toSnapshot(),
            )
        }

    private fun Item.toSnapshot(): ItemSnapshot =
        ItemSnapshot(
            id = id,
            name = runCatching { itemManager.getItemComposition(id).name }.getOrDefault("Unknown"),
            quantity = quantity,
        )

    companion object {
        private const val VARP_SPECIAL = 300
        private const val MAX_NEARBY_DISTANCE = 25
        private const val MAX_NEARBY_NPCS = 30
        private const val GROUND_RADIUS = 15
        private const val MAX_GROUND_ITEMS = 30
        private const val OBJECT_RADIUS = 12
        private const val MAX_NEARBY_OBJECTS = 25

        // POH location varbit → name. Order matches OSRS POH portal locations.
        private val POH_LOCATIONS = mapOf(
            0 to "Rimmington",
            1 to "Taverley",
            2 to "Pollnivneach",
            3 to "Rellekka",
            4 to "Brimhaven",
            5 to "Yanille",
            6 to "Hosidius",
            7 to "Prifddinas",
            8 to "Aldarin",
            9 to "Civitas illa Fortis",
        )

        // Slayer master varbit values → canonical names. Sourced from RuneLite's
        // SlayerPlugin and verified against current OSRS.
        private val SLAYER_MASTER_NAMES = mapOf(
            1 to "Turael",
            2 to "Mazchna",
            3 to "Vannaka",
            4 to "Chaeldar",
            5 to "Nieve / Steve",
            6 to "Duradel",
            7 to "Krystilia",
            8 to "Konar quo Maten",
            9 to "Aya",
        )

        fun empty(): GameSnapshot = GameSnapshot(
            timestamp = 0L,
            loggedIn = false,
            player = null,
            skills = emptyList(),
            inventory = ContainerSnapshot(emptyList(), null),
            equipment = emptyList(),
            bank = BankSnapshot(emptyList(), null, 0L),
            quests = emptyList(),
            nearbyNpcs = emptyList(),
            slayer = null,
            geOffers = emptyList(),
            buffs = null,
            groundItems = emptyList(),
            nearbyObjects = emptyList(),
            combat = null,
            diary = null,
            spellbook = null,
            attackStyle = null,
            combatAchievements = null,
            poh = null,
        )
    }
}
