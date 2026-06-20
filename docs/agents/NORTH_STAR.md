# North star — re-read every loop

This is the single source of truth for "what are we building tonight".
If something I'm about to do can't be traced to a line in this doc, I stop.

## The product, in one paragraph

**OSRS LLM Helper** is a paid SaaS that turns any LLM into a live, in-game OSRS
co-pilot. The user installs a RuneLite plugin and gets a chat that knows their
inventory, bank, quests, gear, slayer task, clue scroll, location, XP rates,
and more — and that can *act* by marking tiles, highlighting NPCs/objects,
building bank tabs, and pointing the player around the world. Our backend
drives the LLM (via OpenRouter), bills the customer (via Stripe), and
orchestrates tool calls back into their plugin over a persistent connection.

## The three must-ships by tomorrow morning (USER-CONFIRMED PRIORITIES)

**Quote (paraphrased):** "By tomorrow we have delivered three things: a productized
LLM API client backed by OpenRouter, a potentially very nice and somewhat live
marketing page, and a backend system for them to log in and add credits / track
tokens. If we decide login isn't necessary that's probably even better — but
fundamentally we must support whatever is needed to apply tokens securely
against their payments."

1. **Productized client connected to backend.** The current RuneLite plugin no
   longer calls `claude -p` locally — it opens a connection to OUR backend,
   which drives OpenRouter. Tools still execute locally inside the plugin
   (that's where game state lives), but the chat loop, token accounting, and
   the model itself are server-side. Frictionless onboarding.

2. **Marketing page.** Nice, somewhat live (real-time "X agents online"
   counter etc.), RuneScape-native visual feel using OSRS Wiki sprites.
   Hero, problem/solution, pricing, demo, FAQ. Must explain why a player
   pays. Must look good to a first-time visitor.

3. **Backend system** for users to log in (or not — see frictionless note),
   add credits, see token usage, manage their subscription / billing.
   Secure mapping from payment → tokens available → token consumption.

**Login is conditional.** If we can achieve secure payment + per-user token
tracking *without* a login screen (e.g. by binding the device-key the plugin
generates to a Stripe customer via a one-time browser handshake), that is
preferred. If we need a login to make payments + token gating safe, do it.
Either way, the system must:
- Stop chat when credits run out.
- Attribute every OpenRouter call to a paying customer.
- Be impossible to abuse by re-using someone else's device key.

## Supporting must-ships (because the three above require them)

4. **Monorepo restructure.** Current plugin → `apps/plugin/`. Add
   `apps/backend/`, `apps/dashboard/`, `apps/marketing/`,
   `packages/shared-types/`, `docs/`, `infra/`. Root scripts work.
5. **Token economy.** Tool gating: small "core" always-on, the rest behind
   keyword routing + an `enable_tools(family)` meta-tool. Drop turn-1 tool
   surface from ~8K tokens to ≤1.5K. This is how we keep margin.
6. **Stripe wiring.** Subscription tiers + metered top-ups. Webhook → DB.
7. **Presence/network feed.** WebSocket "X agents online" for marketing
   real-time feel (low priority but high marketing ROI).

## Nice-to-haves (deferrable)

- Full E2E test coverage of the dashboard.
- Detailed competitor research.
- Polished blog/docs page.
- Mobile responsiveness of the dashboard.

## The three hard constraints

- **Stack:** Bun + TypeScript + React + Tailwind. PGLite (dev) → Postgres (prod).
- **LLM:** OpenRouter only. No `claude -p` in the runtime path. Default models:
  Haiku 4.5 routing, Sonnet 4.6 chats, Opus 4.7 premium-tier deep reasoning.
- **Visual language:** OSRS Wiki imagery is the marketing aesthetic. Even
  when the wiki's overall layout isn't great, their sprite library is.

## The three explicit non-goals

- **No in-game automation.** We help the player — we don't play for them.
  No keyboard/mouse synthesis. No bot logic.
- **No real-money trading helpers.** No price-flipping bots that auto-execute,
  no "make money fast" features that violate game rules.
- **No client modifications outside the plugin sandbox.** We are a normal
  RuneLite plugin. We do not patch the game client.

## What "we succeeded tonight" looks like

- `bun install && bun run dev` at the repo root brings up backend + dashboard
  + marketing locally.
- The plugin builds with `./gradlew shadowJar` and connects to local backend.
- A test user can register a device key, pair to an OSRS account, send a chat
  message to the backend, watch the agent call a (mocked) tool, see the token
  cost.
- Marketing site has at least: hero, problem/solution, pricing tiers, demo
  block, FAQ. OSRS Wiki sprites used in ≥3 sections.
- Dashboard has at least: login, usage chart, account binding, Stripe portal link.
- Token-per-turn baseline is documented and budget cap is enforced.
- Every agent's work is committed, on a feature branch, with passing tests.

## Anchors when in doubt

When picking between options: choose the path that
1. Ships value the user can SEE tonight.
2. Keeps token cost per chat as low as possible.
3. Makes onboarding more frictionless.
4. Matches the OSRS-native visual language.
5. Stays inside the three hard constraints.
