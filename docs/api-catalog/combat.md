# Combat, prayer, magic

Everything below is on the **client thread** unless noted: `@Subscribe` handlers,
`Client.getVarbitValue(...)`, `getVarpValue(...)`, `isPrayerActive(...)`, `getBoostedSkillLevel(...)`,
and any `Actor`/`Player`/`NPC` getters must be called from there (use
`ClientThread.invokeLater` from other threads).

Most of the numeric constants in `net.runelite.api.Varbits`, `VarPlayer`, `HitsplatID`,
and `AnimationID` are now considered legacy — RuneLite added a generated `gameval` package
(`net.runelite.api.gameval.VarbitID`, `VarPlayerID`, `AnimationID`, `SpotanimID`, `NpcID`,
etc.) that is the canonical name source and is what new plugins (and the slayer/special-
counter plugins below) use. Both work; the gameval names tend to be more complete and
match the cache.

## Reading combat state

There is no single "in combat" flag on the client. Plugins infer it from a combination
of signals, with a short timeout window:

- **`Actor.isInteracting()` / `Actor.getInteracting(): Actor`** — non-null when this actor
  is targeting another actor for attack, follow, or trade. Combined with checking that
  the target is an `NPC` (or PvP `Player`), this is the cheapest "currently engaged"
  signal. Updated continuously by the server; can briefly disappear between attacks even
  when still in combat, so consumers usually keep a "last interacted at tick N" timer.
- **`InteractingChanged`** event (fields `source: Actor`, `target: Actor` — `target` may
  be null when interaction ends). Fired whenever the target changes. Good for "started
  fighting NPC X" detection.
- **`HitsplatApplied(actor, hitsplat)`** event — fires for *every* hitsplat the server
  sends, including ones that aren't rendered when 4+ are already on the actor. Hitsplats
  on the local player or on an NPC the local player just hit are reliable combat ticks
  (see Hitsplats section).
- **`AnimationChanged(actor)`** — fires whenever `Actor.getAnimation()` changes. Look up
  `actor.getAnimation()` in the event handler to read the new id. Combat attack
  animations (e.g. `gameval.AnimationID.HUMAN_2H_AXE_ATTACK = 10602`,
  `HUMAN_2H_AXE_SPECIAL = 10601`, `HUMAN_BOW = 426`, `HUMAN_STAFF_SPIKE = 412`) are the
  best per-swing signal for the local player. Note: `AnimationChanged` also fires for
  woodcutting, dancing, etc. — filter on a known set.
- **`Player.getAnimation(): int`** / **`Actor.getPoseAnimation(): int`** — current main
  anim and idle/walk anim respectively. `-1` (`AnimationID.IDLE`) means no active anim.
  The pose animation also changes when a weapon is equipped (different "ready" stance).
- **`Actor.isDead(): boolean`** plus **`ActorDeath(actor)`** event — fires once when the
  death animation begins. Useful to count kills.
- **`Actor.getHealthRatio(): int` and `getHealthScale(): int`** — server-quantized
  health: `ratio` is `0..scale`, not raw HP. `scale` is 30 for most NPCs but larger for
  bosses with wide health bars. Both return `-1` when the actor has no visible bar.
  To estimate remaining HP %: `ratio / (double) scale`. Raw HP is not transmitted.
- **`Actor.getCombatLevel(): int`** — NPC combat level (or own combat level for the local
  player). For the local player's *combat level* the canonical formula is in
  `Experience.getCombatLevel(attack, strength, defence, hitpoints, magic, ranged, prayer)`.
- **`Actor.getOverheadText(): String`** + **`OverheadTextChanged`** event — chat-bubble
  text (e.g. NPC battle taunts, "Eek!" from spiders). Some bosses use this to telegraph
  attacks.

### Special attack energy

| Var | id | Type | Units / values |
|---|---|---|---|
| `VarPlayer.SPECIAL_ATTACK_PERCENT` / `gameval.VarPlayerID.SA_ENERGY` | 300 | varp | Special-attack energy ×10. `1000` = 100%, decreases by `250` per spec (most weapons). |
| `VarPlayer.SPECIAL_ATTACK_ENABLED` / `gameval.VarPlayerID.SA_ATTACK` | 301 | varp | `1` if the spec orb is toggled on, `0` otherwise. The next attack consumes spec. |
| `Varbits.PVP_SPEC_ORB` | 8121 | varbit | `1` when player is in a PvP zone (was the old "spec orb disabled in PvP" flag; the disable was removed in 2023 but the varbit still updates). |

Read via `client.getVarpValue(VarPlayerID.SA_ENERGY)`. Watch for changes with
`VarbitChanged` and filter on `event.getVarpId() == VarPlayerID.SA_ENERGY` — see
`SpecialCounterPlugin.onVarbitChanged` for the canonical "spec just fired" detection
(value decreased compared to last cached value).

### Run energy and weight (not combat per se, but read alongside)

- **`Client.getEnergy(): int`** — run energy in units of 1/100th of a percent (0..10000).
  Divide by 100 for percent.
- **`Client.getWeight(): int`** — carry weight in kg.

## Skills

`Skill` (`net.runelite.api.Skill`) is an enum of the 24 skills, with each entry exposing
`getName(): String` and `isMembers(): boolean`. Combat-relevant entries:
`ATTACK`, `STRENGTH`, `DEFENCE`, `HITPOINTS`, `RANGED`, `MAGIC`, `PRAYER`, `SLAYER`.
The deprecated sentinel `Skill.OVERALL` is `null`; do not branch on it.

| Method on `Client` | Returns | Notes |
|---|---|---|
| `getRealSkillLevel(Skill): int` | 1–99 (or 120 for cape grinds; max from xp table) | Static unboosted level from xp. Updated on level-up. |
| `getBoostedSkillLevel(Skill): int` | current visible level | Includes overloads, prayer drains, stat-up potions. Equal to real level unless boosted. |
| `getSkillExperience(Skill): int` | total xp in skill, 0..200_000_000 | Max xp = 200M; level table in `Experience`. |
| `getOverallExperience(): long` | sum across skills | Useful for total-xp tracking. |
| `getBoostedSkillLevels(): int[]` | length-24 array indexed by `Skill.ordinal()` | Cheaper if you read several at once. |
| `getRealSkillLevels(): int[]` | same | |
| `getSkillExperiences(): int[]` | same | |
| `queueChangedSkill(Skill): void` | — | Force a `StatChanged` to be posted next tick; rarely needed. |

**Events:**
- **`StatChanged(skill, xp, level, boostedLevel)`** — fires when xp, real level, or boosted
  level changes for any skill. Filter on `event.getSkill()`.
- **`FakeXpDrop(skill, xp)`** — pseudo xp drops (e.g. dharok damage taken). No actual xp
  is awarded; surfaces only because the client renders an xp-drop sprite.
- **`BoostedLevelChanged(skill)`** (in `events/` if present) — boost change without xp
  change.

The osrs-llm-helper plugin already subscribes to `StatChanged` for xp deltas.

## Prayer

`net.runelite.api.Prayer` is an enum of all in-game prayers. Each entry exposes
`getVarbit(): int` returning the varbit id that is `1` while the prayer is active. The
enum covers the standard prayer book (Thick Skin … Augury) and the new Ruinous Powers
book (`RP_*` variants, levels 60–92).

```kotlin
val rigourActive = client.getVarbitValue(Prayer.RIGOUR.varbit) == 1
```

`Client.isPrayerActive(Prayer): boolean` exists but is **`@Deprecated`** because it does
not handle the Deadeye/Eagle Eye and Mystic Vigour/Might variants properly. Prefer
reading `client.getVarbitValue(prayer.getVarbit())` directly.

### Selected prayer varbits (from `Varbits.java`)

| Constant | id | Meaning |
|---|---|---|
| `QUICK_PRAYER` | 4103 | 1 = quick-prayer toggle is on |
| `PRAYER_PROTECT_FROM_MAGIC` | 4116 | active |
| `PRAYER_PROTECT_FROM_MISSILES` | 4117 | active |
| `PRAYER_PROTECT_FROM_MELEE` | 4118 | active |
| `PRAYER_RETRIBUTION` | 4119 | active |
| `PRAYER_REDEMPTION` | 4120 | active |
| `PRAYER_SMITE` | 4121 | active |
| `PRAYER_PIETY` | 4129 | active |
| `PRAYER_RIGOUR` | 5464 | active |
| `PRAYER_AUGURY` | 5465 | active |
| `PRAYER_PRESERVE` | 5466 | active |
| `PRAYER_DEADEYE` | 16090 | active (replaces Eagle Eye when unlocked) |
| `PRAYER_MYSTIC_VIGOUR` | 16091 | active (replaces Mystic Might) |
| `PRAYER_DEADEYE_UNLOCKED` | 16097 | unlock toggle |
| `PRAYER_MYSTIC_VIGOUR_UNLOCKED` | 16098 | unlock toggle |
| `PRAYERBOOK` | 14826 | 0 = standard, 1 = Ruinous Powers |
| `BUFF_PRAYER_REGENERATION` | 11361 | rapid-heal-style buff timer |

For the full list of standard and Ruinous Powers prayer varbits, see lines 146–207 of
`Varbits.java` (constants `PRAYER_THICK_SKIN` … `PRAYER_RP_INTENSIFY`).

### Head icon (overhead prayer / curse icon)

`Player.getOverheadIcon(): HeadIcon?` — the icon currently rendered above the local or
another player's head. `HeadIcon` enum values:
`MELEE, RANGED, MAGIC, RETRIBUTION, SMITE, REDEMPTION, RANGE_MAGE, RANGE_MELEE,
MAGE_MELEE, RANGE_MAGE_MELEE, WRATH, SOUL_SPLIT, DEFLECT_MELEE, DEFLECT_RANGE,
DEFLECT_MAGE` (the last six are Ruinous-Powers curses / multi-style boss icons such as
Kalphite Queen's `RANGE_MAGE`).

NPC overhead protect icons are stored on `NPC` as a sparse pair of arrays:
- `NPC.getOverheadArchiveIds(): int[]?`
- `NPC.getOverheadSpriteIds(): short[]?`

Each `(archive, sprite)` pair at the same index describes one overlay sprite; a value of
`-1` means an empty slot. Iterate together. There is no built-in enum that maps these to
"melee/range/mage" — plugins typically hard-code archive+sprite combos per boss.

### Prayer points

Prayer is just `Skill.PRAYER`. `getBoostedSkillLevel(Skill.PRAYER)` is the current prayer
point count (1 point per level). It ticks down while any prayer is on; rate depends on
prayer bonus from gear.

## Hitsplats / damage tracking

The **`HitsplatApplied`** event delivers a `Hitsplat` for an `Actor` every time the
server sends one. From the event:

- `event.getActor(): Actor` — who took the hit (could be the local player, an NPC, or
  another player).
- `event.getHitsplat(): Hitsplat`:
  - `getHitsplatType(): int` — one of the `HitsplatID` constants below.
  - `getAmount(): int` — damage value shown on the splat (0 for blocks, 0+ for poison
    ticks).
  - `getDisappearsOnGameCycle(): int` — client game-cycle the splat fades on; compare to
    `client.getGameCycle()`.
  - `isMine(): boolean` — true if any of the `*_ME*` types (damage I dealt, including
    max-hit variants and blocks).
  - `isOthers(): boolean` — true if any of the `*_OTHER*` types (damage someone else
    dealt to this actor, or block-by-other).

### `HitsplatID` constants

Damage from / to the local player:

| Constant | id | When |
|---|---|---|
| `DAMAGE_ME` | 16 | Red splat — I took damage. |
| `DAMAGE_OTHER` | 17 | Blue splat — someone else hit this actor. |
| `BLOCK_ME` | 12 | Blue 0 — I blocked a hit. |
| `BLOCK_OTHER` | 13 | Black 0 — someone else blocked. |
| `DAMAGE_MAX_ME` | 43 | Red splat marked as my max hit (visual variant only). |

Tinted variants (used in TOA party damage, raid attribution, etc.):
`DAMAGE_ME_CYAN=18 / DAMAGE_OTHER_CYAN=19 / DAMAGE_ME_ORANGE=20 / DAMAGE_OTHER_ORANGE=21 /
DAMAGE_ME_YELLOW=22 / DAMAGE_OTHER_YELLOW=23 / DAMAGE_ME_WHITE=24 / DAMAGE_OTHER_WHITE=25`
plus matching `DAMAGE_MAX_ME_*` (44–47).

Status / DOT splats (`getAmount()` is the tick damage):

| Constant | id | |
|---|---|---|
| `POISON` | 65 | Green poison damage |
| `VENOM` | 5 | Bright-green venom damage (caps at 20) |
| `DISEASE` | 4 | Yellow disease damage |
| `DISEASE_BLOCKED` | 3 | |
| `HEAL` | 6 | Healing splat (e.g. guthans, soul split self-heal) |
| `PRAYER_DRAIN` | 60 | Smite / Sanguinesti chip |
| `BLEED` | 67 | Bleed mechanics |
| `BURN` | 74 | Burn (Wrath, Inferno) |
| `CORRUPTION` | 0 | Corruption spell DoT |
| `SANITY_DRAIN` | 71 / `SANITY_RESTORE` | 72 | Vardorvis-style sanity |
| `DOOM` | 73 | Colosseum doom |
| `CYAN_UP` | 11 / `CYAN_DOWN` | 15 | Stat-change indicator |
| `DAMAGE_ME_POISE` | 53 / `DAMAGE_OTHER_POISE` | 54 / `DAMAGE_MAX_ME_POISE` | 55 | Poise-mechanic hits |

**Use cases:**
- Total damage dealt: sum `getAmount()` of every `Hitsplat` where `isMine() && actor !=
  localPlayer` (excluding the `BLOCK_ME` block splat with amount 0).
- Damage taken: sum `Hitsplat.getAmount()` for `event.actor == client.getLocalPlayer()`
  and the type is one of `DAMAGE_ME`, `DAMAGE_MAX_ME`, the colour variants, or `POISON` /
  `VENOM` / `BURN` for over-time chip.
- Max-hit detection: `getHitsplatType()` in the `DAMAGE_MAX_ME*` range. Note the server
  marks the visual variant; the actual numeric value still appears in `getAmount()`.

**Gotcha:** Hitsplats are applied with a delay matching the attack travel time. For
projectile (ranged/magic) attacks, the splat lands several ticks after the animation. To
attribute a splat to a specific spec, plugins record the attack tick (from `AnimationChanged`)
and only count splats arriving N ticks later — see `SpecialCounterPlugin`.

## Slayer

Slayer state is split between `VarPlayer` (`varp`) for the per-task numbers and
`Varbits`/`gameval.VarbitID` for the master, points, and streaks. The slayer plugin
(`client/plugins/slayer/SlayerPlugin.java`) reads these directly.

| Var (canonical gameval name) | id | What |
|---|---|---|
| `VarPlayerID.SLAYER_COUNT` (`VarPlayer.SLAYER_TASK_SIZE`) | 394 | Creatures remaining on current task. `0` = no task. |
| `VarPlayerID.SLAYER_TARGET` (`VarPlayer.SLAYER_TASK_CREATURE`) | 395 | Task creature id (look up name in the `slayer_task` enum). |
| `VarPlayerID.SLAYER_AREA` (`VarPlayer.SLAYER_TASK_LOCATION`) | 2096 | Assigned task location id. `0` for "anywhere". |
| `VarPlayerID.SLAYER_COUNT_ORIGINAL` | 4258 | Initial assigned amount (for "X of Y" progress). |
| `VarbitID.SLAYER_MASTER` | 4067 | Master that assigned the current task (`0` Turael, … `7` Krystilia, etc.). |
| `VarbitID.SLAYER_POINTS` / `Varbits.SLAYER_POINTS` | 4068 | Current slayer reward points. |
| `VarbitID.SLAYER_TASKS_COMPLETED` / `Varbits.SLAYER_TASK_STREAK` | 4069 | Normal-task completion streak. |
| `VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED` | 5617 | Krystilia (wilderness) streak — used instead of 4069 when last master == Krystilia. |
| `VarbitID.SLAYER_TARGET_BOSSID` / `Varbits.SLAYER_TASK_BOSS` | 4723 | When boss-slayer is unlocked and the task is a boss, this holds the boss id. |
| `VarPlayerID.SLAYER_REWARDS_UNLOCKS` / `_UNLOCKS1` / `_BLOCKED` etc. | 1076 / 1344 / 1096 | Bitfields of unlocked rewards / blocked tasks. |
| `Varbits.SUPERIOR_ENABLED` | 5362 | Bigger and Badder unlock toggle on/off. |

To resolve a task creature id to a name, look up the value of `SLAYER_TARGET` in the
`slayer_task` cache enum (or use the hard-coded `Task` enum in the bundled slayer plugin).

**Detecting a kill against task:** subscribe to `NpcLootReceived` or `NpcDespawned`, match
the NPC name (or its slayer category) against the current task, and decrement a counter.
The bundled plugin also listens to chat for "Your Slayer task is complete!" messages.

## Spellbook / magic

| Var | id | Type | Values |
|---|---|---|---|
| `Varbits.SPELLBOOK` | 4070 | varbit | `0` Standard, `1` Ancients, `2` Lunar, `3` Arceuus. |
| `Varbits.SPELLBOOK_SWAP` | 3617 | varbit | `1` while a temporary spellbook-swap (MA2 cape, dream) is active. |
| `Varbits.SPELLBOOK_SUBMENU` | 9730 | varbit | Selected submenu inside the spellbook tab. |
| `VarbitID.AUTOCAST_SET` | 275 | varbit | `1` if an autocast spell is currently set, `0` otherwise. |
| `VarbitID.AUTOCAST_SPELL` | 276 | varbit | Encoded selected autocast spell id (lookup in spell defs). |
| `VarbitID.AUTOCAST_DEFMODE` / `Varbits.DEFENSIVE_CASTING_MODE` | 2668 | varbit | `1` = defensive autocast, `0` = aggressive autocast. |
| `Varbits.MAGIC_IMBUE` | 5438 | varbit | Magic Imbue timer: remaining ticks = `value × 10`. |
| `Varbits.VENGEANCE_ACTIVE` | 2450 | varbit | `1` while vengeance is loaded. |
| `Varbits.VENGEANCE_COOLDOWN` | 2451 | varbit | Cooldown timer. |
| `Varbits.DEATH_CHARGE` | 12411 | varbit | `1` while Death Charge is active. |
| `Varbits.RESURRECT_THRALL` | 12413 | varbit | `1` while a thrall is summoned. |
| `Varbits.SHADOW_VEIL` | 12414 | varbit | `1` while Shadow Veil is active. |
| `Varbits.CHARGE_GOD_SPELL` (`VarPlayer.CHARGE_GOD_SPELL`) | 272 | varp | Remaining duration = `value × 2` ticks. |
| `Varbits.TELEBLOCK` | 4163 | varbit | `<=100`: ticks of immunity remaining. `>100`: actively teleblocked. |

Detect active spellbook in one read:

```kotlin
val spellbook = when (client.getVarbitValue(Varbits.SPELLBOOK)) {
    0 -> "standard"; 1 -> "ancients"; 2 -> "lunar"; 3 -> "arceuus"; else -> "?"
}
```

Spellbook-change events come through `VarbitChanged` with `getVarbitId() == 4070`.

## Attack styles

| Var | id | Type | Values |
|---|---|---|---|
| `VarPlayer.ATTACK_STYLE` | 43 | varp | 0–3: the four style tabs on the combat panel. The meaning of each index depends on the weapon. |
| `Varbits.EQUIPPED_WEAPON_TYPE` | 357 | varbit | Weapon-type id used to look up the available styles in the `wpn_type_*` cache enums. |

Together these tell you whether the player is currently "accurate / aggressive / defensive
/ controlled / rapid / longrange / standard spells". To translate the pair into a
human-readable style or to detect (e.g.) "is on Defence XP", plugins normally hard-code
a `WeaponType -> AttackStyle[]` table or look up the `wpn_type_*` struct. There is no
direct API enum.

Also relevant:
- `Varbits.MULTICOMBAT_AREA` (4605) — `1` when the local player stands on a multi-way
  tile.

## Other combat-adjacent state

| Var / API | Meaning |
|---|---|
| `VarPlayer.POISON` (102) | See the long doc-comment in `VarPlayer.java`: negative = poison/venom immunity timer; positive small = poison damage; `>= 1_000_000` = venomed; capped at 20. |
| `Varbits.CORP_DAMAGE` (999) | Damage dealt to Corporeal Beast (for instance contribution). |
| `Varbits.PARASITE` (10151) | Nightmare parasite infected (`1`) vs not (`0`). |
| `Varbits.GOD_WARS_ALTAR_COOLDOWN` (4099) | Ticks remaining ×100 before re-blessing at GWD altar. |
| `Varbits.DRAGONFIRE_SHIELD_COOLDOWN` (6539) | Ticks remaining ×8. |
| `Varbits.IMBUED_HEART_COOLDOWN` (5361) | Ticks remaining ×10. |
| `Varbits.MENAPHITE_REMEDY` (14448) / `BUFF_STAT_BOOST` (14344) / `NMZ_OVERLOAD_REFRESHES_REMAINING` (3955) / `COX_OVERLOAD_REFRESHES_REMAINING` (5418) | Stat-boost refresh counters that tick down every 25 ticks (15 s). |
| `Varbits.COMBAT_ACHIEVEMENT_TIER_EASY..GRANDMASTER` (12863–12868) | `2` once that CA tier is completed. |
| `Player.getSkullIcon(): int` (`SkullIcon` constants) | `-1` if not skulled; otherwise the skull-icon id (regular skull, deadman, etc.). |

## Recipe: "did I just hit a thing?"

```kotlin
@Subscribe
fun onAnimationChanged(e: AnimationChanged) {
    if (e.actor !== client.localPlayer) return
    val anim = client.localPlayer.animation
    if (anim in COMBAT_ANIMS) {
        lastAttackTick = client.tickCount
    }
}

@Subscribe
fun onHitsplatApplied(e: HitsplatApplied) {
    if (e.actor === client.localPlayer && e.hitsplat.isMine.not()) {
        // damage I took
        totalTaken += e.hitsplat.amount
    } else if (e.hitsplat.isMine) {
        // damage I dealt to e.actor (an NPC or PvP player)
        totalDealt += e.hitsplat.amount
    }
}

@Subscribe
fun onVarbitChanged(e: VarbitChanged) {
    if (e.varpId == VarPlayerID.SA_ENERGY) {
        val pct = e.value / 10  // 0..100
        if (pct < lastSpecPct) onSpecJustFired()
        lastSpecPct = pct
    }
}
```

`COMBAT_ANIMS` should be a `Set<Int>` of animation ids relevant to the player's weapon
class — the easiest source is the `gameval.AnimationID.HUMAN_*_ATTACK` /
`HUMAN_*_SPECIAL` constants matching the equipped weapon type.
