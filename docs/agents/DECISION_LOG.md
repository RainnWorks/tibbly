# Decision log — append only

## D-1 — Loop cadence: 20 minutes — 2026-06-21

**Context:** User initially said 2 min; later clarified 20 min.
**Chosen:** 20 minutes via ScheduleWakeup `delaySeconds: 1200`.
**Rationale:** User-stated. Also: most workflow stages take >15 min; 2 min
wakeups would burn cache and add no signal.
**Reversible?:** trivially.

## D-2 — Tech stack: Bun + TypeScript + React + Tailwind + Drizzle — 2026-06-21

**Context:** User specified Bun + React + Tailwind. Need an ORM + framework.
**Chosen:** Hono for HTTP + Bun's native WebSocket. Drizzle ORM for type-safe
SQL. Zod at boundaries. Vite for frontends. PGLite for dev DB.
**Rationale:** Lowest friction with Bun; Hono runs on Bun natively; Drizzle
plays nicely with both SQLite (PGLite) and Postgres.
**Reversible?:** Moderate — switching frameworks later is painful.

## D-3 — Identity model: device-key + pairing code, no email by default — 2026-06-21

**Context:** User wants frictionless auth via RuneLite identity. RuneLite
itself doesn't sign anything for us.
**Chosen:** Plugin generates a long-lived device key at install. The user
pairs the device with a billing account via a one-time 6-digit code shown
in-game and entered on the dashboard. One Stripe customer can own many
device keys (≈ many OSRS accounts).
**Rationale:** Zero email signup. The dashboard handshake is one-time.
**Reversible?:** yes — we can layer a magic-link email later if needed.

## D-4 — Stripe model: subscription + metered token top-ups — 2026-06-21

**Context:** Need both predictable monthly revenue and overage protection.
**Chosen:** Three subscription tiers (Hobbyist / Pro / Iron). Each tier
includes a monthly token quota. Overage: Stripe metered usage at a per-1K
rate. Stop-at-zero unless customer enables auto-top-up.
**Rationale:** Familiar pattern; protects margin.
**Reversible?:** yes.

## D-5 — Hide the duplicate tools rather than rename — 2026-06-21

**Context:** Plugin has duplicate `get_slayer_task` and `get_xp_rates`
registrations.
**Chosen:** Token Optimizer (B4) will merge the data into single tools with
combined fields. Drop the old `tools.slayerTask()` / `tools.xpRates()` once
their data is folded into the integration-based versions.
**Reversible?:** yes.

## D-6 — Three deliverables, login conditional — 2026-06-21

**Context:** User said the three must-ships are productized client,
marketing page, backend with login. Login conditional if frictionless.
**Chosen:** Build with login optional from the start. Device-key flow has no
login screen. Add `/login` route only as a recovery path (for users replacing
their machine). Stripe Customer Portal is the billing UX.
**Reversible?:** yes.

## D-7 — Operating model: CTO + Linear MCP + companion personality lane — 2026-06-21

**Context:** User said "I don't care how you do the sub agents" + "act as CTO"
+ "use the linear MCP to create tasks" + "it's a companion, should feel like
a friend — or should it? personality aspect to this".
**Chosen:**
- Operate as the CTO — I own pace, agent choreography, and trade-offs.
- Track work as Linear issues when the Linear MCP becomes available
  (currently not loaded — placeholder: log to STATUS.md, then create issues
  on the next loop after `ToolSearch` returns linear tools).
- Add an explicit **personality / brand voice** task. Default position: the
  agent has a quietly competent OSRS-veteran tone — knows the lore, makes
  occasional callbacks ("don't be a noob"), but never gets in the way.
  Skews slightly companion ("we're in this together") rather than tool
  ("here is your data"). Test against power-user opinions in R2 research.
- Branding: "RuneCanine"? "Cleverbird"? Provisional name TBD by marketing
  agent; for now the product is `osrs-llm-helper` in docs.
**Reversible?:** yes.
