# World & location

Everything you need to answer "where am I, what's around me, and what kind of
world is this?" in the RuneLite API. All paths below are inside
`docs/runelite/sources/`.

Key files:

- `api/net/runelite/api/coords/WorldPoint.java`
- `api/net/runelite/api/coords/LocalPoint.java`
- `api/net/runelite/api/coords/WorldArea.java`
- `api/net/runelite/api/coords/Direction.java`, `Angle.java`
- `api/net/runelite/api/Scene.java`, `WorldView.java`, `Tile.java`, `TileItem.java`
- `api/net/runelite/api/GameObject.java`, `WallObject.java`, `DecorativeObject.java`, `GroundObject.java`, `TileObject.java`
- `api/net/runelite/api/Constants.java`
- `api/net/runelite/api/WorldType.java`
- `client/net/runelite/client/plugins/discord/DiscordGameEventType.java` (region->name table)

## Coordinates

Three coordinate spaces. Keep them straight.

### WorldPoint — absolute tile coords
`WorldPoint(int x, int y, int plane)` — immutable, `@Value` (lombok), so use
`dx(int)`, `dy(int)`, `dz(int)` to produce shifted copies.

- Range: x roughly 1024..3968, y roughly 2496..15360. `Constants.OVERWORLD_MAX_Y = 4160` — anything above is caves/dungeons/instances.
- Plane: 0..3 (`Constants.MAX_Z = 4`, exclusive). Basements and caves below the
  surface are still plane 0 — they use a tile offset, not negative z.
- Distances:
  - `distanceTo(WorldPoint)` — Chebyshev distance, returns `Integer.MAX_VALUE`
    if planes differ.
  - `distanceTo2D(WorldPoint)` — same but ignores plane.
  - `distanceTo(WorldArea)` — delegates to `WorldArea.distanceTo`.
- Area helpers: `toWorldArea()` (1x1), `isInArea(WorldArea...)`,
  `isInArea2D(WorldArea...)`.
- Packed-coord helper: `WorldPoint.fromCoord(int)` decodes Jagex's packed format
  `(z<<28) | (x<<14) | y` (14 bits each axis, 2 bits plane).

### LocalPoint — scene-local fixed-point
`LocalPoint(int x, int y, int worldView)`. Unit is **1/128 of a tile** (i.e. 7
bits of sub-tile precision, see `Perspective.LOCAL_COORD_BITS`).

- `getSceneX()`, `getSceneY()` — divide by 128, gives 0..103 (scene-tile coords).
- Lifetime: invalidated by a loading zone — do **not** persist across region
  changes.
- Plus/minus: `dx(int)`, `dy(int)`, `plus(int, int)` (in 1/128 tile units).

### Scene-tile coords (0..103)
Bare ints, used as indexes into `Scene.getTiles()[plane][x][y]`. Not a class.
Derived via `LocalPoint.getSceneX/Y()` or `worldPoint.getX() - scene.getBaseX()`.

### Region IDs

The world is partitioned into 64x64-tile **regions** (`Constants.REGION_SIZE = 64`).

```
regionId = ((worldX >> 6) << 8) | (worldY >> 6)
```

- `WorldPoint.getRegionID()` — yields the region the point belongs to.
- `WorldPoint.getRegionX()` / `getRegionY()` — offset within the region (0..63).
- `WorldPoint.fromRegion(regionId, regionX, regionY, plane)` — inverse.
- Loaded regions: `client.getMapRegions()` / `worldView.getMapRegions()` —
  array of region IDs currently in memory. The scene is a 13x13 chunk grid
  drawn from these.

`Constants.CHUNK_SIZE = 8` (one chunk), `Constants.SCENE_SIZE = 104` (13 chunks
square), `Constants.EXTENDED_SCENE_SIZE = 184`.

### Conversions

```java
// WorldPoint -> LocalPoint (in current scene)
LocalPoint lp = LocalPoint.fromWorld(client, worldPoint);          // or wv/scene
// returns null if not in scene or wrong plane

// LocalPoint -> WorldPoint
WorldPoint wp = WorldPoint.fromLocal(client, localPoint);

// scene coords -> WorldPoint
WorldPoint wp = WorldPoint.fromScene(scene, sceneX, sceneY, plane);

// instance-aware (returns template coords for instanced areas)
WorldPoint real = WorldPoint.fromLocalInstance(client, localPoint);
Collection<WorldPoint> wps = WorldPoint.toLocalInstance(wv, templatePoint);
```

Instances rotate chunks; the rotation bits are embedded in
`scene.getInstanceTemplateChunks()[z][cx][cy]` — handled internally by
`fromLocalInstance` / `toLocalInstance`.

### Prifddinas / overworld mirror
`WorldPoint.REGION_MIRRORS` maps the four Prifddinas regions to their
"overworld" alias. Use `WorldPoint.getMirrorPoint(wp, toOverworld)` if you need
to normalize.

## Named locations

**There is no built-in coordinate-to-name lookup in the RuneLite API itself.**
`WorldPoint`, `Scene`, and `WorldView` only expose numeric region IDs.

There is, however, a near-complete region-ID → human name table maintained by
the **Discord plugin**:

`client/net/runelite/client/plugins/discord/DiscordGameEventType.java`

Each enum value is `("Display Name", DiscordAreaType.{BOSSES|CITIES|DUNGEONS|MINIGAMES|RAIDS|REGIONS}, regionId...)`.
The plugin builds `Map<Integer, DiscordGameEventType> FROM_REGION` once and
exposes `DiscordGameEventType.fromRegion(regionId)` for O(1) lookup. The exact
usage pattern (worth copying):

```java
int rid = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
DiscordGameEventType ev = DiscordGameEventType.fromRegion(rid);
String areaName = ev != null ? ev.getState() : null;          // e.g. "Lumbridge"
DiscordAreaType type = ev != null ? ev.getDiscordAreaType() : null;
```

Caveats from how the plugin uses it:

- Always run `WorldPoint.fromLocalInstance(...)` first so instanced raids/ToA
  resolve to their template region.
- Region collisions exist. The plugin special-cases NMZ vs KBD (same region
  9033 vs 9033-adjacent — see `DiscordPlugin#updateState`): "NMZ uses the same
  region ID as KBD. KBD is always on plane 0 and NMZ is always above plane 0."
- The enum is package-private (`enum DiscordGameEventType`) — to reuse, either
  shade it into your plugin or copy out the data into your own resource.

### Strategies for richer naming

1. **Reuse / fork the Discord table.** Copy `DiscordGameEventType` into the
   plugin and rename. Covers all major cities, dungeons, bosses, raids,
   minigames, and big "regions" (Misthalin, Kandarin, Morytania, etc.).
2. **Wiki Cargo table.** The OSRS Wiki maintains the
   [`Locations`](https://oldschool.runescape.wiki/w/Special:CargoTables/Locations)
   Cargo table with name + bounding rectangles + plane. Query at build time
   into a static JSON shipped with the plugin. Higher resolution than region
   IDs ("Falador west bank" vs just "Falador").
3. **Hand-rolled `WorldArea` table.** For sub-region resolution (a specific
   bank, altar, GE booth), define `WorldArea` constants and walk the list in
   priority order. `WorldPoint.isInArea(WorldArea...)` is built for this.
4. **`worldmap` plugin location enums.** `DungeonLocation`,
   `FairyRingLocation`, `FarmingPatchLocation`, `MinigameLocation`,
   `MiningSiteLocation`, `RareTreeLocation`, `RunecraftingAltarLocation`,
   `FishingSpotLocation`, `HunterAreaLocation`, `AgilityCourseLocation`,
   `TeleportLocationData`, `TransportationPointLocation` — each is an
   `(name, WorldPoint)` enum. Great seed data for POI-style lookups.

### Sample region IDs (from `DiscordGameEventType`)

| Region | ID | Region | ID |
|---|---|---|---|
| Lumbridge | 12850 | Varrock | 12853 (plus 12596/97, 12852/54, 13108-10) |
| Falador | 11828 (plus 11572/827, 12084) | Edgeville | 12342 |
| Draynor | 12338/39 | Al Kharid | 13105/06 |
| Grand Exchange | 12598 | Ardougne | 9779/80, 10035/36, 10291/92, 10547/48 |
| Catherby | 11317/18, 11061 | Seers' Village | 10806 |
| Camelot/Kandarin overworld | many — see `REGION_KANDARIN` |
| Wintertodt | 6462 | Zulrah | 9007 |
| Vorkath | 9023 | Cerberus | 4883, 5140, 5395 |
| CoX | 12889 + 13136..13141, 13145, 13393..13397, 13401 |
| ToB | 12611-13, 12867, 12869, 13122/23/25, 13379 |
| ToA | 14160-15700 (see `RAIDS_TOMBS_OF_AMASCUT`) |
| Wilderness regions | (none broken out by name — use y > 3520 heuristic) |

(Full table: ~250 entries in `DiscordGameEventType.java` — copy it.)

## Scene / tiles

### Scene
`client.getScene()` (or `worldView.getScene()`) returns `Scene`:

- `getTiles()` — `Tile[4][104][104]` indexed `[plane][x][y]`. Cells may be null.
- `getExtendedTiles()` — `Tile[4][184][184]`; same data plus surrounding chunks
  not normally rendered. Convert via offset `(184-104)/2 = 40`.
- `getBaseX()`, `getBaseY()` — world coords of scene tile `(0,0)`.
- `getMapRegions()` — int[] of loaded region IDs.
- `isInstance()`, `getInstanceTemplateChunks()` — instance plumbing.
- `getTileHeights()`, `getTileShapes()`, `getOverlayIds()`, `getUnderlayIds()`,
  `getExtendedTileSettings()` — terrain data.
- `getRoofs()`, `setRoofRemovalMode(int)` — roof removal flags
  (`Constants.ROOF_FLAG_POSITION/HOVERED/DESTINATION/BETWEEN`).

### WorldView
The modern wrapper around a scene. `client.getTopLevelWorldView()` for the
main world, `client.getWorldView(id)` for nested ones (world entities,
ships-in-instances). Mirrors most `Client` world methods: `getPlane()`,
`getBaseX/Y()`, `getMapRegions()`, `getScene()`, `players()`, `npcs()`,
`worldEntities()`, `getCollisionMaps()`, `contains(WorldPoint|LocalPoint)`.

Most legacy `Client.getXxx` calls (`getScene`, `getPlane`, `getBaseX`,
`getMapRegions`, `getPlayers`, `getNpcs`, `getCollisionMaps`,
`getInstanceTemplateChunks`, `isInInstancedRegion`) are now `@Deprecated`
defaults that delegate to `getTopLevelWorldView()`. Use the worldview directly
if you need to support nested worlds.

### Tile (`api/net/runelite/api/Tile.java`)

Per-tile contents on each cell of the scene grid:

- `getWorldLocation()` — `WorldPoint`
- `getLocalLocation()` — `LocalPoint`
- `getSceneLocation()` — `Point` (sceneX, sceneY)
- `getPlane()`, `getRenderLevel()`
- `getGameObjects()` — `GameObject[]` (up to 5 slots; entries can be null)
- `getWallObject()` — `WallObject` (the wall sitting between this tile and a neighbour)
- `getDecorativeObject()` — `DecorativeObject`
- `getGroundObject()` — `GroundObject`
- `getItemLayer()` — `ItemLayer` (a linked list node of items, mostly internal)
- `getGroundItems()` — `List<TileItem>` (this is what you want for ground drops)
- `getSceneTilePaint()`, `getSceneTileModel()` — render data
- `getBridge()` — for bridge tiles, the tile underneath (e.g. water under a bridge)

### TileItem (`api/net/runelite/api/TileItem.java`)

- `getId()` — item id (see `gameval.ItemID`)
- `getQuantity()`
- `getVisibleTime()`, `getDespawnTime()` — server ticks (`client.getTickCount()`)
- `getOwnership()` — `OWNERSHIP_NONE | _SELF | _OTHER | _GROUP`
- `isPrivate()`

## Objects on a tile

All four object-flavour interfaces extend `TileObject`. Shared API:

`TileObject` (`api/net/runelite/api/TileObject.java`)

- `getId()` — game object id (see `gameval.ObjectID`)
- `getWorldLocation()`, `getLocalLocation()`
- `getX()`, `getY()`, `getZ()` — local coords
- `getPlane()`
- `getWorldView()`
- `getCanvasLocation()`, `getCanvasTilePoly()`, `getCanvasTextLocation()`,
  `getMinimapLocation()`, `getClickbox()` — render helpers
- `getHash()` — packed `(wv | id | wall | type | plane | sceneY | sceneX)`;
  type: 0 player, 1 NPC, 2 game object, 3 item, 4 world entity
- `getOpOverride(int)`, `isOpShown(int)` — menu metadata

Specific variants:

| Interface | What it represents | Extras |
|---|---|---|
| `GameObject` | Trees, anvils, chests, multi-tile objects | `sizeX()`, `sizeY()`, `getSceneMinLocation()`, `getSceneMaxLocation()`, `getOrientation()`, `getModelOrientation()`, `getConfig()` |
| `WallObject` | Walls on tile edges | `getOrientationA()`, `getOrientationB()` (bitfield 1=W 2=N 4=E 8=S 16=NW 32=NE 64=SE 128=SW), two renderables |
| `DecorativeObject` | Wall hangings, signs | `getXOffset()`, `getYOffset()` (+ `2` variants), two renderables |
| `GroundObject` | Flat-on-floor objects | `getConfig()`, one renderable |

### Resolving object names

`TileObject.getId()` gives a numeric id only. Use `Client.getObjectDefinition(int)`
(returns `ObjectComposition`) for the name:

```java
ObjectComposition comp = client.getObjectDefinition(gameObject.getId());
String name = comp.getName();                  // e.g. "Bank booth"
String[] actions = comp.getActions();          // ["Bank", "Examine", null, ...]
int sx = comp.getSizeX(), sy = comp.getSizeY();
```

Multi-loc objects (vary by varbit/varp) need an extra step:
`comp.getImpostorIds()` is non-null when the displayed form depends on game
state — call `comp.getImpostor()` to get the currently-shown variant. The cache
is `client.getObjectCompositionCache()`.

(For item names use `Client.getItemDefinition(int)` returning `ItemComposition`
— same pattern. For NPC names use `Client.getNpcDefinition(int)` returning
`NPCComposition`.)

## Nearby actors

### Players
- `client.getLocalPlayer()` — the logged-in player.
- `client.getPlayers()` (deprecated default) or
  `worldView.players()` (returns `IndexedObjectSet<? extends Player>`).
- `player.getWorldLocation()` and `getWorldArea()` (from `Actor`).
- `player.getName()`.

### NPCs
- `client.getNpcs()` / `worldView.npcs()`.
- Pets/followers: `client.getFollower()`.

### World entities (instanced sub-scenes)
- `worldView.worldEntities()`, `worldView.worldViews()` — for things like the
  Tempoross boat, ToA wrapper worlds, etc. Each has its own `WorldView` with
  independent base coords.

## WorldArea (`api/net/runelite/api/coords/WorldArea.java`)

A rectangular region on one plane: `(x, y, width, height, plane)` or
`(WorldPoint location, width, height)`.

Useful methods:

- `contains(WorldPoint)`, `contains2D(WorldPoint)` (ignores plane)
- `intersectsWith(WorldArea)`
- `distanceTo(WorldArea|WorldPoint)`, `distanceTo2D(...)`
- `isInMeleeDistance(WorldArea|WorldPoint)` — Chebyshev distance == 1
- `canTravelInDirection(WorldView, dx, dy[, Predicate<WorldPoint>])` — does
  full collision-flag walk; respects walls and an optional actor-blocking
  predicate. Returns false if `CollisionData` for the plane is null.
- `hasLineOfSightTo(WorldView, WorldArea|WorldPoint)` — Bresenham over
  `CollisionDataFlag.BLOCK_LINE_OF_SIGHT_*` flags. **Asymmetric** (A->B can
  succeed while B->A fails). Same plane only.
- `toWorldPoint()` — SW corner.
- `toWorldPointList()` — every tile (allocates `w*h` points).

Use this for "is the player inside the bank?", "how far am I from this NPC?",
"is the boss in melee range?" questions.

## Directions and angles

`Direction` enum: `NORTH | SOUTH | EAST | WEST`. Angle ranges per direction in
its javadoc.

`Angle` (`@Value` int wrapper):

- Range 0..2047. **0 = South, 512 = West, 1024 = North, 1536 = East.**
- `Angle.getNearestDirection()` — bucket into a `Direction`.

Most actor orientations and `GameObject.getOrientation()` use this 0..2047
JAU space; `getModelOrientation()` is normally 0 because models are baked
during scene load.

## World types (`api/net/runelite/api/WorldType.java`)

Enum + bitmask. Members (each is `(1 << bit)`):

- `MEMBERS` (1), `PVP` (4), `BOUNTY` (32), `PVP_ARENA` (64),
- `SKILL_TOTAL` (128), `QUEST_SPEEDRUNNING` (256),
- `HIGH_RISK` (1024), `LAST_MAN_STANDING` (16384),
- `BETA_WORLD` (1<<16), `LEGACY_ONLY` (1<<22), `EOC_ONLY` (1<<23),
- `NOSAVE_MODE` (1<<25), `TOURNAMENT_WORLD` (1<<26),
- `FRESH_START_WORLD` (1<<27), `DEADMAN` (1<<29), `SEASONAL` (1<<30).

Use:

```java
EnumSet<WorldType> wt = client.getWorldType();
boolean pvp = WorldType.isPvpWorld(wt);            // PVP or DEADMAN
boolean members = wt.contains(WorldType.MEMBERS);
boolean league = wt.contains(WorldType.SEASONAL);
```

There is **no F2P member**; "F2P" is `!wt.contains(MEMBERS)`.

`WorldType.fromMask(int)` / `WorldType.toMask(EnumSet)` for marshalling.
`client/net/runelite/client/util/WorldUtil.java` converts from the HTTP-API
flavour of `WorldType` (used by the world list service) into the runelite-api
flavour, if you ingest external world metadata.

## Quick recipes

Get the player's current region name (best-effort):

```java
LocalPoint lp = client.getLocalPlayer().getLocalLocation();
WorldPoint wp = WorldPoint.fromLocalInstance(client, lp);
int rid = wp.getRegionID();
String area = Optional.ofNullable(DiscordGameEventType.fromRegion(rid))
                      .map(DiscordGameEventType::getState)
                      .orElse("Region " + rid);
```

Enumerate every object on the player's tile:

```java
WorldView wv = client.getTopLevelWorldView();
LocalPoint lp = client.getLocalPlayer().getLocalLocation();
Tile t = wv.getScene().getTiles()[wv.getPlane()][lp.getSceneX()][lp.getSceneY()];
List<TileObject> objs = new ArrayList<>();
if (t.getGroundObject() != null) objs.add(t.getGroundObject());
if (t.getWallObject() != null)   objs.add(t.getWallObject());
if (t.getDecorativeObject() != null) objs.add(t.getDecorativeObject());
for (GameObject g : t.getGameObjects()) if (g != null) objs.add(g);
```

Find the nearest bank booth within N tiles (sketch):

```java
WorldPoint me = client.getLocalPlayer().getWorldLocation();
Scene scene = client.getScene();
Tile[][][] tiles = scene.getTiles();
GameObject best = null; int bestD = Integer.MAX_VALUE;
for (int x = 0; x < 104; x++) for (int y = 0; y < 104; y++) {
    Tile t = tiles[client.getPlane()][x][y];
    if (t == null) continue;
    for (GameObject g : t.getGameObjects()) {
        if (g == null) continue;
        ObjectComposition c = client.getObjectDefinition(g.getId());
        if (c == null) continue;
        if (!"Bank booth".equals(c.getName())) continue;
        int d = me.distanceTo(g.getWorldLocation());
        if (d < bestD) { best = g; bestD = d; }
    }
}
```

Check whether you are in the Wilderness (quick heuristic, since there's no
single region ID):

```java
WorldPoint wp = client.getLocalPlayer().getWorldLocation();
boolean wildy = wp.getY() >= 3520 && wp.getY() <= 3967 && wp.getPlane() == 0;
```

(For an exact match use `WorldArea` definitions or the in-game `VarbitID.IN_WILDERNESS`.)

## Gotchas

- **Plane != z.** Caves/dungeons are at higher world Y, plane 0. There's no
  negative plane. Surface buildings go 0..3.
- **LocalPoint dies on region change.** Never cache one across a loading zone.
- **Instances are deceptive.** Region IDs of an instance reflect the instance's
  *placed* coordinates, not the template area. Always use
  `WorldPoint.fromLocalInstance` before doing name lookups or comparisons.
- **`Scene.getTiles()` cells may be null** — tiles outside the loaded extent
  or unreachable interiors.
- **`GameObject[]` slots may be null** — a tile is allocated 5 game-object
  slots; iterate with a null guard.
- **`isInScene(Client,...)` deprecated** — prefer the `WorldView`/`Scene`
  overloads.
- **Multi-tile `GameObject.getWorldLocation()`** is the centre rounded SW —
  use `sizeX()`/`sizeY()` + `getSceneMinLocation()`/`getSceneMaxLocation()` for
  the full footprint.
- **Region collisions** — a single region ID can map to multiple gameplay
  contexts (NMZ vs KBD share 9033). Check plane and surrounding context.
- **No coordinate-to-name lookup is exhaustive.** The Discord plugin's table
  is the most comprehensive in-tree source (~250 named areas) but is missing
  sub-region detail (it'll say "Varrock" but not "Varrock west bank"). For
  bank-level granularity, build your own `WorldArea` table from wiki data.
