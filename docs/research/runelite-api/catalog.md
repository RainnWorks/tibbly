# RuneLite API — depth catalog (RAI-5)

> One-stop reference for "everything the RuneLite client can tell our LLM".
> Sourced from a walk of the [runelite-api](https://github.com/runelite/runelite/tree/master/runelite-api/src/main/java/net/runelite/api)
> javadoc + GitHub source on 2026-06-21, cross-referenced against what we
> already register in `apps/plugin/src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt`
> and `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolRegistry.kt`.
>
> Purpose: input to RAI-25's family-tag refinement (GAPS.md A7). Where a
> tool already exists in `ToolRegistry.kt` it is tagged "Existing"; new
> proposals are tagged "Proposed". A focused recommendation set lives in
> [`_SUMMARY.md`](./_SUMMARY.md); this file is the long-form catalog.

## Table of contents

1. [Methodology + hard non-goals](#1-methodology--hard-non-goals)
2. [What we already expose (audit of the current 72)](#2-what-we-already-expose-audit-of-the-current-72)
3. [Tier 0 — top-five unblockers (ship next)](#3-tier-0--top-five-unblockers-ship-next)
4. [Tier 1 — high-value unexposed surfaces](#4-tier-1--high-value-unexposed-surfaces)
   - [4.1 Account / identity / world-state](#41-account--identity--world-state)
   - [4.2 Varbits and VarPlayers — the goldmine](#42-varbits-and-varplayers--the-goldmine)
   - [4.3 Combat depth — prayers, projectiles, hitsplats, animations](#43-combat-depth--prayers-projectiles-hitsplats-animations)
   - [4.4 Raids & instanced content](#44-raids--instanced-content)
   - [4.5 Group iron man + clan + friends](#45-group-iron-man--clan--friends)
   - [4.6 Leagues / seasonal content](#46-leagues--seasonal-content)
   - [4.7 Music + ambient audio](#47-music--ambient-audio)
   - [4.8 World map + minimap + navigation hooks](#48-world-map--minimap--navigation-hooks)
   - [4.9 NPC dialog + chatbox panel](#49-npc-dialog--chatbox-panel)
   - [4.10 Farming / Hunter / Construction skill-specific state](#410-farming--hunter--construction-skill-specific-state)
   - [4.11 Player rendering / appearance / spotanims](#411-player-rendering--appearance--spotanims)
   - [4.12 Camera + viewport (overlay positioning)](#412-camera--viewport-overlay-positioning)
   - [4.13 Walker hooks + pathfinding telemetry](#413-walker-hooks--pathfinding-telemetry)
   - [4.14 Overhead icons / headicons](#414-overhead-icons--headicons)
   - [4.15 Widgets — readable interface telemetry](#415-widgets--readable-interface-telemetry)
5. [Tier 2 — exotic / edge surfaces](#5-tier-2--exotic--edge-surfaces)
6. [Family-tag refinement recommendations for ToolFamily.kt](#6-family-tag-refinement-recommendations-for-toolfamilykt)
7. [Token-cost flagging](#7-token-cost-flagging)
8. [Headline table (also reproduced in _SUMMARY.md)](#8-headline-table)
9. [Open questions](#9-open-questions)
10. [Source links](#10-source-links)

---

## 1. Methodology + hard non-goals

**How this catalog was built**

1. Read `apps/plugin/src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt`
   (2479 lines, the developer-only debug surface) and
   `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolRegistry.kt`
   (the cloud mirror, 73 entries including the `enable_tools` meta-tool —
   so the "72 tools" claim in CLAUDE.md is off by one; flagged in §9).
2. Walked the runelite-api package tree on GitHub at `master`:
   `net.runelite.api`, `.events`, `.clan`, `.coords`, `.hooks`, `.vars`,
   `.widgets`, `.worldmap`. Plus `.gameval` / `.dbtable` for completeness.
3. For each unexposed surface, asked: "would the LLM use this if it knew?"
   If yes, scored on LLM-leverage × implementation cost.
4. Family assignment: existing tools keep their `ToolFamily.kt` value;
   proposed tools get a recommended family and may motivate a new family
   (see §6).

**Hard non-goals — enforced by every entry in this catalog**

- **NO in-game automation.** No keyboard or mouse synthesis. No
  `Robot`-style input. No `MenuOptionClicked` synthesis. No autoclickers.
  We are stating game state and rendering overlays — that is the line.
  See `docs/runelite-hub/POLICY_SUMMARY.md` (rejected: any plugin doing
  bot logic) and PR #11453's rejection text for context.
- **NO writes to game state.** The only "writes" we do are:
  - overlays / world-map markers / tile markers (rendering only)
  - chatbox notifications surfaced to the local user
  - bank-tag / loadout local config writes (RuneLite-owned config keys)
- **NO data egress without consent.** Every state-read tool here is
  cleared by `cloud/EgressGate.kt` before it leaves the device. New
  tools that broaden the data leaving the device require a re-pass on
  `docs/runelite-hub/DATA_DISCLOSURE.md`.
- **NO HTTP listeners on the plugin process.** GAPS.md A1 documents
  the still-live legacy `McpServerService` HTTP listener; new tools
  surface to the cloud via WS only, never on a localhost socket.

Each entry below specifies R (read), O (overlay / render-only write),
or M (local-config / meta — e.g. bank tag, loadout). No entry is ever
W-against-game-state. If you see a future PR adding a tool that would
synthesise input, that is the bright-line failure.

---

## 2. What we already expose (audit of the current 72)

This section reconciles `ToolRegistry.kt` against the RuneLite API to
make the gap analysis concrete. Each entry: existing tool name, the
RuneLite API surface it ultimately calls, current family, and a note
on whether the family assignment looks right.

> The cloud registry has **73** entries (not 72). `enable_tools` is the
> meta-tool that lets the LLM widen its tool surface mid-turn; CLAUDE.md
> rounded down. We could either drop `enable_tools` from the per-tool
> token-cost math (it's MCP-meta, not game-state) or update CLAUDE.md.
> See §9 Q-RAI5-1.

### CORE (15 tools)

| Tool | RuneLite API call (illustrative) | R/O/M | Family OK? |
|---|---|---|---|
| `chat_message` | `Client.addChatMessage(ChatMessageType, ...)` | O | Yes |
| `enable_tools` | meta-tool — handled by `EnableToolsTool` only | — | Yes |
| `find_item` | `ItemManager.search(name)` (RL client extension) | R | Yes |
| `ge_price` | `ItemManager.getItemPrice(id)` | R | Yes |
| `get_active_clue` | `Client.getItemContainer(InventoryID.INVENTORY)` + clue-step varbits | R | Yes |
| `get_equipment` | `Client.getItemContainer(InventoryID.EQUIPMENT)` | R | Yes |
| `get_event_log` | Subscribed event bus tail (`@Subscribe` ring-buffer) | R | Yes |
| `get_inventory` | `Client.getItemContainer(InventoryID.INVENTORY)` | R | Yes |
| `get_player_state` | `Client.getLocalPlayer()` + `getEnergy()` + `getWeight()` + plane | R | Yes |
| `get_stats` | `Client.getRealSkillLevels()` + `getBoostedSkillLevels()` + `getSkillExperiences()` | R | Yes |
| `list_open_interfaces` | walk `Client.getWidgetRoots()` filtered by visibility | R | Yes |
| `notify_player` | `Notifier.notify(...)` (RL client extension) | O | Yes |
| `read_interface` | `Client.getWidget(group, child)` recursive dump | R | Yes |
| `wiki_page` | OSRS Wiki REST | R | Yes |
| `wiki_search` | OSRS Wiki REST | R | Yes |

Verdict: CORE is well-sized. `find_item` arguably belongs in `LOADOUTS`
(it's mostly a bank-loadout helper), but the convenience cost of moving
it out of CORE outweighs the family-purity gain.

### BANKING (10), QUEST (4), QUEST_ITEMS (1), NAV (5), COMBAT (8), GE (1), SKILLS (2), HIGHLIGHTS (19), LOADOUTS (2), GROUNDSTATE (3), FISHING (1), PARTY (1), SLAYER (1)

Per-family verdict against `ToolRegistry.kt`:

- **BANKING (10/10).** Tight. No obvious gaps; `Client.getItemContainer(InventoryID.BANK)` plus `BankTagPlugin` config covers everything we register.
- **QUEST (4).** `get_quest_data`, `get_quest_guide`, `get_quests`,
  `get_diary_progress`. All read-only against `Client.getQuests()` and
  the wiki. **Gap:** no varbit-driven *current quest step* tool — quests
  embed their progress in `VarPlayer.QUEST_NUMBER` (and many quest-
  specific varbits). Proposal: `get_quest_progress` (Tier 1 §4.2).
- **QUEST_ITEMS (1).** Underweight; `get_item_mapping` is a single
  cross-reference table. Family arguably folds into `QUEST`. See §6.
- **NAV (5).** Pathfinding + transports + POIs. Solid. **Gap:** no
  world-map *current-target* read (`WorldMap.getWorldMapPosition()`
  surfaces what the user is looking at on the map — useful for "where
  am I planning to go?" intent). Proposal: `get_world_map_focus` (§4.8).
- **COMBAT (8).** `attack_style`, `buffs`, `combat_achievements`,
  `combat_info`, `hitsplat_history`, `npc_max_hp`, `poh`, `spellbook`.
  **Major gap:** no *active prayer flick* read, no *target projectile*
  read, no *target NPC overhead icon* read. These are the three highest-
  leverage combat reads we're missing. Tier 0 §3.
  Note: `get_poh` (Player-Owned House) likely belongs in a future
  HOUSING family rather than COMBAT — it's only here because PoH is
  often combat-prep (pool + altar). Low priority.
- **GE (1).** Just `get_ge_offers`. **Gap:** no GE history, no GE
  search-result, no current-search-query. Tier 2 §5.
- **SKILLS (2).** `get_session_loot` + `get_xp_rates`. Tightly scoped.
  **Gap:** no farming patch state, no hunter trap state. Tier 1 §4.10.
- **HIGHLIGHTS (19).** Largest family. Already well-shaped: 4 entity
  types × {add, remove, list} + `add/remove/list_persistent_markers` +
  `list_user_markers` + `mark_tile` + `point_at` + `clear_visuals`.
  Solid. No proposed expansion.
- **LOADOUTS (2).** `best_in_slot_from_bank`, `save_equipment_loadout`.
  Tight. **Mild gap:** no "load saved loadout into UI guidance" — but
  that's adjacent to automation, so deliberately not exposed.
- **GROUNDSTATE (3).** `get_ground_items`, `get_nearby_npcs`,
  `get_nearby_objects`. Solid. **Gap:** no `get_nearby_decorative_objects`
  (rare-needed) and no `get_world_entities` (only matters in raids /
  POH / leagues). Tier 1 §4.4.
- **FISHING (1).** Just `find_fishing_spot`. Underused family.
- **PARTY (1).** Just `get_party`. **Gap:** entire group-iron-man state
  surface is missing — clan channel members, GIM HP/prayer/inventory of
  party-mates. Tier 1 §4.5.
- **SLAYER (1).** Just `get_slayer_task`. **Gap:** no slayer-streak,
  no slayer-points, no superior-active read (`Varbits.SUPERIOR_ENABLED`).
  Tier 1 §4.2.

---

## 3. Tier 0 — top-five unblockers (ship next)

Highest LLM-leverage × lowest implementation cost. These five are the
"if we only ship five new tools this month, these five" recommendation.
Each has a 1-2 line Kotlin sketch against the RuneLite API.

### Tier 0 (1/5): `get_account_identity` — **CORE**

What it returns: the canonical handle the LLM needs to talk to the
player coherently. Display name, account type (NORMAL / IRONMAN /
HARDCORE_IRONMAN / ULTIMATE_IRONMAN / GROUP_IRONMAN / HARDCORE_GROUP_IRONMAN),
world id, world type set (MEMBERS / PVP / DEADMAN / LEAGUES / TOURNAMENT /
SEASONAL), the launcher display name (which can differ on Jagex accounts).

API surface:
- `Client.getLocalPlayer()?.getName()` — display name.
- `Client.getAccountType()` returning `AccountType` enum.
- `Client.getWorld()` — world number.
- `Client.getWorldType()` returning `EnumSet<WorldType>`.
- `Client.getLauncherDisplayName()` — Jagex-account display.
- `Client.getAccountHash()` (via `AccountHashChanged` event for persistence).

Kotlin sketch:
```kotlin
val ident = JsonObject().apply {
    addProperty("name", client.localPlayer?.name)
    addProperty("accountType", client.accountType?.name)
    addProperty("world", client.world)
    add("worldFlags", client.worldType.map { it.name }.toJsonArray())
    addProperty("launcherName", client.launcherDisplayName)
    addProperty("accountHash", client.accountHash)
}
```

Family: **CORE** — the LLM should know what kind of account it's
advising on the first turn (HCIM advice differs from main advice).

Gating heuristic: always on (CORE).

Token cost: ~150 (~7 fields, all primitives + small string array).

Marketing pitch: *"Tibbly never confuses your main with your iron — and
knows when to stop suggesting trades because you're on a Hardcore
Ironman world."*

Why this is #1: today the LLM has to infer account type from the
event log (`get_event_log` surfaces hit-splats and inventory changes,
but not "this is an Ultimate Ironman, do not suggest banking"). The
fix is two getters and a token-cheap JSON object.

### Tier 0 (2/5): `get_raid_layout` — new family **RAID** or extend **COMBAT**

What it returns: which raid the player is in (CoX / ToB / ToA / none),
which room / phase, raid points, party size, party HP snapshots
(within fair play), and any raid-specific scaling varbits.

API surface:
- `Client.getVarbitValue(Varbits.IN_RAID)` — CoX-in-raid flag.
- `Client.getVarbitValue(Varbits.TOTAL_POINTS)` — CoX points.
- `Client.getVarbitValue(Varbits.RAID_STATE)` — CoX state machine.
- `Client.getVarbitValue(Varbits.THEATRE_OF_BLOOD)` — ToB state.
- `Client.getVarbitValue(Varbits.THEATRE_OF_BLOOD_ORB1..5)` — ToB
  party orb states (alive / dead / DC'd).
- `Client.getVarbitValue(Varbits.TOA_RAID_LEVEL)` — ToA invocation.
- `Client.getVarbitValue(Varbits.TOA_RAID_DAMAGE)` — ToA party damage.
- `Client.getVarbitValue(Varbits.TOA_MEMBER_0_HEALTH..7)` — ToA HP.
- `Client.getMapRegions()` — which region the player is in (the raid
  map regions are well-known: 12889 ToA, 14642 CoX, 12613 ToB).
- `Client.getInstanceTemplateChunks()` — which CoX rooms generated.

Kotlin sketch:
```kotlin
val r = JsonObject()
r.addProperty("activeRaid", whichRaid(client.mapRegions, client.varbitValue(Varbits.IN_RAID), ...))
r.addProperty("points", client.getVarbitValue(Varbits.TOTAL_POINTS))
r.addProperty("toaInvocation", client.getVarbitValue(Varbits.TOA_RAID_LEVEL))
r.add("partyHp", listOf(0..4).map { client.getVarbitValue(Varbits.TOA_MEMBER_0_HEALTH + it) }.toJsonArray())
```

Family: new family **RAIDS** (recommended) or extend `COMBAT`. See §6.

Gating heuristic: enable when user keywords include `raid`, `cox`,
`chambers`, `tob`, `theatre`, `theatre of blood`, `toa`, `tombs`,
`olm`, `verzik`, `wardens`, `akkha`, `zebak`, `kephri`, `baba`.

Token cost: ~180 (state-rich JSON: raid id + 5–8 numerics + party
array).

Marketing pitch: *"Tibbly knows you're at Verzik phase 3 with 47 HP
left on Soulreaper Axe and can call the next defensive flick."*

### Tier 0 (3/5): `get_target_projectiles` — **COMBAT**

What it returns: every projectile currently flying toward the player
or their target — projectile id (which maps to a spell / attack
animation), start tick, end tick, source actor, target actor,
remaining ticks. The most-requested-feature shape for combat advice.

API surface:
- `Client.getProjectiles()` returning `Deque<Projectile>`.
- For each projectile: `getRemainingCycles()`, `getStartCycle()`,
  `getEndCycle()`, `getInteracting()` (target), `getId()` (graphic id),
  `getX1()/getY1()` (origin tile), `getX()/getY()/getZ()` (current
  world coord).

Kotlin sketch:
```kotlin
val out = JsonArray()
for (p in client.projectiles) {
    out.add(JsonObject().apply {
        addProperty("id", p.id)
        addProperty("remainingTicks", p.remainingCycles)
        addProperty("targetIsLocalPlayer", p.interacting == client.localPlayer)
        addProperty("targetName", (p.interacting as? Actor)?.name)
    })
}
```

Family: **COMBAT**.

Gating heuristic: enable when user keywords include `prayer`, `flick`,
`mage`, `range`, `melee attack`, `protect from`, `incoming`, `boss`,
`vorkath`, `zulrah`, `nightmare`, `akkha`, `wardens`, `inferno`, `jad`,
`zuk`, `corp`, `sara`, `bandos`, `zamorak`, `arma`, `nex`. The list is
long; intent here is "any boss-fight talk".

Token cost: per-projectile ~25 tokens; cap at 8 projectiles → ~200
for the JSON envelope.

Marketing pitch: *"Tibbly sees the projectile leaving Akkha's hands
and tells you to pray Mage on tick 2 — before it lands."*

### Tier 0 (4/5): `get_farming_state` — new family **FARMING** or **SKILLS**

What it returns: farming patch state — every herb / tree / allotment /
flower / hops / fruit-tree / cactus / vine / spirit-tree / calquat /
crystal / hardwood patch on the player's account, with planting time,
elapsed real time, growth stage (varbit-driven), disease flag, weed
state, compost type. Read-mostly from varbits (every patch is one or
two varbits).

API surface:
- 200+ varbits, one or two per patch. The `FarmingTracker` plugin
  enumerates these; the canonical map lives in
  `net.runelite.client.plugins.timetracking.farming.FarmingPatch`.
- We do not need to import the plugin — we can mirror the constants.
- `Client.getVarbitValue(int)` for each.

Kotlin sketch (per patch):
```kotlin
val falador = JsonObject().apply {
    val raw = client.getVarbitValue(Varbits.FARMING_PATCH_FALADOR_HERB)
    addProperty("crop", FarmingCropCatalog.decode(raw)?.name)
    addProperty("stage", FarmingCropCatalog.decode(raw)?.stage)
    addProperty("diseased", FarmingCropCatalog.decode(raw)?.diseased)
}
```

Family: new family **FARMING** (recommended) or **SKILLS**. See §6.

Gating heuristic: enable when user keywords include `farm`, `farming`,
`patch`, `herb`, `tree`, `allotment`, `compost`, `tithe`, `disease`,
`disease-free`, `magic seed`, `palm tree`, `spirit tree`.

Token cost: ~600 if we ship every patch (300+ patches × 2 fields).
**Flagged as oversized** — see §7. Mitigation: chunk by "ready
patches only" + "in-progress patches" + paginated full list.

Marketing pitch: *"Tibbly tracks all 24 of your herb patches across
worlds and DMs you when ranarrs are ready — including the Hosidius one
your other plugin forgot."*

### Tier 0 (5/5): `get_active_prayers` — **COMBAT**

What it returns: the prayers currently active on the local player.
We currently surface `get_buffs` (potion timers etc.) but not the
binary "is prayer X active right now". This is the single most-asked
question in any combat advice flow.

API surface:
- `Client.getVarbitValue(Varbits.PRAYER_*)` returns 1 if active. The
  full list is 24+ standard prayers + 24 Ruinous Powers prayers (see
  Varbits.java).
- `Client.getBoostedSkillLevel(Skill.PRAYER)` for remaining points.
- `Client.getRealSkillLevel(Skill.PRAYER)` for max points.
- `Client.getVarbitValue(Varbits.QUICK_PRAYER)` for quick-prayer state.

Kotlin sketch:
```kotlin
val active = StandardPrayer.values().filter {
    client.getVarbitValue(it.varbit) == 1
}.map { it.name }
val ruinous = RuinousPrayer.values().filter {
    client.getVarbitValue(it.varbit) == 1
}.map { it.name }
return JsonObject().apply {
    add("active", active.toJsonArray())
    add("ruinous", ruinous.toJsonArray())
    addProperty("prayerPoints", client.getBoostedSkillLevel(Skill.PRAYER))
    addProperty("prayerMax", client.getRealSkillLevel(Skill.PRAYER))
    addProperty("quickPrayer", client.getVarbitValue(Varbits.QUICK_PRAYER) == 1)
}
```

Family: **COMBAT**.

Gating heuristic: enable when user keywords include `prayer`, `flick`,
`pray`, `protect`, `piety`, `rigour`, `augury`, `chivalry`, `protect
from`, `pking`, `pvm`, `boss`.

Token cost: ~140 if we cap the active arrays at a sensible upper
bound (the player can have at most ~3 prayers active at once + 1
overhead protect, so payload is small).

Marketing pitch: *"Tibbly sees your prayer is on Piety + Protect-from-
Magic with 12 points left and tells you to flick to Augury before the
Wardens mage attack."*

---

## 4. Tier 1 — high-value unexposed surfaces

The next 40+ tools. Less marketing-flashy than Tier 0 but each is
material to a real player-flow.

### 4.1 Account / identity / world-state

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 1 | `get_world_info` | `Client.getWorld()` + `getWorldType()` + `getWorldHost()` | R | CORE | always-on (cheap) | 90 |
| 2 | `get_membership_state` | `Client.getVarbitValue(Varbits.ACCOUNT_TYPE)` + `getMembershipDays()` | R | CORE | "members", "f2p", "p2p" | 80 |
| 3 | `get_pet` | `Client.getFollower()` + composition name | R | new family **PETS** or CORE | "pet", "follower", "metamorphic", "jar" | 100 |
| 4 | `get_player_idle_state` | `Client.getMouseIdleTicks()` + `getKeyboardIdleTicks()` | R | CORE | "afk", "idle", "logout" | 80 |
| 5 | `get_run_energy_and_weight` | `Client.getEnergy()` + `getWeight()` + `getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE)` | R | CORE | always-on (cheap) | 90 |
| 6 | `get_game_state` | `Client.getGameState()` enum | R | CORE | always-on (cheap) | 70 |
| 7 | `get_local_destination` | `Client.getLocalDestinationLocation()` | R | NAV | "where am I going", "destination" | 90 |

### 4.2 Varbits and VarPlayers — the goldmine

The single biggest unexposed surface. `Varbits.java` exposes ~200
named constants; `VarPlayer.java` ~50. Each one is one method call
(`client.getVarbitValue(int)`) away. We should not expose them as one
mega-tool — too much token surface for a single turn — but instead
ship a focused set per category, behind the right family gate.

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 8 | `get_quest_progress` | `Client.getVarpValue(VarPlayer.QUEST_X)` + quest-specific varbits | R | QUEST | "quest", "step", "stuck", "where am I in", quest-name | 200 |
| 9 | `get_diary_varbits` | varbits for every achievement diary task | R | QUEST | "diary", "elite", "hard", "medium", "easy", region name | 250 |
| 10 | `get_slayer_state_ext` | `Varbits.SLAYER_POINTS` + `SLAYER_TASK_STREAK` + `SLAYER_TASK_BOSS` + `SUPERIOR_ENABLED` + `FOSSIL_ISLAND_WYVERN_DISABLE` | R | SLAYER | "slayer", "task", "streak", "points" | 140 |
| 11 | `get_combat_potion_timers` | varbits for `STAMINA_EFFECT`, `ANTIFIRE`, `SUPER_ANTIFIRE`, `MAGIC_IMBUE`, `DIVINE_*` | R | COMBAT | "potion", "antifire", "stamina", "divine", "imbue" | 180 |
| 12 | `get_vengeance_state` | `Varbits.VENGEANCE_ACTIVE` + `VENGEANCE_COOLDOWN` | R | COMBAT | "veng", "vengeance" | 80 |
| 13 | `get_spellbook_buffs` | `DEATH_CHARGE`, `RESURRECT_THRALL`, `SHADOW_VEIL`, `WARD_OF_ARCEUUS_COOLDOWN`, `SPELLBOOK_SWAP` | R | COMBAT | "thrall", "death charge", "shadow veil", "arceuus" | 160 |
| 14 | `get_wilderness_state` | `Varbits.IN_WILDERNESS` + `MULTICOMBAT_AREA` + `TELEBLOCK` + `PVP_SPEC_ORB` | R | COMBAT | "wildy", "wilderness", "pk", "pvp", "teleblock" | 130 |
| 15 | `get_chatbox_settings` | `TRANSPARENT_CHATBOX`, `CHAT_SCROLLBAR_ON_LEFT`, `SIDE_PANELS` | R | CORE (cheap) | "ui", "chatbox", "side panel" | 90 |
| 16 | `get_xp_tracker_settings` | `EXPERIENCE_TRACKER_POSITION`, `EXPERIENCE_DROP_COLOR`, `DISABLE_LEVEL_UP_INTERFACE` | R | SKILLS | "xp tracker", "level up", "experience" | 100 |
| 17 | `get_imbued_heart_cooldown` | `Varbits.IMBUED_HEART_COOLDOWN` | R | COMBAT | "imbued heart", "heart cd" | 70 |
| 18 | `get_dragonfire_shield_state` | `Varbits.DRAGONFIRE_SHIELD_COOLDOWN` | R | COMBAT | "dfs", "dragonfire shield", "spec" | 70 |
| 19 | `get_ring_of_endurance_state` | `Varbits.RING_OF_ENDURANCE_EFFECT` | R | COMBAT | "ring of endurance", "roe" | 70 |
| 20 | `get_corp_damage` | `Varbits.CORP_DAMAGE` | R | COMBAT | "corp", "corporeal" | 70 |

For families "SLAYER" and "QUEST" these significantly upgrade what
the LLM can answer. Each varbit is ~5 tokens of JSON; bundling
related ones into one tool is the right balance.

### 4.3 Combat depth — prayers, projectiles, hitsplats, animations

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 21 | `get_active_prayers` | (see Tier 0 §3) | R | COMBAT | — | 140 |
| 22 | `get_target_projectiles` | (see Tier 0 §3) | R | COMBAT | — | 200 |
| 23 | `get_target_animation` | `(Client.getInteracting() as Actor).getAnimation()` + `getAnimationFrame()` | R | COMBAT | "boss", "phase", "animation" | 100 |
| 24 | `get_target_overhead_icon` | `(Actor).getOverheadIcon()` → `HeadIcon` enum | R | COMBAT | "overhead", "prayer icon", "skull" | 100 |
| 25 | `get_target_spotanim` | `(Actor).hasSpotAnim(int)` + iterate `ActorSpotAnim` deque | R | COMBAT | "spotanim", "graphic", "gfx" | 120 |
| 26 | `get_player_spotanims` | `Client.getLocalPlayer().getSpotAnims()` | R | COMBAT | "self gfx", "buff", "vengeance" | 100 |
| 27 | `get_local_player_animation` | `Client.getLocalPlayer().getAnimation()` + name lookup against `AnimationID` | R | COMBAT | "attacking", "what am I doing" | 90 |
| 28 | `get_recent_chat_messages` | `Client.getMessages()` (`ChatLineBuffer`) | R | CORE | "chat", "what did X say" | 200 |
| 29 | `get_npc_health_bar` | `(NPC).getHealthRatio()` + `getHealthScale()` | R | COMBAT | "boss hp", "health" | 80 |
| 30 | `get_combat_stats_ext` | `Client.getRealSkillLevels()` filtered to combat + max-hit estimator | R | COMBAT | "max hit", "dps", "stats" | 200 |

### 4.4 Raids & instanced content

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 31 | `get_raid_layout` | (see Tier 0 §3) | R | new **RAIDS** | — | 180 |
| 32 | `get_instance_chunks` | `Client.getInstanceTemplateChunks()` decoded to (z, x, y, region) | R | RAIDS | "instance", "raid", "room" | 250 |
| 33 | `get_world_entities` | `Client.getWorldView(int).worldEntities()` (Sailing prep) | R | NAV / RAIDS | "ship", "sailing", "boat" | 200 |
| 34 | `get_player_owned_house_room` | `Varbits` for PoH layout + `Client.getMapRegions()` filter | R | RAIDS (or new **HOUSING**) | "poh", "house", "altar", "pool" | 200 |
| 35 | `get_cox_olm_phase` | `Varbits` plus CoX-room varbit (community-known) | R | RAIDS | "olm", "cox" | 100 |
| 36 | `get_tob_phase` | `Varbits.THEATRE_OF_BLOOD` + party orbs | R | RAIDS | "tob", "verzik", "sotetseg", "xarpus" | 130 |
| 37 | `get_toa_invocation` | `Varbits.TOA_RAID_LEVEL` + `TOA_RAID_DAMAGE` + `TOA_MEMBER_0_HEALTH..7` | R | RAIDS | "toa", "tombs", "invocation" | 160 |

### 4.5 Group iron man + clan + friends

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 38 | `get_clan_channel` | `Client.getClanChannel()` → `ClanChannel.getMembers()` + ranks | R | PARTY | "clan", "cc", "clan chat" | 200 |
| 39 | `get_guest_clan_channel` | `Client.getGuestClanChannel()` | R | PARTY | "guest cc", "guest clan" | 150 |
| 40 | `get_friends_chat` | `Client.getFriendsChatManager()` → members | R | PARTY | "friends chat", "fc" | 200 |
| 41 | `get_friends` | `Client.getFriendContainer()` → friends + online state | R | PARTY | "friends list", "friend" | 200 |
| 42 | `get_ignores` | `Client.getIgnoreContainer()` | R | PARTY | "ignore", "blocked" | 100 |
| 43 | `get_clan_settings` | `Client.getClanSettings()` → titles, ranks | R | PARTY | "clan rank", "clan title" | 150 |
| 44 | `get_group_ironman_state` | `Varbits.ACCOUNT_TYPE == 5 \|\| 6` + clan-channel-as-GIM-channel | R | PARTY | "gim", "group iron", "team" | 200 |

Note on GIM: RuneLite does *not* expose `Client.getGimMembers()`
directly. The community-standard pattern (used by Group Ironmen
Tracker and gim-hub) is: detect GIM via `Client.getAccountType()`,
then treat the player's clan channel as the GIM party — `ClanChannel`
returns members + rank + world. We can mirror that without writing a
new RL API call.

### 4.6 Leagues / seasonal content

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 45 | `get_active_relics` | `Varbits.LEAGUE_RELIC_1..8` | R | new **LEAGUES** | "league", "relic", "leagues" | 130 |
| 46 | `get_combat_mastery_levels` | `LEAGUES_MELEE/RANGED/MAGIC_COMBAT_MASTERY_LEVEL` | R | LEAGUES | "combat mastery", "mastery", "leagues" | 100 |
| 47 | `get_league_region_unlocks` | bespoke varbits (community-mapped) | R | LEAGUES | "region", "unlock", "area" | 160 |
| 48 | `get_seasonal_world_state` | `Client.getWorldType().contains(WorldType.SEASONAL)` + `LEAGUES` | R | LEAGUES | "leagues", "dmm", "seasonal", "tournament" | 100 |

### 4.7 Music + ambient audio

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 49 | `get_current_music_track` | `Client.getMusicCurrentTrackId()` + `MusicConfig` mapping | R | new **AMBIENT** or CORE | "song", "track", "music", "what's playing" | 120 |
| 50 | `get_unlocked_music_tracks` | iterate music-list widget at `WidgetID.MUSIC` | R | AMBIENT | "music collection", "what songs do I have" | 250 |
| 51 | `get_ambient_sounds` | `AmbientSoundEffectCreated` event tail | R | AMBIENT | "ambient", "background sound" | 150 |
| 52 | `play_jukebox` | request the player play track id (overlay prompt only, *not* automation) | O | AMBIENT | "play song" | 80 |

Note: music tools are charming, low-leverage marketing wins ("ask
Tibbly what's playing"). Family is light.

### 4.8 World map + minimap + navigation hooks

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 53 | `get_world_map_focus` | `Client.getWorldMap().getWorldMapPosition()` | R | NAV | "world map", "where am I looking", "map" | 100 |
| 54 | `set_world_map_target` | `WorldMap.setWorldMapPositionTarget(WorldPoint)` (overlay only) | O | NAV | "show me on map", "point at", "where is X" | 110 |
| 55 | `get_minimap_clue_arrow` | `Client.getHintArrowType()` + `getHintArrowPoint()` | R | NAV | "arrow", "hint" | 90 |
| 56 | `add_world_map_marker` | `WorldMapPointManager.add(WorldMapPoint)` (RL client overlay) | O | HIGHLIGHTS / NAV | "mark on map", "place marker" | 130 |
| 57 | `get_minimap_npc_dots` | scene NPCs filtered to minimap viewport | R | GROUNDSTATE | "minimap", "dots" | 200 |

### 4.9 NPC dialog + chatbox panel

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 58 | `get_npc_dialog` | dialog widgets at `WidgetID.DIALOG_NPC_GROUP_ID` / `DIALOG_PLAYER_GROUP_ID` / `DIALOG_OPTION_GROUP_ID` | R | CORE | "dialog", "npc said", "what did", "option" | 200 |
| 59 | `get_dialog_options` | dialog-option widget children parsed to JSON | R | CORE | "options", "choices", "pick" | 150 |
| 60 | `get_overhead_chat` | `OverheadTextChanged` event tail | R | CORE | "shout", "said", "overhead" | 130 |
| 61 | `get_chatbox_input_state` | `ChatboxPanelManager.getCurrentInput()` | R | CORE | "input", "typing", "field" | 100 |

### 4.10 Farming / Hunter / Construction skill-specific state

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 62 | `get_farming_state` | (see Tier 0 §3) | R | new **FARMING** | — | 600 (flagged) |
| 63 | `get_birdhouse_state` | birdhouse-completion varbits | R | FARMING | "birdhouse", "hunter passive" | 130 |
| 64 | `get_hunter_traps` | scene-object iteration filtered to trap object IDs + cooldown state | R | new **HUNTER** | "trap", "hunter", "snare" | 200 |
| 65 | `get_kingdom_state` | Miscellania kingdom varbits | R | FARMING | "kingdom", "miscellania", "managing" | 130 |
| 66 | `get_collection_log_state` | walk `WidgetID.COLLECTION_LOG` if open | R | new **COLLECTION** | "collection log", "clog", "obtained" | 200 |

### 4.11 Player rendering / appearance / spotanims

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 67 | `get_player_appearance` | `Player.getPlayerComposition().getEquipmentIds()` + kit ids | R | new **APPEARANCE** or CORE | "fashion", "outfit", "wearing" | 200 |
| 68 | `get_chat_head` | dialog-head widget + `Client.getChatHeadColor()` | R | CORE | "who's talking" | 80 |
| 69 | `get_player_color_overrides` | `Player.getColors()` | R | APPEARANCE | "color", "recolor" | 80 |
| 70 | `get_chat_rank` | `FriendsChatRank` / `ClanRank` for a name | R | PARTY | "rank", "icon next to name" | 80 |

### 4.12 Camera + viewport (overlay positioning)

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 71 | `get_camera_state` | `Client.getCameraPitch()` + `getCameraYaw()` + `getCameraX/Y/Z()` + `getCameraFpPitch()` | R | new **CAMERA** | "zoom", "view", "angle", "camera" | 140 |
| 72 | `set_camera_target` | `Client.setCameraYawTarget(int)` + `setCameraPitchTarget(int)` (rendering only) | O | CAMERA | "look at", "rotate camera" | 120 |
| 73 | `get_viewport_size` | `Client.getViewportWidth()` + `getViewportHeight()` | R | CAMERA | UI sizing | 70 |
| 74 | `get_resize_state` | `Client.isResized()` + canvas dims | R | CAMERA | UI sizing | 70 |

### 4.13 Walker hooks + pathfinding telemetry

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 75 | `get_collision_data` | `Client.getCollisionMaps()` + `CollisionDataFlag` decoding | R | NAV | "blocked", "wall", "can I walk" | 250 |
| 76 | `get_walker_destination` | `Client.getLocalDestinationLocation()` + path snapshot | R | NAV | "walking to", "auto walk" | 100 |
| 77 | `get_walker_path` | scene-walker + transports planned path | R | NAV | "path", "route", "shortest way" | 250 |
| 78 | `get_agility_obstacles` | iterate `GameObject` filtered to agility-shortcut object ids | R | NAV | "shortcut", "agility", "obstacle" | 200 |

### 4.14 Overhead icons / headicons

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 79 | `get_player_overhead_icons` | `Player.getOverheadIcon()` + `getSkullIcon()` | R | COMBAT | "skull", "overhead", "headicon" | 90 |
| 80 | `get_nearby_overhead_chat` | scan players' `getOverheadText()` | R | CORE | "what are people saying" | 200 |

### 4.15 Widgets — readable interface telemetry

| # | Tool | API surface | R/O/M | Family | Gating keywords | ~tokens |
|---|---|---|---|---|---|---|
| 81 | `get_widget_tree` | recursive walk from `Client.getWidgetRoots()` returning compact tree (depth-capped) | R | CORE | "ui", "interface", "panel" | 400 (flagged) |
| 82 | `read_widget_text` | `Client.getWidget(group, child).getText()` | R | CORE | "text", "label", "what does X say" | 80 |
| 83 | `get_modal_widget` | filter widget roots by `WidgetModalMode != NONE` | R | CORE | "popup", "modal", "dialog" | 130 |
| 84 | `get_widget_item` | `Widget.getWidgetItem(int)` for item-grid reading | R | CORE | "what's in slot N" | 100 |

---

## 5. Tier 2 — exotic / edge surfaces

These score lower but are worth knowing exist. Probably worth shipping
only after a real user asks "can it do X?".

| # | Tool | API surface | R/O/M | Family | Notes |
|---|---|---|---|---|---|
| 85 | `get_gpu_pipeline_state` | `Client.getDrawCallbacks()` etc | R | CAMERA | Useful only for GPU-plugin debug. Skip for users. |
| 86 | `get_fps` | `Client.getFPS()` | R | CAMERA | Niche; debug only. |
| 87 | `get_revision` | `Client.getRevision()` + `getBuildID()` | R | CORE | Telemetry / bug-report context. |
| 88 | `get_grand_exchange_search` | `GrandExchangeSearched` event tail | R | GE | Only fires when player searches GE. |
| 89 | `get_grand_exchange_history` | walk RL config (GE history plugin) | R | GE | RL-client extension, not core API. |
| 90 | `get_kourend_favour` | favour varbits | R | new **REGION** | One-off; players ask "where do I get favour?" rarely. |
| 91 | `get_blast_furnace_state` | BF varbits | R | SKILLS | Minigame-specific. |
| 92 | `get_pest_control_state` | PC varbits + clan chat info | R | new **MINIGAME** | Minigame-specific. |
| 93 | `get_motherlode_state` | MLM varbits | R | SKILLS | Mining minigame-specific. |
| 94 | `get_volcanic_mine_state` | VM varbits | R | MINIGAME | High-density varbit; valuable to streamers. |
| 95 | `get_zalcano_state` | Zalcano varbits | R | MINIGAME | Niche. |
| 96 | `get_temple_trekking_state` | TT varbits | R | MINIGAME | Niche. |
| 97 | `get_inferno_wave` | Inferno wave varbit | R | new **MINIGAME** | High-value for inferno runners but tiny audience. |
| 98 | `get_fight_caves_wave` | FC wave varbit | R | MINIGAME | Niche. |
| 99 | `get_chambers_layout_seed` | CoX-specific deterministic generation | R | RAIDS | Speedrun-tier. |
| 100 | `get_struct_composition` | `Client.getStructComposition(int)` | R | CORE | Dev surface. |
| 101 | `get_enum_composition` | `Client.getEnum(int)` | R | CORE | Dev surface. |
| 102 | `get_item_composition_ext` | `ItemManager.getItemComposition(id)` fields (price, ge limit, examine) | R | CORE | Better than `find_item` for deep queries. |
| 103 | `get_npc_composition_ext` | `NPCComposition` (max hit table not exposed by RL, would need wiki) | R | COMBAT | Stronger than `get_npc_max_hp`. |
| 104 | `get_object_composition` | `ObjectComposition.getName()` + actions | R | NAV | When standing next to an unknown object. |
| 105 | `get_loot_tracker` | RL loot-tracker plugin config | R | SKILLS | Lifetime + session loot. We already have session via `get_session_loot`. |

That's a hundred-plus-five — more than enough to land the catalog at
the catalog's stated "60+" bar with room to drop weak entries during
RAI-25 family-tag refinement.

---

## 6. Family-tag refinement recommendations for ToolFamily.kt

Current enum (14 families: CORE, BANKING, QUEST, QUEST_ITEMS, NAV,
COMBAT, GE, SKILLS, HIGHLIGHTS, LOADOUTS, GROUNDSTATE, FISHING,
PARTY, SLAYER).

### Proposed additions (high signal, ≥3 candidate tools each)

| New family | Tools that justify it | Reasoning |
|---|---|---|
| **RAIDS** | Tier 0 #2, Tier 1 #31–37 | Raid intent is well-isolated (vocabulary is specific) and the varbit surface is large. Mixing into COMBAT bloats COMBAT's keyword list. |
| **LEAGUES** | Tier 1 #45–48 | Seasonal-only; should be cheap to leave off when it's not Leagues season. |
| **FARMING** | Tier 0 #4, Tier 1 #63, #65 | High patch-count, large varbit surface, distinct keywords. |
| **APPEARANCE** | Tier 1 #67, #69 | Marketing-coded ("show me my fashion") and low-cost. |
| **AMBIENT** | Tier 1 #49–52 | Music + ambient. Pure charm; cheap to keep off by default. |

### Proposed removals / folds

| Current family | Action | Reasoning |
|---|---|---|
| `QUEST_ITEMS` | Fold into `QUEST`. | One tool only; the keyword surface already triggers QUEST. |
| `FISHING` | Fold into `SKILLS`. | One tool only. Will reseparate if the catalog grows fishing-specific. |

### Proposed enum re-order (for legibility)

Current ordering in `ToolFamily.kt` puts CORE first then BANKING /
QUEST / NAV / COMBAT / GE / SKILLS / HIGHLIGHTS / LOADOUTS /
GROUNDSTATE / FISHING / PARTY / SLAYER. Recommended new ordering:

```kotlin
CORE,
COMBAT, RAIDS, LEAGUES,           // fight-related families clustered
QUEST, SLAYER,                    // task-related
NAV,                              // movement
SKILLS, FARMING,                  // economy / progress
BANKING, GE, LOADOUTS,            // gear + economy
GROUNDSTATE, HIGHLIGHTS,          // world-around-me
PARTY,                            // social
APPEARANCE, AMBIENT, PETS,        // charm / low-leverage
```

This is a small breaking change (the wire-form names are stable; only
the enum ordinal moves, which only matters if anything depends on
ordinal — currently nothing does, per a grep over `apps/`).

### Adjacent improvement opportunities found during this scan

- **CLAUDE.md says "72 MCP tools".** `ToolRegistry.kt` has 73 entries
  (`enable_tools` is the meta-tool). Either drop `enable_tools` from
  the per-tool token math or update CLAUDE.md. The number we should
  actually use in token-budget math is **72 real game-state tools +
  one ~30-token meta-tool**.
- **`get_active_clue` lives in CORE on the strength of "clue queries
  arrive without 'clue' keyword".** This is correct; flagging it
  here so the next family-tag review doesn't move it.
- **`get_party` is a one-tool family.** Either rename to `PARTY` →
  `SOCIAL` and absorb clan/friends/GIM additions, or keep narrow
  and add a new `SOCIAL` family for the §4.5 additions. Recommend:
  rename PARTY → SOCIAL.

---

## 7. Token-cost flagging

Catalog entries that would exceed ~200 tokens (the soft cap from
CLAUDE.md's "~120 tokens average" target):

| Tool | Est tokens | Mitigation |
|---|---|---|
| `get_farming_state` (Tier 0 #4) | 600 | Chunk by "ready / in-progress / planted today"; pagination via `since=<tick>` param. |
| `get_widget_tree` (#81) | 400 | Depth-cap, default depth=2. Allow LLM to call `expand_widget(id)` for sub-trees. |
| `get_collection_log_state` (#66) | varies | Only return open page contents, not the whole log. |
| `get_chat_messages` (#28) | 200 | Default `limit=10`, allow override. |
| `get_diary_varbits` (#9) | 250 | Bucket by `easy / medium / hard / elite` and accept `region=` param. |
| `get_clan_channel` (#38) | 200 | Cap at 30 members shown; provide `next_page_token`. |

The remaining ~95 entries are under the 200-token cap.

---

## 8. Headline table

(Also reproduced in [`_SUMMARY.md`](./_SUMMARY.md).)

| Rank | Tool | Family | Why it matters |
|---|---|---|---|
| 1 | `get_account_identity` | CORE | First-turn anchor: HCIM advice ≠ main advice. |
| 2 | `get_raid_layout` | new RAIDS | Marketing-grade unlock for the biggest engagement loops. |
| 3 | `get_target_projectiles` | COMBAT | The "prayer flick" feature in 8 lines of Kotlin. |
| 4 | `get_farming_state` | new FARMING | Largest skill cohort by daily-actives; existing trackers feel old. |
| 5 | `get_active_prayers` | COMBAT | Highest "did I bring the right prayer" signal. |

The next 25 worth scheduling sometime in the M1 follow-up window:

`get_quest_progress`, `get_slayer_state_ext`, `get_npc_dialog`,
`get_combat_potion_timers`, `get_target_animation`, `get_recent_chat_messages`,
`get_target_overhead_icon`, `get_vengeance_state`, `get_world_map_focus`,
`set_world_map_target`, `get_clan_channel`, `get_group_ironman_state`,
`get_current_music_track`, `get_world_info`, `get_run_energy_and_weight`,
`get_active_relics`, `get_pet`, `get_player_appearance`, `get_camera_state`,
`get_collision_data`, `get_minimap_clue_arrow`, `get_player_overhead_icons`,
`get_hunter_traps`, `get_npc_health_bar`, `get_wilderness_state`.

---

## 9. Open questions

> Light-touch flags for the next loop. None blocks this PR.

- **Q-RAI5-1.** `ToolRegistry.kt` has 73 entries; CLAUDE.md says 72.
  The discrepancy is `enable_tools` (meta-tool, ~30-token schema, not
  a game-state read). Either (a) drop it from token-budget math and
  fix CLAUDE.md, or (b) leave the slight overcount as a worst-case
  buffer. **Default:** (a). Cheap to fix. (Adjacent improvement #1.)
- **Q-RAI5-2.** Should `PARTY` be renamed to `SOCIAL` to absorb the
  GIM / clan / friends / ignores additions, or do we keep a narrow
  `PARTY` and add a new `SOCIAL` family? **Default:** rename.
- **Q-RAI5-3.** Group iron man state has no first-class RL API. Are
  we comfortable inferring GIM party from clan-channel-as-GIM-channel
  the way the Group Ironmen Tracker plugin does, or do we want to
  wait for an RL upstream API? **Default:** infer now; revisit if
  Jagex / RL ship something official.
- **Q-RAI5-4.** `get_farming_state` projected at 600 tokens — should
  we cap to ready-only by default and require the LLM to ask for
  in-progress? **Default:** yes.
- **Q-RAI5-5.** Should the catalog's "Tier 2" entries even land in
  `ToolRegistry.kt`, or should we cap the registered surface at Tier 1
  to keep the gated-but-discovery-eligible total under ~120 tools?
  **Default:** cap at Tier 1. Tier 2 stays in this doc as a future
  pipeline.

---

## 10. Source links

- runelite-api master tree:
  https://github.com/runelite/runelite/tree/master/runelite-api/src/main/java/net/runelite/api
- Client.java:
  https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Client.java
- Varbits.java (~200 named varbits used to size §4.2):
  https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Varbits.java
- Prayer.java (55 prayer enum constants):
  https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Prayer.java
- Skill.java (24 skills including Sailing):
  https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Skill.java
- WorldMap.java:
  https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/worldmap/WorldMap.java
- Events package (82 event types):
  https://github.com/runelite/runelite/tree/master/runelite-api/src/main/java/net/runelite/api/events
- Clan package (ClanChannel + ClanSettings + ranks):
  https://github.com/runelite/runelite/tree/master/runelite-api/src/main/java/net/runelite/api/clan
- Group Ironmen Tracker (precedent for inferring GIM party):
  https://github.com/christoabrown/group-ironmen-tracker
- Music plugin docs (precedent for music-track surface):
  https://static.runelite.net/runelite-client/apidocs/net/runelite/client/plugins/music/MusicPlugin.html
- Cross-references in this repo:
  - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt`
  - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolRegistry.kt`
  - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolFamily.kt`
  - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ContextRouter.kt`
  - `docs/architecture/TOOL_ECONOMY.md`
  - `docs/agents/GAPS.md` (A7 resolved by this doc)
