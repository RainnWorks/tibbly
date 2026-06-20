# RuneLite Plugin Hub — Policy Summary

> **Authority:** This document distills the rules that govern whether our plugin
> can ship via the official RuneLite Plugin Hub. Hub distribution is mission
> critical — every other channel is a non-starter for OSRS plugin reach.
>
> **Re-read before any code change that touches network egress, data exposure,
> automation, or in-game UI manipulation.**

## Primary sources

- RuneLite Plugin Hub README — https://github.com/runelite/plugin-hub
- "Rejected or Rolled-Back Features" wiki — https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- "Information about the Plugin Hub" wiki — https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub
- "Plugin takeover policy" wiki — https://github.com/runelite/runelite/wiki/Plugin-takeover-policy
- Jagex third-party client guidelines (linked from plugin-hub README) — https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1 (403 to scrapers; quoted indirectly via the RuneLite hub README + RL wiki)
- Reference rejection: PR #11453 "Add OSRS MCP plugin" — https://github.com/runelite/plugin-hub/pull/11453 (the same author archetype as us; rejected May 2026)
- Reference rejection / extended review: PR #7459 "RuneGPT Submission" — https://github.com/runelite/plugin-hub/pull/7459

## Three review criteria (RuneLite verbatim)

A submission must pass all three. Maintainers state explicitly:
> "If it is difficult for us to ensure the plugin isn't against the rules we will not merge it."
> — https://github.com/runelite/plugin-hub README

1. **Not malicious** — no credential theft, no covert exfiltration, no RAT-like
   behaviour, no obfuscation that hides what the plugin does.
2. **Does not break Jagex's rules** for third-party clients — see the
   Jagex guidelines linked from the plugin-hub README.
3. **Not a previously rejected or rolled-back feature** — the
   "Rejected or Rolled-Back Features" wiki page is the canonical list.

## ALLOWED

These categories are explicitly permitted by precedent.

- **Quality-of-life overlays** — tile markers, highlighted NPCs/objects,
  fixed-position info panels, custom HUDs, world-map annotations.
- **Stat / progress sync to a third-party site, opt-in** — Wise Old Man,
  WikiSync, RuneProfile, Group Ironmen Tracker, Collection Log Luck, all
  ship with the same `warning=` field in their manifest disclosing the data
  egress in one short sentence.
- **Discord / webhook outbound notifications** — Dink, Discord Loot Logger.
- **Bring-your-own-key LLM connectors that run locally** — precedent is
  RuneGPT (PR #7459); the maintainers required (a) the `warning=` line
  describing the data sent over HTTP, (b) no `ProcessBuilder` usage, (c)
  no shared thread-pool blocking. Conditionally permitted, never bulk-rejected.
- **Local-network MCP/HTTP exposure on `127.0.0.1`** — narrowly. PR #11453
  shows the bar: data must not include user-identifying information by
  default, and even loopback HTTP exposure of player state can be rejected
  under the "exposes player information over HTTP" rule (see Forbidden).
- **Downloading data files at runtime** (model weights, lookup tables) — explicitly
  allowed by maintainer comment on PR #12259 (RuneSpeak), as long as it's
  data, not code.
- **Per-user opt-in warning dialogs** before sending data — strongly encouraged.
- **Multiple OSRS accounts tied to one user** — no rule against this; the
  player chooses what RSN data to bind.

## BORDERLINE (must justify in PR description, must include `warning=`)

These will pass review if and only if disclosure, scope limits, and opt-in are
all in place. Default the design to the most restrictive version.

- **Sending player data to a remote server we operate.** Precedent:
  player-stats-sync (#11223 merged) — only sends RSN + skill levels + XP +
  combat level. *Bank, inventory, equipment, looting bag were removed
  before merge.* This is the line.
- **Cloud-relay / tunnels / dynamic networking** — PR #11453 maintainer
  raiyni: *"What is the point of this cloud relay? I don't think it's a
  good idea to be allowing plugins to establish their own ssh tunnels and
  exposing clients to an unknown service like this."* And later:
  *"We generally don't allow process execution and generating ssh keys
  and tunnels for users isn't going to be allowed I'd imagine."* — avoid.
- **Plugins that expose an HTTP API for external consumption** — even
  loopback. The "OSRS MCP plugin" was rejected precisely because *"Plugins
  which expose player information over HTTP"* is on the rolled-back list.
  Our design must NOT ship a localhost HTTP endpoint exposing game state.
  Replace with **outbound-only WebSocket** to a server we control.
- **Plugins that store credentials** — only allowed via vetted password
  managers (Bitwarden), never direct storage. We sidestep this by not
  asking for OSRS credentials at all.
- **Runtime library/code download** — explicitly forbidden ("We are not
  going to allow you to download libraries at run time" — PR #12259).
  Data downloads OK; native libraries / executable code, no.
- **Paid LLM features** — no rule against the *plugin* being a frontend to
  a paid service we operate. ScapeGPT shipped (PR #4271 merged 2023) as
  a paid OpenAI frontend. It was later disabled (`disabled=true` in its
  manifest) when the backend went down — abandoned plugin handling, not
  policy enforcement. The lesson: maintainers will ship a paid SaaS plugin,
  but the moment the backend goes silent the plugin gets disabled.

## FORBIDDEN — automatic rejection (the "Rolled Back" list)

Source: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

### Automation & input synthesis
- Touchscreen / controller plugins (triggers Jagex macro detection).
- Auto-rejoin parties (server strain).
- Auto-clickers, AFK trainers, mouse/keyboard synthesis of any kind.
- Anything that simulates a `MenuAction` invocation as if the user clicked.
- Auto-typing chat messages or programmatically inserting chatbox text.

### Data & privacy
- **"Plugins exposing player information via HTTP"** — *this is the line PR #11453 was rejected against. Our current `McpServerService` design violates it. MUST be replaced with outbound WebSocket egress before submission.*
- Crowdsourcing player location / gear / names (anti-griefing).
- Hiscores for personal bests (data is easily spoofed).
- ID-based plugins that take user input (moderation surface).

### Game-balance & content
- Removed historically *per Jagex request*: AoE plugin, Zulrah Helper,
  Volcanic Mine Helper, Demonic Gorilla plugin, Cerberus plugin, Fight Cave
  / Jad plugin, BA left-click calling, inventory pane background removal,
  hidden attack menu entries.
- New boss helper plugins — currently not accepted.
- Conditional menu-entry removal that could hide attack options unfairly.

### PvP-specific
- Opponent freeze timers, PK warnings, level-based PvP player indicators.
- Anything that gives one side a real-time combat advantage the other
  side does not have.

### Adult / illegal / RWT
- Sexually explicit content.
- Real-money trading helpers, auto-flipping bots, gold-farming aids.
- Anything that violates RuneScape Rule 7 (macroing).

### Technical
- Languages other than Java (Kotlin allowed only because RuneLite itself
  uses Java + Kotlin? **Verify** — wiki says "non-Java languages
  forbidden", but several existing plugins use Kotlin. Default-restrictive
  read: ship Java-only by submission time, retain Kotlin only if a clear
  precedent plugin uses it. Flagged in OPEN_QUESTIONS.)
- Java reflection (without justification).
- JNI / external program execution.
- `ProcessBuilder` of any kind — explicitly called out in PR #11453.
- Runtime code download.

### Operational
- Closed-source dependencies — every transitive dep must have a public
  source and a cryptographic hash via Gradle dependency verification.
- License other than BSD 2-Clause Simplified for the plugin repo (the
  contributing guide says BSD 2-Clause; MIT is widely accepted in practice
  for plugin-hub entries — confirm).

## Manifest requirements (verbatim from README)

- File path: `plugin-hub/plugins/<plugin-name>` (no extension).
- Required fields: `repository`, `commit` (full 40-char SHA), `authors`.
- Conditional: `warning=` is **required** for any plugin that performs
  network egress to a third party — iProdigy on PR #7459 told the RuneGPT
  author: *"the runegpt file in this repo needs a `warning=` line that
  explains what data is sent over HTTP."*
- Optional: `disabled=true` (used to disable abandoned plugins like
  ScapeGPT).

Example `warning=` lines (verbatim, from the hub today):

- WikiSync: `Similar to the official HiScores, this plugin pushes up data
  about your character that can be viewed by anyone.`
- LeaguesSync: `This plugin submits your IP address, username and completed
  task IDs to a 3rd-party server (osrsleaguetracker.com) not controlled or
  verified by RuneLite developers.`
- Wise Old Man: `This plugin submits the names of you, your friends and
  members of your clan chat to wiseoldman.net.`
- RuneProfile: `This plugin submits your player data and IP address to a
  server not controlled or verified by the RuneLite developers.`

Our `warning=` line is drafted in SUBMISSION_CHECKLIST.md.

## Ambiguities — defaulted restrictive, flagged

1. **License** — BSD 2-Clause per the README, but most plugins ship MIT.
   Tom's brief asks for MIT on `apps/plugin/`. We will ship MIT and confirm
   in the PR description; the maintainers have merged many MIT plugins
   already. Flagged in OPEN_QUESTIONS Q-7.
2. **Kotlin** — wiki says "non-Java languages forbidden" but Kotlin
   plugins exist on the hub. Restrictive read: pre-emptively port the
   plugin code to Java *if* the maintainers push back. Default: ship
   Kotlin, be ready to port. Flagged in OPEN_QUESTIONS Q-8.
3. **WebSocket egress to our backend** — no precedent rejection found
   for *outbound* WebSocket connections. Restrictive read: behave like a
   sync plugin (Wise Old Man, LeaguesSync) — opt-in, plain-English
   warning, never sends bank/inventory until the user types a chat
   that requires it.
4. **Tool invocation that highlights tiles / NPCs** — overlay-only is
   established precedent (every QoL plugin does this). Restrictive read:
   never call `MenuAction` synthesis; *only* paint overlays and emit text
   suggestions. Match what entity-hider, tile-indicators, NPC-indicators
   already do.
