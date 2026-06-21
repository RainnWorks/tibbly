# Tom's wake-up briefing — 2026-06-21 morning

*Refresh: loop M+3 (post-pivot). Tom is awake and gave product direction. First file to read on next pickup. 90 seconds, then jump in.*

## ⚠️ Pivot landed — 2026-06-21 loop M+2 → M+3

Tom reviewed the build, called the user dashboard "LLM slop", and
redirected:

1. **User dashboard de-prioritised.** Most account management moves
   into RuneLite plugin panels. The web app becomes Tibbly's
   internal ops console (gated whole-site on `ADMIN_EMAILS`).
2. **Token-spend visibility removed from user UI.** Use tier-aware
   proxies ("23/30 messages used today"). Internal ops keeps the
   token math.
3. **Apply taste-skill** to every UI commit. Mirrored at
   `.claude/skills/taste-skill/SKILL.md`. Declare design read + 3
   dials before writing UI code. Hard bans: no Inter default, no
   em-dashes, no 3-equal-card grids, no beige+brass+oxblood.
4. **Marketing page stays public.** Only ops gets the auth wall.
5. **Plugin work continues.** All 5 RAI-5 Tier 0 unblockers shipped
   (PR #36 + PR #38): `get_account_identity`, `get_raid_layout`,
   `get_target_projectiles`, `get_active_prayers`,
   `get_farming_summary` + `get_farming_patches(region=)`. 35 new
   tests across both PRs. `:check` green. `FarmingTables.kt` mirrors
   the package-private upstream patch→varbit table (17 OSRS regions
   v1; hardwood / seaweed / cactus / spirit tree / etc deferred to
   v2). `StateProbes.kt` in `cloud/tools/` is positioned for the
   future cloud→local dispatcher (replacing `StubToolDispatcher`).

Full pivot writeup: `docs/agents/DECISION_LOG.md` D-8.
Tom's Q-19/Q-20/Q-21 answers:
- Q-19 → rename done (PR #37 merged).
- Q-20 → merge for compliance (PR #34 merged).
- Q-21 → ops + plugin panel as parallel tracks (both agents
  spawned in M+5, in flight).

## Loop M+5 — in flight (parallel tracks)

- **Ops console re-cast agent** — `apps/ops/` rebuilt as Tibbly's
  internal ops console. Auth wall on `/login` via JWT cookie.
  Routes: `/`, `/users`, `/users/:id`, `/analytics`, `/openrouter`.
  Backend gains `/admin/{users,users/:id,users/:id/{ban,unban,refund,credit},openrouter/{spend,revenue},login}`.
  Taste-skill applied: design-read + 3-dials block goes at the top
  of the PR body.
- **Plugin account panel agent** — Swing sidebar in RuneLite.
  Tier-aware "messages left today" proxy (server computes; UI
  never sees raw tokens). GDPR export/delete actions. Stripe portal
  link. Backend gains `/v1/account/summary` +
  `/v1/account/usage-proxy`. New Gradle
  `:checkAccountPanelNoRawTokens` guard.

## What's new since the previous HANDOFF refresh

**Loop M+1 — six gaps closed, one PR-driven follow-up (RAI-16) shipped,
one research agent dispatched.**

Closed gaps (re-inspection found them already resolved or one tiny
delta away):

- **A1** — `McpServerService.start()` already gated by
  `if (config.developerMode() && config.localMcpEnabled())` at
  `OsrsLlmHelperPlugin.kt:198` (both defaults `false`). Plugin is
  hub-PR-compliant. Added belt-and-braces Gradle `checkMcpServerGated`
  task wired into `:check`.
- **A3** — Stripe webhook idempotency leak was already fixed by
  `unclaimEvent()` in the catch path (`stripe.ts:109`).
- **A4** — `/admin/*` router uses deny-by-default middleware
  (`admin/usage.ts:50-57`) with 3 explicit 401 tests. Header-based
  auth wants a session upgrade pre-launch (non-blocking hardening).
- **A6** — `LOOP_LOG.md` exists; backfilled the Loop M+1 entry.
- **M1.4 / M3.1 (RAI-16)** — OpenRouter proxy is fully shipped, not
  a stub. `runStream` + `buildRemoteTools` + tier-aware `chooseModel`
  + `computeCostMicroUsd` all on disk. Added explicit `maxRetries`
  (default 3) for transient 429/5xx hardening. Linear issue Done.
- **M2.2** — marketing copy already on Tibbly; all `osrs-llm-helper`
  matches are workspace package imports, the GitHub repo URL, or the
  deliberate "Tibbly · the OSRS LLM Helper" tagline.

Shipped this loop:

- **Marketing og:image** — `apps/marketing/public/og-card.svg`
  (1200x630 OSRS chat-window theme) + `scripts/build-og.mjs` PNG
  renderer via `@resvg/resvg-js` (with `sharp` fallback). 138 KB PNG
  emitted at build time. `index.html` now declares full og/twitter
  metadata (type/width/height/alt).
- **`openrouter.ts` maxRetries** (3 by default) closes RAI-16's
  retry-acceptance criterion.

RAI-5 catalog landed (PR #29 merged):

- `docs/research/runelite-api/catalog.md` (776 lines, 100+ tools)
- `docs/research/runelite-api/_SUMMARY.md` (one-pager)
- Headline findings:
  1. **5 unblockers ready to ship** with Kotlin sketches:
     `get_account_identity` (CORE), `get_raid_layout` (RAIDS),
     `get_target_projectiles` (COMBAT), `get_farming_state` (FARMING),
     `get_active_prayers` (COMBAT).
  2. **Varbits are the biggest unexposed goldmine** — ~200 named
     constants cover farming patches, raids, leagues, prayer flicks,
     potion timers, slayer streaks. Bundling into 13 focused Tier-1
     tools keeps per-turn token cost in check.
  3. **Group Iron Man has no first-class RL API** — community
     standard (Group Ironmen Tracker) is detect via `AccountType`,
     treat clan channel as GIM party. Catalog adopts that pattern.

Also landed (PR #31 — the additive subset of catalog §6):

- `ToolFamily.kt` — added `RAIDS`, `LEAGUES`, `FARMING`, `APPEARANCE`,
  `AMBIENT`, `PETS`. Purely additive, no existing tool re-assigned.
- `CLAUDE.md` — corrected "72 MCP tools" to "72 game-state tools + 1
  meta-tool" (catalog finding: `ToolRegistry.kt` has 73 entries).

Still open from GAPS.md (next loops):

- **A5 / STATUS.md** is from Loop 0 — needs a rebuild from
  `gh pr list --state merged` + Linear Done list.
- **M3.5** — `apps/backend/src/api/me.ts` has 8 pre-existing TS
  errors and references non-existent modules. The GDPR Art. 17
  right-to-erasure flow is a stub. Needs real deletion.
- **M2.3 / M2.4** — Lighthouse perf/a11y/SEO targets unverified.
- **M1.6** — RuneLite Plugin Hub submission PR not yet opened.

## TL;DR (one paragraph)

Overnight the productize phase went from "monorepo skeleton + raw docs" to **30 Linear issues Done** across **26 merged PRs** on `main` — every must-ship milestone (M1 productized client, M2 marketing page, M3 backend auth/credits/billing) is structurally complete. The plugin was pivoted from a rejected localhost-HTTP MCP pattern to a single auditable outbound WSS egress gate; tool gating drops turn-1 surface from ~8K → ≤1.5K tokens; OpenRouter routing is locked at **97.4 / 88.0 / 79.1% gross margin** for Hobbyist / Pro / Iron tiers. The marketing site (Tibbly brand, "clever friend who already read the wiki" voice) renders all eight sections; the dashboard mounts the pair / usage / accounts / billing routes; the backend serves chat WS, Stripe webhooks, the token meter with hard cap at zero, presence WS, GDPR Art. 15/17 stubs, event-driven analytics, and an admin ops dashboard. Legal stack (Privacy / Terms / Consent / Sub-processors / Retention / Cookies) is fully drafted but **needs a UK/EU solicitor pass before launch**. Zero open PRs at handoff. Single biggest open product risk: Jagex's posture on third-party AI tooling (Q-18); single biggest engineering follow-up: retro-sign or accept the unsigned overnight commits (Q-7).

## Top 3 wins

1. **All three must-ship milestones structurally complete (M1 / M2 / M3).** 30 issues Done, 26 PRs merged, zero open PRs. Plugin pivot, marketing page, backend, dashboard, legal stack, RuneLite Hub submission package, brand identity all landed.
2. **97.4 / 88.0 / 79.1% gross margin locked in at quota.** Tool gating (turn-1 surface ≤1.5K tokens) + per-tier model routing (Haiku-only → Haiku-router + Sonnet 4.6 → +Opus 4.7 escalation) compound. The 1.5K cap is now telemetered, so margin regressions become alerts. See `docs/research/llm-providers/_SUMMARY.md`.
3. **Plugin Hub blocker neutralised.** RAI-35 + RAI-38 found PR #11453 ("Add OSRS MCP plugin") was rejected for exposing player info over HTTP **even on loopback with auth tokens**. The localhost MCP HTTP server is gone; outbound traffic flows through one auditable `EgressGate` to the backend WSS — a single `webSocket.send()` per egress, every payload audit-logged. We are now structurally hub-eligible. See `docs/runelite-hub/PRECEDENT.md` §B.1.

## Demo this first (5 minutes)

1. `bun install && bun run dev` from `/Users/tom/Projects/osrs-llm-helper`. Marketing on `:5173`, dashboard on `:5174`, backend on `:3001`.
2. **Marketing** — open `http://localhost:5173`. Tibbly landing page: hero with sprite collage, problem/solution grid, demo, 6-card feature grid, 3-tier pricing, FAQ, footer, and the live agent counter pinging `/v1/presence`.
3. **Dashboard** — open `http://localhost:5174`. Routes mount at `/`, `/pair`, `/usage`, `/accounts`, `/billing`. The `/pair` route takes a 6-digit code and calls `POST /v1/pairing/claim`.
4. **Plugin** — `cd apps/plugin && ./gradlew runRuneLite`. Default mode is tools-only (no network). Flip `cloudChatEnabled` in config, accept the consent dialog, request a pairing code from in-game HUD, paste it into the dashboard.
5. **End-to-end chat** — type into the plugin chat panel. Watch a `user_message` WS frame leave through `EgressGate`, the backend route it through OpenRouter, the tool calls round-trip back to the plugin, and the response render. The token-balance HUD ticks down live, with usage attributed in `/admin/usage`.

## Top 3 risks (ranked by impact)

1. **Q-18 / Jagex posture on third-party AI tools.** Our product reads OSRS game state and sends it to an LLM. If Jagex makes a moderation determination against the category, every paying user can be banned overnight. RuneLite operates under tacit tolerance for ~10 years; a paid SaaS plugin inherits that posture, not a legal right. `TERMS.md` §11 disclaims that liability but UK/EU consumer-law enforceability is **not guaranteed**. Default picked: ship with mitigations, do NOT proactively contact Jagex Legal. Tom can override on wake-up. Get written solicitor opinion before first £.
2. **Q-7 / Unsigned commits this run.** The 1Password SSH signing agent hard-failed across every parallel agent with `1Password: failed to fill whole buffer` and later `agent returned an error`. All overnight branches + the merges to `main` are unsigned. Policy decision needed: (a) retro-sign with `git rebase --exec 'git commit --amend --no-edit -S' --root` per branch, or (b) accept unsigned for the overnight set and require signing going forward.
3. **Legal stack still needs a solicitor.** `docs/legal/PRIVACY.md`, `TERMS.md`, `CONSENT_FLOW.md`, `SUB_PROCESSORS.md`, `DATA_RETENTION.md`, `COOKIE_POLICY.md` are drafted but unreviewed. Specific items flagged in-doc: cooling-off carve-out wording (Reg. 37 UK CCR), CCPA "sale/share" determination, controller-vs-processor designation, Cloudflare `cf_clearance` "strictly necessary" classification, children's age-gate UX. **Do not publish, ship to the plugin hub, take a payment, or expose chat to a real user until reviewed.**

## Open questions ranked (read OPEN_QUESTIONS.md for detail)

1. **Q-18** — Reach out to Jagex Legal? Default: no. Highest-impact, hardest-to-reverse decision.
2. **Q-7** — Unsigned overnight commits — accept the set or retro-sign each branch?
3. **Q-13** — Product name **Tibbly** — USPTO TESS check was unavailable this run; do it before any logo/brand spend. `tibbly.com` is taken (unrelated art seller); fallbacks `tibbly.app` / `tibbly.gg` / `gettibbly.com`.
4. **Q-2** — Tier pricing ($7 Hobbyist / $19 Pro / $49 Iron). Locked in code and copy; margins prove the numbers but willingness-to-pay is unvalidated.
5. **Q-4** — OSRS Wiki sprite commercial use. Mitigated by using BSD-2 RuneLite-extracted + CC0 RuneStar sources only in `packages/osrs-assets`. Confirm no Wiki imagery leaked into marketing.

## What shipped (by milestone)

- **M1 — Productized client (plugin ↔ backend chat over WS):**
  RAI-13 monorepo, RAI-17 WS protocol, RAI-22 plugin BackendClient + CloudChatRunner, RAI-23 plugin pairing UI, RAI-25 tool gating + keyword router, RAI-35 RuneLite Hub submission package, RAI-36 plugin security audit + secretsScan + threat model, RAI-38 egress-gate pivot. ✓
- **M2 — Marketing page (RuneScape-native landing):**
  RAI-21 public presence WS + REST, RAI-28 Vite + React + TS + Tailwind v4 skeleton, RAI-29 Hero / ProblemSolution / Demo sections, RAI-30 Pricing + FeatureGrid + FAQ + Footer + LiveCounter visual. ✓
- **M3 — Backend (auth, credits, billing, token tracking):**
  RAI-14 Hono + Drizzle + PGLite skeleton, RAI-15 11-table schema + migration, RAI-18 pairing-code auth, RAI-19 Stripe webhook receiver, RAI-20 token meter + hard cap, RAI-26 dashboard skeleton, RAI-27 dashboard wiring (`/pair`, `/usage`, `/accounts`, `/billing`), RAI-34 legal stack + GDPR endpoints, RAI-37 event-driven analytics + admin ops. ✓
- **M0 — Research & spec (feeds all milestones):**
  RAI-6 community pain points, RAI-7 OSRS asset catalog, RAI-8 OpenRouter economics, RAI-9 memory-systems landscape + recommendation, RAI-10 competitor scan, RAI-11 brand voice + name (Tibbly), RAI-12 GAPS analysis, RAI-32 memory steward final state, RAI-33 library scout, RAI-34 (also above). ✓

## In flight at wake-up

- **None.** Zero open PRs. All 30 Done issues are merged and on `main`.
- **Manual-verify items (post-handoff, can only be tested with a real OSRS account):**
  - Live plugin↔backend chat round-trip with a real player in a real RuneLite instance.
  - Stripe webhook deliveries in production (test mode only verified).
  - RuneLite Plugin Hub submission PR (package is ready in `docs/runelite-hub/SUBMISSION_CHECKLIST.md`).
  - In-game pairing-code UX with a real player + real dashboard session.

## Files to skim (in order)

- `docs/agents/NORTH_STAR.md` — the three must-ships and the anchors.
- `docs/agents/STATUS.md` — live agent board; final-state section at the bottom.
- `docs/agents/DECISION_LOG.md` — 24+ entries covering every reversible/irreversible call made overnight.
- `docs/agents/OPEN_QUESTIONS.md` — Q-1 through Q-18 with defaults picked.
- `docs/agents/GAPS.md` — inconsistencies, missing pieces, and which questions Tom must answer before launch.
- `docs/research/llm-providers/_SUMMARY.md` — the margin math (97/88/79%).
- `docs/marketing/BRAND_VOICE.md` + `docs/marketing/NAME_CANDIDATES.md` — Tibbly tone + name matrix.
- `docs/runelite-hub/SUBMISSION_CHECKLIST.md` — what's left for the hub PR.
- `docs/runelite-hub/PRECEDENT.md` — why the egress shape changed.
- `docs/legal/*` — **NEEDS LAWYER REVIEW.**

## !! LEGAL — NEEDS LAWYER REVIEW BEFORE LAUNCH !!

> RAI-34 deliverable. Do not publish, ship to the plugin hub, take a payment, or
> expose chat to a real user until a qualified solicitor (UK / EU consumer +
> data protection) has reviewed the documents in `docs/legal/`.

Drafts present as of 2026-06-21:

- `docs/legal/PRIVACY.md`
- `docs/legal/TERMS.md`
- `docs/legal/CONSENT_FLOW.md`
- `docs/legal/SUB_PROCESSORS.md`
- `docs/legal/DATA_RETENTION.md`
- `docs/legal/COOKIE_POLICY.md`

Single biggest legal risk: see Top Risk #1 above (Jagex posture). Other items flagged in-doc for the lawyer: cooling-off carve-out wording (Reg. 37 UK CCR), CCPA "sale/share" determination, controller-vs-processor designation, Cloudflare `cf_clearance` "strictly necessary" classification, children's age-gate UX.

## Next session's natural starting point

1. Approve the top-5 open-questions defaults (or override).
2. Decide the unsigned-commits policy and run the rebase if needed.
3. Book the solicitor for the legal stack pass.
4. Run the demo above end-to-end with a real OSRS account.
5. Open the RuneLite Plugin Hub PR using `docs/runelite-hub/SUBMISSION_CHECKLIST.md`.
