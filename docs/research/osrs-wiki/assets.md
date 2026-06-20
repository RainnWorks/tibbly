# OSRS visual asset catalog

> Owner: R3 · Issue: RAI-7 · Updated: 2026-06-21
> See licensing.md. RuneLite (BSD-2) is our primary source. OSRS Wiki is research-only.

Categories:
- cleared for paid SaaS use (RuneLite BSD-2, RuneStar CC0): GREEN
- reference-only (legally risky to re-host): YELLOW
- do not touch (NC, ARR): RED

## GREEN — use in production

### RuneLite skill icons (26 PNGs, BSD-2)

Mirror base: https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources/skill_icons/
Cached: packages/osrs-assets/skill_icons/ and skill_icons_small/

Files: attack, strength, defence, ranged, prayer, magic, runecraft,
construction, hitpoints, agility, herblore, thieving, crafting, fletching,
slayer, hunter, mining, smithing, fishing, cooking, firemaking, woodcutting,
farming, sailing, combat (summary), overall — 26 total.

### RuneLite plugin icons (BSD-2)

Path prefix: runelite-client/src/main/resources/net/runelite/client/plugins/

| Set | Folder | Notes |
|---|---|---|
| Teleport icons (50+) | worldmap/ | varrock_, camelot_, ardougne_, barrows_, digsite_pendant_ |
| Skill capes | worldmap/ | attack_cape_icon.png etc — level-99 CTA |
| Altar icons | worldmap/ | air_altar_, blood_altar_, astral_altar_ |
| Cluescroll | cluescrolls/ | emote.png |
| Prayer overlay | prayer/ | back.png, front.png |
| Time tracker UI | timetracking/ | start_, pause_, lap_, loop_, watch, arrow_right |
| Info panel | info/ | discord_, github_, patreon_, wiki_, info_ |
| Skill calculator | skillcalculator/ | calc.png |

Full enumeration:
```bash
gh api repos/runelite/runelite/git/trees/master?recursive=1 \
  | jq -r '.tree[] | select(.path|startswith("runelite-client/src/main/resources")) | select(.path|endswith(".png")) | .path'
```
~500 PNGs, all BSD-2.

### RuneLite UI chrome (BSD-2)

Path: runelite-client/src/main/resources/net/runelite/client/ui/
- open.png, open_rs.png — open affordances
- runelite_128.png, runelite_16.png — RuneLite logo (compatible-with badge)
- runelite_splash.png — splash background (hero inspiration)

### RuneStar fonts (CC0)

Repo: github.com/RuneStar/fonts
Cached: packages/osrs-assets/fonts/
- runescape.ttf — body display
- runescape_bold.ttf — headings
- runescape_small.ttf — chat/captions

Load via @font-face. CC0 = no attribution required; credit voluntarily.

### Our re-illustrations (TBD)

Commission 4 hero illustrations (chatbox character, inventory grid, world-map
waypoint, level-up flame) in pixel style. Original IP, fully owned.

## YELLOW — reference only, DO NOT re-host

### Wise Old Man asset inventory (MIT code, Jagex IP)

Path: wise-old-man/wise-old-man/app/public/img/
- metrics/ — 114 PNGs: boss icons (Zulrah, Vorkath, Nex, TOA, TOB, COX,
  Cerberus, Hydra, Demonic Gorillas, etc.) plus skill icons.
- metrics_small/ — 114 small variants.
- flags/ — 253 country flags (these ARE safe; use flag-icons npm instead).
- player_types/ — hardcore, ironman, regular, ultimate, unknown (HCIM/UIM
  crowns are Jagex IP).
- backgrounds/, group_roles/.

Inventory map only. Find RuneLite equivalent, commission, or skip.

### OldSchoolBot data tree (MIT code, Jagex IP)

Path: oldschoolgg/oldschoolbot/data/
- bso/ — fan-original alt-universe; closer to safe.
- osb/ — real-OSRS; Jagex IP.
- misc/ — mixed.

Useful for drop-table data, monster stats, item IDs. Asset images unsafe.

### osrsbox-db (GPL-3.0)

Repo: osrsbox/osrsbox-db. 30K+ item icons. GPL-3.0 viral + Jagex IP. Avoid.

## RED — do not embed

### OSRS Wiki (oldschool.runescape.wiki)

License: CC-BY-NC-SA 3.0 (NonCommercial).

Contains but cannot use: skill capes, master capes, quest reward art, monster
art, quest cinematics, equipment infoboxes (every weapon/armour), NPC
portraits, world map tiles, item icons (full inventory).

Source via RuneLite or commission instead.

### Jagex press kit / marketing

License: All rights reserved unless press-kit terms grant editorial use.
Do not use as marketing imagery without permission.

## Quick-reference for marketing/dashboard agents

1. Hero background: RuneLite splash + gradient OR commissioned hero art.
2. Skill grid showcase: 26 icons from packages/osrs-assets/skill_icons/.
3. Pricing tier badges: BSD cape icons from RuneLite worldmap/.
4. Display font: runescape_bold.ttf (CC0) headings; system font body.
5. Feature icons: RuneLite info plugin set.
6. UI chrome: RuneLite UI dir or hand-roll Tailwind.

## Categorisation summary

- Skill icons: 26 (RuneLite) + 26 small + 114 (WOM ref) = 166 catalogued
- Teleport / map icons: 50+ in RuneLite worldmap/
- UI chrome / buttons: 12 timetracking, 9 ui/
- Info / social icons: 5 in RuneLite info/
- Player-type / role icons: 5 WOM, 4 WOM group_roles/
- Fonts: 3 TTFs (RuneStar)
- Flags (incidental): 253 in WOM

Total: 250+ assets across 7 categories. Acceptance met.
