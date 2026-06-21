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

## Apps

- `apps/dashboard/` — Vite + React 18 + Tailwind v4 + TanStack Router skeleton
  for paying users. Routes: `/`, `/pair`, `/usage`, `/accounts`, `/billing`.
  Real backend wiring lands in RAI-27. See `apps/dashboard/README.md` if one is
  added later; for now run `cd apps/dashboard && bun install && bun run dev`.

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
- [`architecture/MODEL_PLATFORM.md`](architecture/MODEL_PLATFORM.md) — 5-layer model platform: live catalog (step 1 shipped), routing policies / segments / experiments / sandbox *(loop M+9, D-9)*

## RuneLite Plugin Hub compliance *(r10 / RAI-35)*

- [`runelite-hub/POLICY_SUMMARY.md`](runelite-hub/POLICY_SUMMARY.md) — allowed / borderline / forbidden, distilled
- [`runelite-hub/SUBMISSION_CHECKLIST.md`](runelite-hub/SUBMISSION_CHECKLIST.md) — every requirement + our status
- [`runelite-hub/PRECEDENT.md`](runelite-hub/PRECEDENT.md) — approved + rejected plugins we cite in our PR
- [`runelite-hub/PLUGIN_DESCRIPTION.md`](runelite-hub/PLUGIN_DESCRIPTION.md) — hub listing copy + `warning=` line
- [`runelite-hub/DATA_DISCLOSURE.md`](runelite-hub/DATA_DISCLOSURE.md) — every field that can leave the client
- [`runelite-hub/SECURITY_AUDIT.md`](runelite-hub/SECURITY_AUDIT.md) — verbatim grep audit, PASS/FAIL per rule *(RAI-36)*
- [`runelite-hub/THREAT_MODEL.md`](runelite-hub/THREAT_MODEL.md) — STRIDE table, mitigations, accepted risks *(RAI-36)*

## Legal (RAI-34 — NEEDS LAWYER REVIEW BEFORE LAUNCH)

- [`legal/PRIVACY.md`](legal/PRIVACY.md) — Privacy Policy draft
- [`legal/TERMS.md`](legal/TERMS.md) — Terms of Service draft
- [`legal/CONSENT_FLOW.md`](legal/CONSENT_FLOW.md) — in-plugin first-launch consent UX spec
- [`legal/SUB_PROCESSORS.md`](legal/SUB_PROCESSORS.md) — Art. 28(2) sub-processor list
- [`legal/DATA_RETENTION.md`](legal/DATA_RETENTION.md) — retention schedule + Stripe carve-out
- [`legal/COOKIE_POLICY.md`](legal/COOKIE_POLICY.md) — essential-cookies-only stance

## Marketing

- [`marketing/POSITIONING.md`](marketing/POSITIONING.md) — copy, pillar messages
- [`marketing/ASSET_CATALOG.md`](marketing/ASSET_CATALOG.md) — OSRS Wiki sprites + URLs
- [`marketing/BRAND_VOICE.md`](marketing/BRAND_VOICE.md) — picked voice ("clever friend who read the wiki") + 5 sample exchanges *(r7 / RAI-11)*
- [`marketing/NAME_CANDIDATES.md`](marketing/NAME_CANDIDATES.md) — Tibbly (pick) + Scribbins + Wikit with TM/domain checks *(r7 / RAI-11)*

## Research (continuously growing, owned by research agents)

- [`research/runelite-api/`](research/runelite-api/) — APIs we could use we haven't yet *(r-RAI5)*
  - [`research/runelite-api/_SUMMARY.md`](research/runelite-api/_SUMMARY.md) — one-page top-5-unblockers + family-tag recommendations
  - [`research/runelite-api/catalog.md`](research/runelite-api/catalog.md) — 100+ tool catalog, family-by-family, with API + Kotlin sketches + token costs
- [`research/community/`](research/community/) — OSRS/RuneLite user trends, pain points
  - [`research/community/_SUMMARY.md`](research/community/_SUMMARY.md) — one-page pain-points + creator brief *(r2)*
  - [`research/community/_naming-signals.md`](research/community/_naming-signals.md) — how OSRS tools are named in the wild *(r7 / RAI-11)*
  - [`research/community/pain-points.md`](research/community/pain-points.md) — 13 pain points with sourced evidence + top-50 plugin install corpus
  - [`research/community/creators.md`](research/community/creators.md) — J1mmy / Soup / Settled / Limpwurt / B0aty profiles + outreach principles
  - [`research/community/trends.md`](research/community/trends.md) — Leagues 6, Sailing, Varlamore, calendar opportunities
- [`research/memory-systems/`](research/memory-systems/) — how to do durable agent memory
- [`research/llm-providers/`](research/llm-providers/) — OpenRouter model + pricing
  - [`research/llm-providers/_SUMMARY.md`](research/llm-providers/_SUMMARY.md) — TL;DR + hand-off map
  - [`research/llm-providers/openrouter-catalog.md`](research/llm-providers/openrouter-catalog.md) — 12-model pricing/context table
  - [`research/llm-providers/routing-strategy.md`](research/llm-providers/routing-strategy.md) — 3-tier routing contract
  - [`research/llm-providers/cost-model.md`](research/llm-providers/cost-model.md) — per-tier per-chat math + margin
- [`research/competitor/`](research/competitor/) — other AI/OSRS tools
  - [`research/competitor/_SUMMARY.md`](research/competitor/_SUMMARY.md) — one-page summary of the AI-for-OSRS landscape (R6 / RAI-10)
  - [`research/competitor/landscape.md`](research/competitor/landscape.md) — 7 competitors documented with sourced quotes + URLs
  - [`research/competitor/differentiation.md`](research/competitor/differentiation.md) — our wedge, four-quadrant view, marketing copy hooks
- [`research/osrs-wiki/`](research/osrs-wiki/) — OSRS-themed visual asset catalog + licensing risk matrix
  - [`research/osrs-wiki/_SUMMARY.md`](research/osrs-wiki/_SUMMARY.md) — top recommendation (RuneLite BSD-2 + RuneStar CC0)
  - [`research/osrs-wiki/assets.md`](research/osrs-wiki/assets.md) — categorised catalog (250+ assets, 7 categories)
  - [`research/osrs-wiki/licensing.md`](research/osrs-wiki/licensing.md) — per-source go/no-go, loud risk warnings, Jagex Fan Content Policy
- [`research/libraries/`](research/libraries/) — library scout: which npm package to use per area
  - [`research/libraries/_SUMMARY.md`](research/libraries/_SUMMARY.md) — one-page cheat sheet of all picks
  - [`research/libraries/llm.md`](research/libraries/llm.md) — Vercel AI SDK + @openrouter/ai-sdk-provider
  - [`research/libraries/backend.md`](research/libraries/backend.md) — Hono on Bun
  - [`research/libraries/db.md`](research/libraries/db.md) — Drizzle + PGLite (dev) + Postgres (prod)
  - [`research/libraries/auth.md`](research/libraries/auth.md) — better-auth + custom device-key/pairing-code flow
  - [`research/libraries/billing.md`](research/libraries/billing.md) — Stripe Node SDK v22
  - [`research/libraries/react.md`](research/libraries/react.md) — Vite + React + TanStack Query/Router + Tailwind + shadcn + RHF + Zod
  - [`research/libraries/charts.md`](research/libraries/charts.md) — recharts
  - [`research/libraries/realtime.md`](research/libraries/realtime.md) — Bun native WS + hono/bun WS
  - [`research/libraries/testing.md`](research/libraries/testing.md) — bun:test + vitest + Claude-in-Chrome
  - [`research/libraries/logging.md`](research/libraries/logging.md) — pino + pino-pretty
  - [`research/libraries/ids.md`](research/libraries/ids.md) — nanoid + uuid v7
  - [`research/libraries/time.md`](research/libraries/time.md) — date-fns
- [`research/analytics/`](research/analytics/) — token-usage analytics at scale (RAI-37)
  - [`research/analytics/_SUMMARY.md`](research/analytics/_SUMMARY.md) — what we mimic from Langfuse / Helicone / Vercel AI SDK
  - [`research/analytics/langfuse.md`](research/analytics/langfuse.md) — trace/observation data model + async ingestion
  - [`research/analytics/helicone.md`](research/analytics/helicone.md) — real-time first dashboards + cost as integer
  - [`research/analytics/vercel-ai-sdk.md`](research/analytics/vercel-ai-sdk.md) — OTel GenAI semantic conventions

## How to use this index

- **Building agents:** read `NORTH_STAR.md`, `SCOPE_GUARD.md`, your row in
  `ASSIGNMENTS.md`, then jump to the architecture doc most relevant to your work.
- **Research agents:** read `RAW_INSTRUCTIONS.md` first, then the topic-folder.
  Every research output gets a `_SUMMARY.md` so other agents can grep without
  full-text reads.
- **Operating agent (Claude, me):** open this file at every loop start.
