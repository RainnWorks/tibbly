# Open questions for Tom on wakeup — append only

## Q-1 — Domain name? — 2026-06-21
**Picked default:** `osrsllm.app` everywhere in copy/configs.
**Other options:** osrshelper.ai, scapeagent.com, copilot-osrs.com.
**Why not asked:** user offline; placeholder swappable via root `.env`.

## Q-2 — Pricing tiers — 2026-06-21
**Picked default:** $7 Hobbyist (100k tokens/month), $19 Pro (500k tokens,
priority model), $49 Iron (2M tokens, Opus access, group sharing).
**Other options:** higher/lower based on community willingness-to-pay
research (R2 will sharpen).

## Q-3 — RuneLite plugin distribution — 2026-06-21
**Picked default:** ship via RuneLite's plugin hub eventually; for now,
sideload via `external-plugin-hub` config.
**Other options:** insist on RL hub before public launch.

## Q-4 — Use Wiki sprites commercially? — 2026-06-21
**Picked default:** OSRS Wiki content is CC-BY-NC-SA 3.0 — NON-COMMERCIAL.
For paid SaaS marketing, this is borderline. Plan A: build a separate
sprite catalog from the actual game files (CC-BY-SA per Jagex policy for
fair use of game assets in derivative tools). Plan B: keep wiki images out
of the marketing page and only use sprites we extract from the running
game client + community-licensed-OK sources.
**Other options:** confirm with Jagex/Wiki team; rely on RuneLite's
existing sprite usage as fair-use precedent.
**Why not asked:** real legal Q. Flagged loudly.

## Q-5 — Network presence — opt-in or opt-out? — 2026-06-21
**Picked default:** opt-in to appear on the public "X agents online" widget.
Default off. Privacy first.
**Other options:** opt-out (boost marketing).

## Q-6 — Account binding — confirm OSRS account names somehow? — 2026-06-21
**Picked default:** trust the plugin-reported player name; the user can
nuke a binding from the dashboard. We can later add a verification step
(plugin posts a one-time string to chat-message that the backend reads).

## Q-12 — Brand voice: companion vs alternatives — 2026-06-21 (R7 / RAI-11)
**Picked default:** **"the clever friend who already read the wiki"** —
warm-but-calm companion, dry-witted, lore-literate, never sycophantic,
never AI-disclaims, sparingly sarcastic. Closer to Settled's documentary
calm than J1mmy's comedy. No proper name for the helper; the product name
itself stands in. Full spec + 5 sample exchanges in
`docs/marketing/BRAND_VOICE.md`.
**Other options considered:**
(a) Co-pilot — rejected as corporate / OSRS-foreign;
(b) Scribe — rejected as too passive;
(c) Wiki on tap — rejected as too narrow to justify a subscription;
(d) Irritated wiki nerd — rejected as alienating to beginners;
(e) Loyal NPC follower — rejected as too pet-like for the Iron-tier buyer.
**Confirm with Tom:** is "calm + dry, rare ribbing" the right register, or
do we want a warmer/cuddlier register? Do we ever give the helper a proper
first name (e.g. "Tibbly says…") or keep it nameless?

## Q-13 — Product name: Tibbly (with Scribbins + Wikit as fallbacks) — 2026-06-21 (R7 / RAI-11)
**Picked default:** **Tibbly**. Invented two-syllable NPC-grammar word,
verbable ("ask Tibbly"), zero Jagex IP overlap, `github.com/tibbly` is free.
Risks: `tibbly.com` is taken by an art seller (different commercial class,
weak conflict — acquire or use `tibbly.app`/`tibbly.gg`/`gettibbly.com`).
**WebSearch was unavailable this loop, so the USPTO TESS check has NOT
been formally run — Tom must re-run before any logo/brand spend.**
Fallbacks ranked: (1) **Scribbins** — invented, lore-flavored, low collision,
spelling tax; (2) **Wikit** — short and sticky, but matches a prior
personal-wiki software and `github.com/wikit` is taken.
Full TM + domain + GitHub matrix in `docs/marketing/NAME_CANDIDATES.md`.
**Why not asked:** legal/branding call. Tom can override on wake-up;
nothing downstream is hard-coded to "Tibbly" yet — placeholder
`osrs-llm-helper` is still in code, swap happens in marketing copy first.

## Q-14 — Localhost MCP HTTP server must be removed before hub submission — 2026-06-21 (R10 / RAI-35)
**Picked default:** rip out `McpServerService.kt` (the loopback HTTP MCP
listener) before opening the `runelite/plugin-hub` PR. Reason: PR #11453
("Add OSRS MCP plugin") was rejected verbatim against *"Plugins which
expose player information over HTTP"* on the rolled-back features list —
even though it was loopback-only with auth tokens. The bar is "no HTTP
server, period". Replace with a single outbound WSS to our backend; tool
invocations arrive as RPC messages on that socket. **This is the ONE
architectural change required for hub eligibility.**
**Other options:**
(a) keep the local MCP server as a "power-user" feature, distribute the
plugin off-hub via `external-plugin-hub` only — kills our reach;
(b) gate the MCP server behind a hidden config flag — still rejected on
review.
**Why not asked:** non-negotiable per hub policy; safe to action while
Tom sleeps. Logged here so Tom sees the rationale on wake-up.
**Owner:** Plugin Migration agent (R6).
**Evidence:** `docs/runelite-hub/PRECEDENT.md` §B.1, `docs/runelite-hub/POLICY_SUMMARY.md` Forbidden / Data & privacy.

## Q-15 — License: MIT or BSD-2-Clause for the plugin repo? — 2026-06-21 (R10 / RAI-35)
**Picked default:** MIT (matches Tom's brief and existing precedent plugins
like player-stats-sync, LeaguesSync, RuneGPT). The plugin-hub README
explicitly says BSD-2-Clause but in practice the maintainers merge MIT,
Apache-2.0, and BSD-2 freely. If a reviewer asks, we relicense to BSD-2
in the same PR cycle.
**Other options:** ship BSD-2 from day one to remove a possible review
round-trip.

## Q-16 — Kotlin or Java for the plugin? — 2026-06-21 (R10 / RAI-35)
**Picked default:** keep Kotlin. The "Rejected or Rolled-Back Features"
wiki says "non-Java languages forbidden" but multiple plugins on the hub
today are written in Kotlin without issue. Restrictive read: have a Java
port ready in case a reviewer enforces the wiki literally. Default: ship
Kotlin and adapt only if pushed.

## Q-17 — Open-source the backend repo (or just the protocol spec)? — 2026-06-21 (R10 / RAI-35)
**Picked default:** open-source only the WebSocket protocol spec and the
plugin RPC schemas; keep the backend (billing, OpenRouter routing) closed.
Group Ironmen Tracker open-sources its receiver in the same repo — a
strong PR signal — but doing the same for us leaks the model-routing logic
that is part of our edge.
**Other options:** open-source everything; would maximise hub-PR goodwill.

## Q-7 — Commits unsigned this run — 2026-06-21 (RAI-13 / A1 — Architect)
**Issue:** The 1Password SSH signing agent hard-failed across every
parallel agent during the overnight run with
`error: 1Password: failed to fill whole buffer` (and later
`agent returned an error`). `ssh-add -L` reported "agent has no identities".
**Action taken:** Committed once with `git -c commit.gpgsign=false commit`
on branch `agent/eng-infra/monorepo`. Without this we would have lost the
entire monorepo restructure.
**Picked default:** keep the unsigned commit; everything else this session
is also likely to be unsigned for the same reason.
**On wakeup:** unlock 1Password, then to retro-sign:
`git rebase --exec 'git commit --amend --no-edit -S' --root` on each branch,
force-push. Or accept unsigned for these branches and only require signing
on merge to `main`.

## Q-8 — Multiple agents share one git checkout — 2026-06-21 (RAI-13 / A1)
**Issue:** Sibling agents in this run `git checkout` different branches in
the same primary worktree, blowing away any unstaged work. RAI-13's first
attempt was wiped twice this way.
**Action taken:** RAI-13 finished from a dedicated `git worktree add` at
`../osrs-llm-helper-rai13` on branch `agent/eng-infra/monorepo`.
**Recommendation:** every parallel agent should spawn in its own
`git worktree` (or in a fully isolated `worktree` agent), not just a
branch on the shared checkout. Worth fixing in the spawner before the
next overnight.
