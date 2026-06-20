# Items, Grand Exchange, pricing

Reference for the osrs-llm-helper plugin. Covers `ItemManager` (the workhorse we
already `@Inject`), `ItemComposition` (per-item metadata), Grand Exchange offer
state, item-variant collapsing, equipment stats, and external price APIs.

Source roots referenced below:
- `sources/client/net/runelite/client/game/` (ItemManager, ItemMapping, ItemStats, ItemEquipmentStats, ItemClient, ItemVariationMapping)
- `sources/api/net/runelite/api/` (ItemComposition, GrandExchangeOffer, GrandExchangeOfferState, Client)
- `sources/api/net/runelite/api/events/` (GrandExchangeOfferChanged, GrandExchangeSearched, PostItemComposition)

---

## ItemManager (the workhorse)

Singleton, inject directly. All methods are safe to call off-thread **except**
`getItemComposition` / `getItemStats` / anything that hits `getItemDefinition`
(those touch the client and must run on `ClientThread`). `getImage` schedules
itself on the client thread internally and is safe from any thread.

### Price lookup
- `int getItemPrice(int itemID)` - returns price in coins. Internally calls
  `getItemPriceWithSource(itemID, runeLiteConfig.useWikiItemPrices())`, so it
  honors the user's RuneLite setting for wiki vs Jagex price.
- `int getItemPriceWithSource(int itemID, boolean useWikiPrice)` - force one or
  the other. Behavior:
  - Coins (id `995`) -> 1, Platinum tokens -> 1000 (hard-coded shortcuts).
  - If item is noted, follows `linkedNoteId` to the unnoted form first.
  - If item is in `WORN_ITEMS` (worn graceful, skillcape, etc.), maps to the
    inventory variant.
  - If `ItemMapping.map(id)` returns non-null (Barrows-degraded, ornament-kit,
    corrupted Bounty Hunter, etc.), recursively sums `price * quantity` over the
    tradeable parts. Returns **0** when no price entry exists.
- `int getWikiPrice(ItemPrice ip)` - sanity-checked wiki price. Falls back to
  Jagex price if wiki <= 0 or if `wiki >= jagex * activePriceThreshold` (default
  5) and wiki > `lowPriceThreshold` (default 1000). Protects against
  manipulation on low-volume items.
- Source: the price map is loaded from
  `https://api.runelite.net/runelite-<ver>/item/prices.js` every 30 minutes (and
  again on login if stale). Each `ItemPrice` has Jagex GE price + wiki price.
  Not realtime - 1 hour staleness is normal.

### Composition
- `ItemComposition getItemComposition(int itemId)` - `@Nonnull`, never returns
  null. Delegates to `client.getItemDefinition(id)`. **MUST be called on the
  client thread** (use `clientThread.invokeLater` if not on it). See
  `ItemComposition` section below for fields.

### Search
- `List<ItemPrice> search(String itemName)` - substring (case-insensitive) match
  against the cached price list. Returns every item whose name contains the
  query. Empty list if the price cache hasn't loaded yet. Returns `ItemPrice`
  objects (id, name, Jagex price, wiki price) - not `ItemComposition`, so no
  examine / alch / members info.

### Canonicalization
- `int canonicalize(int itemID)` - collapse note -> unnoted, placeholder ->
  real, worn -> inventory. Use this before keying caches by item id so noted
  and unnoted variants share an entry. Does **not** apply `ItemMapping` (degrade
  states), only the simple 1:1 mappings.

### Equipment stats
- `@Nullable ItemStats getItemStats(int itemId)` - returns `null` for items
  with no name or for noted items. Stats are loaded once at startup from
  `runelite.static.base + /item/stats.ids.min.json`. See `ItemStats` /
  `ItemEquipmentStats` section.

### Sprites / images
- `AsyncBufferedImage getImage(int itemId)` - quantity 1, non-stackable.
- `AsyncBufferedImage getImage(int itemId, int quantity, boolean stackable)` -
  cached (LRU, 128 entries, 1h). May return a blank image if not on the client
  thread; pixels fill in later. For Swing UI use
  `AsyncBufferedImage.addTo(component)` so it repaints when ready.
- `BufferedImage getItemOutline(int itemId, int qty, Color outlineColor)` -
  outline-only render, cached separately. Synchronous - only safe on the client
  thread (it calls `client.createItemSprite` directly with no async wrapper).

### Gotchas
- `getItemPrice` returns `0` for untradeable items not in `ItemMapping`
  (quest-only items, holiday drops, etc.) - check for 0 before doing math.
- The price map is empty until the first HTTP fetch completes. Calls during
  client startup may return 0.
- `ItemManager` registers itself on the event bus in its constructor; do not
  re-register it.

---

## ItemComposition (`net.runelite.api.ItemComposition`)

Per-item template returned by `getItemComposition`. Extends `ParamHolder` (so
you can call `getIntValue(paramId)` / `getStringValue(paramId)` for cs2 params).

### Identity
- `int getId()` - the item id.
- `String getName()` - in-game name. **On F2P worlds, member items are suffixed
  with " (Members)"**. Strip this if you want a stable key.
- `String getMembersName()` - real name regardless of world type. Prefer this
  for logging / display when membership is unknown.
- `void setName(String)` - mutates the cached composition. Don't unless you're
  building a transmog feature.

### Note / placeholder / variant
- `int getNote()` - returns `799` if this id **is** a noted item, `-1`
  otherwise.
- `int getLinkedNoteId()` - the paired id. If `getNote() == 799`, this is the
  unnoted form; if `getNote() == -1`, this is the noted form (or -1 if the item
  cannot be noted).
- `int getPlaceholderTemplateId()` - `14401` if this id **is** a placeholder
  (bank empty slot), `-1` otherwise.
- `int getPlaceholderId()` - paired id, same convention as note.

### Pricing
- `int getPrice()` - **store price** (Jagex's general-store base value). Not GE
  price. Used to derive alch values.
- `int getHaPrice()` - high alchemy yield in coins. All items have one; not all
  items can actually be alched (need `isTradeable`/`isAlchable` semantics from
  the wiki - the API doesn't expose alchable directly).
- `Constants.HIGH_ALCHEMY_MULTIPLIER` (= 0.6f). Low alch is 0.4f. Useful only
  if you want to recompute from `getPrice()` instead of trusting `getHaPrice()`.

### Flags
- `boolean isMembers()` - members-only item.
- `boolean isStackable()` - true for runes/arrows/coins/notes.
- `boolean isTradeable()` - tradeable between players.
- `boolean isGeTradeable()` - listable on the Grand Exchange (stricter than
  `isTradeable` - bonds and a few items are tradeable but not GE-listable).

### Menu / interaction
- `String[] getInventoryActions()` - the right-click menu strings for this item
  in inventory (e.g. `["Drink", "Use", null, null, "Drop"]`). Length is always
  5; entries can be null.
- `String[][] getSubops()` - submenu strings keyed by op index (varrock teleport
  -> ["GE", "PVP arena", ...]).
- `int getShiftClickActionIndex()` / `setShiftClickActionIndex(int)` - which
  inventory action is bound to shift-click.

### Visual (skip unless doing custom rendering)
`getInventoryModel`/`setInventoryModel`, `getColorToReplace[With]`,
`getTextureToReplace[With]`, `getXan2d/Yan2d/Zan2d`, `getAmbient`, `getContrast`.
After mutating, flush `Client.getItemModelCache()` and `getItemSpriteCache()`.

### Important: where's `getExamine()`?
**There isn't one.** Examine text is not on the composition. It's only emitted
as `ChatMessage` of type `ChatMessageType.ITEM_EXAMINE` when the player clicks
Examine in-game. To capture it, subscribe to `ChatMessage` and correlate with
the last `MenuOptionClicked` for `MenuAction.EXAMINE_ITEM`. For arbitrary ids
without an in-game click, use the wiki API `/mapping` endpoint (below).

---

## Grand Exchange (player offers)

### Reading current offers
- `Client.getGrandExchangeOffers()` - returns `GrandExchangeOffer[]`, indexed by
  slot. Length is the slot count (8 for members, 3 for F2P - but the array is
  always full length; empty slots have `getState() == EMPTY`). Safe to call
  whenever logged in.

### `GrandExchangeOffer` (`net.runelite.api`)
- `int getItemId()` - 0 / unset when slot is empty.
- `int getPrice()` - price-per-item the offer was placed at (the user's bid).
- `int getTotalQuantity()` - the offer size.
- `int getQuantitySold()` - filled so far (despite the name, applies to both
  buy and sell).
- `int getSpent()` - total coins moved so far. For buys, coins paid out; for
  sells, coins received. Lets you compute average fill price as
  `getSpent() / getQuantitySold()` (matters when a buy partially fills below
  the bid).
- `GrandExchangeOfferState getState()`.

### `GrandExchangeOfferState` (enum)
- `EMPTY` - unused slot.
- `BUYING` / `SELLING` - in progress, not yet fully filled.
- `BOUGHT` / `SOLD` - completed.
- `CANCELLED_BUY` / `CANCELLED_SELL` - aborted by the player. (Note: there are
  two cancelled states, not a single `CANCELLED`.)

### Event: `GrandExchangeOfferChanged`
Fired whenever a slot's offer mutates (placed, partially filled, completed,
cancelled, or cleared on login).
- `GrandExchangeOffer getOffer()` - the new state.
- `int getSlot()` - which slot (0..7).
- **On login, this fires for every slot with `state == EMPTY` once before any
  real data arrives.** Always check `state != EMPTY` before treating it as a
  real event, or you'll log spurious "offer cleared" entries on every login.

### Event: `GrandExchangeSearched`
Fired when the user runs a GE search. Set `event.consume()` to suppress vanilla
search and inject your own results (used by plugins that want custom search).

### Event: `PostItemComposition`
Fired when an `ItemComposition` is first created (lazy, on first access). Use
to wholesale-rewrite items (transmog) before any other code sees them.

---

## Item variants

There are several flavors of "the same item":
1. **Noted vs unnoted** - separate ids, paired via `getLinkedNoteId()`.
2. **Placeholder** - the empty-bank-slot id, paired via `getPlaceholderId()`.
3. **Worn vs inventory** - some weight-reducing items have separate ids when
   equipped (graceful, skillcapes, hunter gear). Hardcoded in
   `ItemManager.WORN_ITEMS`.
4. **Degraded / charged variants** - barrows at 100/75/50/25/0, infernal
   pickaxe vs trailblazer reskin, dragon ornament kits, bounty hunter
   corrupted versions. Handled by `ItemMapping`.
5. **Cosmetic variants of the same gameplay item** - graceful house colors,
   trimmed skillcapes. Handled by `ItemVariationMapping`.

### `ItemMapping.map(int itemId)` -> `Collection<ItemMapping>` (nullable)
Given an untradeable / non-stable id, returns the tradeable equivalents you'd
have to buy to reproduce it. Each `ItemMapping` entry has:
- `getTradeableItem()` - id you'd buy on the GE.
- `getQuantity()` - how many (e.g. degraded items map to 1 of each component
  plus repair cost; coin-mapped items like Long bone -> 1000 coins).

Returns `null` if no mapping is registered (the common case - regular items).
This is what `ItemManager.getItemPrice` uses to price degraded barrows.

### `ItemVariationMapping.map(int itemId)` -> `int`
Cosmetic-variant collapse. Returns a canonical base id for items that have
recolors / skins (graceful Hosidius, max cape, trimmed skillcapes). Returns the
same id if no variation registered. Source data: bundled
`item_variations.json` resource.
- `ItemVariationMapping.getVariations(int baseId)` -> `Collection<Integer>` -
  returns every id that maps to `baseId` (including itself). Useful for "did
  the player obtain *any* graceful top variant" checks.

---

## Equipment stats

Loaded once at plugin-manager startup from a static JSON on
`runelite.static.base`. Cached in memory. Not present for non-equipable items.

### `ItemStats` (`@Value`)
- `boolean equipable`
- `double weight` - in kg. Worn weight-reducing items are reflected in this
  number (graceful is -3 kg etc.).
- `int geLimit` - Jagex's 4-hour GE buy limit. Use for "can I buy more right
  now" calculations.
- `ItemEquipmentStats equipment` - null when not equipable.

### `ItemEquipmentStats` (`@Value @Builder`)
- `int slot` - matches `EquipmentInventorySlot.getSlotIdx()` (0=HEAD, 1=CAPE,
  2=AMULET, 3=WEAPON, 4=BODY, 5=SHIELD, 7=LEGS, 9=GLOVES, 10=BOOTS, 13=RING,
  14=AMMO).
- `boolean isTwoHanded` (json `is2h`)
- Attack bonuses: `astab`, `aslash`, `acrush`, `amagic`, `arange`
- Defence bonuses: `dstab`, `dslash`, `dcrush`, `dmagic`, `drange`
- `int str` (melee strength), `int rstr` (ranged strength)
- `float mdmg` (magic damage %, stored as float 0..1.0+)
- `int prayer` (prayer bonus)
- `int aspeed` (attack speed in ticks, 4 = standard scim, 5 = whip, etc.)

Gotcha: if `getItemStats` returns null, the item either isn't equipable, is a
note, or the stats JSON failed to load (network outage at startup - it's only
fetched once and never retried).

---

## High alch math

```kotlin
val comp = itemManager.getItemComposition(itemId)
val ha = comp.haPrice                      // direct (preferred)
// equivalent: (comp.price * Constants.HIGH_ALCHEMY_MULTIPLIER).toInt()
val profit = ha - itemManager.getItemPrice(itemId) - natureRunePrice
```

`Constants.HIGH_ALCHEMY_MULTIPLIER = 0.6f`. Low alch is `0.4f` (no constant).
Some items have `haPrice > 0` but aren't actually alchable (untradeable quest
items) - the API doesn't expose an alchable flag, so fall back to wiki data
or `isTradeable` if you need to be strict.

---

## External: wiki real-time price API

When you need fresher / per-minute data than ItemManager's 30-minute cache:

- `https://prices.runescape.wiki/api/v1/osrs/latest` - latest insta-buy /
  insta-sell across all items. Response shape:
  ```json
  { "data": { "<itemId>": { "high": 12345, "highTime": 1700000000,
                            "low":  12000, "lowTime":  1700000000 } } }
  ```
- `.../latest?id=<itemId>` - same shape, single item.
- `.../5m` - 5-minute average buy / sell / volume.
- `.../1h` - 1-hour average.
- `.../timeseries?timestep=5m&id=<itemId>` - history series.
- `.../mapping` - id -> {name, examine, members, lowalch, highalch, limit,
  value, icon}. **This is the only built-in source of `examine` text outside
  the in-game examine action.**

Rules of the road (from the wiki API page):
- Set a descriptive `User-Agent` (project name + contact). Anonymous spammers
  get blocked.
- 100 requests/minute soft limit per IP.
- All prices are in coins; ids match Jagex / `ItemID`.

When to prefer wiki API over `ItemManager`:
- Flipping / margin features (need minute-fresh data).
- Examine text for arbitrary ids without firing an in-game Examine.
- Items not in RuneLite's price cache (rare new releases).

When to prefer `ItemManager`:
- Anything in-overlay / hot path (no network call).
- Compound items (barrows degraded, ornament-kitted) - `ItemMapping`
  recursion is already wired up.
- Respecting the user's wiki-vs-Jagex preference.
