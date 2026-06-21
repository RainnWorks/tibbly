# RuneLite API depth scan — one-page summary

> Headline output of RAI-5. Long-form is in [`catalog.md`](./catalog.md).
> Resolves [`docs/agents/GAPS.md`](../../agents/GAPS.md) A7.

## TL;DR

We already register **73** MCP tools (CLAUDE.md says 72 — the 73rd is
`enable_tools`, the gating meta-tool). The catalog enumerates **100+**
candidate tools — every entry sourced from the RuneLite API or an
existing community plugin's varbit mapping. Tier 0 below is the next
five we should ship.

## If we only ship five new tools, ship these five

| Rank | Tool | Family | API surface | Marketing line |
|---|---|---|---|---|
| 1 | `get_account_identity` | CORE | `Client.localPlayer.name` + `getAccountType()` + `getWorld()` + `getWorldType()` | *"Tibbly never confuses your main with your iron."* |
| 2 | `get_raid_layout` | **new RAIDS** | `Varbits.IN_RAID` + `TOA_RAID_LEVEL` + `Client.getMapRegions()` | *"Tibbly knows you're at Verzik phase 3."* |
| 3 | `get_target_projectiles` | COMBAT | `Client.getProjectiles()` (Deque\<Projectile\>) | *"Tibbly sees the projectile leaving Akkha's hands and calls the prayer flick."* |
| 4 | `get_farming_state` | **new FARMING** | farming patch varbits via `Client.getVarbitValue(int)` | *"Tibbly tracks all 24 herb patches across worlds and pings you when ranarrs are ready."* |
| 5 | `get_active_prayers` | COMBAT | `Varbits.PRAYER_*` + `Client.getBoostedSkillLevel(Skill.PRAYER)` | *"Tibbly sees you're on Piety with 12 points left and tells you to switch to Augury."* |

Each is one or two lines of Kotlin (full sketches in §3 of
`catalog.md`). Cost: ~150–600 tokens of JSON schema per tool. The 600
is `get_farming_state` and is mitigated by ready-only chunking (§7).

## Where the catalog grew from

| Source surface | Existing tools | Proposed Tier-1 | Proposed Tier-2 |
|---|---|---|---|
| Account / world state | 0 | 7 | 0 |
| Varbits + VarPlayers | 8 (via combat/quest/diary) | 13 | 5 |
| Combat depth | 8 | 10 | 2 |
| Raids / instanced | 0 | 7 | 2 |
| GIM / clan / friends | 1 (`get_party`) | 7 | 0 |
| Leagues | 0 | 4 | 0 |
| Music + ambient | 0 | 4 | 0 |
| World map / minimap | 5 (NAV) | 5 | 0 |
| NPC dialog | 0 | 4 | 0 |
| Farming / Hunter / etc | 0 | 5 | 4 |
| Camera + rendering | 0 | 4 | 2 |
| Walker / pathfinding | 5 (NAV) | 4 | 0 |
| Widgets | 2 (`list_open_interfaces`, `read_interface`) | 4 | 1 |
| Misc / minigames | many | — | 8 |

Existing total: **73** registered tools. Proposed Tier-1: **64** new.
Tier-2: **24** parking-lot. Combined upper bound on the family-gated
catalog: ~140 tools — still well under the multi-thousand-tool tail
that bloats other agent products, and the per-turn gated surface stays
≤1.5K tokens because of `ContextRouter` family selection.

## Recommended `ToolFamily.kt` changes

Add: `RAIDS`, `LEAGUES`, `FARMING`, `APPEARANCE`, `AMBIENT`. Fold:
`QUEST_ITEMS` → `QUEST`, `FISHING` → `SKILLS`. Rename: `PARTY` →
`SOCIAL` to absorb clan / friends / GIM. Re-order the enum (no wire-
format change) — see `catalog.md` §6.

## Adjacent improvements found

1. **CLAUDE.md's "72 MCP tools" is off by one** — `ToolRegistry.kt` has
   73 entries because the meta-tool `enable_tools` is registered there.
   Cleanup: either drop `enable_tools` from the per-tool token math or
   update CLAUDE.md. We left CLAUDE.md alone in this PR; flagged for
   the next loop.
2. **`QUEST_ITEMS` is a single-tool family** — fold into `QUEST`.
3. **`FISHING` is a single-tool family** — fold into `SKILLS`.

## What this unblocks

- **RAI-25 family-tag refinement** — `ContextRouter.kt`'s family-gate
  keyword sets can now be informed by the §4.* family proposals rather
  than guessed. GAPS.md A7 marked resolved.
- **M1 token-budget math** — the catalog gives a defensible upper bound
  for the gated-surface token cost (≤1.5K on first turn).
- **Marketing differentiation** — Tier 0 #2–#5 are demoable wins for
  the marketing site's "what Tibbly knows that other tools don't" pillar.

## Links

- [`catalog.md`](./catalog.md) — long-form (100+ entries, source
  citations, family proposals).
- [`docs/architecture/TOOL_ECONOMY.md`](../../architecture/TOOL_ECONOMY.md) — the gating contract this catalog feeds into.
- [`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolFamily.kt`](../../../apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolFamily.kt)
- [`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolRegistry.kt`](../../../apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolRegistry.kt)
