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

## Q-7 — Brand voice: companion vs alternatives — 2026-06-21 (R7 / RAI-11)
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

## Q-8 — Product name: Tibbly (with Scribbins + Wikit as fallbacks) — 2026-06-21 (R7 / RAI-11)
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
