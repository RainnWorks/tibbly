# North star -- re-read every loop

This is the single source of truth for "what are we building".
If something I'm about to do can't be traced to a line in this doc, I stop.

## Operating contract (still in force)

- Never wait. Never pause. Do not ask the user; pick the reasonable default
  and proceed.
- Loop on cadence per CLAUDE.md. Each loop: check agent statuses, kick off
  next-stage agents, unblock anything stuck, do additional work. Never let
  the loop go idle.
- When a workflow completes, immediately spawn the next phase. There is
  always more work -- refining specs, writing more tests, polishing UI,
  adding edge cases.
- If you somehow run out of tasks, re-read the docs in `docs/product/` and
  `docs/architecture/` and find a place to deepen. Add tests. Add docs.
  Add polish. Hunt for inconsistencies. Improve type-safety. Improve
  observability. Re-test E2E.

## Post-pivot anchors (loop M+10)

This file was originally written before the M+2/M+3 pivot. The lines below
are the canonical updates; if anything in this doc disagrees with them, the
anchor wins.

- **D-8** (`docs/agents/DECISION_LOG.md`) -- user dashboard de-prioritised;
  `apps/dashboard` repurposed as Tibbly's internal ops console
  (`apps/ops`); token-spend visibility removed from the user UI;
  whole-site auth wall on the ops app; taste-skill applies to every UI
  commit.
- **D-9** + **MODEL_PLATFORM.md** -- no model id is ever hardcoded in
  plugin, backend, or marketing copy. Every routing decision is a DB row.
- **D-10** + **LICENSING.md** + **REPO_SPLIT.md** -- hybrid licensing
  (MIT plugin, proprietary backend); repos rename to
  `RainnWorks/tibbly-plugin` and `RainnWorks/tibbly-platform`.
- **D-11** -- public pricing locked in GBP; backend tier table stores
  pence; Stripe production price ids are GBP.
- **HUB_RELEASE_STRATEGY.md** -- three-tier value floor in the same plugin
  binary (tools-only / BYOK / Tibbly cloud); single outbound WSS egress;
  no localhost HTTP listener.
- **EMBODIED_COMPANION.md** + **SOCIAL_COMPANION.md** -- Tibbly is a
  presence-in-RuneLite product, not a chat sidebar. The companion is the
  relationship product; the social fabric is the moat.

## The product, in one paragraph

**Tibbly** is a paid SaaS that turns the player's RuneLite client into a
live, in-game OSRS companion. The user installs a RuneLite plugin (open
source, MIT) and the companion chats, marks tiles, highlights NPCs,
remembers their preferences across sessions, and points them around the
world. Our backend drives the LLM (via OpenRouter), bills the customer
(via Stripe in GBP), and orchestrates tool calls back into their plugin
over a single outbound WebSocket egress. The web surface is a private ops
console for Tibbly staff; the player manages their account from inside
RuneLite.

## The three must-ships (post-pivot framing)

1. **Productized client connected to backend (M1).** The RuneLite plugin
   no longer calls `claude -p` locally -- it opens a single outbound WSS
   to OUR backend, which drives OpenRouter. Tools execute locally inside
   the plugin (that's where game state lives), but the chat loop, token
   accounting, and the model itself are server-side. Frictionless
   onboarding. Three-tier value floor inside the same binary
   (tools-only / BYOK / Tibbly cloud) per HUB_RELEASE_STRATEGY.md.

2. **Marketing page (M2).** Nice, somewhat live (real-time "X agents
   online" counter), RuneScape-native visual feel using BSD-2 RuneLite +
   CC0 RuneStar sprites (NOT wiki imagery in the paid product). Hero,
   problem/solution, free strip, pricing, demo, FAQ. Must explain why a
   player pays. Public; no auth wall.

3. **Tibbly ops + in-plugin account panel (M3).** The player manages
   their account from inside RuneLite via the Tibbly plugin panel
   (`apps/plugin/.../AccountPanel.kt`, PR #40) -- pair device, switch
   OSRS account, see subscription status, request GDPR export/delete,
   open Stripe portal. Tibbly's internal ops team manages users from the
   auth-walled web app at `apps/ops/` (PR #43) -- user search, ban,
   refund, credit grant, revenue/spend dashboards. **No user-facing web
   dashboard.** Token-spend numbers are ops-only; the player sees
   tier-aware proxies like "23/30 messages used today".

**Login is conditional.** The pairing flow is "in-game HUD shows a
six-digit code; player enters it once into Stripe Checkout or the plugin
panel". No email signup unless we need a recovery path. The system must:

- Stop chat when credits run out (hard cap at zero, no overage by
  default).
- Attribute every OpenRouter call to a paying customer.
- Be impossible to abuse by reusing someone else's device key.

## Supporting must-ships (status loop M+10 -- historical, all shipped)

The four items below were the original supporting must-ships in the
launch-night frame. All four are now structurally complete per
`docs/agents/HANDOFF.md` § "What shipped". Listed here for traceability;
do NOT treat them as open work.

- ~~Monorepo restructure~~ -- shipped (RAI-13).
- ~~Token economy~~ -- shipped (RAI-25; turn-1 ≤1.5K tokens).
- ~~Stripe wiring~~ -- shipped (RAI-19 / RAI-20).
- ~~Presence/network feed~~ -- shipped (RAI-21).

## Nice-to-haves (deferrable)

- Full E2E test coverage of the ops console.
- Detailed competitor research (R10 covers the baseline).
- Polished blog/docs page.
- Mobile responsiveness of the ops console.

## The three hard constraints

- **Stack:** Bun + TypeScript + React + Tailwind. PGLite (dev) → Postgres (prod).
- **LLM:** OpenRouter only. No `claude -p` in the runtime path. No model
  id is hardcoded anywhere -- model choice is a routing-policy DB row
  resolved per (segment, intent) per D-9. Marketing copy uses
  tier-language ("routing tier", "deeper-reasoning tier", "long-horizon
  tier"), not slugs.
- **Visual language:** OSRS-native via BSD-2 RuneLite-extracted +
  CC0 RuneStar sprites in the paid product. Wiki imagery is fair-use in
  research notes only.

## The three explicit non-goals

- **No in-game automation.** We help the player -- we don't play for them.
  No keyboard/mouse synthesis. No bot logic.
- **No real-money trading helpers.** No price-flipping bots that
  auto-execute, no "make money fast" features that violate game rules.
- **No client modifications outside the plugin sandbox.** We are a
  normal RuneLite plugin. We do not patch the game client.

## What "we succeeded" looks like

- `bun install && bun run dev` at the repo root brings up backend + ops
  console + marketing locally.
- The plugin builds with `./gradlew shadowJar` and connects to local
  backend through a single outbound WSS (no localhost HTTP listener;
  hub-eligible).
- A test user can install the plugin, pair to an OSRS account from
  inside RuneLite, send a chat message to the backend, watch the agent
  call a tool, see the tier-aware "messages used today" proxy update.
- Marketing site has hero, problem/solution, free strip, pricing (three
  visible tiers + Iron footnote per IA.md), demo block, FAQ. Sprite
  catalog used in ≥3 sections.
- The ops console (auth-walled) shows revenue (GBP), spend (USD),
  margin, per-model breakdown, user search with ban/refund/credit
  actions.
- Token-per-turn baseline is documented and budget cap is enforced.
- Every agent's work is committed, on a feature branch, with passing tests.

## Anchors when in doubt

When picking between options: choose the path that
1. Ships value the player can SEE.
2. Keeps token cost per chat as low as possible.
3. Makes onboarding more frictionless (in-RuneLite pairing > web auth).
4. Matches the OSRS-native visual language.
5. Stays inside the three hard constraints.
6. Honours the post-pivot anchors above.
