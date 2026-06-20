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

## Q-7 — Reach out to Jagex for explicit SaaS approval? — 2026-06-21 (R3)
**Picked default:** ship with mitigations; do NOT proactively contact Jagex
Legal. Rationale: RuneLite has operated under Jagex's tolerated-not-licensed
posture for ~10 years; a paid SaaS plugin operates in the same grey zone.
Proactive contact risks a "no" that locks us out before launch. Better: ship
with strong RuneLite-parity defence (only BSD-2 and CC0 assets, "not
affiliated with Jagex" disclaimer everywhere, no wiki sprites in the paid
product) and monitor for cease-and-desist signals. Resolves Q-4 above.
**Other options:**
(a) ask Jagex Legal directly; or
(b) launch via RuneLite Plugin Hub first.
**Why not asked:** legal posture call — Tom can override on wake-up.
See docs/research/osrs-wiki/licensing.md for full risk breakdown.
