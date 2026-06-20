# docs/ index

Entry point for every agent in this repo. Read in this order on first read,
then jump as needed.

## Operating contract (read every loop)

- [`agents/NORTH_STAR.md`](agents/NORTH_STAR.md) — what we're building, must-ships, anchors
- [`agents/SCOPE_GUARD.md`](agents/SCOPE_GUARD.md) — what NOT to do
- [`agents/MEMORY_ARCHITECTURE.md`](agents/MEMORY_ARCHITECTURE.md) — how memory works here
- [`agents/ASSIGNMENTS.md`](agents/ASSIGNMENTS.md) — per-agent brief
- [`agents/STATUS.md`](agents/STATUS.md) — live status board *(auto-maintained)*
- [`agents/LOOP_LOG.md`](agents/LOOP_LOG.md) — append-only loop history
- [`agents/DECISION_LOG.md`](agents/DECISION_LOG.md) — every decision made
- [`agents/OPEN_QUESTIONS.md`](agents/OPEN_QUESTIONS.md) — flagged for user review
- [`agents/HANDOFF.md`](agents/HANDOFF.md) — what to show the user on wake-up
- [`agents/GAPS.md`](agents/GAPS.md) — current gap analysis *(auto-maintained)*

## Product

- [`product/RAW_INSTRUCTIONS.md`](product/RAW_INSTRUCTIONS.md) — verbatim user words, source-of-truth for intent
- [`product/VISION.md`](product/VISION.md) — the productized SaaS vision
- [`product/PERSONAS.md`](product/PERSONAS.md) — who pays and why
- [`product/PRICING.md`](product/PRICING.md) — tiers + sample math

## Architecture

- [`architecture/SYSTEM.md`](architecture/SYSTEM.md) — runtime, sequence diagrams, dataflow
- [`architecture/IDENTITY.md`](architecture/IDENTITY.md) — RuneLite-driven auth, device key, pairing
- [`architecture/BILLING.md`](architecture/BILLING.md) — Stripe model, metering, hard caps
- [`architecture/TOOL_ECONOMY.md`](architecture/TOOL_ECONOMY.md) — how we keep tool context lean
- [`architecture/MONOREPO.md`](architecture/MONOREPO.md) — layout, scripts, conventions
- [`architecture/PROTOCOL.md`](architecture/PROTOCOL.md) — WS protocol plugin ↔ backend

## Marketing

- [`marketing/POSITIONING.md`](marketing/POSITIONING.md) — copy, pillar messages
- [`marketing/ASSET_CATALOG.md`](marketing/ASSET_CATALOG.md) — OSRS Wiki sprites + URLs

## Research (continuously growing, owned by research agents)

- [`research/runelite-api/`](research/runelite-api/) — APIs we could use we haven't yet
- [`research/community/`](research/community/) — OSRS/RuneLite user trends, pain points
  - [`research/community/_SUMMARY.md`](research/community/_SUMMARY.md) — 1-page summary for marketing + roadmap
  - [`research/community/pain-points.md`](research/community/pain-points.md) — 13 pain points with sourced evidence + top-50 plugin install corpus
  - [`research/community/creators.md`](research/community/creators.md) — J1mmy / Soup / Settled / Limpwurt / B0aty profiles + outreach principles
  - [`research/community/trends.md`](research/community/trends.md) — Leagues 6, Sailing, Varlamore, calendar opportunities
- [`research/memory-systems/`](research/memory-systems/) — how to do durable agent memory
- [`research/llm-providers/`](research/llm-providers/) — OpenRouter model + pricing
- [`research/competitor/`](research/competitor/) — other AI/OSRS tools
- [`research/osrs-wiki/`](research/osrs-wiki/) — OSRS Wiki imagery catalog

## How to use this index

- **Building agents:** read `NORTH_STAR.md`, `SCOPE_GUARD.md`, your row in
  `ASSIGNMENTS.md`, then jump to the architecture doc most relevant to your work.
- **Research agents:** read `RAW_INSTRUCTIONS.md` first, then the topic-folder.
  Every research output gets a `_SUMMARY.md` so other agents can grep without
  full-text reads.
- **Operating agent (Claude, me):** open this file at every loop start.
