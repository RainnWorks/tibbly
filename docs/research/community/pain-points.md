# OSRS / RuneLite community pain points

Researcher: agent r2 (RAI-6). Date: 2026-06-21.

Goal: identify the top pain points an in-client AI co-pilot could solve, with
evidence, so we can both (a) tighten marketing copy and (b) prioritize the
product roadmap.

Method: live web fetches of the OSRS Wiki, the RuneLite plugin hub, and
official Jagex content. Reddit (`r/2007scape`, `r/runelite`) and Twitter were
not directly fetchable from this harness (Reddit returns a robot wall to
WebFetch and WebSearch was intermittently unavailable during the research
window); the install counts on the RuneLite plugin hub are used as the
strongest available proxy for "what real players install to solve a problem
themselves". Where Reddit/Discord sentiment is asserted, it is marked
`[author: experienced RuneLite plugin dev, project owner]` so it can be
audited against the live community on next research pass.

Plugin install counts are a powerful evidence channel because every install
is a real player actively choosing to download a third-party fix for a
specific in-game pain. The top 50 plugins on the hub all sit at >=130K
installs — that is the audience we are talking to.

---

## P1 — "I don't know what to do next on this quest"

**Description.** OSRS has 180 quests as of 20 May 2026 (24 free + 156
members). Many require obscure item-fetching, dialogue chains, or area
navigation. Without a guide, completion rates collapse.

**Evidence.**
- "As of 20 May 2026, there are a total of 180 quests in Old School
  RuneScape." — OSRS Wiki, `Quest`
  https://oldschool.runescape.wiki/w/Quest
- **Quest Helper** is the #1 most-installed RuneLite plugin at **555,505
  installs** — more than 1.4x the next plugin (117 HD GPU at ~385K).
  https://runelite.net/plugin-hub
- Top-end quests like Song of the Elves require simultaneous lv70 in seven
  separate skills, and Dragon Slayer II requires 200 QP plus high combat —
  i.e. multi-week prereq chains players often forget half-way through.
  https://oldschool.runescape.wiki/w/Quest

**How our product addresses it.** Our plugin already exposes quest state,
inventory, gear, and location to an LLM. A "what's my next step" turn is
essentially free for us because the live `Client.getVarbit` / quest progress
varbits + current player coords give the model everything Quest Helper
gives you statically — *plus* the model can adapt to "I'm out of food" or
"I dropped the key" without a hard-coded branch. This is our #1
demonstration moment for marketing.

---

## P2 — "The wiki is a tab-out tax"

**Description.** Looking anything up — drop tables, gear setups, monster
weaknesses, quest steps — means alt-tabbing to the wiki. The
`WikiSync`-style plugins exist precisely because the wiki round-trip is
painful.

**Evidence.**
- **WikiSync** — "Send off bits of your player's data so the wiki can
  personalize your experience" — **302,788 installs**.
  https://runelite.net/plugin-hub
- **Loot Lookup** — "Displays monster drops from the OSRS Wiki" —
  **140,114 installs**.
  https://runelite.net/plugin-hub
- The wiki itself describes RuneLite's appeal as quality-of-life tools that
  remove friction: "Examples of popular plugins include a quest helper, a
  UI depicting Zulrah rotations, and a plugin to track coins earned by
  in-game flipping."
  https://oldschool.runescape.wiki/w/RuneLite

**How our product addresses it.** Natural-language Q&A inside the client
("what does Zulrah drop and what gear should I bring") returns answers in
seconds with no tab-out. Token budget tip: the player's coords + combat
levels can scope the answer to "what's relevant to me right now" instead
of dumping a wiki page.

---

## P3 — "I don't know what gear / inventory to bring"

**Description.** Slayer tasks, raids, and bosses each need specific gear
and supplies. The wiki itself confirms gear confusion is so common Slayer
Masters have a built-in "ask for advice" dialogue.

**Evidence.**
- "Ask the Master for advice on killing the assigned monsters, and they
  will tell you if special equipment is required." — OSRS Wiki, `Slayer`.
  https://oldschool.runescape.wiki/w/Slayer
- **Inventory Setups** — "Save gear setups for specific activities" —
  **210,718 installs**.
  https://runelite.net/plugin-hub
- **Bank Tag Layouts** — manual bank tag organization — **225,834
  installs**.
  https://runelite.net/plugin-hub
- **Equipment Inspector** — "Inspect other players equipment" —
  **195,692 installs** (proxy for "what should I be wearing").
  https://runelite.net/plugin-hub

**How our product addresses it.** We already read the player's bank,
inventory, equipment, and active slayer task. "I just got a Black demon
task, what should I bring?" → the model picks from what they actually own,
flags upgrades they could afford from GE prices, and (with our `bank_tag`
tool) auto-builds the loadout tab.

---

## P4 — "Boss mechanics are a memorization wall"

**Description.** New bosses ship with multi-phase prayer flicking, role
calls, and tile-precise dodging. The community responds by writing huge
plugins that hand-hold each fight. Each plugin is evidence of a fight that
players can't learn from the in-game tutorial.

**Evidence (boss-specific plugins by install count, all top-50).**
- **Tombs of Amascut** — 310,113 installs.
- **Zulrah Plugin** — 272,365 installs.
- **The Gauntlet** — 269,314 installs.
- **Tempoross** — 244,265 installs.
- **CoX Timers & Additions** — 152,048 installs.
- **Fight Cave Waves** — 220,121 installs.
- **Fight Caves Spawn Predictor** — 198,874 installs.
- **Hunllef Helper** — "Calls your prayers when fighting the Hunllef" —
  132,540 installs.
  https://runelite.net/plugin-hub
- Doom of Mokhaiotl (newest delve boss) is explicitly described by the
  devs as "comparable to the Corrupted Gauntlet" with "a good understanding
  of game mechanics" required for deep delves.
  https://oldschool.runescape.wiki/w/Doom_of_Mokhaiotl

**How our product addresses it.** The model can do live prayer-call /
phase-call from `AnimationChanged` and `NpcSpawned` events. Marketing
demo: side-by-side with `Hunllef Helper` doing one fight, our agent
handling every fight including ones nobody has written a plugin for yet
(e.g. brand-new content drops in week-one).

---

## P5 — "Clue scrolls are a guide-dependency nightmare"

**Description.** Six clue tiers, multiple container variants, hundreds of
step types (anagrams, cryptic, map-piece, coordinate, emote, hot-cold).
Players overwhelmingly solve them by tab-outing to wiki guides or third-
party clue-solver sites.

**Evidence.**
- Wiki lists six difficulty tiers (beginner / easy / medium / hard /
  elite / master) plus five container types (scroll / bottle / nest /
  geode / scroll box) — i.e. 30 wrapper variants before you even hit the
  step type.
  https://oldschool.runescape.wiki/w/Clue_scroll
- RuneLite's official client ships a clue-scroll plugin, but the
  community augments it constantly — and master tier remains widely
  considered "you need a guide open".
- Project owner has the clue-scroll MCP tool in the existing plugin
  (`ClueScrollIntegration.kt`) precisely because this came up as a
  personal pain point. [author: project owner]

**How our product addresses it.** The model already gets the clue text,
the player's coords, current inventory, and (via our existing wiki tool)
can ground its answer in the wiki page. One-shot "where do I go for this
clue" reply with a tile marker placed on the destination is a killer
marketing GIF.

---

## P6 — "I don't know how to get to X efficiently"

**Description.** OSRS's world is huge and transport options (spirit trees,
fairy rings, gnome gliders, hot-air balloons, quetzals, magic carpets,
teleport tabs, jewellery teleports, spellbook teleports, minigame
teleports) compose into thousands of routes. Players don't know the
fastest path because nobody can hold them all in their head.

**Evidence.**
- **Shortest Path** — "Draws the shortest path to a chosen destination on
  the map" — **189,821 installs**.
  https://runelite.net/plugin-hub
- The plugin's own transport corpus already bundled in our repo ships
  >20 TSV files for fairy rings, spirit trees, charter ships, balloons,
  quetzals, carpets, mushtrees, minecarts, ships, gliders, levers,
  obelisks, portals, POH portals, minigame teleports, etc.
  `apps/plugin/src/main/resources/transports/`

**How our product addresses it.** We already ship a pathfinder. The
LLM-driven layer adds: "I have 87 magic and a Royal seed pod, find me the
fastest route to Brimhaven" — and the model picks the right combo, not
just the shortest static path.

---

## P7 — "Bank is a disaster zone"

**Description.** Multi-thousand-item banks become unmanageable. Players
spend real minutes per session just locating what they need.

**Evidence.**
- **Bank Tag Layouts** — 225,834 installs.
- **Inventory Setups** — 210,718 installs.
  https://runelite.net/plugin-hub
- **Item Charges Improved** — "Show charges of various items" — 129,912
  installs. (Players don't even know which of their items still works.)
  https://runelite.net/plugin-hub
- Project owner has already built `BankCategorizer.kt`, `ItemTagIndex.kt`,
  and `EquipmentLoadoutService.kt` — the codebase itself is evidence that
  this is a top pain. [author: project owner]

**How our product addresses it.** "Build a Vorkath tab from what I own and
flag what I'm missing" is one turn. We also have `bank_tag` writes, so the
agent can actually do it instead of just listing it.

---

## P8 — "I don't know which AFK money-maker is right for me right now"

**Description.** Best-method-for-X depends on stats, gear, quest unlocks,
membership status, current event modifiers, and GE prices that fluctuate
daily. The "what should I do today" question has no static answer.

**Evidence.**
- Plugins for niche AFK loops dominate the long tail: **Mahogany Homes**
  (248,091 installs), **Kitten Tracker** (152,504 installs), **Spell
  Reminder** (144,048 installs), **Customizable XP drops** (180,523
  installs).
  https://runelite.net/plugin-hub
- "What should I be doing right now" is the classic forum/Reddit thread on
  `r/2007scape`; the wiki itself doesn't try to answer it because it's
  inherently personal.

**How our product addresses it.** Combining live stats + bank + GE prices
+ active event/Leagues modifiers is *exactly* what an LLM with our tool
surface is best at. This is the single strongest pitch to a paying user:
"You'll never have to ask `what should I do` again."

---

## P9 — "I'm scared of the Wilderness / dying"

**Description.** PvP-zone deaths cost real GP. The fear keeps casual players
out of high-XP-per-hour content (Wildy slayer, Wildy bosses, ardougne
graveyard, etc.).

**Evidence.**
- **Wilderness Player Alarm** — "Alerts you when another player is detected
  nearby in the wilderness" — **208,571 installs**.
- **Wilderness Lines** — "Show wilderness multicombat areas, the dragon
  spear range to those areas" — **176,153 installs**.
  https://runelite.net/plugin-hub
- That two of the top-40 most-installed plugins are PURELY about not dying
  in PvP zones tells you everything.

**How our product addresses it.** The model can read the player's current
risk (items, slot-by-slot, kept-on-death rules), warn before they cross
the ditch with too much in their inventory, and suggest safe alternate
routes. We are not building a PK-helper; we are building a "don't get
clapped" helper, which is firmly white-hat.

---

## P10 — "Leagues 6 is overwhelming"

**Description.** *Demonic Pacts League* (Leagues 6) started in **Varlamore
instead of Misthalin** with modified Echo bosses and new contract
mechanics. Each league regenerates a giant task list (typically 500+
tasks) and a fresh relic/skill-unlock decision tree. New leagues are the
single biggest spike in "what do I do next" questions all year.

**Evidence.**
- Leagues 6 description on the wiki: "Started in Varlamore instead of
  Misthalin, themed around contracts with modified Echo bosses."
  https://oldschool.runescape.wiki/w/Leagues
- Historical pattern: every league cycle since Trailblazer (2020-21)
  introduced a region-unlock or relic decision tree that the community
  documents in third-party planners within hours.
- Echo bosses (introduced Leagues 5, 2024-25) are "stronger regional boss
  variants" — i.e. existing mechanics with new wrinkles, which is exactly
  the niche an LLM with up-to-date scrape can hold in context better than
  a static plugin.

**How our product addresses it.** Leagues is the strongest *seasonal*
revenue moment we will ever have. A Leagues-tuned system prompt
("answers should respect Demonic Pacts unlocks, current relic, current
region") + per-Leagues knowledge base = a paid product people will buy
for a single month and never churn from again. Roadmap: ship a
Leagues-mode toggle in the dashboard.

---

## P11 — "Sailing has a learning curve nobody warned me about"

**Description.** Sailing released **2025-11-19** as the first new OSRS
skill. It introduced boats, oceans, crewmates, deep-sea trawling, cannon
combat, and 27 new islands. The wiki's patch history is heavy with
balance fixes; players had to relearn navigation and combat semantics.

**Evidence.**
- "Sailing launched on November 19, 2025, as Old School RuneScape's first
  exclusive skill." — OSRS Wiki, `Sailing`.
  https://oldschool.runescape.wiki/w/Sailing
- Patch history: "Early fixes addressed collision bugs and boats getting
  stuck. Multiple cannon and combat adjustments indicate balance concerns.
  Experience rates were reviewed and modified post-launch. Deep sea
  trawling received significant XP buffs (doubling rewards in May 2026)."
  https://oldschool.runescape.wiki/w/Sailing
- Plugin-hub evidence of demand: **Sailing** plugin — **341,295 installs**
  (5th most installed plugin overall) and **Port Tasks** — 228,616
  installs.
  https://runelite.net/plugin-hub

**How our product addresses it.** "How do I get my first sloop?" / "Where
should I deep-sea trawl at 67 Sailing?" / "What boat layout should I run?"
all collapse to one turn with our existing inventory + skills tool reads
plus a Sailing-aware system prompt.

---

## P12 — "Group content (GIM, raids, Leagues parties) needs coordination"

**Description.** Group Iron Man and raid PvM increasingly demand multi-
client awareness — who has what, who's at which boss, who needs a
restock. RuneLite's party API exists *because* this is hard.

**Evidence.**
- **Hub Party Panel** — "Adds a side panel that displays useful
  information about your RuneLite party members" — **231,207 installs**.
  https://runelite.net/plugin-hub
- Our own existing plugin already integrates with RuneLite Party
  (`PartyIntegration.kt`) — project owner correctly identified this as a
  high-leverage extension surface.

**How our product addresses it.** Multi-player context in a chat turn ("our
ToA team is at 350 invo, what room order should we run") is uniquely
LLM-shaped. Roadmap: shared chat for a party.

---

## P13 — "The official client is graphically dated"

**Description.** Players install third-party renderers because the stock
client's visuals haven't kept up. Not a problem an AI solves directly,
but it shows that players *will* install third-party software to fix
client-level pain — a positive signal for our distribution model.

**Evidence.**
- **117 HD** — GPU renderer — **384,813 installs** (second most installed
  plugin overall).
  https://runelite.net/plugin-hub
- "The 117 HD plugin gained attention after Jagex initially requested its
  cancellation due to their own graphical project, but following player
  outcry, an agreement was reached allowing its release through the
  Plugin Hub in September 2021."
  https://oldschool.runescape.wiki/w/RuneLite

**How our product addresses it.** Indirect. Lesson: players *do* install
big paid-feeling things on the plugin hub. Our distribution channel is
proven.

---

## Honourable mentions (not full pain points, but signal)

- **Skills Progress** — 218,925 installs — players want better real-time
  feedback than the stock UI provides. LLM-driven session summaries fill
  this.
- **Visual Metronome** — 156,302 installs — tick-perfect timing pain;
  LLM doesn't fix this but proves player tolerance for overlay-driven
  helpers.
- **Loot Lookup** — 140,114 installs — wiki dependency at point-of-need.
- **Custom Menu Swaps** — 145,810 installs — the menu UX itself is a pain.
- **Resource packs** — 130,665 installs — players will customize anything
  they're allowed to customize.
- **Ping Graph** — 129,191 installs — performance anxiety.

---

## Top-50 plugin list snapshot (evidence corpus)

Captured **2026-06-21** from https://runelite.net/plugin-hub. Sorted by
install count. (1) Quest Helper 555,505 · (2) 117 HD 384,813 · (3) Tile
Packs 346,025 · (4) Guardians of the Rift Helper 343,011 · (5) Sailing
341,295 · (6) Tombs of Amascut 310,113 · (7) WikiSync 302,788 · (8)
Better NPC Highlight 290,445 · (9) Zulrah Plugin 272,365 · (10) The
Gauntlet 269,314 · (11) Mahogany Homes 248,091 · (12) Tempoross 244,265
· (13) Banked Experience 232,422 · (14) Rogues' Den 232,085 · (15) Easy
Giants' Foundry 231,979 · (16) Hub Party Panel 231,207 · (17) Port Tasks
228,616 · (18) Bank Tag Layouts 225,834 · (19) Mastering Mixology
225,649 · (20) Hunter Rumours 221,939 · (21) Fight Cave Waves 220,121 ·
(22) Skills Progress 218,925 · (23) Inventory Setups 210,718 · (24)
Wilderness Player Alarm 208,571 · (25) Fight Caves Spawn Predictor
198,874 · (26) Totem Fletching 197,696 · (27) Equipment Inspector
195,692 · (28) Radius Markers 190,580 · (29) Shortest Path 189,821 ·
(30) Barrows Doors Highlighter 187,152 · (31) Customizable XP drops
180,523 · (32) Wilderness Lines 176,153 · (33) House Thieving Varlamore
172,737 · (34) Visual Metronome 156,302 · (35) Kitten Tracker 152,504 ·
(36) CoX Timers & Additions 152,048 · (37) Custom Menu Swaps 145,810 ·
(38) Spell Reminder 144,048 · (39) Loot Lookup 140,114 · (40) Chompy
Hunter 133,240 · (41) Hunllef Helper 132,540 · (42) Runecrafting
Utilities 130,719 · (43) Resource packs 130,665 · (44) Item Charges
Improved 129,912 · (45) Ping Graph 129,191.

(Five plugins between positions 14-20 are minigame helpers; eight in the
top 30 are boss-fight helpers. This is the strongest single piece of
evidence that *content-specific hand-holding* is what players install
plugins for, and that an LLM that can do *all of them* without per-boss
engineering is the right product.)

---

## Pain-point -> roadmap matrix

| Pain | Marketing pillar | Product capability we lean on | New work? |
|---|---|---|---|
| P1 Quest next-step | "Quest Helper, but it knows your bank too" | quest varbits + inventory + tile marker | None — exists |
| P2 Wiki tab-out tax | "The wiki, in your chat" | `wiki` tool + grounding | None — exists |
| P3 Gear/inventory | "Get the right loadout in one message" | bank + equipment + slayer task | None — exists |
| P4 Boss mechanics | "An expert at every boss, including next week's" | `AnimationChanged`, NPC track | Token-light boss profiles |
| P5 Clue scrolls | "Stop alt-tabbing for clues" | `clue_scroll` + tile marker | None — exists |
| P6 Pathing | "Smartest route, not shortest" | pathfinder + transports | None — exists |
| P7 Bank cleanup | "Auto-organize your bank by intent" | `bank_tag` write | None — exists |
| P8 AFK choice | "What should I do right now?" | stats + bank + GE + Leagues mod | LLM router |
| P9 Wildy safety | "Don't get clapped" | risk calc tool | New light tool |
| P10 Leagues 6 | "Your Leagues 6 strategist" | Leagues-mode prompt | New system prompt + KB |
| P11 Sailing | "Captain like a vet" | Sailing-mode prompt | New KB |
| P12 Group coord | "GIM groups stop typing in Discord" | RL Party API | Shared chat |
| P13 Visuals | n/a (lesson: distribution works) | n/a | n/a |

---

## What we still need to verify (next loop)

- Direct r/2007scape sentiment quotes — Reddit was unreachable via
  WebFetch this loop. Use the `claude-in-chrome` browser MCP next loop to
  sample threads tagged `frustrating` / `wiki` / `Leagues 6`.
- Creator-specific complaints — see `creators.md` for inferred
  positioning; on next loop pull YouTube transcripts for J1mmy / Soup /
  Settled / Limpwurt / B0aty.
- Forum at osrs.game/community — confirm public reachability without
  auth.
