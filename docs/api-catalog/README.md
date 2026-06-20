# RuneLite API catalog

Curated references for the bits of RuneLite that matter for **osrs-llm-helper**.
Generated from `docs/runelite/sources/` by parallel research agents — when the
RuneLite version we depend on bumps, re-run the agents to refresh.

Each file is a focused reference, not exhaustive javadoc. Skim the headers, jump
to the table or section you need. When you're about to add a new tool, read the
relevant catalog first.

## Files

| File | Domain | What's in here |
| --- | --- | --- |
| [`events.md`](events.md) | Event bus | Every `@Subscribe`-able event grouped by category, with payload, fire condition, and LLM-helper use case. |
| [`game-state.md`](game-state.md) | Player / Client / Vars | What's readable from `Client`, `Player`, `NPC`, and which varbits/varplayers carry useful state. Has a "Quick wins" ranked list at the end. |
| [`combat.md`](combat.md) | Combat / prayer / magic | Reading combat state, prayers (and why `isPrayerActive` is deprecated), hitsplats, spec attack, slayer, spellbook, attack styles. |
| [`world.md`](world.md) | Location / scene | Coordinates (`WorldPoint`/`LocalPoint`), regions, scene/tile/object inspection, named locations (Discord plugin enum), `WorldArea`, `WorldType`. |
| [`market.md`](market.md) | Items / GE / pricing | `ItemManager`, `ItemComposition`, GE offers, item variant collapsing, equipment stats, wiki realtime price API. |
| [`output-channels.md`](output-channels.md) | Talking back | Chat messages, `Notifier`, overlays, infoboxes, tooltips, world-map points, hint arrows, sidebar panels, audio. |

## Conventions used in catalogs

- **Threading note** appears wherever it matters. Most game-state reads need the
  client thread; sidebar/panel updates need the Swing EDT; container snapshots
  in our `GameStateStore` are read off-thread (lock-free `AtomicReference`).
- **Injection availability** flagged on each class — most plugin services are
  `@Inject`-able singletons; a few (`TabManager.loadAllTabNames`, etc.) are
  package-private and need reflection.
- **Deprecations** called out — there's a lot of `Client.getNpcs` /
  `isPrayerActive` etc. that has newer replacements.

## Refresh

```sh
# 1. Re-download sources if RuneLite version changed (look at gradle cache)
# 2. Re-run the cataloging agents from the project root
```

The agents read `docs/runelite/sources/`. That directory is git-ignored — local
working copy only. The `api-catalog/` files themselves are tracked.

## Currently-exposed tools (cross-reference)

These MCP tools live in `src/main/kotlin/co/rowm/osrsllm/McpServerService.kt`
and back onto the APIs cataloged here:

| Tool | Backed by |
| --- | --- |
| `get_inventory`, `get_bank`, `get_equipment` | `ItemContainer` + `ItemManager` ([market.md](market.md)) |
| `get_stats` | `StatChanged` event + `client.getRealSkillLevel` ([events.md](events.md), [game-state.md](game-state.md)) |
| `get_player_state` | `Client` / `Player` + varbits ([game-state.md](game-state.md)) |
| `get_quests` | `Quest.getState()` on the client thread |
| `get_nearby_npcs` | `client.getNpcs()` + `WorldPoint` chebyshev ([world.md](world.md)) |
| `get_event_log` | `EventLogService` subscribing to `ChatMessage`, `HitsplatApplied`, `StatChanged`, `ActorDeath`, `GameStateChanged` ([events.md](events.md)) |
| `list_bank_tabs`, `get_bank_tab`, `create_bank_tab`, `remove_bank_tab`, `open_bank_tab` | `TagManager` + `TabManager` + `BankTagsService` |
| `wiki_search`, `wiki_page` | `oldschool.runescape.wiki/api.php` (no RuneLite dep) |
