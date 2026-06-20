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
