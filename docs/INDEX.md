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
- [`architecture/DATA_FLOW.md`](architecture/DATA_FLOW.md) — Mermaid sequence diagrams of every wire crossing *(r10 / RAI-35)*

## RuneLite Plugin Hub compliance *(r10 / RAI-35)*

- [`runelite-hub/POLICY_SUMMARY.md`](runelite-hub/POLICY_SUMMARY.md) — allowed / borderline / forbidden, distilled
- [`runelite-hub/SUBMISSION_CHECKLIST.md`](runelite-hub/SUBMISSION_CHECKLIST.md) — every requirement + our status
- [`runelite-hub/PRECEDENT.md`](runelite-hub/PRECEDENT.md) — approved + rejected plugins we cite in our PR
- [`runelite-hub/PLUGIN_DESCRIPTION.md`](runelite-hub/PLUGIN_DESCRIPTION.md) — hub listing copy + `warning=` line
- [`runelite-hub/DATA_DISCLOSURE.md`](runelite-hub/DATA_DISCLOSURE.md) — every field that can leave the client

## Marketing

- [`marketing/POSITIONING.md`](marketing/POSITIONING.md) — copy, pillar messages
- [`marketing/ASSET_CATALOG.md`](marketing/ASSET_CATALOG.md) — OSRS Wiki sprites + URLs
- [`marketing/BRAND_VOICE.md`](marketing/BRAND_VOICE.md) — picked voice ("clever friend who read the wiki") + 5 sample exchanges *(r7 / RAI-11)*
- [`marketing/NAME_CANDIDATES.md`](marketing/NAME_CANDIDATES.md) — Tibbly (pick) + Wikit + Scribbins with TM/domain checks *(r7 / RAI-11)*

## Research (continuously growing, owned by research agents)

- [`research/runelite-api/`](research/runelite-api/) — APIs we could use we haven't yet
- [`research/community/`](research/community/) — OSRS/RuneLite user trends, pain points
  - [`research/community/_SUMMARY.md`](research/community/_SUMMARY.md) — one-page pain-points + creator brief *(r2)*
  - [`research/community/_naming-signals.md`](research/community/_naming-signals.md) — how OSRS tools are named in the wild *(r7 / RAI-11)*
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
