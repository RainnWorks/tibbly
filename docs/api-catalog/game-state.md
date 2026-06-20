# Game state APIs (not yet exposed)

Catalog of additional game state that the RuneLite `Client` interface and
related types expose but our MCP server does not yet surface. Use this as a
shopping list when adding new tools/resources.

All paths below are inside `docs/runelite/sources/`. Threading rule of thumb:
**every method on `Client`, `Player`, `NPC`, `Actor`, `Widget`, `ItemContainer`,
`Scene`, `WorldView` must be called on the client thread** (use
`clientThread.invoke { ... }` from your plugin). Reading a cached snapshot off
the client thread is fine.

Key files reviewed:
- `api/net/runelite/api/Client.java` (~2400 lines)
- `api/net/runelite/api/Player.java`, `NPC.java`, `Actor.java`
- `api/net/runelite/api/Varbits.java`, `VarPlayer.java`
- `api/net/runelite/api/gameval/VarbitID.java`, `VarPlayerID.java`, `VarClientID.java`, `InventoryID.java`
- `api/net/runelite/api/WorldView.java`, `Scene.java`, `Tile.java`, `ItemContainer.java`
- compositions, clan, friends, widget, hitsplat, projectile, GE

---

## Player (additional fields on `Player`/`Actor`)

`Player extends Actor extends Renderable, CameraFocusableEntity` —
`api/net/runelite/api/Player.java`, `Actor.java`.

### From `Actor` (applies to LocalPlayer, NPCs, other players)
- `getAnimation(): int` — current attack/skill anim id; -1 if idle. Match against `gameval.AnimationID`. The single biggest "what is the player doing right now?" signal. Changes mid-tick via `AnimationChanged` event.
- `getPoseAnimation(): int` — idle/walking pose. Useful to detect *moving* vs *standing*.
- `getIdlePoseAnimation(): int` — what they'd do if they stopped.
- `getWalkAnimation(): int`, `getRunAnimation(): int` — walk/run pose ids; combined with pose anim tells you whether they're running.
- `getAnimationFrame(): int` / `getPoseAnimationFrame(): int` — frame within the current anim (for sub-tick timing).
- `getOrientation(): int` / `getCurrentOrientation(): int` — facing direction in JAU (1024 per revolution). See `coords/Angle.java`.
- `getInteracting(): Actor` — who/what we are interacting with (attacking, following, talking to). Note: returns null if target is out of visibility range; `isInteracting()` is true regardless. We currently expose target *name* but not the live `Actor` so we miss out on combat level, hp ratio, anim, world point of the target.
- `getHealthRatio(): int` / `getHealthScale(): int` — opponent's hp **fraction** (0..scale, scale is usually 30 but bigger for bosses). Server does not transmit absolute hp for actors; you compute percent as `ratio / scale`. -1 if no info.
- `isDead(): boolean` — true after death animation triggers.
- `getOverheadText(): String` / `getOverheadCycle(): int` — chat bubble text above head and ticks remaining. Useful to capture NPC dialogue, autocast spell names, "Eat!" etc.
- `getSpotAnims(): IterableHashTable<ActorSpotAnim>` / `hasSpotAnim(int): boolean` — graphic effects (vengeance flash, poison splat, freeze, splash). Each `ActorSpotAnim` has `getId()`, `getHeight()`, `getFrame()`. See `gameval/SpotanimID.java`.
- `getWorldArea(): WorldArea` — tile footprint (bigger than 1x1 for large NPCs). Better than `getWorldLocation()` for "is X next to me" checks.
- `getLocalLocation(): LocalPoint` — client-side, animation-affected position. Server lags ahead in `getWorldLocation()`.
- `getLogicalHeight(): int` — model height; where the hp bar floats. Handy for canvas overlay positioning.
- `getFootprintSize(): int` — tile size of the actor.
- `getWorldView(): WorldView` — which world view this actor lives in (instances, alt scenes).

### Player-specific (`Player.java`)
- `getId(): int` — player index id (not stable across logins; index into world view player table).
- `getCombatLevel(): int` — for *other* players too, not just local.
- `getPlayerComposition(): PlayerComposition` — the equipped-kit composition:
  - `isFemale()` / `getGender()`
  - `getEquipmentId(KitType): int` — item ID of helm/cape/amulet/weapon/body/shield/legs/hands/feet/jaw. Note: returns the *kit-shifted* id; subtract `PlayerComposition.ITEM_OFFSET = 2048` and check `>= 0` for an item, else it's a kit (cosmetic). This is how you read another player's gear without their permission.
  - `getKitId(KitType): int` — cosmetic kit id (hair, beard).
  - `getTransformedNpcId(): int` — non-zero when player is morphed (NPC contact, etc.).
- `getTeam(): int` — team-cape number (0 if none); for clan wars / castle wars detection.
- `isFriendsChatMember(): boolean`, `isFriend(): boolean`, `isClanMember(): boolean` — relationship to local player.
- `getOverheadIcon(): HeadIcon` — prayer icon overhead: `MELEE`, `RANGED`, `MAGIC`, `RETRIBUTION`, `SMITE`, `REDEMPTION`, `RANGE_MAGE`, `RANGE_MELEE`, `MAGE_MELEE`, `RANGE_MAGE_MELEE`, `WRATH`, deadeye/mystic vigour, ruinous powers. See `HeadIcon.java`. Already used for self; valuable for other players in PvP.
- `getSkullIcon(): int` — `SkullIcon.SKULL` (0), `SKULL_FIGHT_PIT`, `SKULL_HIGH_RISK`, `FORINTHRY_SURGE`, `SKULL_DEADMAN`, `LOOT_KEYS_*`, or -1 if unskulled. Detect PK risk.

---

## NPC (additional fields on `NPC`)

`NPC extends Actor` — `api/net/runelite/api/NPC.java`. We already expose name + combat level + position. Additional:

- `getId(): int` — NPC id; match against `gameval.NpcID`. Useful to disambiguate same-name NPCs (e.g. different bankers).
- `getIndex(): int` — slot in client NPC table; stable across the spawn, useful for diffing.
- `getComposition(): NPCComposition` — see below.
- `getTransformedComposition(): NPCComposition` — composition *after* multiloc transform (e.g. Vorkath asleep → awake). Always prefer this over `getComposition()` when reading current state.
- `getOverheadArchiveIds(): int[]` / `getOverheadSpriteIds(): short[]` — sparse arrays describing icons above the NPC head (skull icons in CoX, raid prep, etc.).
- `getModelOverrides()`, `getChatheadOverrides()` — runtime model swaps; usually null.

### `NPCComposition` (`api/net/runelite/api/NPCComposition.java`)
- `getName(): String`, `getId(): int`, `getCombatLevel(): int`
- `getActions(): String[]` — right-click options ("Attack", "Talk-to", "Trade", "Pickpocket", "Examine"). Tells you what's possible without opening menu.
- `getSize(): int` — tile footprint (1 for human-size, 5 for KBD).
- `isInteractible(): boolean` — can be right-clicked at all.
- `isMinimapVisible(): boolean` — appears as yellow dot on minimap.
- `isFollower(): boolean` — pet / follower flag.
- `getConfigs(): int[]` — varbit/varp ids that determine multiloc transform.
- `transform(): NPCComposition` — manually apply transform based on current varbit/varp.
- `getStringValue(int paramId): String` / `getIntValue(int paramId): int` — read `ParamID`-style metadata attached to the NPC (e.g. slayer level requirement).

---

## Client — sessions, world, account, energy

Section-by-section through `Client.java`.

### Account & session
- `getAccountHash(): long` — from `OAuthApi` superinterface. Per-RS-Account stable id (-1 if not logged in). The right key to use for storing per-user state, not username.
- `getLauncherDisplayName(): String` — Jagex launcher display name (nullable).
- `getUsername(): String` — DEPRECATED; the original login string. Prefer `accountHash`.
- `getBuildID(): String` — game build identifier.
- `getRevision(): int` — client revision number.
- `getEnvironment(): int` — environment number (live/beta/dev).
- `getGameState(): GameState` — `STARTING`, `LOGIN_SCREEN`, `LOGGING_IN`, `LOADING`, `LOGGED_IN`, `CONNECTION_LOST`, `HOPPING`. Critical guard for everything — most queries are invalid unless `LOGGED_IN`.
- `getAccountType(): AccountType` — `NORMAL`, `IRONMAN`, `HARDCORE_IRONMAN`, `ULTIMATE_IRONMAN`, `GROUP_IRONMAN`, etc. Deprecated wrapper; the new way is `getVarbitValue(VarbitID.IRONMAN)` (1777, see VarbitID gameval).

### World
- `getWorld(): int` — current world number (e.g. 301, 415).
- `getWorldHost(): String` — DNS hostname for the world server.
- `getWorldList(): World[]` — full world list (player counts, locations, types, activity). `World` has `getId()`, `getActivity()` (activity text or minigame), `getLocation()` (US/UK/DE/AU), `getPlayerCount()`, `getTypes()`.
- `getWorldType(): EnumSet<WorldType>` — flags for *current* world: `MEMBERS`, `PVP`, `BOUNTY`, `DEADMAN`, `SEASONAL` (leagues), `HIGH_RISK`, `FRESH_START`, `TOURNAMENT`, `BETA_WORLD`, `QUEST_SPEEDRUNNING`. `WorldType.isPvpWorld()` is a helper.
- `getFPS(): int` — current frames/sec.
- `getGameCycle(): int` — local client cycle, increments every 20ms (`Constants.CLIENT_TICK_LENGTH`).
- `getTickCount(): int` — server tick count; increments every 600ms (`Constants.GAME_TICK_LENGTH`). The canonical "tick" used by OSRS docs.
- `getRevision(): int` — see above.

### Player state at the client level
- `getEnergy(): int` — run energy in **1/100ths of a percent** (0..10000). Divide by 100 for the typical 0..100 display. We currently expose this; mention scale gotcha in MCP doc.
- `getWeight(): int` — equipped+carried weight in kg; drives energy drain rate.
- `getLocalDestinationLocation(): LocalPoint` — tile the player is walking *toward* (red flag click). Useful to detect "is the player traveling somewhere".
- `getLocalPlayer(): Player` — our player.
- `getFollower(): NPC` — pet or follower (Hellpup, etc.), nullable.
- `getOverallExperience(): long` — total XP across all skills.
- `getTotalLevel(): int` — sum of real levels.
- `getBoostedSkillLevels(): int[]` / `getRealSkillLevels(): int[]` / `getSkillExperiences(): int[]` — bulk arrays indexed by `Skill.ordinal()`.
- `getBoostedSkillLevel(Skill): int` vs `getRealSkillLevel(Skill): int` — boosted is current (potion'd up or drained); real is the base. We expose real; **boosted minus real per skill is a useful "buff active" signal**.

### Camera / viewport
- `getCameraX/Y/Z(): int` (+ `Fp` double variants); `getCameraPitch/Yaw(): int` in JAU; `getCameraPitchTarget/YawTarget()` for player-intended vs actual.
- `getMapAngle(): int` (minimap rotation), `getScale(): int` (zoom, default 512), `getCameraMode(): int` (0 normal, 1 free/oculus), `getCameraFocusEntity()`.
- `getCanvasHeight/Width()`, `getViewportHeight/Width()`, `getViewportXOffset/YOffset()`, `isResized()`, `isStretchedEnabled()`, `isGpu()`.

### Mouse / keyboard
- `getMouseCanvasPosition(): Point`, `getMouseCurrentButton(): int`, `getMouseIdleTicks(): int`, `getKeyboardIdleTicks(): int`, `getMouseLastPressedMillis(): long`.
- `isKeyPressed(int keycode): boolean` — `KeyCode.*` constants.
- `getIdleTimeout(): int` — auto-logout threshold (good for AFK detection).

### Menus
- `getMenu(): Menu` / `getMenuEntries(): MenuEntry[]` — open right-click menu. Each entry: `getOption()` ("Attack", "Use", "Walk here"), `getTarget()` (with color tags), `getType(): MenuAction`, `getIdentifier()`, `getParam0/1()`, `getItemId()`, `getNpc()`, `getPlayer()`, `getWidget()`.
- `isMenuOpen()`, `getMenuScroll()`.
- `getSelectedWidget(): Widget` / `isWidgetSelected()` — "Use"-mode item or selected spell. Detect mid-action.
- `getFocusedInputFieldWidget(): Widget` — chatbox/search field with focus.

### Hint arrow (quest pointer)
- `hasHintArrow()`, `getHintArrowType()` (see `HintArrowType.java`), `getHintArrowPoint(): WorldPoint`, `getHintArrowPlayer(): Player`, `getHintArrowNpc(): NPC`.

### Grand Exchange
- `getGrandExchangeOffers(): GrandExchangeOffer[]` — all 8 slots. Each: `getItemId()`, `getPrice()` (per item), `getTotalQuantity()`, `getQuantitySold()`, `getSpent()` (gp moved), `getState(): GrandExchangeOfferState` (EMPTY, BUYING, BOUGHT, SOLD, CANCELLED_BUY, CANCELLED_SELL).

### Containers (besides inventory/bank/equipment)
- `getItemContainer(InventoryID): ItemContainer` — *named* containers, primarily `INVENTORY` (93), `EQUIPMENT` (94), `BANK` (95), `SEED_VAULT` (626). See `InventoryID.java` enum.
- `getItemContainer(int id): ItemContainer` — *any* container by id; opens up many more from `gameval/InventoryID.java`:
  - `LOOTING_BAG = 516`
  - `BONDS_POUCH = 536`
  - `SEED_BOX = 573`
  - `TRADEOFFER = 90` (our side of an active trade)
  - `WIELDED_WEAPON_INV = 355`
  - Hundreds of shop inventories (every shop has its own id).
- `getItemContainers(): HashTable<ItemContainer>` — iterate every currently-tracked container.
- `ItemContainer` API: `getItems(): Item[]`, `getItem(int slot): Item`, `contains(int itemId)`, `count(int itemId)`, `size()`, `count()`, `find(int itemId)`. Each `Item` has `getId()` and `getQuantity()`.

### Item metadata
- `getItemDefinition(int itemId): ItemComposition` — `getName()`, `getMembersName()`, `isStackable()`, `isMembers()`, `isTradeable()`, `isGeTradeable()`, `getPrice()` (raw store price), `getHaPrice()` (high alch value = price * 0.6 typically), `getNote()` (item id this is a note of), `getLinkedNoteId()` (note ↔ unnoted toggle id), `getPlaceholderId()`, `getShiftClickActionIndex()`, `getInventoryActions()`. Already used; flag more of these for "what does this item do?" tooling.
- `getObjectDefinition(int objectId): ObjectComposition` — `getName()`, `getActions(): String[]` (object right-click ops like "Mine", "Chop", "Bank"), `getMapSceneId()`, `getMapIconId()`, `getVarbitId()` / `getVarPlayerId()` (multiloc backing), `getImpostor()` (transformed form), `getSizeX/Y()`.
- `getNpcDefinition(int npcId): NPCComposition` — same as `NPC.getComposition()` but from an id directly.
- `getStructComposition(int id): StructComposition` — generic param table; many in-game data (slayer master tasks, quest reward tables) live as structs.
- `getEnum(int id): EnumComposition` — server-side enums (e.g. herb seed → herb mapping); read with `getIntValue(int key)` or `getStringValue(int key)`.

### Skill / XP machinery
- `queueChangedSkill(Skill)` — force-fire `StatChanged`/`FakeXpDrop` if you mutate locally.

### Chat
- `getMessages(): IterableHashTable<MessageNode>` — every chat line currently buffered.
- `MessageNode`: `getType(): ChatMessageType`, `getName(): String` (sender display name), `getSender(): String` (channel/clan), `getValue(): String` (text), `getRuneLiteFormatMessage(): String` (with RL formatting tags), `getTimestamp(): int`. Great for "what's the last thing the game/player said to me?" → reading dialogue choices, drop notifications, level-ups.
- `getChatLineMap(): Map<Integer,ChatLineBuffer>` — per-chat-type rolling buffer.

### Friends / Ignore / Clan
- `getFriendContainer(): NameableContainer<Friend>` — `Friend`: `getName()`, `getPrevName()`, `getWorld()` (0 if offline), `getRank()`.
- `isFriended(name, mustBeLoggedIn)` — quick check; `getIgnoreContainer()` for ignore list.
- `getFriendsChatManager()` — `getName()`, `getOwner()`, `getMyRank()`, `getKickRank()`; iterates `FriendsChatMember`.
- `getClanChannel()` (primary), `getGuestClanChannel()` — `getName()`, `getClanId(): long`, `getMembers()` with `getRank()`, `getWorld()`.
- `getClanSettings()` / `getGuestClanSettings()` — full clan home roster: `getMembers(): List<ClanMember>` with `getName()`, `getRank()`, `getJoinDate(): LocalDate`.

### Prayer
- `isPrayerActive(Prayer)` is DEPRECATED — misses deadeye/mystic vigour/ruinous powers. Loop `Prayer.values()` and read each `prayer.getVarbit()` instead.

### World map, hopping, cross-world
- `getWorldMap(): WorldMap` (open/closed, position, target); `getMapElementConfig(id): MapElementConfig` (minimap icon info).
- `hopToWorld(World)`, `openWorldHopper()`, `changeWorld(World)`.
- `getCrossWorldMessageIds(): long[]` — shared events like ToB starts.

### Projectiles & graphics
- `getProjectiles(): Deque<Projectile>` — every live projectile. Each: `getId()` (spotanim), `getSourcePoint()`, `getTargetPoint()`, `getSourceActor()`, `getTargetActor()`, `getRemainingCycles()`, `getStartCycle/EndCycle()`, `getAnimation()`. Boss mechanic detection.
- `WorldView.getGraphicsObjects(): Deque<GraphicsObject>` — short-lived ground effects (Inferno tiles, AoE markers, altar swirls).
- `getActiveMidiRequests(): List<MidiRequest>` — current music tracks.

### Misc
- `getPlayerOptions(): String[]` / `getPlayerOptionsPriorities(): boolean[]` / `getPlayerMenuTypes(): int[]` — right-click options I offer to others ("Attack", "Trade", "Follow").
- `getDraggedWidget(): Widget` / `getDraggedOnWidget(): Widget` — drag-and-drop state.

---

## WorldView (multi-instance world support) — `api/net/runelite/api/WorldView.java`

Most "scene" stuff is now on `WorldView` rather than `Client` directly. The top-level world view is `client.getTopLevelWorldView()`. Instanced raids/skill bosses can spawn additional worldviews via `WorldEntity`.

- `players(): IndexedObjectSet<? extends Player>` — every loaded player in this view. Iterate for "who else is around me?".
- `npcs(): IndexedObjectSet<? extends NPC>` — every loaded NPC. We expose this for nearby; can also count, group by id, find specific by name/id.
- `worldEntities(): IndexedObjectSet<? extends WorldEntity>` — sub-worldviews (e.g. moving raid platforms, Varlamore mini-instances).
- `getScene(): Scene` — full 104×104×4 tile scene.
- `getPlane(): int` — current plane the local player is on (0..3).
- `getBaseX(): int`, `getBaseY(): int` — south-west corner of the loaded scene in world coords. Convert local↔world via these.
- `getMapRegions(): int[]` — array of loaded 64-tile region IDs (we expose just our own region; full list shows what's loaded around us).
- `isInstance(): boolean` / `getInstanceTemplateChunks(): int[][][]` — if true, the actual world coords are munged; use `WorldPoint.fromLocalInstance(...)` to reverse-map.
- `getCollisionMaps(): CollisionData[]` — per-plane walkability flags. `CollisionDataFlag` for bit meanings (BLOCK_MOVEMENT_FULL, BLOCK_LINE_OF_SIGHT_*, etc.). Enables pathfinding.
- `getTileHeights(): int[][][]` — 3D height map for rendering.
- `getTileSettings(): byte[][][]` — per-tile flags (under-roof, bridge, vis-below).
- `getSelectedSceneTile(): Tile` — last right-clicked tile.
- `contains(WorldPoint/LocalPoint): boolean` — is point in this view.
- `getMainWorldProjection(): Projection` — for instances, projects sub-world coords back to main.
- `getYellowClickAction(): int` — `CLICK_ACTION_NONE` / `WALK` / `SET_HEADING`.

---

## Scene & Tile — `api/net/runelite/api/Scene.java`, `Tile.java`

`Scene` is the 3D-array of `Tile`. Cheap to traverse if you stay on client thread.

- `Scene.getTiles(): Tile[][][]` — `[plane][x][y]` over 104×104.
- `Scene.getExtendedTiles(): Tile[][][]` — 184×184 padded version (for plugins drawing past the viewport).
- `Scene.getDrawDistance(): int` / `setDrawDistance(int)` — render distance.
- `Scene.getMinLevel(): int` — clip plane.
- `Scene.isInstance(): boolean`.
- `Scene.getRoofRemovalMode(): int` — bitmask of when roofs hide.
- `Tile`:
  - `getGameObjects(): GameObject[]` — interactable scenery (banks, trees, doors, monsters that are objects). Each `GameObject` has `getId()`, `getOrientation()`, `getRenderable()`, `getWorldLocation()`. Lookup name via `client.getObjectDefinition(id).getName()`.
  - `getWallObject(): WallObject` — walls/fences/doors (one per tile).
  - `getDecorativeObject(): DecorativeObject` — wall hangings, plaques.
  - `getGroundObject(): GroundObject` — flat-on-ground stuff (rugs, candles).
  - `getGroundItems(): List<TileItem>` — every loot pile on this tile. Each `TileItem`: `getId()`, `getQuantity()`, `getOwnership()` (NONE/SELF/OTHER/GROUP), `isPrivate()`, `getVisibleTime()`, `getDespawnTime()`. **This is how to enumerate ground loot near the player.**
  - `getItemLayer(): ItemLayer` — internal item layer (use `getGroundItems()` instead).
  - `getWorldLocation(): WorldPoint`, `getSceneLocation(): Point`, `getLocalLocation(): LocalPoint`.
  - `getPlane(): int`, `getRenderLevel(): int`.
  - `getBridge(): Tile` — for bridge tiles, the tile underneath.

---

## ItemContainer methods we may not be using

`api/net/runelite/api/ItemContainer.java`:
- `getId(): int`
- `getItems(): Item[]` — full array, can include `id=-1`/`quantity=0` empty slots.
- `getItem(int slot): Item` — single slot (handy for equipment, since slots map via `EquipmentInventorySlot`).
- `contains(int itemId): boolean`, `count(int itemId): int` — without iterating yourself. `count` correctly sums stack quantity.
- `find(int itemId): int` — slot index of first match, -1 if absent.
- `size(): int` — capacity (28 for inv, 14 for equipment, ~800+ for bank).
- `count(): int` — number of filled slots.

`EquipmentInventorySlot` enum maps slot ids: `HEAD=0`, `CAPE=1`, `AMULET=2`, `WEAPON=3`, `BODY=4`, `SHIELD=5`, `LEGS=7`, `GLOVES=9`, `BOOTS=10`, `RING=12`, `AMMO=13`. We already expose equipment but can use this enum for cleaner slot names.

---

## Varbits worth reading

`Varbits.java` is deprecated in favor of `gameval/VarbitID.java`. Read via `client.getVarbitValue(int)`. Subscribe to `VarbitChanged` event to detect changes.

### Account / character
| Varbit | What | Notes |
|---|---|---|
| `Varbits.ACCOUNT_TYPE` (1777) | 0 normal, 1 iron, 2 UIM, 3 HCIM, 4 GIM, 5 HC GIM, 6 unranked GIM | replaces deprecated `Client.getAccountType()` |
| `Varbits.QUEST_TAB` (8168) | active quest interface tab | |
| `Varbits.SLAYER_POINTS` (4068) | current slayer reward points | |
| `Varbits.SLAYER_TASK_STREAK` (4069) | consecutive task count | |
| `Varbits.SLAYER_TASK_BOSS` (4723) | boss task id (0 if not a boss task) | |
| `Varbits.PRAYERBOOK` (14826) | 0 normal, 1 ruinous powers | |
| `Varbits.SPELLBOOK` (4070) | 0 modern, 1 ancient, 2 lunar, 3 arceuus | |
| `Varbits.SPELLBOOK_SUBMENU` (9730) | which spell submenu is open | |
| `Varbits.SPELLBOOK_SWAP` (3617) | 1 if temp spellbook swap active | |
| `Varbits.EQUIPPED_WEAPON_TYPE` (357) | weapon style id (drives attack-style options) | |
| `Varbits.DEFENSIVE_CASTING_MODE` (2668) | 1 if defensive autocast | |
| `Varbits.CURRENT_BANK_TAB` (4150) | active bank tab 0..9 | |
| `Varbits.BANK_TAB_*_COUNT` (4171-4179) | item count per bank tab 1..9 | |

### Combat / location flags
| Varbit | What |
|---|---|
| `Varbits.IN_WILDERNESS` (5963) | 1 if local player is in wilderness |
| `Varbits.MULTICOMBAT_AREA` (4605) | 1 if current tile is multi |
| `Varbits.PVP_SPEC_ORB` (8121) | 1 if in PvP area (despite the name) |
| `Varbits.IN_RAID` (5432) | 1 if inside CoX |
| `Varbits.RAID_STATE` (5425) | 0 not started, >0 in progress (CoX) |
| `Varbits.TOTAL_POINTS` (5431) | CoX raid points |
| `Varbits.THEATRE_OF_BLOOD` (6440) | 1 in party, 2 inside, 3 dead-spectating (ToB) |
| `Varbits.TOA_RAID_LEVEL` (14380) | ToA invocation level |
| `Varbits.TOA_RAID_DAMAGE` (14325) | damage taken in ToA |
| `Varbits.IN_LMS` (5314) | 1 if in Last Man Standing |
| `Varbits.BOSS_HEALTH_CURRENT` (6099) | current HP of HUD-tracked boss |
| `Varbits.BOSS_HEALTH_MAXIMUM` (6100) | max HP of HUD-tracked boss |

### Buff/debuff timers (mostly in game ticks; check the JavaDoc for tick scaling)
| Varbit | What |
|---|---|
| `STAMINA_EFFECT` (24), `RUN_SLOWED_DEPLETION_ACTIVE` (25) | stamina (×10 ticks); active flag |
| `RING_OF_ENDURANCE_EFFECT` (10385) | ring of endurance extra (×10 ticks) |
| `ANTIFIRE` (3981, ×30), `SUPER_ANTIFIRE` (6101, ×20) | dragonfire protection |
| `MAGIC_IMBUE` (5438, ×10) | imbue runes |
| `VENGEANCE_ACTIVE` (2450), `VENGEANCE_COOLDOWN` (2451) | veng up + cooldown |
| `DIVINE_*` (8429-8433, 13663-13665) | divine potion timers |
| `IMBUED_HEART_COOLDOWN` (5361, ×10), `DRAGONFIRE_SHIELD_COOLDOWN` (6539, ×8) | spec cooldowns |
| `MOONLIGHT_POTION` (10029) | moonlight defence dose |
| `CHARGE_GOD_SPELL` (`VarPlayer 272`, ×2) | god spell charge |
| `TELEBLOCK` (4163) | <0 immune; 1-100 immunity countdown; ≥101 blocked |
| `GOD_WARS_ALTAR_COOLDOWN` (4099, ×100) | GWD altar pray cooldown |
| `FARMERS_AFFINITY` (11765, ×20), `MENAPHITE_REMEDY` (14448), `LIQUID_ADERNALINE_ACTIVE` (14361) | misc buffs |
| `NMZ_OVERLOAD_REFRESHES_REMAINING` (3955), `COX_OVERLOAD_REFRESHES_REMAINING` (5418) | overload doses left |
| `PRAYER_DEADEYE` (16090), `PRAYER_MYSTIC_VIGOUR` (16091) | needed (deprecated isPrayerActive misses these) |
| `QUICK_PRAYER` (4103) | quick prayers toggled |

### Activity progress (browse `Varbits.java` for many more)
- Diaries: `DIARY_*_EASY/MEDIUM/HARD/ELITE` (4458-4498) across all regions.
- Combat Achievements: `COMBAT_ACHIEVEMENT_TIER_*` (12863-12868, 2=done) and `COMBAT_TASK_*` (12885-12890) counts.
- Favour: `KOUREND_FAVOR_*` (4894-4899) for each of 5 houses, `KINGDOM_APPROVAL` (72) + `KINGDOM_COFFER` (74) for Miscellania.
- Barrows: `BARROWS_KILLED_*` (457-462), `BARROWS_REWARD_POTENTIAL` (463), `BARROWS_NPCS_SLAIN` (464).
- Minigame scores: `WINTERTODT_WARMTH` (11434), `NMZ_POINTS` (3949), `TITHE_FARM_POINTS` (4893), `BLAST_MINE_*` (10698-10702), `PYRAMID_PLUNDER_*` (2375-2377).
- Leagues: `LEAGUES_*_COMBAT_MASTERY_LEVEL` (11580-11582), `LEAGUE_RELIC_1..8` (10049-17302).
- Environmental: `OXYGEN_LEVEL` (5811), `COLOSSEUM_DOOM` (9801), `CURSE_OF_THE_MOONS` (9853).
| `Varbits.CLOG_*` via VarPlayer | see below |

### UI / settings
- `TRANSPARENT_CHATBOX` (4608), `SIDE_PANELS` (4607)
- `BANK_REARRANGE_MODE` (3959), `BANK_QUANTITY_TYPE` (6590) (1/5/10/X/All), `BANK_REQUESTEDQUANTITY` (3960) (the "X" value)

### Rune pouch (specifically valuable)
- `RUNE_POUCH_RUNE1..6` (29, 1622, 1623, 14285, 15373, 15374) — rune type id per slot.
- `RUNE_POUCH_AMOUNT1..6` (1624-1626, 14286, 15375, 15376) — rune count per slot.
- `ESSENCE_POUCH_*_AMOUNT` (603-606, 13682) — essence in small/medium/large/giant/colossal.
- `ESSENCE_POUCH_COLOSSAL_DEGRADE` (13683) — colossal pouch degrade.

### Quiver & team health orbs
- `VarPlayer.DIZANAS_QUIVER_ITEM_ID` (4142), `_ITEM_COUNT` (4141).
- `THEATRE_OF_BLOOD_ORB1..5` (6442-6446) — teammate HP 0..27 (30=dead).
- `TOA_MEMBER_0..7_HEALTH` (14346-14353).

---

## VarPlayers worth reading

`VarPlayer.java`; read via `client.getVarpValue(int)`. Newer ids in `gameval/VarPlayerID.java`.

| VarPlayer | What |
|---|---|
| `QUEST_POINTS` (101) | total QP |
| `ATTACK_STYLE` (43) | 0..3 index into current weapon's stance options |
| `POISON` (102) | poison/venom: <0 immune-remaining, >0 poison dmg, ≥1000000 venom (decode per JavaDoc) |
| `DISEASE_VALUE` (456) | disease level (Tirannwn, etc.) |
| `SPECIAL_ATTACK_PERCENT` (300), `SPECIAL_ATTACK_ENABLED` (301) | spec energy 0..1000, armed flag |
| `MEMBERSHIP_DAYS` (1780) | days remaining |
| `CANNON_STATE` (2), `CANNON_AMMO` (3), `CANNON_COORD` (4) | dwarf cannon placement/ammo |
| `IN_RAID_PARTY` (1427), `RAIDS_PERSONAL_POINTS` (4609), `NMZ_REWARD_POINTS` (1060) | minigame/raid points |
| `BANK_TAB` (115), `CURRENT_GE_ITEM` (1151) | active UI selections |
| `SLAYER_TASK_SIZE` (394), `SLAYER_TASK_CREATURE` (395), `SLAYER_TASK_LOCATION` (2096), `SLAYER_UNLOCK_1/2` (1076, 1344) | slayer task |
| `HP_HUD_NPC_ID` (1683) | NPC id whose health bar is shown on HUD |
| `CLOG_LOGGED` (2943), `CLOG_TOTAL` (2944) | collection log progress |
| `MUSIC_VOLUME` (168), `SOUND_EFFECT_VOLUME` (169), `AREA_EFFECT_VOLUME` (872), `MOUSE_BUTTONS` (170) | settings |
| `LAST_HOME_TELEPORT` (892), `LAST_MINIGAME_TELEPORT` (888) | minutes since epoch (for cooldowns) |
| `CHARGE_GOD_SPELL` (272), `BUFF_BAR_WC_GROUP_BONUS` (4007) | buffs |
| `*_GOAL_START` (1229-1251) / `*_GOAL_END` (1253-1275) | per-skill XP tracker goals |
| `BIRD_HOUSE_*` (1626-1629), `ESSENCE_POUCH_*_DEGRADE` (488-490), `THRONE_OF_MISCELLANIA` (359) | misc state |

---

## VarClientInt / VarClientStr (client-only vars)

Read via `client.getVarcIntValue(int)` / `getVarcStrValue(int)`. Defined in `gameval/VarClientID.java`. These are *not* persisted server-side; lost on relog.

Notable:
- `VarClientID.CHATINPUT` — current chatbox typed text.
- `VarClientID.MESLAYERINPUT` — last text typed into a "Enter amount" / "Enter name" dialog.
- `VarClientID.WORLDMAP_SEARCH`, `WORLDMAP_SEARCHING` — world map search state.
- `VarClientID.HAS_DISPLAYNAME`, `DISPLAYNAME_LOOKUP` — display name lookups in progress.
- `VarClientID.LAST_NAMEDIALOG` — last name typed into "Who do you want to send to?" boxes.
- `VarClientID.SETTINGS_SEARCH_STRING` — settings search box.
- `TOB_CLIENT_NAME0..4` — ToB party display names.
- `VarClientID.LAST_MACRO_NAME` — last clan broadcast macro.

There are ~500 more covering UI state of every interface; treat `VarClientID.java` as a lookup table when you need to read interface state without parsing widgets.

---

## Other notable types

### `WorldArea` — `coords/WorldArea.java`
- Constructors: `new WorldArea(x, y, width, height, plane)` or `new WorldArea(WorldPoint, w, h)`.
- `distanceTo(WorldArea/WorldPoint): int` — 3D distance (huge if different plane).
- `distanceTo2D(...)` — ignores plane.
- `contains(WorldPoint)` / `contains2D(WorldPoint)`.
- `isInMeleeDistance(WorldArea/WorldPoint): boolean` — adjacency check (for large NPCs).
- `intersectsWith(WorldArea)`.
- `canTravelInDirection(WorldView wv, dx, dy)` — collision-aware step test.
- `hasLineOfSightTo(WorldView wv, WorldArea/WorldPoint)` — projectile LOS.
- `toWorldPoint(): WorldPoint`, `toWorldPointList(): List<WorldPoint>`.

### `WorldPoint` — `coords/WorldPoint.java`
- `getRegionID(): int`, `getRegionX(): int`, `getRegionY(): int`; `distanceTo(...)` / `distanceTo2D(...)`; `dx/dy/dz(int)` (immutable shifts).
- `WorldPoint.fromLocal(WorldView, LocalPoint)`, `fromLocalInstance(Scene, LocalPoint, plane)` (instance-aware), `toLocalInstance(WorldView, WorldPoint)` returns all *real* coords a logical point maps to inside an instance.

### `Prayer` enum
- `prayer.getVarbit()` returns the underlying varbit id; iterate `Prayer.values()` and read varbits for the full active-prayer set (including deadeye/mystic vigour, which deprecated `isPrayerActive` misses).

### `GameState` enum
- `STARTING`, `LOGIN_SCREEN`, `LOGGING_IN`, `LOADING`, `LOGGED_IN`, `CONNECTION_LOST`, `HOPPING`. Subscribe via `GameStateChanged` event; guard all reads with `LOGGED_IN`.

### `Hitsplat` — `Hitsplat.java`
- `getHitsplatType(): int` (see `HitsplatID`: poison, venom, heal, block, normal, max), `getAmount(): int`, `getDisappearsOnGameCycle(): int`. Live via `HitsplatApplied` event.

### `MenuEntry` / `MenuAction`
- `MenuAction` enum has ~150 values for every interaction type (WALK, NPC_FIRST_OPTION..FIFTH, GAME_OBJECT_*, WIDGET_TARGET, etc.). Useful for predicting what a click *would* do.

### `ChatMessageType`
Enum covering every chat channel: `PUBLICCHAT`, `PRIVATECHAT`, `GAMEMESSAGE` (server msgs), `SPAM`, `TRADE`, `CLAN_CHAT`, `CLAN_GUEST_CHAT`, `CLAN_MESSAGE`, `BROADCAST`, `ITEM_EXAMINE`, `OBJECT_EXAMINE`, `NPC_EXAMINE`, `MESBOX` (dialog modal), etc.

### `World` (`World.java`)
- `getId()`, `getActivity(): String`, `getLocation(): int`, `getPlayerCount(): int`, `getTypes(): EnumSet<WorldType>`, `getAddress(): String`.

### `Constants` — `api/net/runelite/api/Constants.java`
- `CLIENT_TICK_LENGTH = 20` ms, `GAME_TICK_LENGTH = 600` ms, `CHUNK_SIZE = 8`, `REGION_SIZE = 64`, `SCENE_SIZE = 104`, `EXTENDED_SCENE_SIZE = 184`, `MAX_Z = 4`, `OVERWORLD_MAX_Y = 4160`.
- Tile setting bits: `TILE_FLAG_BRIDGE`, `TILE_FLAG_UNDER_ROOF`, `TILE_FLAG_VIS_BELOW`.

---

## Quick wins (suggested next exposures, roughly ranked)

1. `Player.getAnimation()` + nearby NPC animations — "what's happening right now?".
2. `Actor.getInteracting()` for nearby NPCs — which monster is targeting *me*.
3. Rune pouch contents (`Varbits.RUNE_POUCH_RUNE/AMOUNT1..6`).
4. Special attack energy (`VarPlayer.SPECIAL_ATTACK_PERCENT` ÷10) + armed flag.
5. Poison/venom (`VarPlayer.POISON`, decoded).
6. Carried weight (`Client.getWeight()`).
7. Account hash + account type (`getAccountHash()`, `Varbits.ACCOUNT_TYPE`).
8. GE offers (`Client.getGrandExchangeOffers()`).
9. Slayer task — creature, count, streak, points, location, boss flag.
10. Ground items nearby — iterate `Tile.getGroundItems()` within N tiles.
11. Quest points + collection log totals.
12. World type & wilderness/PvP flags.
13. Active buffs/timers (stamina, antifire, vengeance, divine, charge).
14. Recent chat messages via `getMessages()` filtered by `ChatMessageType`.
15. Overhead text on nearby actors — capture NPC barks.
16. Boss HP (`Varbits.BOSS_HEALTH_CURRENT/MAXIMUM` + `VarPlayer.HP_HUD_NPC_ID`).
17. Diary / Combat Achievement progress bitfields.

---

## Gotchas

- **Threading**: every read above must happen on the client thread. Off-thread reads can race with the game tick and return stale arrays.
- **`getEnergy()` is 0..10000**, not 0..100. Divide by 100.
- **`SPECIAL_ATTACK_PERCENT` is 0..1000**, not 0..100. Divide by 10.
- **`getInteracting()` returns null for out-of-view targets** even if `isInteracting()` is true — read `getInteracting()` *before* the target despawns or moves out of range.
- **`getHealthRatio()` is fractional**, not absolute HP. Compute `100 * ratio / scale`. -1 means unknown.
- **Instances** mangle world coords; use `WorldPoint.fromLocalInstance(...)` when in an instance, otherwise positions you log won't match the wiki map.
- **`Client.isPrayerActive()`** is deprecated and wrong for deadeye/mystic vigour/ruinous powers — read the varbit directly via `Prayer.getVarbit()`.
- **Varbits vs VarPlayers**: a varbit reads N bits out of a backing varp. Use `getVarbitValue()` for varbits, `getVarpValue()` for raw varps. Don't mix.
- **`Varbits` class is `@Deprecated`** in favor of `gameval.VarbitID` — same int values, just regenerated/comprehensive. New code should reference `VarbitID`.
- **`getNpcs()` / `getPlayers()` on `Client` are deprecated** — go through `client.getTopLevelWorldView().npcs()` / `.players()`.
- **`InventoryID` enum** only has 4 entries; for everything else use the int-id overload with `gameval.InventoryID` constants.
- **Player composition kit/item offset**: equipment ids returned by `PlayerComposition.getEquipmentId()` are offset by `+2048` for items, `+256` for kits. Subtract to compare against `ItemID`.
- **Disposable types**: `Projectile`, `GraphicsObject`, `Hitsplat`, `MenuEntry` are short-lived. Capture data you want into your own value classes; don't hold references across ticks.
