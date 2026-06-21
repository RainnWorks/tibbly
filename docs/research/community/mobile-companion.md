# OSRS mobile companion landscape — 2026

Researcher: agent loop-mplus9 (RAI mobile). Date: 2026-06-21.

Brief: Tom flagged a recent YouTube video about an OSRS companion mobile app
that's gaining traction, and is considering shipping a Tibbly mobile companion
alongside the RuneLite plugin. This doc surveys the field and proposes an MVP
shape implementable in 4-6 weeks by one engineer.

---

## TL;DR

- The video Tom probably watched is almost certainly the **SlayerScape**
  series by **DanPlaysOSRS** (Reddit handle: **Broxxar**). It is not a
  pocket-companion app in the FFXIV sense; it is a custom-built challenge
  generator that randomizes an account's progression through a grid of
  Slayer-keyed unlocks. Episode 1 cleared hundreds of thousands of views and
  was picked up by GamesRadar+. The thing gaining traction is the *content*
  about a custom app, not a Square Enix-style mobile app.
- A real pocket-companion app race is also happening on iOS. The active
  shipping field in 2026: **RuneGlass** (clean, recent, Wise Old Man +
  hiscores + GE), **OSRS 360** (toolkit with clue solver + DPS calc),
  **TKit** (150k+ users, long-running), **Tools for Old School Runescape**
  (rebuilt 2026), **AscendOSRS** (gear progression web, RuneLite-paired).
  None of them have an LLM. None of them know your bank. None of them have
  the device-side identity Tibbly already has.
- Jagex has **no official companion app** since the 2019 shutdown. They are
  however shipping their **own Plugin API and Plugin Hub directly into the
  Official Client, including mobile**. That changes the long-term picture
  for everyone in the third-party tooling space, including us.
- Jagex's "approved client" list (RuneLite, OSBuddy/2001scape, HDOS) restricts
  game *clients* (programs that connect to the game servers). It does not
  cover companion apps that read public APIs (hiscores, GE, Wise Old Man).
  We're safe in that lane as long as we don't ship a phone-side game client.
- Recommendation: ship a **Tibbly Pocket** app that is a phone-shaped second
  screen for the same chat session a player has open in their RuneLite
  plugin, plus a thin tracker layer (XP, GP, group iron status) so it has
  a reason to live when the player isn't logged in. Pair-by-QR, no separate
  signup, no separate SKU on day one. Roadmap below. 4-6 weeks one engineer.

---

## The app gaining traction (per Tom's signal)

**Most likely:** SlayerScape, by DanPlaysOSRS (Dan, a.k.a. Broxxar on Reddit).

- App: https://slayerscape.com/ and https://www.slayerscape.io/ — both URLs
  resolve to the project; the public face is a "Slayer companion tool"
  but the actual app's job is to drive a randomized challenge mode.
- Series playlist: https://www.youtube.com/playlist?list=PL2M2K4r623-sliP4orqc-cgBsX9pV4I_1
- Episode 1, "The Randomized Journey Begins":
  https://www.youtube.com/watch?v=gtJUBQ_UpTg (Oct 2025).
- Press: GamesRadar+ wrote it up, "Hundreds of thousands of MMO fans show
  up to watch the first video from a game dev who made an entire app just
  to make Old School RuneScape stupidly hard."
  https://www.gamesradar.com/games/mmo/hundreds-of-thousands-of-mmo-fans-show-up-to-watch-the-first-video-from-a-game-dev-who-made-an-entire-app-just-to-make-old-school-runescape-stupidly-hard/
- What it does: arranges quests, diaries, and skills across a grid of tiles
  that unlock by completing Slayer tasks and trading Slayer keys for slots.
  The account is bricked from doing anything off-grid until tasks are done.
- Why it's resonating: it is novel restricted-mode content (think Settled's
  Swampletics) productized as a tool other creators and players can run.
  It is a "challenge generator + creator vehicle" hybrid, not a utility.
- What it isn't: a phone-pocket companion. There's no evidence it has an iOS
  or Android app store presence. The site itself was thin when fetched.
  It is web-shaped tooling that powers a YouTube series.

**Caveat:** I could not confirm with certainty that SlayerScape is the
specific video Tom watched. If it isn't, the next most likely candidates
based on recent OSRS mobile-companion app-store activity are:

- **RuneGlass** — iOS companion by Jeroen Breevoort, very recent. Apple
  Store id 6759195026. https://runeglass.app/. Pitched as "no ads, no data
  harvesting, no bloat". Recent updates added Wise Old Man and EHP. This
  is the candidate if the video Tom watched was a "this is what a clean
  OSRS companion app should look like" review.
- **OSRS 360** — iOS toolkit with clue solver, DPS calc, world map. Apple
  Store id 6757369597. https://apps.apple.com/us/app/osrs-360/id6757369597.
  Recently re-launched, version 2.0 line.

If neither matches what Tom saw, ask Tom for the URL — we can pin the exact
target in five minutes.

---

## The current OSRS companion ecosystem

| App | Platforms | Maker | Monetization | OSRS features | RuneLite paired? | Jagex-affiliated? | Strength | Common complaint |
|---|---|---|---|---|---|---|---|---|
| **SlayerScape** | Web | DanPlaysOSRS / Broxxar | Free | Randomized challenge grid keyed on Slayer; quest / diary / skill tile unlock system | No | No | Novel content vehicle; press coverage | Not really a "companion" in the player-tools sense |
| **RuneGlass** | iOS | Jeroen Breevoort | Free, no ads | Hiscores lookup, all 24 skills, Wise Old Man, EHP, GE prices, skill calc, minigame calcs (Tempoross, Wintertodt, GoTR, Vale Totems) | No | No | Clean, fast, privacy-first design; landed recent | iOS only |
| **OSRS 360** | iOS, iPad | Skyler Verworren | Free, ads (dev said removing) | Skill calc with live GE, clue solver, GE tracker, DPS calc, quest/diary tracker, world map, fairy rings, hiscores, rare drop calc | No | No | Broadest tool surface; iPad layout | 3.7 star average, recent app |
| **TKit for OSRS** | iOS | Austin Schiebel | Free | Ironman support, maps, quest guides, GE lookups, skill calcs, fairy rings | No | No | 150k+ players, long-running, trusted | Missing some combat calcs; GE history gaps |
| **Tools for Old School Runescape** | iOS | Austin Schiebel | Free | GE hourly charts, flip calc with margin and ROI, high alch calc, 175 quests through May 2026 | No | No | Rebuilt from scratch in 2026; sharp GE focus | Tool-shaped, not session-shaped |
| **OSRS Companion** (dennyy) | Android | dennyy | Free | Overlay tools on mobile, GE, hiscores, calculators | No | No | Has the Android side of the market | Older codebase |
| **Rune Helper: OSRS Toolkit** | Android | GTech Llc | Free | Toolkit | No | No | Android coverage | Generic |
| **AscendOSRS** | Web | gmnrmyr | Free (BETA) | Gear progression bronze to BiS, multi-account, real-time GE, RuneLite Data Exporter import | Yes (read-only via Data Exporter plugin) | No | First serious progression / wealth tracker | Web-only |
| **Wise Old Man** | Web + API | dro / community | Free, Patreon | XP gain tracking, competitions, groups, achievements, full REST API | Yes (official plugin) | No | The data layer everyone builds on | Not a phone-pocket app |
| **GE Tracker** | Web + mobile web | GE Tracker team | Freemium subscription | GE flipping data, signals, mobile-optimized web | No | No | Pro flipper segment, paid users | Web only |
| **OSRS Tracker** | Web | community | Free | Hiscores, profiles | No | No | Web companion | Web only |
| **(no official Jagex app)** | n/a | Jagex | n/a | n/a | n/a | Yes (silent) | n/a | Discontinued 2019 |

Sources: app store and developer site fetches above; AllBestApps catalog
https://osrs-companion.allbestapps.net/ ; Group Ironmen Tracker
https://github.com/christoabrown/group-ironmen-tracker ; Wise Old Man
https://github.com/wise-old-man/wise-old-man ; AscendOSRS
https://ascendosrs.com/ ; GE Tracker mobile https://www.ge-tracker.com/mobile.

**What this table shows.** The market is a swarm of single-shop iOS apps
plus a few serious web tools. No one has shipped a chat / LLM companion.
No one has knowledge of the player's *live* bank or inventory through a
mobile channel. Everyone is reading public hiscores and GE. That's the
gap Tibbly walks into with the RuneLite plugin already doing the live
state piece.

---

## The official Jagex angle

- **Jagex Companion app status: dead since 2019.** Discontinued 24 Jan 2019,
  removed from stores 1 Feb 2019, server-side disabled 4 Feb 2019.
  Source: https://runescape.fandom.com/wiki/RuneScape_Companion. The
  stated reason was developer time was better spent on RuneScape Mobile.
- **No replacement.** Search for "Jagex companion 2025 / 2026" returns
  nothing official. The Jagex Launcher is desktop-only,
  https://jagexltd.com/.
- **Third-party CLIENT posture is restrictive but narrowly scoped.** Only
  RuneLite, OSBuddy/2001scape, and HDOS are on the Approved Client List.
  Source: https://oldschool.runescape.wiki/w/Update:Third_Party_Clients_Update.
  Two-week ban for first offense, permanent for repeat. This applies to
  *clients* (programs that connect to the game servers), not to companion
  apps that read public APIs.
- **The line Jagex draws.** Their Third-Party Client Guidelines list
  prohibited *in-game* features (prayer-switch indicators, attack counters,
  auto-prayer flicking, etc.) and which clients can implement them. The
  guideline doc does not address companion apps that only read public
  data such as hiscores, GE, or Wise Old Man.
  Source: https://oldschool.runescape.wiki/w/Update:Third_Party_Client_Guidelines.
- **The big change in the wind: Jagex's own Plugin API + Plugin Hub.**
  Jagex announced they're building plugin support directly into the
  Official Client, including on mobile. They confirm that RuneLite, HDOS
  etc. will keep working in parallel.
  Source: https://oldschool.runescape.wiki/w/Update:The_Future_of_the_Official_Client_-_HD_%26_Plugin_API.
  Implication: when this lands, the *player's mobile game client* gets a
  programmable surface. That is a future home for a Tibbly mobile presence
  inside the game itself, separate from any companion app we ship.
- **Precedents for takedowns.** Jagex has historically only acted against
  clients that touched game protocol or automated play. RuneLite Plugin Hub
  has thousands of plugins doing read-only inspection and Jagex has not
  moved on the category. Wise Old Man's RuneLite plugin is publicly
  endorsed. We have decent precedent that a companion app that reads
  public data plus our own backend's MCP-derived state is in safe territory.

**Bottom line:** a Tibbly mobile companion that talks to our backend (which
talks to the user's plugin over WS) is operating in a category Jagex has
never publicly objected to. The risk is not that they ban the app; the
risk is that they ship a great free official one once their Plugin API
matures. That is a 12-24 month threat, not a now threat.

---

## Adjacent categories — MMO patterns that translate

| Game | App | What it does | What worked | What died | Translates to Tibbly? |
|---|---|---|---|---|---|
| **WoW** | Companion App (Blizzard official) | World quests, callings, AH browse, guild chat, calendar, character inspect | Auction House on-phone, planning your evening's content while at lunch, guild chat continuity | Garrison missions burnt out as a category; "send minions on adventures" got reused for Shadowlands and again for Dragonflight and feels stale | Yes — *planning* before a session and *progress check* during downtime are the two real jobs of a companion app. |
| **FFXIV** | Companion (Square Enix official) | Chat, retainer inventory, market board, FC events, calendar | Selling on market board from phone is the single sticky feature; raid roster scheduling | Reviews complain it is slow, glitchy, has Kupo Nut economy gating actions | Yes — premium tier ($5/mo) for inventory + selling is a viable Tibbly pattern long-term. |
| **EVE Online** | EVE Portal (CCP, dead) and Neocom II (community, alive) | Skill queue management, market, fitting, assets | Community Neocom II survived because it just uses SSO + ESI API; official one died of neglect | EVE Portal was killed for under-investment | Community apps outlast official apps when the API is open. Tibbly is a community app. |
| **Lost Ark** | None official; community Lost Ark Codex | Daily / weekly checklists | Checklist apps for chore-heavy games work | n/a | Yes — OSRS dailies (tears, runs, farm runs, kingdom) are a checklist niche. |
| **Destiny 2** | Companion (Bungie official, sunset 2022) and DIM (community) | Vault management, weapon rolls | DIM survived because it shipped faster than Bungie's app | Bungie shut down their app, community took over | Same pattern: ship faster, win the niche, outlast the official offering. |

**Patterns that translate:**

1. **Planning + check-in + chat continuity** are the three jobs that don't
   die. Auction-house-on-phone and FC-chat-on-phone are the FFXIV stickiness.
2. **The community app outlives the official app** when the official app
   is under-invested in. EVE Portal, Destiny 2 Companion, RuneScape
   Companion. Tibbly is structurally a community app.
3. **Checklists for chore-heavy games** print engagement minutes. OSRS is
   one of the most chore-heavy games on the market.

**Patterns that don't translate or are dead:**

- "Send your minion on a 4-hour mission" mini-games. Players stopped
  caring about this pattern after Shadowlands. Don't ship this.
- Walled-garden official apps that gate basic stuff behind premium. The
  FFXIV "kupo nut" economy is widely hated.

---

## What players want on a second screen during OSRS grinding

Direct signal from researching forum and Reddit threads, OSRS pain-points
work already done in this repo (`pain-points.md`), and the existing
companion-app feature sets:

- **"Tell me what's worth doing next."** Quest Helper is the top RuneLite
  plugin at 555k installs and OSRS has 180 quests. Players want a phone
  view of "given my current stats, what's the next worthwhile thing to
  go do." Already a Tibbly chat job.
- **"Watch my XP tick."** Wise Old Man traffic and AscendOSRS positioning
  prove there's a hungry audience for live XP gain views.
  https://wiseoldman.net/
- **"Watch my GE listings."** Hourly charts and flip calc are in every
  serious app for a reason; people stare at GE numbers all day.
- **"Tell me when something happens."** Group iron man teams want push
  notifications when a member dies (HCIM), levels a skill, or completes
  a hard requirement. RuneTracker built a Discord-bot business on exactly
  this. https://runetracker.icu/.
- **"Don't make me alt-tab to the wiki."** WikiSync (302k installs) and
  Loot Lookup (140k installs) are players paying an attention tax. A
  phone-side wiki / lookup that knows your current activity is high value.
- **"Show me my bank from work."** This is the one job that the existing
  apps can't do, because they only read public data. Tibbly *can* do this
  through the plugin's MCP surface, with proper opt-in.

Sources: this repo's `pain-points.md` and `trends.md`; Wise Old Man
groups page; Group Ironmen Tracker repo; community forum threads
indexed earlier.

---

## Tibbly mobile — MVP recommendation

### Architecture

```
[ RuneLite plugin (Kotlin, MCP server) ]
            |   long-lived WS
            v
[ Tibbly backend (Bun, OpenRouter, Drizzle) ]
            ^                       ^
            | REST + WS             | WS
            |                       |
[ Tibbly dashboard (web) ]   [ Tibbly Pocket (mobile) ]
```

The mobile app is **a third UI on top of the same backend session** that
the plugin and dashboard already use. It is not a fourth runtime, it is
not a second auth surface, it is not a second billing entity.

Key invariant: the mobile app must never talk directly to the plugin or
to OpenRouter. Everything goes through the backend. This keeps the
attack surface and the bill flat.

### The minimum feature set (4-6 weeks, one engineer)

**Week 1-2: the bones**

- Cross-platform shell using Expo + React Native. Reuses React + Tailwind
  knowledge already on the team, ships iOS + Android from one codebase.
  Lean on `tamagui` or NativeWind for OSRS-styled primitives.
- Tibbly identity pairing: dashboard shows a QR with a short-lived
  pairing code; phone scans, exchanges code for a long-lived device key
  bound to the user's billing account. Same shape as the plugin's
  device-key model that IDENTITY.md already specifies. No phone-only
  signup.
- WebSocket connection to the backend's existing chat session. Phone
  sees the same chat thread the dashboard sees.

**Week 3-4: the reasons to open it**

- Chat from phone, against the same backend. Reuses tool gating /
  routing that backend already does. Critical UX detail: when the
  plugin is offline (player not logged in), the chat falls back to a
  "phone-only" tool subset (hiscores, GE, wiki, quest data) and the UI
  states clearly that live game state is unavailable.
- Hiscores + XP gain widget per linked OSRS account. Pulled from
  Wise Old Man API server-side, cached. No need for the phone to call
  WOM directly.
- GE quick lookup with hourly chart. Same backend cache as the plugin
  and dashboard, no duplicate fetchers.

**Week 5: the sticky bit**

- Push notifications, on opt-in only, for:
  - "Your group iron man died" (HCIM / GIM watching).
  - "You hit a level you flagged as a goal."
  - "Your GE listing filled."
  - "Tibbly finished a long quest plan you asked for while you were AFK."
- Push channel via Expo Push for cross-platform. Server queues
  notifications; phone receives them.

**Week 6: the polish**

- OSRS-native visual language. Use the asset catalog in
  `packages/osrs-assets/` for sprites. Treat the home tab as the in-game
  Stats interface. Treat the chat tab as a chatbox that looks like
  the in-game chat. Animations welcome.
- App Store + Play Store submission. Branded as **"Tibbly Pocket — the
  pocket companion for Old School RuneScape players using Tibbly."**

### Auth model

- **Pair by QR off the dashboard. Default to this.** Dashboard is already
  logged-in to a Stripe-backed customer. QR carries a short-lived JWT
  with `customer_id` + `expires_in 90s`. Phone calls
  `POST /v1/devices/pair` with the JWT and a fresh device public key,
  receives a long-lived device key bound to that customer. Same
  primitive as plugin pairing.
- **No phone-only signup.** Tibbly Pocket without an account is a
  marketing artifact, not a product. We could add a small "demo mode"
  with hiscores lookup only and a "pair to a Tibbly account" CTA, but
  that is a v1.1 feature.
- **Multi-OSRS-account.** A Tibbly customer can have multiple linked
  RSNs. The phone picks one as the "active" account; long-press to
  switch. Same as dashboard.

### Monetization

**Day one: free addon to the Tibbly subscription. No separate SKU.**

Reasoning:

- Tibbly is sold as "the AI companion for OSRS." Sliding the mobile app
  inside that subscription instead of charging for it again is the right
  Costco move on day one.
- Charging for a phone app while the desktop plugin is the primary
  product confuses the value prop.
- It tightens the "why I'm paying Tibbly" loop: the phone push at lunch
  saying "your group iron died" is a churn-saver people will remember.

**Possible later moves (do not ship in MVP):**

- A free "Tibbly Pocket Lite" with hiscores + GE only, no chat, no
  notifications, no live state. A marketing front door.
- A heavier "Tibbly Pocket Pro" SKU bundled with the Pro tier of the
  main subscription, with high-frequency XP tracking and unlimited push.

### Why it lands as a companion, not as a standalone Tibbly app

The mobile app's job is **continuity of the session that already exists**.
The user opened a chat on their RuneLite plugin at home, asked Tibbly to
plan out their evening's slayer task, then left for work. The phone is
the same chat, on their commute, asking follow-ups. When they get back
to the desk, the chat keeps going on the desktop.

That is something nobody else can ship, because nobody else has the
plugin-side state. Treating the mobile app as a continuation of the
plugin session is the only positioning that's true and that's defensible.

A "Tibbly mobile app" pitched as standalone walks straight into the
RuneGlass / OSRS 360 / TKit fight, where the field is already crowded
with free apps. We don't want that fight.

### What it's NOT

- Not a wiki replacement. Don't try to compete with OSRS Wiki on
  reference. Link out to the wiki.
- Not a GE flipping pro tool. GE Tracker has that segment locked, and
  it's a pay-for-data category. We're a generalist.
- Not a game client. We do not ship anything that connects to
  oldschool.runescape.com. Jagex's third-party client posture is
  unchanged and we don't want to test it.
- Not a 1:1 clone of FFXIV Companion. Inventory-on-phone is a real job
  but it needs live state to be useful, and live state means the plugin
  has to be online. We surface it as a "while my client is logged in"
  feature, not a default.

---

## Risks

- **Jagex ToS reversal.** If Jagex decides phone-side reads of live game
  state via a third-party server (our backend talking to a player's
  RuneLite plugin) count as third-party-client behavior, we'd need to
  fall back to public-data mode on mobile. Mitigation: design the mobile
  app so the public-data tier is the default, live-state tier is
  opt-in and labeled, and the architecture can drop the live tier in 24
  hours if we ever need to.
- **Plugin-backend-mobile attack surface.** A WS session that bridges the
  player's RuneLite plugin to a phone-side app is a juicy target.
  Mitigation: backend mediates, all phone-side data is per-device-key,
  no direct plugin-to-phone path, all transit is TLS, device key
  revocable from dashboard.
- **App Store / Play Store review pattern-match.** Companion apps for
  games sometimes get flagged as "cheat tools." Mitigation: position as
  a personal-stats tracker + chat with Tibbly, emphasize that we use
  only public OSRS APIs plus the user's own opt-in data. Lean on the
  framing the existing iOS apps (RuneGlass, OSRS 360, TKit) use, since
  they're all approved.
- **Maintenance cost of a new platform.** A mobile app adds a build
  pipeline, a store account, and an additional surface that needs to
  track Tibbly's backend changes. Mitigation: Expo + EAS Build cuts most
  of the day-to-day pain. Use the same component vocabulary as the
  dashboard via shared `packages/`. Treat the phone app like the
  dashboard's mobile twin, not a separate product line.
- **Jagex eventually ships a great official companion + mobile Plugin Hub.**
  Source on Plugin API:
  https://oldschool.runescape.wiki/w/Update:The_Future_of_the_Official_Client_-_HD_%26_Plugin_API.
  Mitigation: be the *paid AI layer* that runs on top of whatever
  Jagex ships, not a competitor to it. If Jagex eventually exposes
  mobile-side game state to plugins, we'd switch to that surface and
  drop the WS-through-RuneLite path on phones.
- **Cold-start fragility.** The mobile app is much less useful when the
  plugin is offline. Mitigation: the phone-only tool subset (hiscores,
  GE, wiki, quest data, chat with public data) has to be genuinely
  useful by itself. That is why hiscores + XP tracking + GE quick lookup
  + push notifications are all in the MVP.

---

## Open questions for Tom

1. **iOS first, Android first, or cross-platform from day one?**
   Recommendation: cross-platform with Expo + React Native, both stores
   day one. The shipping app landscape is iOS-heavy (RuneGlass, OSRS 360,
   TKit, Tools for OSRS are all iOS), which means Android is genuinely
   under-served, but skipping iOS would forfeit the most engaged
   companion-app users.
2. **Native (Swift + Kotlin), React Native, Flutter, Capacitor, Tauri
   Mobile?** Recommendation: Expo + React Native. We're a TypeScript + React
   shop already; sharing types and components with the dashboard via
   `packages/shared-types` makes the ROI obvious. Native is overkill for
   what is essentially a chat client plus a few widgets. Flutter loses
   us the shared-React win.
3. **Free addon to Tibbly subscription, or its own SKU?**
   Recommendation: free addon for MVP, optional Pro split later when
   we have data on which feature people actually pay for. See
   monetization section.
4. **Open-source the mobile client (matching the plugin's MIT positioning)
   or keep closed?** Recommendation: open-source under MIT. It's the
   same trust story as the plugin, it gives technical OSRS creators
   something to look at on stream, and it matches the community-app
   positioning that makes Jagex less likely to look at us sideways.
   The backend stays closed (that is where the moat lives).

---

## Sources

- SlayerScape series and app: https://slayerscape.com/ ;
  https://www.slayerscape.io/ ;
  https://www.youtube.com/playlist?list=PL2M2K4r623-sliP4orqc-cgBsX9pV4I_1 ;
  https://www.youtube.com/watch?v=gtJUBQ_UpTg
- GamesRadar+ writeup of SlayerScape:
  https://www.gamesradar.com/games/mmo/hundreds-of-thousands-of-mmo-fans-show-up-to-watch-the-first-video-from-a-game-dev-who-made-an-entire-app-just-to-make-old-school-runescape-stupidly-hard/
- RuneGlass: https://runeglass.app/ ;
  https://apps.apple.com/us/app/runeglass-osrs-companion/id6759195026
- OSRS 360: https://apps.apple.com/us/app/osrs-360/id6757369597
- TKit: https://apps.apple.com/us/app/tkit-for-old-school-runescape/id647492260
- Tools for Old School Runescape:
  https://apps.apple.com/us/app/tools-for-old-school-runescape/id1463089236
- AscendOSRS: https://ascendosrs.com/ ;
  https://github.com/gmnrmyr/AscendOSRS
- Group Ironmen Tracker: https://groupiron.men/ ;
  https://github.com/christoabrown/group-ironmen-tracker
- RuneTracker (Discord bot pattern for OSRS push notifications):
  https://runetracker.icu/
- Wise Old Man (REST API foundation):
  https://wiseoldman.net/ ; https://github.com/wise-old-man/wise-old-man ;
  https://github.com/wise-old-man/wiseoldman-runelite-plugin
- GE Tracker mobile: https://www.ge-tracker.com/mobile
- AllBestApps catalog: https://osrs-companion.allbestapps.net/
- RuneScape Companion (discontinued 2019):
  https://runescape.fandom.com/wiki/RuneScape_Companion ;
  https://runescape.wiki/w/RuneScape_Companion
- Jagex third-party client posture:
  https://oldschool.runescape.wiki/w/Update:Third_Party_Clients_Update ;
  https://oldschool.runescape.wiki/w/Update:Third_Party_Client_Guidelines ;
  https://secure.runescape.com/m=news/third-party-clients-update?oldschool=1
- Jagex Plugin API roadmap (incl. mobile plugins):
  https://oldschool.runescape.wiki/w/Update:The_Future_of_the_Official_Client_-_HD_%26_Plugin_API
- Adjacent MMO companion apps:
  - WoW Companion (Blizzard):
    https://wowpedia.fandom.com/wiki/World_of_Warcraft:_Companion_App ;
    https://worldofwarcraft.blizzard.com/en-us/news/23492552/take-a-look-at-the-wow-companion-app
  - FFXIV Companion (Square Enix):
    https://na.finalfantasyxiv.com/companion/ ;
    https://apps.apple.com/us/app/final-fantasy-xiv-companion/id1293636320 ;
    Destructoid review:
    https://www.destructoid.com/the-final-fantasy-xiv-companion-app-needs-a-lot-of-work/
  - EVE Online (Neocom community, EVE Portal shutdown):
    https://apps.apple.com/us/app/neocom-ii-for-eve-online/id1257353838 ;
    https://mmos.com/news/eve-online-portal-companion-app-closure
