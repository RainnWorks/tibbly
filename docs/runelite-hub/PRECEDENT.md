# Precedent — plugins that ship player data to a remote service

This is the file we cite when a maintainer asks "why should this be merged?".
Each row below is a plugin currently live on the RuneLite Plugin Hub that
sends OSRS game state to a server the plugin author controls. The disclosure
patterns are remarkably consistent: a one-sentence `warning=` line in the
manifest plus a config flag that defaults to off (for some) or on with a
clear consent path (for others).

We also include the **rejection precedents** because they define the hard
edges of the policy — most relevantly PR #11453, which rejected an MCP
plugin nearly identical in spirit to ours.

---

## A. Approved precedents (live on the hub)

### A.1 Wise Old Man — https://github.com/wise-old-man/wiseoldman-runelite-plugin

- **Active installs (June 2026):** ~73,727.
- **Plugin-hub manifest:** `plugins/wiseoldman`.
- **Manifest `warning=`:** *"This plugin submits the names of you, your friends and members of your clan chat to wiseoldman.net."*
- **Data sent:** RSN, friends list, clan chat member names; on demand, hiscore-derived skill XP for tracking.
- **Architecture:** Plugin → wiseoldman.net REST API. Opt-in via plugin config (user enters their group token or uses passive sync).
- **License:** MIT (in repo).
- **Why this is precedent for us:** establishes that sending *player identity + social-graph data* to a third-party server is acceptable as long as it is disclosed and the user opted in by installing the plugin and toggling features.

### A.2 WikiSync — https://github.com/weirdgloop/WikiSync

- **Active installs:** ~302,788 (the most-installed third-party-sync plugin).
- **Plugin-hub manifest:** `plugins/wikisync`.
- **Manifest `warning=`:** *"Similar to the official HiScores, this plugin pushes up data about your character that can be viewed by anyone. We don't suggest using this if you are (for example) a PvP-locked hardcore ironman, where broadcasting your quest status could be detrimental to your account by giving away information about what you're currently doing."*
- **Data sent:** quest progress, achievement diary state, music tracks unlocked, combat achievements — to oldschool.runescape.wiki.
- **Architecture:** Plugin → wiki REST API; user signs in once via OAuth-style flow served by the wiki.
- **License:** BSD-2-Clause.
- **Why this is precedent for us:** establishes that *broad game-state sync* (quests, diaries, achievements) is acceptable, *and* shows the gold-standard `warning=` line — call out specific risk scenarios for paranoid players (HC iron, PvP) rather than hiding behind boilerplate.

### A.3 Group Ironmen Tracker — https://github.com/christoabrown/group-ironmen-tracker

- **Active installs:** ~21,403.
- **Plugin-hub manifest:** `plugins/group-ironmen-tracker`.
- **Manifest `warning=`:** (none currently set — relies on the in-plugin onboarding + the public-by-design nature of GIM tracker sites).
- **Data sent:** **skills, inventory, bank, equipment, rune pouch, looting bag, quests, runelite quest list** to a self-hosted or community-hosted tracker URL the user configures.
- **Architecture:** Plugin → user-configurable backend URL. Server source is included in the same repository (open source the receiver too — strong move).
- **License:** BSD-2-Clause.
- **Why this is precedent for us:** this plugin sends **everything we need to send** — bank, inventory, equipment, location. It got merged. The shape of the disclosure is therefore *not* a blocker for bank/inventory egress in principle; what matters is **opt-in + open-source backend + clear purpose**.

### A.4 LeaguesSync — https://github.com/RPBTwisted/leaguessync

- **Plugin-hub manifest:** `plugins/leaguessync` (merged April 2026, PR #11524).
- **Manifest `warning=`:** *"This plugin submits your IP address, username and completed task IDs to a 3rd-party server (osrsleaguetracker.com) not controlled or verified by RuneLite developers."*
- **Data sent:** RSN + completed Leagues task IDs every 30s.
- **Architecture:** Plugin → REST POST to `osrsleaguetracker.com/sync/{username}`. Backend source is in the same repo.
- **License:** MIT.
- **Why this is precedent for us:** shows the **canonical modern `warning=` template** — "IP address, username, and <specific data> to a 3rd-party server (<URL>) not controlled or verified by RuneLite developers." We will mirror this almost verbatim.

### A.5 player-stats-sync — https://github.com/mexetys/osrs-duels (PR #11223, merged March 2026)

- **Manifest `warning=`:** *"This plugin submits your IP address and player data to a third-party server not controlled or verified by the RuneLite developers."*
- **Data sent:** RSN, combat level, all skill levels and XP. *Crucially*, the earlier version (PR #11213) was closed because it also sent bank/inventory/equipment/looting bag. The author removed those, resubmitted, and was merged.
- **Why this is precedent for us:** sets the bar — *what you actually send* must be minimal for the feature. We don't need to send bank+inventory every minute; we only need to send them *when the chat needs them*. Design lever: the LLM chat decides when to request bank state, the plugin streams that one snapshot, then forgets.

### A.6 ScapeGPT — https://github.com/polyphilz/scapegpt (PR #4271, merged 2023)

- **Manifest `warning=`:** *"This plugin submits your IP address to a server not controlled or verified by the RuneLite developers."*
- **Now `disabled=true`** in the manifest — the backend went down, the plugin was disabled rather than removed.
- **Data sent (per PR review):** very minimal — chat prompts to a backend operated by the author, which forwarded to OpenAI. RuneLite auth specifics were stripped before merge (raiyni explicitly asked them removed).
- **Why this is precedent for us:** **direct precedent** that a paid LLM-frontend plugin can ship via the hub. Also a warning: if our backend ever stops responding, the plugin gets `disabled=true` and our distribution dies. Treat backend uptime as P0.

### A.7 RuneProfile — https://github.com/ReinhardtR/runeprofile-plugin

- **Manifest `warning=`:** *"This plugin submits your player data and IP address to a server not controlled or verified by the RuneLite developers."*
- **Why precedent:** another "small player-data sync" plugin using the canonical modern warning string.

### A.8 Collection Log Luck — peanubnutter/collection-log-luck

- **Manifest `warning=`:** *"This plugin submits your username and IP address to a server not controlled or verified by the RuneLite developers."*

### Summary of the approved-precedent disclosure template

After eight plugins, the modern (2026) `warning=` template is:

> *This plugin submits your IP address, **<list of fields>**, to a server (`<domain>`) not controlled or verified by the RuneLite developers.*

We will use this exact shape.

---

## B. Rejection precedents — the edges of the policy

### B.1 PR #11453 — Add OSRS MCP plugin — **CRITICAL precedent for us**

- **Author:** nickbeddows-ctrl. **Outcome:** closed without merge, May 2026.
- **What it did:** local MCP server on `127.0.0.1:8282` exposing player stats, equipment, inventory, location, quests, with three connection modes: local, LAN, cloud relay (SSH tunnel via serveo.net).
- **Why rejected (verbatim, riktenx):**
  > *"thanks for the submission but we are going to reject this per the following to prevent abuse: 'Plugins which expose player information over HTTP.' https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features"*
- **Sub-objections (raiyni):**
  > *"What is the point of this cloud relay? I don't think it's a good idea to be allowing plugins to establish their own ssh tunnels and exposing clients to an unknown service like this."*
  > *"We generally don't allow process execution and generating ssh keys and tunnels for users isn't going to be allowed I'd imagine."*
  > *"I meant all uses of process builder. If a user needs to use tailscale, they can just run tailscale."*
- **Lessons:**
  1. **An MCP server bound to localhost is still "exposing player information over HTTP" in the maintainers' read.** Loopback does not save us. We must drop the local HTTP/MCP listener and replace it with an **outbound** WebSocket from the plugin to our backend.
  2. **No `ProcessBuilder` anywhere in the codebase.** Even removing the cloud relay was not enough; the maintainer wanted the entire `ProcessBuilder` capability removed.
  3. **No dynamic network setup helpers** (ssh keygen, ngrok-style tunnels, automatic Tailscale enrollment). If the user wants cross-device, they install Tailscale themselves.
- **How our design differs from the rejected one (and why we believe it can pass):**
  - We do NOT expose any HTTP server on the client.
  - We make a single outbound WSS connection to `wss://api.osrsllm.app`.
  - The "tools" of MCP run *inside* the plugin process, invoked by an RPC message arriving over the WSS, not by an external HTTP caller.
  - Cloud chat is opt-in; the plugin works in local-tools-only mode without any egress.
  - We mirror Wise Old Man / LeaguesSync / RuneProfile in disclosure form.

### B.2 PR #7459 — RuneGPT — extended review, eventually closed

- **What it did:** brought your own Gemini API key, plugin sent prompts directly to Gemini API.
- **Outcome:** closed by LlemonDuck after the author stopped responding to review comments.
- **Maintainer asks (iProdigy):**
  > *"the runegpt file in this repo needs a `warning=` line that explains what data is sent over http; examples can be found at https://github.com/search?q=repo%3Arunelite%2Fplugin-hub%20warning%3D&type=code"*
- **Lessons:**
  - LLM-frontend plugins are not rejected on principle; they are rejected on details (deprecated APIs, blocking the shared executor, missing `warning=`).
  - The maintainers will tell you exactly which code patterns to fix.
  - Be responsive in PR review — closed for non-response, not for policy.

### B.3 PR #12259 — RuneSpeak — closed for dependency size

- **Lesson:** native libraries (ONNX runtime) and HuggingFace tokenizers were rejected because they're too large and require expert review of binary deps. Maintainer was clear:
  > *"we cannot approve these dependencies because they make your plugin too large"*
  > *"We are not going to allow you to download libraries at run time."*
- **Application to us:** keep the plugin tiny — no embedded ML models. The LLM runs server-side.

---

## C. Implications for our design

1. **Outbound WebSocket only.** No localhost HTTP/MCP server.
2. **`warning=` line in the canonical 2026 shape**, listing every datum.
3. **Opt-in cloud chat**, with a first-run dialog. Local-tools-only mode is the install default.
4. **Tiny plugin JAR** — no embedded models, no heavy deps.
5. **Open-source backend code** would strengthen our PR (Group Ironmen does this). At minimum, document the protocol publicly.
6. **Be responsive in PR review.** RuneGPT was closed for non-response, not policy.
7. **Plan for the backend going down** — manifest `disabled=true` is the maintainer's escape hatch. Build SLO/uptime monitoring from day one.
