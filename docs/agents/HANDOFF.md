# Tom's wake-up briefing — 2026-06-21 afternoon

*Refresh: loop M+19. **Real ops login + real OpenRouter chat round-trip both proven E2E** this loop (phantom-green gap closed). Launch-blockers still 0 across all three categories. 30+ PRs merged today.*

## 🟢 Live state (verified 13:29 UTC)

- **Ops console:** `http://localhost:8787/ops/` — backend serves the built SPA same-origin; login with `thomas@rainn.works` mints `ops_session` cookie; all dashboard endpoints (users, openrouter spend, realtime, catalog, chat-daily) return real data.
- **Marketing:** `http://localhost:5173/` — vite dev server still running standalone.
- **Backend:** `:8787` — `bun --hot src/server.ts`. Drizzle migrations now auto-apply on boot. PGLite local DB has events/model_catalog/messages/etc populated.
- **Real LLM proof:** `/tmp/probe-real-chat.ts` opens `ws://localhost:8787/ws/plugin`, auths with `DEVKEY_dev_probe001`, sends "Reply with exactly the four characters: PONG", gets back `PONG` from real OpenRouter in ~1s, 86+6 tokens, 348µUSD. Dashboard's `/admin/chat-daily` now shows the rolled-up cost (696µUSD across 2 probe runs).

### What this loop fixed (Tom flagged "I still can't login on the backend / actually maybe we haven't tested it e2e")

1. **Ops dev port was a dead end** — vite 5174 had no proxy to the backend on 8787, so every POST `/api/admin/login` 404'd at vite.
2. **Phantom `/api/admin/*` prefix** — the ops client defaulted to `getBackendUrl() = "/api"` and all 7 test files mocked `/api/admin/*` URLs that the real backend never served. Tests were green; the live console had never reached the backend. Classic phantom-green pattern.
3. **No migrations on boot** — PGLite DB was missing `events`, `model_catalog`, etc., so every gated admin endpoint 500'd against "relation does not exist."
4. **Many routers not mounted** — server.ts only enabled `admin: "auto"` + `adminLogin` + `adminCatalog`. `adminUsers`, `adminOpenRouter`, `account`, `accounts`, `pairing`, `usage`, `me` were never instantiated.

**Fix shape:** backend serves `apps/ops/dist/*` under `/ops/*` with SPA fallback (`hono` route + `Bun.file`); ops vite `base: "/ops/"` + tanstack router `basepath: "/ops"`; `getBackendUrl()` default → `""` (same-origin, no prefix); `applyMigrations()` runs on every boot; server.ts mounts every available router. Test mocks corrected to the real paths and all 12 ops tests green.

### What's left on the gap Tom called out

- `/admin/openrouter/spend` widget reads from `messages` table; the WS handler logs to events bus but doesn't persist to `messages`/`chats`. So that one widget shows £0 even though chat-daily shows the rolled-up cost. Real fix: have the WS handler INSERT a `chats` row on first turn + `messages` rows per turn. **Deferred to a separate PR.**
- Plugin Kotlin compile is **NOT broken** (`./gradlew compileKotlin` → BUILD SUCCESSFUL, deprecation warnings only). The PR #83 agent's "unresolved Starter/AnimationState/Direction" report referenced a stale worktree state.

## Where we are right now (loop M+18)

**Cron `8e5a4446`** firing every 20 min, healthy.

### The companion build — Tom's "the marketing grab, build EVERYTHING out" directive

Three of four companion PRs landed today; two agents still working.

**Landed:**

- **PR #75 — Visual bible + asset commissioning plan** (RAI-64). 6975 words. Originally specced four humanoid starters (Wiki Veteran + Fox + Wisp + Golem) with a $4-6K commission budget. **Tom pivoted off commission** mid-build: "no artists — find a model online, like a robot that can float, Fallout style." The 3D robot agent is now retiring the commission section and replacing the starter set with four Probe variants.
- **PR #77 — Marketing companion section** (RAI-66). Hero refresh with the companion sprite + speech bubble cycling through the 5 magical-moment lines verbatim. New `Companion` section between Demo and FreeTierStrip with five staggered asymmetric vignettes + a "what makes it alive" 4-bullet capsule + the opinionated take *"Tibbly is the only OSRS plugin you'd say goodbye to."* `og-card-companion.svg` is the new default. **56/56 taste-skill pre-flight pass. 36/36 tests green.**
- **PR #78 — Plugin overlay** (RAI-65). `CompanionRenderer` extends RuneLite's `Overlay` API. `CompanionPathfinder` (A* with smooth 600ms sub-tile interpolation + fade-respawn on long-distance teleport). `CompanionStateMachine` with AFK ladder Idle→Yawn→Read→Sit. `CompanionDialogueOrchestrator` with strict cooldown discipline (≤1/30s, ≤8/hr, decaying density, session-floor 25%). `SpeechBubble` with fade-in + typewriter + length-scaled auto-dismiss. New `:checkCompanionConsentGated` Gradle guard. 3 new `OutboundPayload` variants through `EgressGate`. **46 tests green, all 11 Gradle gates green.**

**In flight:**

- **Companion backend brain** — `companion_profile` + `companion_memories` tables, 4 personality archetype prompt builders, reactive-dialogue WS handler, end-of-session memory extraction via cheap-tier model from `model_catalog` per D-9, nightly memory decay job, GDPR cascade extension. Linear-first.
- **Companion 3D robot source** — Sketchfab/Quaternius/Kenney CC0 hunt + Blender bake pipeline to PNG atlases. Replaces the commission pipeline. Updates `COMPANION_VISUAL_BIBLE.md` to retire the vendor list and renames the four starters from Veteran/Fox/Wisp/Golem to Probe variants (default Probe + comm-visor + heavy-armor + research-array tints). When this lands, the plugin overlay's `PlaceholderAtlas` swaps to the baked robot frames with zero code change.

### Other landed work this loop block (M+15 → M+18)

- PR #76 — Wave-2 fix (E2E suite was silently broken via 4-bug stack: missing drizzle-orm, JWT mismatch, route-mount order, wrong bearer in scenario 03; Stripe webhook double-cast; duplicate device-key generator). E2E now 12/12 real green.
- RAI-68/69/70 umbrella issues queued for the 19 deferred items from the hub fix, auth fix, and SQL safety review.

### Launch-blocker history

Today (still true):

- 0 critical security on `main` — auth fix PR #69 closed all 4 critical findings (forgeable headers).
- 0 hub blockers on `main` — hub fix PR #67 closed all 7 maintainer-review blockers; `:checkLocalNotInJar` Gradle gate physically prevents the rejected code from re-entering the shadowJar.
- 0 currency hazard — PR #68 D-11 locks GBP end-to-end (`monthlyPriceCents` → `monthlyPricePence`).

### What I'm NOT doing automatically

- Submitting the hub PR upstream — plugin is structurally ready, but the `runelite/plugin-hub` PR is sensitive enough that it waits for Tom.
- Splitting the monorepo into `tibbly-plugin` / `tibbly-platform` per D-10 — same reason.
- Spawning more code-quality hats (#3 React patterns, #4 TanStack, #6 dead code, #7 docs, #9 bundle) — holding until the companion build lands so the review surfaces are stable.

### Linear ledger

Today's RAI ids: RAI-39 (auth), RAI-40 (hub), RAI-41 (currency), RAI-42–RAI-57 (strategic backfill), RAI-58 (SQL review), RAI-60 (TS strictness), RAI-61 (Kotlin idiomatic), RAI-62 (test quality), RAI-63 (wave-2 fix), RAI-64 (visual bible), RAI-65 (plugin overlay), RAI-66 (marketing), RAI-67 (was the 3D robot pivot — pending agent's Linear creation), RAI-68/69/70 (deferred umbrella issues).

**Fix wave outcomes:**

- **PR #69** auth fix — `requireUser` now argon2id-verifies `Authorization: Bearer <rawDeviceKey>` against `devices.device_key_hash` with LRU cache. `adminGate` verifies the `ops_session` HS256 JWT cookie. Legacy `x-user-id` + `x-admin-email` + raw-userId-as-token paths deleted. 14 dedicated negative tests confirm the audit attacks return 401. 215/215 backend tests green.
- **PR #67** hub blockers fix — `ClaudeRunner.kt` + `LocalClaudeBackend` + "Install in Claude CLI" button deleted. shadowJar excludes `co/rowm/osrsllm/local/**`. ktor-server + MCP SDK moved to `compileOnly`. New `:checkLocalNotInJar` Gradle gate opens both jars with `ZipFile`. Plugin manifest fixes. Audit docs no longer lie. All 7 hub blockers closed.
- **PR #68** strategic consistency fix — D-11 locks GBP end-to-end (`monthlyPriceCents` → `monthlyPricePence`). NORTH_STAR rebuilt post-pivot. STATUS rebuilt. Q-13/15/19/20/21 marked RESOLVED. EMBODIED_COMPANION repo paths reconciled.
- **PR #70** E2E harness — Bun-runnable fake-plugin emulator that speaks the WSS protocol verbatim using `packages/shared-types` Zod schemas. 12 game-state fixtures. 5 scenarios. Orchestrator boots backend on ephemeral ports with in-memory PGLite seeded from production migrations. Stripe stubbed at the right seam. Plus 2 Claude-in-Chrome runbooks for marketing-checkout + ops-login-and-debug. **Caught a real WS dispatch race bug during construction.**

**Review wave 2 (PRs #71/#72/#73) outcomes:**

The review pattern keeps catching real things:

- **Hat 1 Kotlin idiomatic (#71)** — duplicate device-key generator in `OsrsLlmHelperPlugin.kt:514-521` (UUID concat) vs `DeviceKey.kt` (`SecureRandom`). Two generators of different strength undermines the auth trust root.
- **Hat 2 TypeScript strictness (#72)** — Stripe webhook double-cast at `apps/backend/src/api/webhooks/stripe.ts:180-189` bypasses Stripe's discriminated `Stripe.Event` union. Next dated API ships, the cast compiles, `new Date(null * 1000)` returns 1970, and `tier.quotaTokens` credits against a meaningless period. Financial bug.
- **Hat 5 Test quality (#73)** — **the E2E suite I just merged is silently broken.** `bun test e2e/scenarios/` can't resolve `drizzle-orm` from repo root, so the assertions never execute. The admin-ban-refund scenario also uses the pre-fix `x-admin-email` header. Phantom green.

**In flight:**

- Wave-2 fix agent — closes all three findings above in one PR (Linear-first).

**Killed by Tom this loop:** SQL migration safety fix (RAI-58). Findings still documented; can respawn if needed.

**Still-deferred items** (each tracked as a follow-up in the PR that deferred them):

- 6 important concerns from PR #67 (EDT-blocking `runBlocking`, raw daemon threads, `Desktop.browse` confirmation, two-sources-of-truth for chat mode, account panel pre-consent, Bearer-token-equals-x-device-key)
- 5 high/medium from PR #69 (`JLabel` HTML auto-rendering, pairing claim rate-limit, catalog refresh debounce, M1-M6)
- 8 important from PR #66 SQL safety (`events.id` `$defaultFn`, `usage_records.model` → catalog FK, etc)
- Wave-2 deferred items as the fix lands

**Linear status:** RAI-39 (auth), RAI-40 (hub), RAI-41 (currency), RAI-48 (E2E harness), RAI-58 (SQL review), RAI-60 (TS strictness), RAI-61 (Kotlin idiomatic), RAI-62 (test quality). Backfill from earlier today is RAI-42 through RAI-57.

**Open questions Tom hasn't touched**:

- Q-22/23/24 (hub strategy follow-ups), Q-25/26/27 (repo split), Q-28/29/30/31 (embodied companion), Q-32/33/34/35 (social companion)

**What I'm NOT doing automatically**:

- Submitting the hub PR upstream — the plugin is structurally ready, but the actual `runelite/plugin-hub` PR is sensitive enough that I'm holding for Tom.
- Splitting the monorepo into `tibbly-plugin` / `tibbly-platform` per D-10 — same reason.
- Building the mobile / companion entity / social fabric implementations — specs exist; greenlight pending.



**Currently in flight (7 strategic deep-dive agents):**

1. **Marketing IA synthesiser** — reads PRs #46/47/48/49 (all 4 proposals merged-to-input, held open as quorum inputs) and produces canonical `docs/marketing/IA.md`.
2. **Embodied solo companion entity** (`docs/product/EMBODIED_COMPANION.md`) — your "client-side character that follows the player" idea. Visual archetype, personality + memory system, technical architecture, 5-7 week roadmap.
3. **Social companion fabric** (`docs/product/SOCIAL_COMPANION.md`) — your "companions can see + semi-interact with each other" addition. S0–S4 interaction tiers, network effects, permission system, safety classifier.
4. **Mobile companion research** (`docs/research/community/mobile-companion.md`) — what OSRS mobile apps exist + the specific app you watched a video about + Tibbly mobile MVP shape.
5. **Licensing split** (`docs/architecture/LICENSING.md` + `REPO_SPLIT.md` + D-10) — captures your decision: MIT plugin, proprietary backend, CC-BY protocol spec; lays out the repo-split migration.
6. **RuneLite BYOK config** — `OsrsLlmHelperConfig.kt` extension + `:checkNoKeyLeak` Gradle guard + tests + `DATA_DISCLOSURE.md` update. DirectChatRunner deferred to a follow-up PR.
7. **Model platform step 1** — live OpenRouter catalog ingester + `model_catalog` schema + ops `/catalog` route + D-9 ("no hardcoded model ids").

**Recently landed today** (in approximate order, all on `main`):

- PR #34 — `/v1/me` GDPR (Art. 15 export + Art. 17 erasure)
- PR #35 — pivot docs (D-8 + Q-19/20/21 + taste-skill mirror)
- PR #36 — first batch of RAI-5 Tier 0 unblockers (4 tools)
- PR #37 — `apps/dashboard` → `apps/ops` rename
- PR #38 — `get_farming_summary` + `get_farming_patches` (5th unblocker, split for token budget)
- PR #39 — HANDOFF refresh
- PR #40 — plugin Tibbly account panel inside RuneLite (no raw tokens)
- PR #41 — me.test.ts fix (caught the `ANY(array)` PGLite bug)
- PR #42 — marketing token-spend strip
- PR #43 — full ops console re-cast with taste-skill applied
- PR #44 — marketing Stripe Checkout CTAs wired
- PR #45 — hub release strategy + 3-tier value model
- PRs #46/47/48/49 — 4 marketing IA proposals (open, fed into synthesiser)

**Open questions awaiting your call** (full text in `OPEN_QUESTIONS.md`):

- Q-22 / Q-23 / Q-24 — hub strategy follow-ups (start BYOK build now? when to start 12-week hub clock? rebrand before submission?)
- Q-25 / Q-26 / Q-27 — repo split decisions (in flight via licensing agent)
- Q-28 through Q-31 — embodied companion (in flight via deep-dive agent)
- Q-32 through Q-35 — social companion fabric (in flight)

**Live-counter strategic threads since you last engaged:**

- "Where did we land on open/closed source?" → MIT plugin, proprietary backend, CC-BY protocol. Captured as D-10 (in flight).
- "Mobile companion?" → research agent in flight, identifying the YouTube-app you mentioned + sketching Tibbly mobile MVP.
- "Client-side entity that follows your character" → solo deep-dive in flight.
- "Companions can see + interact socially" → social fabric deep-dive in flight.



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
