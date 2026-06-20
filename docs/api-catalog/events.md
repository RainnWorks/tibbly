# Event bus reference

RuneLite's `EventBus` (`net.runelite.client.eventbus.EventBus`) is the central pub/sub
hub. A plugin subscribes by writing a non-static instance method that takes a single
concrete event class as its only parameter and annotating it with `@Subscribe`. The
EventBus rejects polymorphic / abstract event parameters and requires the method to be
named `on<EventClassName>` (e.g. `onGameTick(GameTick e)`).

```kotlin
@Subscribe
fun onGameTick(event: GameTick) { ... }

@Subscribe(priority = 1.0f)  // higher priority runs first
fun onMenuOptionClicked(event: MenuOptionClicked) { ... }
```

Subscribers are invoked synchronously on the thread that calls `EventBus.post(...)`.
For events posted from the deob client (most of the events below) that is the **client
thread**, so handlers must be cheap and must not block. Use `ClientThread.invokeLater`
to schedule client-side work and `scheduledExecutorService` for I/O. Events posted by
RuneLite-side services (`ConfigChanged`, `NpcLootReceived`, `ProfileChanged`, etc.) may
fire on the AWT, scheduler, or client thread depending on the source.

Events are dispatched ordered by `priority` (descending), then by subscriber class name.
A handler may `event.consume()` on the few mutable events that support it
(`MenuOptionClicked`, `ChatInput`/`ChatboxInput`/`PrivateMessageInput`,
`GrandExchangeSearched`, `SoundEffectPlayed`) to prevent default client behaviour.

The osrs-llm-helper plugin already subscribes to `GameTick`, `GameStateChanged`,
`ItemContainerChanged`, `StatChanged`, and `ConfigChanged`. The tables below catalog
the remaining events worth knowing about, grouped by use-case.

## Game lifecycle

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `GameStateChanged` | `GameState` | client transitions between states (LOGIN_SCREEN, LOGGING_IN, LOGGED_IN, LOADING, HOPPING, CONNECTION_LOST, etc.) | detect login/logout, reset per-session caches, gate tool calls on `LOGGED_IN` |
| `GameTick` | (empty) | once per server tick (~600 ms), after all packets are processed | poll loop, throttle expensive snapshots, accumulate per-tick deltas |
| `ClientTick` | (empty) | once per client frame; very frequent — usually skip |  |
| `PostClientTick` | (empty) | after a ClientTick, after menu rebuild; usually skip |  |
| `WorldChanged` | (none — query `Client#getWorld`) | world ID/type changed, before reconnect | invalidate world-specific state (e.g. GE prices on DMM) |
| `AccountHashChanged` | (none — query `Client#getAccountHash`) | local account hash changed after login | key per-account storage; preferred over username |
| `UsernameChanged` | (none) | login-screen username field changes; fires per keystroke — rarely useful |  |
| `RuneScapeProfileChanged` | previous/new profile key | switched RS save profile (account hop, league/beta world) | reset profile-scoped state, refetch saved bank, etc. |
| `ProfileChanged` | (empty) | active RuneLite `ConfigProfile` changed | reload config snapshots |
| `SessionOpen` / `SessionClose` | (empty) | RuneLite account session (NOT the game session) opened/closed | sync remote state with RL account |
| `ClientShutdown` | task queue (`waitFor(Future)`) | client is shutting down | flush pending writes, close MCP transport cleanly |

## Player stats & XP

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `StatChanged` | `Skill, xp, level, boostedLevel` | xp, real level, or boosted level changes (also fires once for each skill on login) | XP-gain detection, level-up announcements, boost expiry tracking |
| `FakeXpDrop` | `Skill, xp` | a synthetic xp drop is added (shared xp, lamps, etc.) without a real xp change | render-only xp drops; usually pair with `StatChanged` |

## Item containers (inventory, bank, equipment)

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `ItemContainerChanged` | `containerId, ItemContainer` | any tracked container's contents change (inv, bank, equipment, looting bag, etc.) | diff inventory snapshots, detect pickups/drops, react to bank open |
| `GrandExchangeOfferChanged` | `GrandExchangeOffer, slot` | a GE slot updates state (also fires EMPTY for all slots on login) | track buy/sell progress, build a tool for "are my offers done?" |
| `GrandExchangeSearched` | consumable | player searches GE; setting custom results requires consuming | inject custom search results (advanced) |

## Combat & actors

Most of these fire for both players and NPCs. `Actor` is the common supertype; downcast
to `NPC` or `Player` as needed.

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `HitsplatApplied` | `Actor, Hitsplat` | a hitsplat lands on an actor (even if visually hidden) | DPS meter, "am I taking damage?" alerts, boss HP tracking via `Hitsplat#getAmount` |
| `ActorDeath` | `Actor` | an actor plays its death animation | "boss defeated" detection (pair with `NpcDespawned` to confirm kill credit) |
| `InteractingChanged` | `source, target` (Actor, nullable) | an actor's interaction target changes | detect when local player starts/stops fighting, find aggressors |
| `AnimationChanged` | `Actor` (query `getAnimation()`) | actor's animation ID changes | gathering progress (wc/fishing), spec attacks, prayer flicks |
| `PostAnimation` | `Animation` | an animation pose is loaded; rarely needed |  |
| `GraphicChanged` | `Actor` (query `getGraphic()`) | actor spotanim changes (spells, teleports, vengeance) | combat tells, teleport detection |
| `OverheadTextChanged` | `Actor, overheadText` | the chat-overhead text on an actor changes | NPC dialog scraping ("Sir William speaks…"), boss callouts |
| `ProjectileMoved` | `Projectile, position, z` | a projectile is created or updates its end point | telegraphed attacks (Vorkath zombified spawn, shamans AoE) |
| `SoundEffectPlayed` | `Actor (nullable), soundId, delay`, consumable | a positioned sound effect plays | detect specific game audio cues (e.g. food eat, spec available) |
| `AreaSoundEffectPlayed` | similar to above but with source coords | non-positioned area sound played | rarely useful |
| `VolumeChanged` | (empty) | game volume sliders changed | usually skip |

## NPCs & players (world actors)

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `NpcSpawned` | `NPC` | an NPC enters the loaded scene (incl. on scene rebuild) | track nearby NPCs, find boss instances, detect respawns |
| `NpcDespawned` | `NPC` | an NPC leaves the scene (death, walk-away, scene unload) | confirm kill + claim loot window, clear stale state |
| `NpcChanged` | `NPC, NPCComposition old` | NPC transforms (Vorkath dragon→zombified, hydra phases) | phase detection for multi-form bosses |
| `PlayerSpawned` | `Player` | a player enters loaded scene | minimap dot tracking, PK alerts, party detection |
| `PlayerDespawned` | `Player` | a player leaves loaded scene (does NOT fire for local player) | observe other players leaving |
| `PlayerChanged` | `Player` | a player's appearance/equipment changes | gear inspect, "what is X wearing?" tools |
| `PlayerMenuOptionsChanged` | `index` | a player-right-click option slot changed | usually skip |
| `WorldEntitySpawned` / `WorldEntityDespawned` | `WorldEntity` | world entity (e.g. instance bubble) spawns/despawns; precedes `WorldViewLoaded` | track entered instances (raids, ToA) |
| `WorldViewLoaded` / `WorldViewUnloaded` | `WorldView` | a worldview finished loading / was discarded | run scene-scoped scans on a fresh view |

## Scene objects & ground items

These fire heavily — only subscribe if you actually need them.

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `GameObjectSpawned` / `GameObjectDespawned` | `Tile, GameObject` | a scenery object appears/disappears | find altars, doors, resources nearby |
| `GroundObjectSpawned` / `GroundObjectDespawned` | `Tile, GroundObject` | a ground-attached object (e.g. carpets) changes |  |
| `WallObjectSpawned` / `WallObjectDespawned` | `Tile, WallObject` | a wall-attached object changes |  |
| `DecorativeObjectSpawned` / `DecorativeObjectDespawned` | `Tile, DecorativeObject` | decorative objects (e.g. wall paintings) change |  |
| `ItemSpawned` | `Tile, TileItem` | ground item pile spawns (no despawn fires on scene unload) | loot detection, "what's on the floor near me?" |
| `ItemDespawned` | `Tile, TileItem` | ground item pile despawns explicitly | loot pickup confirmation |
| `ItemQuantityChanged` | `TileItem, Tile, oldQuantity, newQuantity` | ground pile size changed (stacked drop) | loot value updates |
| `PreMapLoad` | `WorldView, Scene` | fires on map-loader thread before map load — **do not touch client state from here** | almost never useful |
| `GraphicsObjectCreated` | `GraphicsObject` | a transient effect graphic was created (e.g. ice barrage splash) | telegraph detection |

## Chat & UI

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `ChatMessage` | `MessageNode, ChatMessageType, name, message, sender, timestamp` | a chat line is added (public, PM, game, broadcast, clan, etc.) — **does NOT fire for NPC dialogues** | log game events, detect drops/level-ups via system messages, surface PMs to the LLM |
| `OverheadTextChanged` | (see Combat & actors) | for NPC dialogue / player chat-bubble |  |
| `MenuOpened` | `MenuEntry[]` | right-click menu opened | inspect available actions on hovered target |
| `MenuEntryAdded` | `MenuEntry` (via getters) | each entry is appended to a menu (fires many times per right-click) | inject custom menu entries; mostly used by overlays |
| `MenuOptionClicked` | `MenuEntry` (+ params, target, action), consumable | **any** click resolved to a menu action (left- or right-click); also fires for "Cancel" left-clicks | observe what the user is doing, block actions via `consume()` |
| `MenuShouldLeftClick` | (mutable flag) | client is deciding whether to force right-click on next interaction | usually skip |
| `WidgetLoaded` | `groupId` | an interface (group) opened (bank, GE, quest tab, etc.) | trigger reads of newly-visible widgets |
| `WidgetClosed` | `groupId, modalMode, unload` | an interface closed | clear cached widget data |
| `WidgetDrag` | drag info | a widget drag completed | inventory rearrangement detection |
| `CommandExecuted` | `command, arguments` | user typed `::cmd args…` | implement plugin chat commands like `::ask <prompt>` |
| `ChatboxInput` (`ChatInput`) | `value, chatType`, resumable+consumable | user pressed Enter on the chatbox; subscriber can rewrite/block | intercept outgoing chat for LLM routing |
| `PrivateMessageInput` (`ChatInput`) | `target, message`, resumable+consumable | user sent a private message | intercept/log outgoing PMs |
| `CanvasSizeChanged` / `ResizeableChanged` / `FocusChanged` | (empty) | window/canvas state changes | rarely useful for LLM helpers |
| `BeforeRender` / `BeforeMenuRender` / `PostMenuSort` / `PostHealthBarConfig` | draw-loop hooks | per-frame; **skip** for LLM use cases |  |

## Vars (varbits, varps, client vars)

Vars are the canonical state slots the OSRS server uses to track quest progress,
diaries, settings, dialogue progress, region IDs, etc.

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `VarbitChanged` | `varpId, varbitId, value` | a varp or varbit changed value. When `varbitId == -1`, this is a raw varp change; otherwise it's a specific varbit | quest step / diary / region detection, prayer/spellbook tracking. The deprecated `getIndex()` is just an alias for `varpId` |
| `VarClientIntChanged` | `index` (`@VarCInt`) | a `varcInt` (client-side int var, e.g. UI tab) changed | detect which tab is open, settings flips |
| `VarClientStrChanged` | `index` (`@VarCStr`) | a `varcStr` (client-side string var, e.g. typed search) changed | detect search box input |

## Config & plugin lifecycle

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `ConfigChanged` | `group, profile, key, oldValue, newValue` | a config entry was set/unset (any plugin, any profile) | hot-reload settings; filter by `group` matching your plugin's `@ConfigGroup` |
| `PluginChanged` | `Plugin, loaded` | a plugin was started or stopped | enable/disable integrations (e.g. only register MCP tool if Loot Tracker is active) |
| `ExternalPluginsChanged` | (empty) | a pluginhub plugin was installed/updated/removed | refresh discovery of peer plugins |
| `ConfigSync` | (empty) | config has been synced from server | usually skip |
| `PluginMessage` | `namespace, name, data: Map<String, Any>` | another plugin posted a typed message intended for other plugins | inter-plugin RPC; the LLM helper can expose itself as a `PluginMessage` consumer |
| `NotificationFired` | `Notification, message, type` | RuneLite issued a tray/sound notification | surface notifications to the LLM context |
| `OverlayMenuClicked` | `OverlayMenuEntry, Overlay` | user clicked a custom menu entry on an overlay | drive overlay buttons |
| `InfoBoxMenuClicked` | `InfoBox, menu entry` | user clicked an infobox right-click entry | drive infobox buttons |

## Loot, party, clan & friends

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `NpcLootReceived` | `NPC, Collection<ItemStack>` | client-side heuristic detected loot from an NPC kill (Loot Tracker plugin) | drop logging, GP/hour estimates; pair with `ActorDeath` |
| `PlayerLootReceived` | `Player, Collection<ItemStack>` | client-side heuristic detected loot from a player (PvP) |  |
| `ServerNpcLoot` | `NPCComposition, Collection<ItemStack>` | server-authoritative loot (newer, in-game loot tracker) | preferred over `NpcLootReceived` when available — no false positives |
| `ClanChannelChanged` | `ClanChannel?, clanId, guest` | local player joined/left a clan channel | track current clan |
| `ClanMemberJoined` / `ClanMemberLeft` | `ClanChannel, ClanChannelMember` | clan member joined/left the channel |  |
| `FriendsChatChanged` | `joined: boolean` | client joined/left a friends chat |  |
| `FriendsChatMemberJoined` / `FriendsChatMemberLeft` | `FriendsChatMember` | FC roster change |  |
| `RemovedFriend` | `Nameable` | friend or ignore entry removed |  |
| `NameableNameChanged` | `Nameable` | a friend/ignore/chat member name was updated | refresh display names |
| `PartyChanged` | party info | RL party plugin joined/left a party |  |
| `PartyMemberAvatar` | avatar bytes | party member avatar updated |  |

## Item/object metadata & scripts (advanced)

These hook deep into the client's data-loading and CS2 scripting. Use sparingly.

| Event | Carries | Fires when | Useful for |
|---|---|---|---|
| `PostItemComposition` | `ItemComposition` | an `ItemComposition` was just initialized | rewrite item names/options/colours |
| `PostObjectComposition` | `ObjectComposition` | object def just initialized | rewrite object data |
| `PostStructComposition` | `StructComposition` | struct def just initialized |  |
| `PostHealthBarConfig` | health bar def | hp bar def loaded | recolour hp bars |
| `ScriptPreFired` | `scriptId, ScriptEvent?` | a CS2 script is about to run | hook specific UI scripts |
| `ScriptPostFired` | `scriptId` | a CS2 script finished | run code after a UI rebuild |
| `ScriptCallbackEvent` | `Script, eventName` | a `runelite_callback` CS2 opcode fires | RL ↔ CS2 bridge for injected client scripts |
| `WorldListLoad` | world list | world list refreshed | world hop logic |
| `WorldsFetch` | fetched worlds | world-list HTTP fetch completed |  |
| `AmbientSoundEffectCreated` | ambient sound | an ambient looping sound spawned | rare |
| `ScreenshotTaken` | image | RL took a screenshot | auto-upload, etc. |

## Notes & gotchas

- **Deprecated**: `VarbitChanged.getIndex()` is an alias for `getVarpId()`; prefer the new
  fields. `MenuOptionClicked.getActionParam()` / `getWidgetId()` are deprecated aliases
  for `getParam0()` / `getParam1()`.
- **No NPC dialogue from `ChatMessage`** — NPC dialog lives in widgets; read them via
  `WidgetLoaded` (dialog group IDs) or scrape on `GameTick`.
- **`GameStateChanged` fires LOADING before LOGGED_IN** — your "I'm logged in"
  initialization should run on `LOGGED_IN` and clear caches on `LOGIN_SCREEN` /
  `CONNECTION_LOST`.
- **`ItemContainerChanged` containerId** — match against `InventoryID` constants
  (e.g. `InventoryID.INV` = inventory, `InventoryID.WORN` = equipment,
  `InventoryID.BANK`). Don't subscribe blindly; filter.
- **Threading** — `EventBus.post` is synchronous on the caller's thread. From a
  `@Subscribe` you are usually on the client thread and may call `Client` methods
  directly; offload anything blocking to an executor.
- **Don't subscribe to draw-loop events** (`BeforeRender`, `BeforeMenuRender`,
  `PostClientTick`, `PostMenuSort`, `ClientTick`) for an LLM helper — they fire ~50x
  per second and only matter to overlays.
