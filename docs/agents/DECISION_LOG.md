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

## D-8 — Product pivot: dashboard de-prioritised; backend becomes Tibbly ops console; hide token-spend from users — 2026-06-21 (loop M+2/M+3 boundary)

**Context:** Tom reviewed the overnight build on the morning of 2026-06-21
and called the user dashboard "LLM slop" visually + conceptually:

- "the backend for managing the subscription doesnt really work
  colour/font wise, it reads a lot like LLM slop"
- "you probably should be gated on the whole thing"
- "token spend isnt interesting to them. the users don't understand
  what token spend is and nor should they have to. That's something
  that we need."
- "I want an observability backend platform for us to manage the users,
  ban people, et cetera, as we might need to manage the platform itself."
- "realistically all of that should probably just be done in the
  RuneLite UI. The backend if it exists needs to be very interactive
  and basically allow for or enhance the experience as opposed to be
  something that users have to manage themselves separately."
- Pointed at https://github.com/Leonxlnx/taste-skill ("go and use this
  guy") for the design fix.

**Chosen:**

1. **User-facing dashboard de-prioritised.** Most account management
   (pair, switch OSRS account, see subscription, see usage proxy)
   moves into RuneLite plugin panels in a follow-up. The web app at
   `apps/dashboard` is repurposed as **Tibbly's internal ops console**.
2. **Token-spend visibility removed from user UI.** Players see
   tier-aware proxies — "23/30 messages used today", "subscription
   active — renews on Dec 14", "out of messages — upgrade or wait
   until tomorrow". Iron tier shows no scary counter at all unless we
   actually hit a cap. Internal ops keeps the raw token math.
3. **Whole-site auth wall on the web app.** No public marketing-style
   pages mixed with the ops console; if you can see it, you're already
   on the `ADMIN_EMAILS` allow-list.
4. **Ops console feature set:** user search/list, ban toggle,
   refund/credit-grant action, paying-customer debug view, plus the
   existing RAI-37 analytics (cost / funnel / errors / realtime).
5. **Taste-skill applies to every UI commit going forward.** Mirrored
   to `.claude/skills/taste-skill/SKILL.md`. Declare design read +
   3 dials (DESIGN_VARIANCE / MOTION_INTENSITY / VISUAL_DENSITY)
   before writing UI code. Pre-flight checklist before shipping.
   Hard bans: no Inter default, no em-dashes, no 3-equal-card grids,
   no beige+brass+oxblood, no fake div screenshots, no hand-rolled
   icon paths.
6. **Marketing page stays public.** Only the dashboard/ops gets the
   auth wall.
7. **Plugin-side work continues unblocked.** The RAI-5 catalog
   unblockers agent (in flight at this writing) is plugin-tool work
   and unaffected by the pivot.

**What this does NOT change:**

- `/v1/me` (GDPR Art. 15 / Art. 17) endpoints stay — legal/compliance
  requirement regardless of who consumes them. PR #34 is the rewrite.
- `/v1/pairing`, `/v1/usage`, `/v1/accounts`, `/v1/billing` endpoints
  stay on the backend; their consumers shift from the web dashboard
  to RuneLite plugin panels.
- The marketing site (`apps/marketing`) brand + copy + asset choices.
- The plugin's hub-compliance posture.

**Reversible?:** Mostly. The token-spend hide is a copy-only change
in the user-facing copy; the underlying meter is unchanged. The
dashboard → ops re-cast is a route-by-route rename; the existing
component tree is mostly reusable. The plugin-account-panel work is
new code but cleanly separable. The taste-skill discipline is
forward-going only.

**Open questions for Tom (queued in OPEN_QUESTIONS.md as Q-19 to Q-21):**

- Q-19: name of the re-cast `apps/dashboard` — keep the name, rename
  to `apps/ops`, or split into a separate package?
- Q-20: PR #34 (me.ts rewrite) — merge-and-forget for compliance, or
  pause until the plugin-side panel design lands?
- Q-21: precedence on plugin account panel vs ops console — which one
  ships first?

## D-10 — Hybrid licensing: MIT plugin, proprietary backend — 2026-06-21 (loop M+9)

**Context:** Tom decided the open-source posture. The plugin must be readable
by RuneLite hub reviewers and privacy-skeptical players. The backend holds the
USP (model routing, prompt shaping, OpenRouter cost control, billing) and
should stay closed. The reasoning matters: license-as-defense is theatre
against LLMs that can ingest any public code; license-as-signal is the real
lever.

**Tom's directive, captured verbatim:**

> I fundamentally don't believe open source is the correct model because of
> how LLMs and agents can use it as full context no matter really the license.
> But that will dramatically impact how RuneLite plugin developers view this
> project. So I'm wondering if it's possible to actually split it: allow the
> RuneLite developers to view the code for the backend... actually no, the
> **plugin** open-source so RuneLite maintainers can audit, the **backend**
> closed-source so we keep the USP. We open source as much as possible but we
> keep the really sort of USP closed source.

**Chosen split:**

| Component | License | Repo |
|---|---|---|
| `apps/plugin/` | MIT | public: `RainnWorks/tibbly-plugin` |
| `apps/backend/` | Proprietary | private: `RainnWorks/tibbly-platform` |
| `apps/ops/` | Proprietary | private: same as backend |
| `apps/marketing/` | Proprietary | private: same as backend |
| `apps/mobile/` (future) | MIT | public: `RainnWorks/tibbly-mobile` |
| `packages/shared-types/` | MIT | published as `@tibbly/shared-types` on npm |
| WebSocket protocol spec | CC-BY-4.0 | public: `RainnWorks/tibbly-protocol` (doc-only) |

**Why not the alternatives:**

- Full open (MIT everywhere): maximum reviewer goodwill but a competitor can
  stand up a hosted clone in a week.
- Full closed: harder hub PR, lower player trust, no audit story.
- Open-core (MIT base + private pro modules): constant maintenance tax keeping
  the boundary clean; few of our improvements actually fit the split.
- BUSL with conversion: still readable and trainable; the conversion clock is
  meaningless to LLMs.
- AGPL: scares enterprise users; does not stop a Group-Ironmen-Tracker-style
  competitor who is happy to publish.

**Reversible?:**

- Plugin MIT: **one-way door.** Once published, every historical commit stays
  MIT. Forks made during the MIT window stay MIT. We can change future commits
  but cannot retroactively close history.
- Shared-types MIT: also a one-way door, but surface area is tiny (Zod schemas
  + type aliases visible on the wire anyway).
- Protocol CC-BY-4.0: published versions are permanently usable by anyone who
  attributes us. That is the point.
- Backend proprietary: **fully reversible.** Closed today; can be opened under
  any license later if we change our mind. Cost of reversing is social
  (explaining the shift) and competitive (model-routing logic becomes
  copyable). No historical commitment locks us out.

**References:**

- `docs/architecture/LICENSING.md`: canonical licensing doc.
- `docs/architecture/REPO_SPLIT.md`: repo migration plan.
- `docs/agents/OPEN_QUESTIONS.md` Q-17: superseded by D-10.
- `docs/agents/OPEN_QUESTIONS.md` Q-25 / Q-26 / Q-27: open follow-ups on
  timing, archive vs delete, and where the private repo lives.

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
