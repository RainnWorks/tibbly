# Gaps analysis — overnight 2026-06-21

Closes RAI-12 (one-shot end-of-night sweep). What we said we'd ship vs.
what's on disk, broken into:

- **(a) Spec ↔ code inconsistencies** — what `docs/architecture/` claims vs.
  what merged.
- **(b) Missing pieces per milestone** — M1 / M2 / M3.
- **(c) OPEN_QUESTIONS Tom must answer before launch.**

Read in conjunction with `docs/agents/HANDOFF.md` (the "what shipped" view)
and `docs/agents/STATUS.md` (the "where everyone landed" view).

---

## (a) Inconsistencies — docs/INDEX.md ↔ what actually exists

The most important inconsistency: **`docs/INDEX.md` lists architecture
docs that were never written**. Multiple agents read INDEX as the
source-of-truth and would have been pointed at dead links.

| `docs/INDEX.md` claims | Actually on disk | Severity |
|---|---|---|
| `architecture/SYSTEM.md` | **missing** | High — INDEX line 35 |
| `architecture/IDENTITY.md` | **missing** | High — INDEX line 36 |
| `architecture/BILLING.md` | **missing** | High — INDEX line 37 |
| `architecture/TOOL_ECONOMY.md` | **missing** | High — INDEX line 38 |
| `architecture/MONOREPO.md` | **missing** | High — INDEX line 39 |
| `architecture/PROTOCOL.md` | **missing** | High — INDEX line 40 |
| `architecture/DATA_FLOW.md` | present | OK |
| `product/VISION.md` | **missing** | Medium — INDEX line 29 |
| `product/PERSONAS.md` | **missing** | Medium — INDEX line 30 |
| `product/PRICING.md` | **missing** | Medium — INDEX line 31 (PRICING is locked in `research/llm-providers/cost-model.md`; not promoted into product/) |
| `research/runelite-api/` | **missing entire directory** | Medium — RAI-5 not started; only directory left empty |
| `marketing/POSITIONING.md` | **missing** | Medium — INDEX line 64 |
| `marketing/ASSET_CATALOG.md` | **missing** | Low — content is in `research/osrs-wiki/assets.md` |

> **Action for next session:** either generate the architecture docs (they
> would crib heavily from existing research summaries + PR descriptions) or
> trim `INDEX.md` to only list what exists. The latter is faster but loses
> the navigational scaffolding. Probably do both: trim now, generate later.

## (a) Inconsistencies — spec ↔ what merged

### A1 — `apps/plugin/local/McpServerService.kt` still exists AND is still started

- **Spec:** `docs/agents/OPEN_QUESTIONS.md` Q-14, RAI-38 acceptance
  criteria, `docs/runelite-hub/PRECEDENT.md` §B.1 — the localhost MCP HTTP
  server **must be removed** before hub submission. PR #11453's verbatim
  rejection reason was *"Plugins which expose player information over
  HTTP"*.
- **Actual:** `apps/plugin/src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt`
  is **2479 lines, still on disk, still injected and started** in
  `OsrsLlmHelperPlugin.kt`:
  ```
  apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:73:
      @Inject private lateinit var mcpServerService: McpServerService
  apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:197:
      mcpServerService.start(config.localMcpHost(), config.localMcpPort())
  apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:324:
      mcpServerService.stop()
  apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:363:
      mcpServerService.restartWith(...)
  ```
- **What HANDOFF.md claims:** "`McpServerService` is now under `local/`
  (legacy) and *not* registered". This is **wrong** — it *is* registered
  and started.
- **PRs that say they fix this:** PR #11 (RAI-38). PR #11 added the
  `cloud/EgressGate` and routed cloud traffic correctly, but **left the
  legacy HTTP listener registered** as a developer-only path.
- **Severity:** **PR-blocker** for the RuneLite Plugin Hub submission. If
  Tom submits the hub PR with this still running, the maintainers reject
  on day one.
- **Fix path:** wrap the `start()`/`restartWith()` calls in
  `if (config.devUnsafeLocalMcpEnabled())` and ship the default as
  `false`. Even better: delete the `local/` package entirely; tools are
  already mirrored 1:1 in `cloud/ToolRegistry.kt`.

### A2 — `apps/marketing` references RAI-30 but is on `main`

- **Status:** RAI-29 + RAI-30 already merged via PR #19. HANDOFF.md still
  describes them as "in flight at wake-up" (line 41).
- **Severity:** low — stale HANDOFF copy; rebuild it on next loop.

### A3 — Stripe webhook lacks documented retry policy

- **Spec:** `apps/backend/src/api/webhooks/stripe.ts` correctly implements
  idempotency via `processed_stripe_events`. But there is no documented
  retry/backoff policy for cases where Stripe's signature parses fine but
  one of the handlers throws after the idempotency claim is recorded
  (the handler exception path returns 500, but the row is already
  claimed — Stripe will replay, hit the idempotency row, return 200 with
  `duplicate: true`, and the event will be **silently dropped**).
- **File:** `apps/backend/src/api/webhooks/stripe.ts` — claim is inserted
  before the switch; if the switch throws, the row is orphaned.
- **Severity:** medium — only bites under handler failure, but if it does
  bite, a paying customer's quota never gets credited and we don't know.
- **Fix path:** either (a) only insert the idempotency row **after** a
  successful handler return, or (b) record `processed_stripe_events.status`
  (`processing`/`done`/`failed`) and let replays re-run failed handlers.
  Option (b) is the production pattern.

### A4 — No `apps/backend/src/api/admin/*` auth guard documented

- **Spec:** `docs/architecture/` has no IDENTITY.md (see A1), so the
  admin-endpoint auth model is undocumented. The file
  `apps/backend/src/api/admin/usage.ts` is mounted at `/admin/usage` and
  is referenced in HANDOFF.md as a demo path.
- **Severity:** high — if `/admin/*` ships without an auth guard, anyone
  with the URL gets the ops dashboard data.
- **Fix path:** verify `_auth.ts` is mounted in front of `/admin`; add a
  short note in `docs/architecture/IDENTITY.md` once it's written.

### A5 — `docs/agents/STATUS.md` is from Loop 0

- **Actual:** `STATUS.md` headline says "Last updated: Loop 0 — initial
  setup. Loop counter: 0." Every Stage 1/2/3 row shows "pending" except
  A1 / R2 / R3 (and even those are inaccurate post-merges).
- **Severity:** medium — STATUS.md is supposed to be the live board. It's
  the *initial* board. Real state lives in Linear + HANDOFF.md.
- **Fix path:** rebuild STATUS.md from `gh pr list --state merged` and
  the Linear Done list. We're partially fixing this in RAI-32 (this PR)
  by appending the final-state section.

### A6 — `LOOP_LOG.md` missing

- **Spec:** `docs/agents/MEMORY_ARCHITECTURE.md` says "Append-only timeline.
  Each loop adds: Read / Worked on / Spawned / Deferred / Next loop
  should." `INDEX.md` line 13 links it.
- **Actual:** file is missing from `docs/agents/`.
- **Severity:** medium — we lost the per-loop episodic log this run. The
  Linear timeline backfills it, but it's a gap in the documented memory
  contract.

### A7 — RuneLite-API research folder empty (RAI-5 not started)

- **Spec:** `docs/INDEX.md` line 71 says `research/runelite-api/` exists.
  RAI-5 is the issue.
- **Actual:** the directory does not exist; RAI-5 is in **Backlog**.
- **Severity:** low (research, not a blocker). But it's the input for
  Token Optimizer's family tag refinement (RAI-25 acceptance criteria
  reference it as "refine after R1 lands"). Without it, family tags are
  guesses.

---

## (b) Missing pieces per milestone

### M1 · Productized client (plugin ↔ backend chat over WS) — milestone 69% done

| Gap | Where | Severity |
|---|---|---|
| **M1.1 — Legacy localhost MCP listener still starts** (A1 above). RuneLite Plugin Hub blocker. | `apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:197,363` | PR-blocker |
| **M1.2 — `RAI-23` plugin pairing UI still in review** (PR #22). No green path from "plugin installed" to "paired" without merging this. | `apps/plugin/.../auth/DeviceKey.kt` (per RAI-23 spec) | High |
| **M1.3 — Token-budget HUD in chat sidebar (RAI-24) not started** — backlog. Means the chat panel doesn't surface remaining quota. | `apps/plugin/.../chat/ChatPanel.kt` | Medium |
| **M1.4 — `RAI-16` OpenRouter proxy still in backlog.** Backend can't actually call OpenRouter end-to-end. The schema, the routing strategy, the meter, and the WS protocol all exist; the *actual outbound LLM call* hasn't shipped. | `apps/backend/src/llm/openrouter.ts` exists as a stub (per RAI-14) | **High** — without this, no chat actually runs |
| **M1.5 — Family-tag refinement requires RAI-5** (the RuneLite API depth scan). Without it, tool-gating families are guesses. Hard to validate the ≤1.5K cap on real workloads. | `apps/plugin/.../cloud/ToolFamily.kt` | Medium |
| **M1.6 — Plugin hub submission package isn't actually submitted.** The compliance docs exist; the PR to `runelite/plugin-hub` does not. | none — needs new PR upstream | High (gating launch) |

### M2 · Marketing page (RuneScape-native landing) — milestone 81% done

| Gap | Where | Severity |
|---|---|---|
| **M2.1 — `LiveCounter` polls `GET /v1/presence` but `RAI-21` (presence endpoint) is still in review** (PR #24). | `apps/marketing/src/sections/LiveCounter.tsx` (probably) + `apps/backend/src/ws/presence.ts` (probably) | High |
| **M2.2 — Marketing copy still says "osrs-llm-helper" in some places** — `CLAUDE.md` says placeholder name lives in code; rename to Tibbly happens in marketing copy first. Search for "osrs-llm-helper" in `apps/marketing/src/` before launch. | `apps/marketing/src/**` | Low |
| **M2.3 — Lighthouse target (≥85 Perf / ≥95 A11y / ≥95 SEO) unverified.** RAI-28 + RAI-29 + RAI-30 all merged but no Lighthouse run reported in any PR description. | n/a — needs Claude-in-Chrome QA pass | Medium |
| **M2.4 — Hero hits a ≤1s local-load target unverified** — same as M2.3. | n/a | Medium |
| **M2.5 — SEO basics (meta, sitemap, opengraph) not confirmed in PR descriptions.** | `apps/marketing/index.html` + `apps/marketing/public/` | Medium |
| **M2.6 — RAI-31 QA E2E smoke (Claude-in-Chrome) not run.** Backlog. | n/a | High — every previous overnight skipped this |

### M3 · Backend (auth, credits, billing, token tracking) — milestone 91% done

| Gap | Where | Severity |
|---|---|---|
| **M3.1 — `RAI-16` OpenRouter proxy not implemented** (same as M1.4). No actual LLM calls happen. | `apps/backend/src/llm/openrouter.ts` | **Highest** — gates the actual product |
| **M3.2 — Stripe webhook handler idempotency leak under handler failure** (A3 above). | `apps/backend/src/api/webhooks/stripe.ts` | Medium |
| **M3.3 — Admin endpoints auth-guard unverified** (A4 above). | `apps/backend/src/api/admin/usage.ts` + `apps/backend/src/api/_auth.ts` | High (security) |
| **M3.4 — Dashboard `/billing` route assumes Stripe portal URL is provided by backend; portal-link generation lives in `billing-portal.ts` but there's no spec doc for the redirect-back URL.** | `apps/backend/src/api/billing-portal.ts` | Low |
| **M3.5 — GDPR Art. 17 (right to erasure) endpoint only stubbed.** HANDOFF.md says "GDPR Art. 15/17 endpoints" landed, but `apps/backend/src/api/me.ts` likely returns a stub. Lawyer pass needs the real deletion flow. | `apps/backend/src/api/me.ts` | High |
| **M3.6 — Production DB migration story:** PGLite locally, Postgres in prod — but no `infra/` docker-compose or Fly-deploy script for the Postgres flip. | `infra/` (mostly empty) | High (launch) |
| **M3.7 — Hard cap → friendly error round-trip in chat** untested end-to-end. The meter rejects, but the plugin's error-rendering path for `balance_zero` is not specified anywhere. | `apps/plugin/.../cloud/InboundCodec.kt` | Medium |
| **M3.8 — Analytics (`RAI-37`) ingestion is event-driven but no documented retention for `events` table.** | `apps/backend/src/events/persist.ts` + `docs/legal/DATA_RETENTION.md` | Medium |

---

## (c) OPEN_QUESTIONS Tom must answer before launch

Ranked by "what blocks shipping the product to a paying user":

| # | Question | Why it's a blocker | Default we picked |
|---|---|---|---|
| **Q-18** | Reach out to Jagex Legal? | Hardest-to-reverse decision. If we ask, they can say no. If we don't, we ship under "tolerated, not licensed." This determines whether we open with a "no reply from Jagex" launch or a "Jagex-acknowledged" launch. | No, ship with mitigations |
| **Q-14** | Localhost MCP HTTP server must be removed | The plugin literally won't pass hub review until we delete the legacy listener (A1 + M1.1 above). | Remove. Owner: RAI-38; **work is incomplete** |
| **Q-2** | Pricing tiers ($7 / $19 / $49) | Locked in code and copy. Margins prove the numbers (97/88/79%) but willingness-to-pay is unvalidated. Affects every checkout link in the dashboard + marketing. | $7 / $19 / $49 |
| **Q-13** | Product name "Tibbly" | USPTO TESS check was unavailable. Do this before any logo/brand spend. `tibbly.com` is taken; fallbacks `tibbly.app` / `tibbly.gg` / `gettibbly.com`. | Tibbly |
| **Q-7** | Unsigned commits — accept or retro-sign? | Every overnight branch + every merge to `main` is unsigned. Either retro-sign now or set the rule for merge-to-`main` going forward. | Accept this run; sign future commits |
| **Q-12** | Brand voice register | "Clever friend who already read the wiki" is the chosen voice. Tom can override or refine; copy already uses it. | Calm + dry, rare ribbing |
| **Q-4** | Use Wiki sprites commercially? (now Q-18 supersedes) | Resolved by Q-18 default + `packages/osrs-assets` shipping only BSD-2 + CC0 sources. Confirm Tom is happy with "no Wiki sprites in the paid product." | No Wiki sprites |
| **Q-5** | Network presence — opt-in or opt-out? | "Opt-in, default off" is on disk. Marketing's "X agents online" widget will look anaemic at launch with everyone opted out by default. Pick a hook (e.g. "show me on the map" toggle in dashboard onboarding). | Opt-in |
| **Q-6** | OSRS account binding — trust plugin-reported player name? | Currently yes. If we want a verification step (one-time chat-message proof), it adds a friction point but kills account-name spoofing. Resolve before paid launch. | Trust the plugin; add later |
| **Q-1** | Domain name (`osrsllm.app` placeholder) | Default placeholder is wired into `.env`. Decide actual domain before DNS + Stripe production checkout setup. | osrsllm.app |
| **Q-15** | License: MIT or BSD-2-Clause for plugin? | The hub README says BSD-2; in practice both ship. If a reviewer enforces literally, we re-license in the PR cycle. | MIT |
| **Q-16** | Kotlin or Java for plugin? | Same risk profile — Kotlin works in practice but is on the rejected list literally. | Kotlin, port if pushed |
| **Q-17** | Open-source the backend repo or just the protocol spec? | Goodwill vs. leaking the routing logic. Affects the hub PR's "are these people good actors" read. | Protocol spec only |
| **Q-3** | RuneLite plugin distribution | "Eventually hub; for now sideload via external-plugin-hub config." If Q-18 is "no Jagex ask," then hub-first is even more important. | Hub eventually |
| **Q-8** | Multiple agents share one git checkout | Operational; affects next overnight, not this launch. | Each agent in its own worktree |

---

## Sanity check on the milestone progress numbers

- Linear says M1 is **69.4%** done, M2 is **81.3%**, M3 is **90.9%**.
- Done count (Linear): **23 issues** in this project at state `Done` as of
  this PR's filing. Open issues across all states (excluding the four
  built-in Linear "tour" tasks RAI-1..4): **8** — RAI-5, RAI-9, RAI-12,
  RAI-16, RAI-21, RAI-23, RAI-24, RAI-31, RAI-32. (This PR closes 9, 12,
  and 32.)
- Open PRs at filing: **PR #24 (RAI-21)** and **PR #22 (RAI-23)**.

## What this PR closes

- **RAI-9** — memory systems research (see `docs/research/memory-systems/`).
- **RAI-12** — this file.
- **RAI-32** — STATUS.md final-state append (see
  `docs/agents/STATUS.md`).
