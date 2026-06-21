# Claude operating instructions — osrs-llm-helper

These instructions OVERRIDE any default behavior. Read fully before acting.

## NORTH STAR — DO NOT STOP

The user, Tom, has handed off this session for an overnight build. From this point on:

- **Never wait. Never pause.** Do not say "I'm good to go" / "let me know when".
  Do not ask questions of the user — they are unavailable for 6–8 hours.
- **Loop every 2 minutes** via ScheduleWakeup with `delaySeconds: 120` (or via /loop).
  Each loop: check agent statuses, kick off next-stage agents, unblock anything stuck,
  do additional work. Never let the loop go idle.
- **When a workflow completes**, immediately spawn the next phase. There is always
  more work — refining specs, writing more tests, polishing UI, adding edge cases.
- **Agents work all night too.** Their prompts must say "do not stop, take initiative,
  expand scope where you find adjacent improvements".
- **If you somehow run out of tasks**, re-read the docs in `docs/product/` and
  `docs/architecture/` and find a place to deepen. Add tests. Add docs. Add polish.
  Hunt for inconsistencies. Improve type-safety. Improve observability. Re-test E2E.

## PRODUCT AT A GLANCE

We are turning the current local-only RuneLite plugin (an MCP server that exposes
live OSRS game state to a local `claude -p`) into a **paid SaaS**:

- **Backend** drives the LLM via **OpenRouter** (key in `.env`, already wired).
- **Bun + TypeScript + React + Tailwind** for backend and frontend.
- **PGLite** for local dev DB, **Postgres** for prod.
- **Stripe** for subscription billing + usage metering.
- **Auth via the player's RuneLite identity** — we want this as frictionless as
  possible. Multiple OSRS accounts can be tied to one paying customer.
- **The plugin** still runs the MCP tool surface locally inside RuneLite (because
  that is where the game state lives) but the chat / LLM loop is driven by our
  backend, which orchestrates tool calls back over a persistent connection.
- **Marketing site** must feel RuneScape-native: lean on OSRS Wiki imagery
  (equipment sprites, NPC art) and grid layouts that evoke the game UI.
  Animations welcome.

Token budget is a P0 concern: 72 game-state MCP tools + 1 `enable_tools`
meta-tool × ~120 tokens each = ~8K tokens before the first user word.
Every paying chat starts that deep in the hole. We must:

- Gate tool families behind keyword / intent routing.
- Pre-fetch frequently-needed state into a compact preamble.
- Track token usage per user per chat for billing.

## MONOREPO LAYOUT (target)

```
osrs-llm-helper/
├── apps/
│   ├── plugin/         # current Kotlin/Gradle RuneLite plugin (was the repo root)
│   ├── backend/        # Bun service: chat orchestration, billing, auth
│   ├── dashboard/      # React app — logged-in user portal
│   └── marketing/      # React app — landing / pricing / blog
├── packages/
│   ├── shared-types/   # TS types shared between backend + frontends
│   ├── osrs-assets/    # OSRS Wiki sprite catalog + helpers
│   └── tooling/        # ESLint / TS configs / scripts
├── docs/
│   ├── product/        # vision, positioning, FAQ
│   ├── architecture/   # system, identity, billing, token-economy, runtime
│   ├── agents/         # agent briefs + status board
│   └── marketing/      # copy + visual brief
└── infra/
    ├── docker/         # compose for local stack
    └── deploy/         # Fly / Render / Vercel scripts
```

## AGENT TEAM

We run 10 parallel agents via Workflow. See `docs/agents/ASSIGNMENTS.md`.

Roughly: Architect (monorepo skeleton), Backend Core (Bun service), Backend
Billing (Stripe + metering), Frontend Dashboard (React), Marketing Site,
Plugin Migration (RuneLite plugin → backend WS), Identity & RuneLite Auth,
Token Optimizer (tool gating + routing), Realtime / Network (live agents,
"X clients online" globe), QA / E2E (Claude in Chrome), RuneLite API Explorer
(go DEEP on every RuneLite/RL plugin API and surface things we haven't used —
animations, world map, music tracks, group iron man state, leagues, varbits,
walker hooks, npc dialog overlay, anything we missed).

## TESTING DISCIPLINE

- Every agent has a test brief. If a unit test can be written, it must be written.
- Every browser-facing thing gets a Claude-in-Chrome E2E smoke.
- The only things we can't test pre-handback are the post-login OSRS in-game
  interactions, because we can't authenticate as a player. Document those test
  cases for manual verification.

## LIVING DOCUMENTATION INDEX

This CLAUDE.md is the entry point. From here, every agent navigates by reading
`docs/INDEX.md` — a maintained index of every doc + research note in the repo.
Agents update INDEX.md when they create a new doc.

```
docs/
├── INDEX.md                   # ← the index every agent reads first
├── product/                   # vision, raw user instructions, personas, pricing
├── architecture/              # system, identity, billing, tool-economy, monorepo
├── marketing/                 # positioning, copy, asset catalog (OSRS Wiki sprites)
├── agents/                    # north star, scope guard, status, loop log,
│                              # decision log, open questions, memory architecture
│                              # ASSIGNMENTS.md (per-agent brief)
└── research/                  # ← continuously growing, owned by research agents
    ├── runelite-api/          # what the RuneLite client + plugins expose
    ├── community/             # OSRS/RuneLite user trends, pain points, content
    ├── memory-systems/        # how autonomous looping agents should remember state
    ├── llm-providers/         # OpenRouter model catalog, pricing, latency
    ├── competitor/            # other AI/OSRS tools in the wild
    └── osrs-wiki/             # asset/imagery catalog with URLs we can use
```

## SOURCE-OF-TRUTH DOCS (write + maintain these)

- `docs/product/VISION.md` — why this exists, who pays, the bet.
- `docs/product/PERSONAS.md` — who the buyer is.
- `docs/product/PRICING.md` — tiers, metering, free trial shape.
- `docs/architecture/SYSTEM.md` — runtime architecture, sequence diagrams.
- `docs/architecture/IDENTITY.md` — auth flow, RuneLite proof, multi-account.
- `docs/architecture/BILLING.md` — Stripe model, usage metering, hard caps.
- `docs/architecture/TOOL_ECONOMY.md` — tool gating, routing, telemetry.
- `docs/architecture/MONOREPO.md` — layout, build, lint, dev scripts.
- `docs/marketing/POSITIONING.md` — copy, pillar messages, asset catalog.
- `docs/agents/ASSIGNMENTS.md` — per-agent brief + acceptance criteria.
- `docs/agents/STATUS.md` — live status board, updated each loop.

## KEY OPEN QUESTIONS (DO NOT BLOCK ON THESE — pick the reasonable default and proceed)

1. **RuneLite identity proof.** RuneLite doesn't sign anything cryptographically for
   us. Best we can do: at plugin install, generate a long-lived device key; on each
   chat session, surface the in-game player name from `Client.localPlayer.name`
   alongside that key. The user binds those (device_key, player_name) tuples to
   their billing account through the dashboard. We can also use a one-time pairing
   code shown in-game to let the user link from the web dashboard. Default to that.
2. **Free tier.** Start with: 30 messages/day on cheap model, watermarked replies.
3. **Paid tier price points.** Default: $7 / $19 / $49 monthly for hobbyist /
   pro / iron. Pro = serious GIM groups + content creators. Adjust based on costs.
4. **Where to host.** Default: Fly.io for backend, Vercel for marketing, Neon for
   prod Postgres. Easy to flip later.
5. **Model selection.** Default: Haiku 4.5 for routing/cheap turns, Sonnet 4.6 for
   complex turns. Premium tier unlocks Opus 4.7 for hard quest walkthroughs.

## SECURITY & SAFETY

- Never commit `.env`. Already in `.gitignore` via wildcard pattern — verify.
- Treat the `OPENROUTER_API_KEY` as production-grade — never embed in plugin
  source, never echo to logs.
- All player→backend traffic over TLS. WebSocket for the live chat link.
- Rate-limit by device key. Hard cap by Stripe tier.

## STYLE FOR THIS REPO

- Backend: Bun + Hono (lightweight), Drizzle ORM, Zod for boundary validation.
- Frontend: React 18 + Tailwind + Vite. Shadcn-style primitives only where it
  speeds us up — otherwise hand-craft to match OSRS visual language.
- Tests: Bun test for backend, Vitest for frontend, Playwright (via Claude in
  Chrome) for E2E.
- No premature abstractions. The current Kotlin plugin's style of "ship the
  concrete thing, generalize later" carries over.

## REMINDERS TO SELF DURING THE LOOP

1. Are all background agents healthy? Check task list.
2. Did anyone complete? If so, kick off the next phase or follow-up.
3. Have I added test coverage in the last N minutes? If not, schedule a test pass.
4. Is the marketing site progressing? UI work is easy to neglect.
5. Have I checked the OSRS Wiki for fresh asset ideas / copy hooks?
6. Is the token-economy story tightening? It's the long-term margin lever.
