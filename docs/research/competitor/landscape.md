# Competitor landscape — AI-for-OSRS

Researcher: agent R6 (RAI-10). Date: 2026-06-21.

This catalogs every AI-flavored OSRS tool we could find via GitHub search,
WebFetch, and the official RuneLite plugin-hub PR history. For each entry we
captured: what it does, distribution channel, model / pricing, sentiment
signal (stars / forks / reviewer reaction), and the takeaway for our wedge.

> Important context: as of June 2026, **no AI/LLM plugin has been merged into
> the RuneLite Plugin Hub.** The single live submission (PR #11453 from
> nickbeddows-ctrl) was rejected on 2026-05-15 under the "plugins which expose
> player information over HTTP" rule. Distribution into RuneLite is therefore
> a contested moat, not a checkbox.

---

## 1 · OSRS MCP plugin — nickbeddows-ctrl (rejected from Plugin Hub)

- **Repo:** https://github.com/nickbeddows-ctrl/osrs-mcp-plugin
  (BSD-2-Clause · 1 star · 0 forks · last pushed 2026-04-15)
- **Plugin Hub PR:** https://github.com/runelite/plugin-hub/pull/11453
  (opened 2026-04-10, closed 2026-05-15)
- **What it does:** Exposes RuneLite client data through a local Model Context
  Protocol server (`127.0.0.1:8282`). Any MCP-compatible AI (Claude Desktop,
  Cursor, Windsurf, generic MCP config) can connect via the `mcp-remote`
  stdio bridge and call tools like `get_player_stats`, `get_equipment`,
  `get_inventory`, `get_location`, `get_all`. Per the public README, the live
  tool surface has grown to "40+ tools" including bank summaries, GE offers,
  collection log, achievement diaries, slayer task, clue scroll, herb-patch
  timings, BIS comparisons, drop-table lookups, and live GE prices.
- **Distribution:** Side-loaded via `runelite --developer-mode` (the standard
  external-jar route). Not in Plugin Hub.
- **Model / pricing:** BYO — the player runs Claude Desktop / Cursor on their
  own subscription. No metering, no billing layer.
- **Why it was rejected (verbatim from riktenx, 2026-05-15):**
  > thanks for the submission but we are going to reject this per the
  > following to prevent abuse: "Plugins which expose player information
  > over HTTP."
  Earlier in the thread reviewer **raiyni** also pushed back on the cloud
  relay: *"We generally don't allow process execution and generating ssh keys
  and tunnels for users isn't going to be allowed I'd imagine."* The author
  removed `ProcessBuilder` and the serveo.net relay, but the underlying
  "expose-over-HTTP" rule still killed the PR.
- **Sentiment signal:** Negative on distribution side (rejected). Author was
  responsive, made every requested change. 1 star — minimal grassroots pull.
- **Takeaway for us:** This is the closest analog to our plugin and the
  single most important data point in the whole landscape. **The Plugin Hub
  will not host an HTTP-exposing AI plugin.** A persistent outbound
  WebSocket to *our* backend is a different surface and we should expect to
  argue for it on those terms (no inbound port, no local HTTP listener, no
  process execution, TLS-only). If we don't get Hub distribution, we ship
  the same way nickbeddows did — side-loaded jar via `--developer-mode`,
  documented step-by-step on the marketing page. Either way, his PR is the
  precedent we cite and the design we cannot repeat.

## 2 · osrs-ai-companion — Scrambles56

- **Repo:** https://github.com/Scrambles56/osrs-ai-companion
  (BSD-2-Clause · 1 star · 0 forks · last pushed 2026-02-19)
- **What it does:** RuneLite plugin with a sidebar chat panel. Maintains
  32K-char history. Sends Claude a comprehensive player snapshot (stats,
  inventory, gear, location, quests, achievement diary, slayer, bank).
  Auto-reacts to in-game milestones (level-ups, quest completions, boss
  kills, valuable drops) without prompting. Tool surface includes
  achievement-diary lookup, combat-achievement status, OSRS wiki search,
  live GE prices. Persistent goal memory across sessions.
- **Distribution:** Side-loaded via dev mode. Not in Plugin Hub.
- **Model / pricing:** BYO Anthropic API key (Haiku, Sonnet, or Opus).
  Player enters their own key from console.anthropic.com. The author
  carries no cost and no billing relationship.
- **Differentiator on their side:** Personality system —
  "Wise Old Man", "Drunken Dwarf", "Proud Dad", "Bob", "Zamorak Zealot".
  Strong on flavor; weak on scale.
- **Sentiment signal:** 1 star, no forks. No live community discussion
  surfaced. Looks like a personal project that was never marketed.
- **Takeaway for us:** Validates that "chat panel inside RuneLite that knows
  your context" is the right shape. Their personality presets are a UX win
  we should copy. Their BYO-key model is the obvious anti-pattern we exist
  to fix — it means every user has to set up an Anthropic billing account
  before they can play, which is a brutal onboarding tax.

## 3 · osrs-ai-assistant — SteveDoesCoding

- **Repo:** https://github.com/SteveDoesCoding/osrs-ai-assistant
  (no license · 0 stars · 0 forks · last pushed 2026-03-13)
- **What it does:** Per the repo description, "Chat with Claude AI in
  RuneLite with live OSRS data access. Claude can look up your real stats,
  GE prices, wiki info, XP gains from Wise Old Man, and more to give
  personalized advice. Requires an Anthropic API key." The repo is sparsely
  populated at time of fetch (README not visible in current snapshot).
- **Distribution:** Side-loaded.
- **Model / pricing:** BYO Anthropic API key.
- **Notable:** This is the only competitor we found that already integrates
  Wise Old Man as a tool. They wire to the third-party hiscore tracker for
  XP-gain history, which is the right move — and one we should match.
- **Sentiment signal:** 0 stars. No traction.
- **Takeaway for us:** Confirms the Wise Old Man integration belongs in
  our M1 tool surface. Also confirms (again) that BYO-key is the lazy
  monetization default in this space, and that the field is wide open for
  the first plugin to ship a real billing relationship.

## 4 · OSRS_AI_System — brandoninkel

- **Repo:** https://github.com/brandoninkel/OSRS_AI_System
  (1 star · 0 forks)
- **What it does:** Stand-alone (not a RuneLite plugin) AI chat that uses
  retrieval-augmented generation over 35,884 OSRS Wiki embeddings. Uses
  LangGraph for agentic orchestration. Runs LLaMA 3.1 locally via Ollama.
  Web GUI at `localhost:3005`. Setup requires three parallel terminals
  (Ollama, Python API server, Node.js frontend).
- **Distribution:** GitHub source only. No installer.
- **Model / pricing:** Local LLaMA 3.1 (free, your hardware).
- **Sentiment signal:** 1 star. Project README is 5,000+ lines — heavy on
  documentation, light on adoption.
- **Takeaway for us:** This is the "RAG-over-wiki" school of OSRS AI.
  Strong on knowledge grounding, **zero connection to live game state.**
  The user has to copy/paste their situation in. Our pitch lands directly
  against this: "you don't have to describe your inventory — we already
  see it."

## 5 · osrs-llm — kineticquant

- **Repo:** https://github.com/kineticquant/osrs-llm
  (MIT · 1 star · 0 forks)
- **Web UI:** https://osrs-llm.pages.dev
- **What it does:** A fine-tuned Phi-3.5-mini-instruct (Microsoft) trained on
  the OSRS Wiki and community datasets through January 2026. Distributed as
  a downloadable model — users plug it into OpenWebUI, Ollama, or
  HuggingFace. 128K context. Targets 12GB-VRAM consumer GPUs.
- **Distribution:** HuggingFace + GitHub. The Cloudflare Pages site is a
  marketing splash, not a hosted chat.
- **Model / pricing:** Free. Player provides hardware.
- **Sentiment signal:** 1 star. No measurable adoption.
- **Takeaway for us:** The "trained-on-OSRS" angle sounds impressive but
  immediately collides with the wiki cadence — every weekly game update
  invalidates the weights. We instead **retrieve fresh wiki on demand**
  via a tool call, which doesn't go stale. Worth a one-liner on the
  marketing page: "trained models go stale every Wednesday. We don't."

## 6 · RunescapeGPT — harmindersinghnijjar

- **Repo:** https://github.com/harmindersinghnijjar/RunescapeGPT
  (MIT · 24 stars · 4 forks)
- **What it does:** GPT-3.5-turbo wrapped in CLI + Flet UI that **generates
  DreamBot Java scripts**. Trained on DreamBot forum discussions.
- **Distribution:** Docker. Self-hosted.
- **Model / pricing:** BYO OpenAI key.
- **Sentiment signal:** Highest star count in the category (24). README
  carries an explicit disclaimer: *"Botting in Old School RuneScape is
  against the Terms of Service of the game and can lead to account bans."*
- **Takeaway for us:** This is the only project here with measurable
  community demand, and it serves the **botting community**, not players.
  It validates that the OSRS audience is hungry for AI tooling — and it
  also reinforces why we must position **loud and early** as the anti-bot,
  Jagex-friendly option. Our copy must read clearly as "helps you play",
  never "plays for you", or we get tarred with the same brush.

## 7 · Other GitHub flotsam (one-line each)

These came back in the same searches but are dormant or low-signal. Listed
for completeness so future loops don't re-research them.

- `Michael-Koers/OSRS-AI-Assistant` — Java repo from RuneLite template,
  0 stars, no README content. Likely abandoned.
- `spicy-tendies/osrs-ai-advisor` — "AI-assisted OSRS route planning",
  0 stars, RuneLite template fork, very early.
- `Captainpax/osrs-ai-discord-bot` — empty description, 0 stars.
- `dustinmcafee/osrs-llm-bot` — 0 stars, empty.
- `Vytautas-sandin/OSRS-LLM` — 0 stars, empty.
- `botsafe-org/safe-bot-osrs` — "AI Integrated Automation" botting tool,
  0 stars. Same anti-pattern as RunescapeGPT.
- `chrissywert/osrs-ai-flipper` — described "#Don't download this".
- `Nikamura/osrs-ai-agent` — 1 star, sparse.
- `tfgast/osr_ai_gm` — actually an Old-School Renaissance TTRPG game-master
  AI; false positive on the acronym.

## Adjacent (not AI, but the audience-capture incumbents)

These are the third-party services every OSRS player already knows. Each is
a candidate **integration target** for us (tool call into their public API)
rather than a competitor — they don't do LLM at all.

- **Wise Old Man** — open-source XP / boss / clue tracker.
  https://wiseoldman.net · https://github.com/wise-old-man/wise-old-man
  (347 GitHub stars, 120 forks). Node.js + Postgres + Redis. Discord bot
  and RuneLite plugin. Patreon-funded. No AI features. We should call
  their public API as a tool — `get_player_gains(name, period)`.
- **TempleOSRS** — competing tracker (couldn't fetch live; 403 on direct
  hit, common for Cloudflare-fronted OSRS community sites). Same shape as
  Wise Old Man — competitions, groups, time-series. No AI.
- **OSRS Wiki** (Weird Gloop) — the canonical knowledge base. No AI search
  or assistant feature exposed on the main page as of 2026-06-21. They
  publish a structured wiki dump we can ground retrieval on, and a live
  GE-price feed at https://prices.runescape.wiki/api/v1/osrs/latest used by
  every flipping plugin. We already consume this — keep doing so.
- **Quest Helper** — community RuneLite plugin (top install at 555K+ per
  R2 community research). Hard-coded quest step overlays. Knows nothing
  about the player's bank or gear. We compete on flexibility, not coverage.

---

## Cross-cutting observations

1. **There is no incumbent.** The most-starred entry in the AI-for-OSRS
   category is a *botting code generator* with 24 stars. Player-facing AI
   assistants top out at **1 star.** This category is unowned.
2. **Every competitor is BYO-API-key.** Not one project has built a billing
   layer. Onboarding requires the player to register with Anthropic or
   OpenAI, generate a key, paste it into a config file. That is a moat we
   close by selling tokens.
3. **Distribution is the real competition.** The Plugin Hub gate (rejected
   PR #11453) is binding. Whoever first ships a Hub-acceptable design wins
   the discovery channel. Our plan — outbound WebSocket only, no inbound
   port, no HTTP listener, no process exec — is purpose-built to clear the
   bar that killed nickbeddows.
4. **Live game state is our wedge.** Three classes exist: (a) wiki-RAG
   chats with no game state (brandoninkel, kineticquant), (b) RuneLite
   plugins that read state but bill nothing (Scrambles56, SteveDoesCoding,
   nickbeddows), (c) bot codegen for the gray-market (harmindersinghnijjar).
   Nobody combines (a) + (b) + a billing relationship + Plugin-Hub-shaped
   distribution. We do.
5. **Trained models go stale.** kineticquant's fine-tune locks in January
   2026 OSRS state. We retrieve fresh wiki on demand. This is a marketing
   line.

---

## Sources

- https://github.com/runelite/plugin-hub/pull/11453
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/nickbeddows-ctrl/osrs-mcp-plugin
- https://github.com/Scrambles56/osrs-ai-companion
- https://github.com/SteveDoesCoding/osrs-ai-assistant
- https://github.com/brandoninkel/OSRS_AI_System
- https://github.com/kineticquant/osrs-llm
- https://osrs-llm.pages.dev
- https://github.com/harmindersinghnijjar/RunescapeGPT
- https://github.com/wise-old-man/wise-old-man
- https://github.com/Quest-Helper/quest-helper (Quest Helper plugin home)
- https://oldschool.runescape.wiki/
- https://prices.runescape.wiki/api/v1/osrs/latest
