# R3 / RAI-7 — Visual asset research summary

> 2026-06-21 · agent/r3/osrs-assets

## Top recommendation (the call)

**Use the RuneLite project's own resource tree as our primary visual asset
library, plus the RuneStar CC0 fonts. Treat the OSRS Wiki as research-only.**

Rationale: RuneLite ships under BSD-2-Clause, includes the full OSRS skill
icon set (26 PNGs), 50+ teleport icons, prayer/cluescroll/UI chrome icons,
and the RuneScape display font — all with permissive licensing. Jagex's
public posture on RuneLite has tolerated this asset surface for ~10 years,
giving us strong "third-party-client parity" cover. Wiki sprites are
CC-BY-NC-SA 3.0 (non-commercial) and individually uploaded under Jagex fair
use; they're a no-go for a paid SaaS.

## Three-bullet brief

- **Primary source: RuneLite (`github.com/runelite/runelite`, BSD-2-Clause).**
  ~500 PNGs across the resource tree, plus our three RuneScape TTFs from
  RuneStar (CC0). 55 BSD/CC0 assets already mirrored at
  `packages/osrs-assets/` (26 skill icons + 26 small icons + 3 fonts).
- **Hard NO on OSRS Wiki sprites in marketing/dashboard.** Wiki text is
  CC-BY-NC-SA 3.0 (Non-Commercial); images are typically uploaded as Jagex
  fair use, not freely licensed at all. Embedding them in a paid product is
  a direct licence violation. Use the wiki ONLY as research material.
- **Flagged risk (loud):** *all* OSRS-themed assets — even BSD-licensed ones
  — depict Jagex IP. Code licences (BSD/MIT/CC0) do not transfer IP rights.
  Our defence is parity with RuneLite, not legal ownership. Mandatory mitigations
  recorded in `licensing.md`: no wiki hot-links, "not affiliated with Jagex"
  footer, monitor Jagex Fan Content Policy for posture changes, plan to
  commission original hero art for higher-safety brand surfaces.

## Deliverables

- `assets.md` — categorised catalog, 250+ assets across 7 categories.
- `licensing.md` — per-source license matrix + four risk warnings.
- `packages/osrs-assets/` — 26 BSD-2 skill icons + 26 small variants + 3 CC0 RuneScape TTFs, cached. Includes `src/index.ts` with `SKILL_ICONS`, `FONTS`, and an `isForbiddenAssetUrl` guard.

## Outstanding question

Q-7 in `OPEN_QUESTIONS.md`: should we proactively reach out to Jagex Legal?
Default: ship with mitigations, don't ask.
