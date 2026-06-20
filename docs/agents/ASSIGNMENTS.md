# Agent assignments

We run 13 agents organized into 3 stages. Each agent has a Mission, In-Scope,
Out-of-Scope, Inputs (docs to read), Outputs (files to produce), and Acceptance
criteria. Each agent's prompt MUST instruct it to read NORTH_STAR.md and
SCOPE_GUARD.md before doing anything.

## Stage 1 — foundations (must finish first or quickly produce stubs others can build on)

### A1 · Architect (monorepo + dev loop)

- **Mission:** Transform this repo into a Bun-powered monorepo with the
  current Kotlin plugin moved to `apps/plugin/` and new app/package skeletons.
- **In-scope:**
  - Top-level `package.json` with workspaces.
  - `apps/{plugin,backend,dashboard,marketing}/`, `packages/shared-types/`,
    `packages/osrs-assets/`, `infra/docker/`.
  - Root scripts: `bun install`, `bun dev`, `bun test`, `bun lint`.
  - Shared ESLint/Prettier/TS configs in `packages/tooling/`.
  - Move (`git mv`) the existing Kotlin plugin files into `apps/plugin/`,
    update gradle paths, keep `./gradlew build` working from inside there.
  - One root README that points at INDEX.md.
- **Out-of-scope:** Writing any backend logic, frontend UI, marketing copy.
- **Outputs:** the structure above + a passing `bun install && bun run typecheck`.
- **Acceptance:** all four apps + both packages build at least to empty types.
  Plugin shadowJar still works.

### A2 · Memory & docs steward

- **Mission:** Maintain the living memory system across the night.
- **In-scope:**
  - Maintain `docs/INDEX.md` (auto-add every new doc).
  - Maintain `docs/agents/STATUS.md` (update each loop with agent statuses).
  - Maintain `docs/agents/LOOP_LOG.md` (append per loop).
  - Maintain `docs/agents/DECISION_LOG.md` (append every decision).
  - Maintain `docs/agents/OPEN_QUESTIONS.md` (append questions for user).
  - Maintain `docs/agents/HANDOFF.md` (always current for user wakeup).
  - Watch for spec/scope drift; alert in OPEN_QUESTIONS.
- **Out-of-scope:** Writing product code.
- **Acceptance:** every other agent's output is reflected in STATUS.md, every
  decision in DECISION_LOG.md, every gap in OPEN_QUESTIONS.md.

## Stage 2 — research + product spec (in parallel, feed Stage 3)

### R1 · RuneLite API explorer

- **Mission:** Catalog every public RuneLite + sibling-plugin API we could
  use that we haven't yet. Output prioritized list of new MCP tool ideas.
- **In-scope:** Read `~/.gradle/caches/.../runelite-api` + `client` sources
  (already extracted at `/tmp/rl-src` earlier — re-extract if missing).
  Cover: events bus, varbits, varclient, animations, music tracks, world map
  state, leagues/seasonal, group ironman, hiscore client, gloves of silence,
  prayer flickering, achievement diaries. Group findings.
- **Output:** `docs/research/runelite-api/catalog.md` with each finding's
  source path, signature, suggested MCP tool, estimated token cost.
- **Acceptance:** ≥15 findings, each with a one-line "why a player would care".

### R2 · Community / market research

- **Mission:** Where is the OSRS/RuneLite community heading; what pain points
  could an AI co-pilot solve. Inform marketing + product.
- **In-scope:** WebSearch + WebFetch on r/2007scape, r/runelite, OSRS Discord
  trends (public), notable creators (Soup, J1mmy, Settled, Limpwurt). Find:
  most-asked questions, most-followed guides, common quest blocker complaints,
  iron man pain points, leagues meta-discussion, what features Jagex has
  shipped recently, common UI/QoL complaints.
- **Output:** `docs/research/community/{pain-points.md, trends.md, creators.md}`.
- **Acceptance:** ≥3 distinct pain points with a one-line "how our product
  addresses it".

### R3 · OSRS Wiki asset catalog

- **Mission:** Build a catalog of OSRS Wiki sprites + image URLs we can use in
  marketing + dashboard. License check (Wiki content is CC-BY-NC-SA 3.0 —
  attribution required).
- **In-scope:** Equipment sprites, NPC images, skill icons, item icons,
  background textures, the in-game font (Runescape font is free for non-
  commercial use; check trademark before using in commercial).
- **Output:** `docs/research/osrs-wiki/assets.md` with categorized URL list,
  attribution rules. `packages/osrs-assets/` index of locally-cached sprites.
- **Acceptance:** ≥50 catalogued sprites + a clear licensing note.

### R4 · Memory system / autonomous loop research

- **Mission:** Research how teams build durable memory for autonomous looping
  AI agents (Letta/MemGPT, CrewAI memory, AutoGen memory, AgentOps, etc).
  Output recommendation for our long-term memory layer that can drive future
  things like GitHub Issues.
- **Output:** `docs/research/memory-systems/recommendations.md`.
- **Acceptance:** Compares ≥3 approaches; picks one for our needs.

### R5 · LLM provider / token economics research

- **Mission:** OpenRouter model catalog, pricing per 1K input/output, latency
  buckets. Model selection strategy per chat turn (cheap classifier → mid →
  premium). Identify the "happy path" cost per chat.
- **Output:** `docs/research/llm-providers/openrouter.md` with a table.
- **Acceptance:** A documented cost-per-chat estimate for the 3 tiers.

### R6 · Gaps analyst

- **Mission:** Continuously read what other agents are producing and flag
  inconsistencies / missing pieces / contradictions. Output gap report each
  loop.
- **Output:** `docs/agents/GAPS.md`, refreshed each loop.
- **Acceptance:** Runs every loop, output never older than 30 min.

## Stage 3 — building (depends on Stage 1 skeleton)

### B1 · Backend Core (Bun + Hono + Drizzle)

- **Mission:** Build the production server. Chat orchestration, OpenRouter
  proxy, WebSocket for plugin/dashboard, REST for billing webhooks. SQLite
  via PGLite locally, Postgres in prod. Drizzle ORM. Zod boundary validation.
- **In-scope:**
  - `apps/backend/src/server.ts` — Hono app.
  - `apps/backend/src/db/` — schema + migrations (users, devices, sessions,
    chats, messages, tool_calls, usage_records, subscriptions).
  - `apps/backend/src/llm/openrouter.ts` — provider client + retries.
  - `apps/backend/src/ws/plugin.ts` — protocol with the plugin.
  - `apps/backend/src/ws/dashboard.ts` — presence + live usage feed.
  - `apps/backend/src/auth/device-key.ts` — pairing flow.
  - Bun tests for each module.
- **Out-of-scope:** UI work, marketing copy, Stripe webhook UI.
- **Acceptance:** `bun test` passes; `bun run dev` brings up a server that
  accepts a WebSocket from the plugin and a REST POST from the dashboard.

### B2 · Backend Billing (Stripe + metering)

- **Mission:** Subscription + metered billing. Tokens deducted per call.
- **In-scope:**
  - Stripe test-mode keys via `.env`.
  - Webhook receiver: `checkout.session.completed`, `customer.subscription.*`,
    `invoice.*`.
  - Metering: per-user token balance, decrement on each OpenRouter call.
  - Hard cap when balance ≤ 0 (return a friendly error to the plugin).
  - Stripe Customer Portal link generation.
- **Acceptance:** End-to-end test using Stripe CLI replay events shows balance
  going up on subscription, down on token spend.

### B3 · Plugin Migration (RuneLite plugin → backend)

- **Mission:** Migrate the Kotlin plugin's chat path from local `claude -p`
  to a WebSocket connection to our backend.
- **In-scope:**
  - New `BackendClient.kt` that opens WS to `wss://api.osrsllm.app/v1/chat`
    (overridable via `.env`).
  - Protocol: client sends `user_message`, server sends `tool_call_request`,
    client executes via MCP, returns `tool_call_result`. Server streams
    `assistant_message_delta`.
  - Auth: send the device key + active OSRS player name each session.
  - Token budget shown in the existing chat sidebar.
  - Keep the local MCP server for tool execution — that doesn't change.
- **Out-of-scope:** Anything other than the chat-loop transport.
- **Acceptance:** Existing chat panel works end-to-end against local backend.

### B4 · Token Optimizer

- **Mission:** Implement tool gating and keyword routing to drop turn-1 tool
  surface from ~8K to ≤1.5K tokens.
- **In-scope:**
  - In the plugin: tag every MCP tool with a `family`. Expose a meta-tool
    `enable_tools(family)` that returns the family's tool descriptions on
    demand. Add a `ContextRouter` that picks default families per turn
    using keyword rules.
  - In the backend: per-turn `allowedTools` filter list passed to OpenRouter.
  - Logging: every chat records `toolsExposed`, `toolsInvoked`, `inputTokens`,
    `outputTokens`.
- **Acceptance:** A test chat shows a turn-1 prompt-token count ≤1500
  (excluding user message + preamble).

### F1 · Dashboard (React + Vite + Tailwind)

- **Mission:** The logged-in user portal.
- **In-scope:**
  - Vite + React 18 + Tailwind + React Router.
  - Routes: `/`, `/login` (pairing-code entry), `/usage`, `/accounts`
    (OSRS-account bindings), `/billing` (Stripe Portal redirect).
  - Auth via the device-key flow (no email/password unless absolutely needed).
  - OSRS-wiki visual cues throughout (skill icons next to xp metrics, etc.).
- **Acceptance:** `bun run dev` from `apps/dashboard/` shows the routes with
  mocked data.

### F2 · Marketing site

- **Mission:** RuneScape-native landing page with the "X agents online" live
  feel.
- **In-scope:**
  - Hero with animated plugin demo (CSS or short video; SVG-of-OSRS-UI ok).
  - Problem / Solution sections using OSRS Wiki sprites.
  - Pricing tiers with sample chats.
  - "Live network" widget that polls the backend's presence WS.
  - FAQ. Footer with NOTICE/attribution.
  - SEO basics: meta tags, sitemap, opengraph card.
- **Acceptance:** Lighthouse score ≥85 on Performance, ≥95 Accessibility,
  ≥95 SEO. Hero loads in <1s on local.

### F3 · Realtime / Network ("X agents online")

- **Mission:** Build the live presence layer that feels alive on the marketing
  page.
- **In-scope:**
  - Backend: presence WS endpoint, per-region aggregation, redacted public
    feed.
  - Frontend: live counter + lightweight world-map widget.
- **Acceptance:** Two browser tabs simulating clients both increment the
  marketing-page counter.

### Q1 · QA / E2E (Claude in Chrome)

- **Mission:** Smoke-test the dashboard + marketing site end-to-end using
  Claude-in-Chrome. Run regression each loop.
- **In-scope:**
  - Scenario: open marketing site, click "Sign in", enter a test pairing code,
    land on dashboard, see usage chart.
  - Visual diff snapshots when things break.
- **Acceptance:** Reports failures to STATUS.md; auto-files items in
  OPEN_QUESTIONS.md.

## Cross-cutting rules for every agent prompt

1. Read `docs/agents/NORTH_STAR.md` and `docs/agents/SCOPE_GUARD.md` BEFORE
   doing anything.
2. Stay strictly inside your In-Scope list.
3. Append your decisions to `docs/agents/DECISION_LOG.md`.
4. Append any new doc paths to `docs/INDEX.md`.
5. Update `docs/agents/STATUS.md` with your final state.
6. NEVER pause. NEVER ask the user a question. If blocked, pick the
   lowest-regret default and log it in `OPEN_QUESTIONS.md`.
7. Commit on a feature branch named `agent/<your-id>/<short-desc>`. Don't
   push unless the work is green.
