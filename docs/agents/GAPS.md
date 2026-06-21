# Gaps analysis -- overnight 2026-06-21

Closes RAI-12 (one-shot end-of-night sweep). What we said we'd ship vs.
what's on disk, broken into:

- **(a) Spec ↔ code inconsistencies** -- what `docs/architecture/` claims vs.
  what merged.
- **(b) Missing pieces per milestone** -- M1 / M2 / M3.
- **(c) OPEN_QUESTIONS Tom must answer before launch.**

Read in conjunction with `docs/agents/HANDOFF.md` (the "what shipped" view)
and `docs/agents/STATUS.md` (the "where everyone landed" view).

---

## (a) Inconsistencies -- docs/INDEX.md ↔ what actually exists

The most important inconsistency: **`docs/INDEX.md` lists architecture
docs that were never written**. Multiple agents read INDEX as the
source-of-truth and would have been pointed at dead links.

| `docs/INDEX.md` claims | Actually on disk | Severity |
|---|---|---|
| `architecture/SYSTEM.md` | **missing** | High -- INDEX line 35 |
| `architecture/IDENTITY.md` | **missing** | High -- INDEX line 36 |
| `architecture/BILLING.md` | **missing** | High -- INDEX line 37 |
| `architecture/TOOL_ECONOMY.md` | **missing** | High -- INDEX line 38 |
| `architecture/MONOREPO.md` | **missing** | High -- INDEX line 39 |
| `architecture/PROTOCOL.md` | **missing** | High -- INDEX line 40 |
| `architecture/DATA_FLOW.md` | present | OK |
| `product/VISION.md` | **missing** | Medium -- INDEX line 29 |
| `product/PERSONAS.md` | **missing** | Medium -- INDEX line 30 |
| `product/PRICING.md` | **missing** | Medium -- INDEX line 31 (PRICING is locked in `research/llm-providers/cost-model.md`; not promoted into product/) |
| `research/runelite-api/` | **missing entire directory** | Medium -- RAI-5 not started; only directory left empty |
| `marketing/POSITIONING.md` | **missing** | Medium -- INDEX line 64 |
| `marketing/ASSET_CATALOG.md` | **missing** | Low -- content is in `research/osrs-wiki/assets.md` |

> **Action for next session:** either generate the architecture docs (they
> would crib heavily from existing research summaries + PR descriptions) or
> trim `INDEX.md` to only list what exists. The latter is faster but loses
> the navigational scaffolding. Probably do both: trim now, generate later.

## (a) Inconsistencies -- spec ↔ what merged

### A1 — `McpServerService` ungated — **REOPENED + RESOLVED 2026-06-21 (loop M+11, RAI-40)**

- **Original concern:** `McpServerService.start()` invoked unconditionally
  at plugin start-up, blocking the RuneLite Plugin Hub submission.
- **Loop M+1 resolution (correct but incomplete):** runtime guard verified —
  `if (config.developerMode() && config.localMcpEnabled()) mcpServerService.start(...)`
  with both flags defaulting to off. The `:checkMcpServerGated` Gradle task
  was added to enforce the guard on every build.
- **What loop M+1 missed:** the hub maintainer's audit
  (`docs/reviews/plugin-hub-readiness-001.md` blocker 3) called out a
  deeper issue: even with the runtime gate, the listener class
  (`co/rowm/osrsllm/local/McpServerService.class`) was still **inside the
  shipped jar**. A maintainer running `unzip -l plugin.jar` finds it and
  rejects on appearance. The `shadowJar` task did not carry any
  `local/**` exclusion — only the grep gates did. The "package boundary"
  claim in SECURITY_DESIGN.md §4 / THREAT_MODEL.md T5 was aspirational
  and unverified.
- **Loop M+11 (RAI-40) resolution:**
  - Removed `McpServerService` and `claudeRunner` from
    `OsrsLlmHelperPlugin.kt` — neither is constructed by Guice on the
    plugin's lifecycle any more.
  - Added `shadowJar.exclude("co/rowm/osrsllm/local/**")` and
    `exclude("**/McpServerService*")` to `build.gradle.kts`.
  - Moved the `io.modelcontextprotocol:kotlin-sdk` and `ktor-server-*`
    dependencies from `implementation` to `compileOnly` so the runtime
    libraries do not ship either.
  - Added the `:checkLocalNotInJar` Gradle task that opens the produced
    jar with `ZipFile` and fails the build if any forbidden entry slips
    in. Wired into `:check`.
  - Updated SECURITY_AUDIT.md row 14, THREAT_MODEL.md T5, and
    SECURITY_DESIGN.md §3/§4 to describe what the build now actually
    enforces, not what an earlier loop claimed.
- **Status:** runtime gate AND artifact exclusion AND build-time
  verification of both. The hub-PR-blocking shape ("plugin exposes
  player information over HTTP") does not apply — the listener is not
  in the jar at all.

### A2 -- `apps/marketing` references RAI-30 but is on `main`

- **Status:** RAI-29 + RAI-30 already merged via PR #19. HANDOFF.md still
  describes them as "in flight at wake-up" (line 41).
- **Severity:** low -- stale HANDOFF copy; rebuild it on next loop.

### A3 -- Stripe webhook handler-failure leak -- **RESOLVED 2026-06-21 (loop M+1)**

- **Original concern:** Idempotency row claimed before handler runs; if
  handler throws, row is orphaned and Stripe replay returns 200 with
  `duplicate: true`, silently dropping the event.
- **Resolution:** re-inspection of
  `apps/backend/src/api/webhooks/stripe.ts:109` shows the catch path
  already calls `unclaimEvent(event.id)`, deletes the orphaned row, and
  returns 500 so Stripe retries cleanly:
  ```ts
  } catch (err) {
    log.error({ err, eventId: event.id, type: event.type }, "stripe webhook: handler threw");
    await unclaimEvent(event.id);
    return c.json({ ok: false, error: "handler_failed" }, 500);
  }
  ```
  The compensating delete (`unclaimEvent`) is itself try/wrapped so it
  can't double-throw. The leak this gap described does not exist.
- **Hardening still worth doing later:** option (b) -- a
  `processed_stripe_events.status` column for retry visibility -- remains
  a nice-to-have for ops dashboards, but isn't needed for correctness.

### A4 -- `/admin/*` auth guard -- **RESOLVED 2026-06-21 (loop M+1)**

- **Resolution:** the admin router in
  `apps/backend/src/api/admin/usage.ts:50-57` installs an
  `app.use("/*", ...)` middleware that returns 401 if **any** of these
  hold: header missing, allow-list empty, header email not in allow-list.
  Allow-list comes from `ADMIN_EMAILS` env (parsed in `env.ts:34-37`)
  and defaults to empty -- i.e. the safe default is "no admin access".
- **Tests:** `apps/backend/test/admin-usage.test.ts` has three explicit
  401 cases -- empty allow-list, missing header, header not in allow-list.
- **Hardening for later (not a blocker):** the guard is header-based, so
  any deployment must strip client-supplied `x-admin-email` at the edge.
  Document this in `docs/architecture/IDENTITY.md` when it's written.

### A5 -- `STATUS.md` rebuilt -- **RESOLVED 2026-06-21 (loop M+2)**

- **Resolution:** rewrote `docs/agents/STATUS.md` as a live milestone
  board with M1/M2/M3 percentages, per-issue state, in-flight agent
  view, and open-questions surface. Source of truth (Linear + merged
  PR list) is referenced rather than copy-pasted, so the board doesn't
  rot between rebuilds.

### A6 -- `LOOP_LOG.md` -- **RESOLVED 2026-06-21 (loop M+1)**

- **Resolution:** the file exists at `docs/agents/LOOP_LOG.md` (the gap
  report missed it). Backfilled the Loop M+1 entry per the
  `docs/agents/MEMORY_ARCHITECTURE.md` contract (Read / Worked on /
  Spawned / Merged / Deferred / Next loop should).

### A7 -- RuneLite-API research folder empty (RAI-5 not started) -- **RESOLVED**

- **Spec:** `docs/INDEX.md` line 71 says `research/runelite-api/` exists.
  RAI-5 is the issue.
- **Status (2026-06-21, post-RAI-5):** the directory now exists. RAI-5
  shipped a 100+ entry catalog (`docs/research/runelite-api/catalog.md`)
  + one-page recommendation (`_SUMMARY.md`). The catalog enumerates
  every existing MCP tool with its RL API call site, plus 60+ proposed
  additions clustered by family with token cost + gating heuristic +
  Kotlin sketch. Tier 0 names the five next tools to ship. Tier 1
  proposes new `ToolFamily.kt` values (`RAIDS`, `LEAGUES`, `FARMING`,
  `APPEARANCE`, `AMBIENT`) and folds (`QUEST_ITEMS` → `QUEST`,
  `FISHING` → `SKILLS`, `PARTY` → `SOCIAL`). RAI-25's family-tag
  refinement now has the input it needed; the "guesses" framing in
  this row no longer applies.
- **Adjacent finding (resolved 2026-06-21 loop M+11):** CLAUDE.md was
  corrected by RAI-5 / PR #31 to read "72 game-state tools + 1
  `enable_tools` meta-tool", matching the 73 entries in
  `ToolRegistry.kt`. Q-RAI5-1 in the catalog's open-questions section
  is closed by the same edit.

---

## (b) Missing pieces per milestone

### M1 · Productized client (plugin ↔ backend chat over WS) -- milestone 69% done

| Gap | Where | Severity |
|---|---|---|
| **M1.1 -- Legacy localhost MCP listener still starts** (A1 above). RuneLite Plugin Hub blocker. | `apps/plugin/.../plugin/OsrsLlmHelperPlugin.kt:197,363` | PR-blocker |
| **M1.2 -- `RAI-23` plugin pairing UI still in review** (PR #22). No green path from "plugin installed" to "paired" without merging this. | `apps/plugin/.../auth/DeviceKey.kt` (per RAI-23 spec) | High |
| **M1.3 -- Token-budget HUD in chat sidebar (RAI-24) not started** -- backlog. Means the chat panel doesn't surface remaining quota. | `apps/plugin/.../chat/ChatPanel.kt` | Medium |
| **M1.4 -- `RAI-16` OpenRouter proxy** ✅ resolved loop M+1. Re-inspection showed `apps/backend/src/llm/openrouter.ts` is fully shipped (not a stub): `runStream`, `buildRemoteTools`, tool-dispatcher pattern. `router.ts` has tier-aware `chooseModel`. `cost.ts` has `computeCostMicroUsd`. WS handler at `ws/plugin.ts:40-48` wires it up. Explicit `maxRetries` (default 3) added loop M+1 to close the last acceptance gap. RAI-16 marked Done in Linear. | `apps/backend/src/llm/{openrouter,router,cost}.ts` | ~~High~~ resolved |
| **M1.5 -- Family-tag refinement requires RAI-5** (the RuneLite API depth scan). Without it, tool-gating families are guesses. Hard to validate the ≤1.5K cap on real workloads. | `apps/plugin/.../cloud/ToolFamily.kt` | Medium |
| **M1.6 -- Plugin hub submission package isn't actually submitted.** The compliance docs exist; the PR to `runelite/plugin-hub` does not. | none -- needs new PR upstream | High (gating launch) |

### M2 · Marketing page (RuneScape-native landing) -- milestone 81% done

| Gap | Where | Severity |
|---|---|---|
| **M2.1 -- `LiveCounter` polls `GET /v1/presence` but `RAI-21` (presence endpoint) is still in review** (PR #24). | `apps/marketing/src/sections/LiveCounter.tsx` (probably) + `apps/backend/src/ws/presence.ts` (probably) | High |
| **M2.2 -- Marketing copy already on Tibbly** ✅ resolved loop M+1. All `osrs-llm-helper` matches in `apps/marketing/src/sections/*.tsx` are either workspace package imports (`@osrs-llm-helper/osrs-assets`), the GitHub repo URL (`RainnWorks/osrs-llm-helper`), or the deliberate "Tibbly · the OSRS LLM Helper" tagline. No stray placeholder user-facing copy. | `apps/marketing/src/**` | ~~Low~~ resolved |
| **M2.3 -- Lighthouse target (≥85 Perf / ≥95 A11y / ≥95 SEO) unverified.** RAI-28 + RAI-29 + RAI-30 all merged but no Lighthouse run reported in any PR description. | n/a -- needs Claude-in-Chrome QA pass | Medium |
| **M2.4 -- Hero hits a ≤1s local-load target unverified** -- same as M2.3. | n/a | Medium |
| **M2.5 -- SEO basics (meta, sitemap, opengraph) not confirmed in PR descriptions.** | `apps/marketing/index.html` + `apps/marketing/public/` | Medium |
| **M2.6 -- RAI-31 QA E2E smoke (Claude-in-Chrome) not run.** Backlog. | n/a | High -- every previous overnight skipped this |

### M3 · Backend (auth, credits, billing, token tracking) -- milestone 91% done

| Gap | Where | Severity |
|---|---|---|
| **M3.1 -- `RAI-16` OpenRouter proxy** ✅ resolved loop M+1 (same as M1.4 above). | `apps/backend/src/llm/openrouter.ts` | ~~Highest~~ resolved |
| **M3.2 -- Stripe webhook handler idempotency leak under handler failure** (A3 above). | `apps/backend/src/api/webhooks/stripe.ts` | Medium |
| **M3.3 -- Admin endpoints auth-guard unverified** (A4 above). | `apps/backend/src/api/admin/usage.ts` + `apps/backend/src/api/_auth.ts` | High (security) |
| **M3.4 -- Dashboard `/billing` route assumes Stripe portal URL is provided by backend; portal-link generation lives in `billing-portal.ts` but there's no spec doc for the redirect-back URL.** | `apps/backend/src/api/billing-portal.ts` | Low |
| **M3.5 -- GDPR Art. 17 (right to erasure) endpoint only stubbed.** HANDOFF.md says "GDPR Art. 15/17 endpoints" landed, but `apps/backend/src/api/me.ts` likely returns a stub. Lawyer pass needs the real deletion flow. | `apps/backend/src/api/me.ts` | High |
| **M3.6 -- Production DB migration story:** PGLite locally, Postgres in prod -- but no `infra/` docker-compose or Fly-deploy script for the Postgres flip. | `infra/` (mostly empty) | High (launch) |
| **M3.7 -- Hard cap → friendly error round-trip in chat** untested end-to-end. The meter rejects, but the plugin's error-rendering path for `balance_zero` is not specified anywhere. | `apps/plugin/.../cloud/InboundCodec.kt` | Medium |
| **M3.8 -- Analytics (`RAI-37`) ingestion is event-driven but no documented retention for `events` table.** | `apps/backend/src/events/persist.ts` + `docs/legal/DATA_RETENTION.md` | Medium |

---

## (c) OPEN_QUESTIONS Tom must answer before launch

Ranked by "what blocks shipping the product to a paying user":

| # | Question | Why it's a blocker | Default we picked |
|---|---|---|---|
| **Q-18** | Reach out to Jagex Legal? | Hardest-to-reverse decision. If we ask, they can say no. If we don't, we ship under "tolerated, not licensed." This determines whether we open with a "no reply from Jagex" launch or a "Jagex-acknowledged" launch. | No, ship with mitigations |
| **Q-14** | Localhost MCP HTTP server must be removed | The plugin literally won't pass hub review until we delete the legacy listener (A1 + M1.1 above). | Remove. Owner: RAI-38; **work is incomplete** |
| **Q-2** | Pricing tiers ($7 / $19 / $49) | Locked in code and copy. Margins prove the numbers (97/88/79%) but willingness-to-pay is unvalidated. Affects every checkout link in the dashboard + marketing. | $7 / $19 / $49 |
| **Q-13** | Product name "Tibbly" | USPTO TESS check was unavailable. Do this before any logo/brand spend. `tibbly.com` is taken; fallbacks `tibbly.app` / `tibbly.gg` / `gettibbly.com`. | Tibbly |
| **Q-7** | Unsigned commits -- accept or retro-sign? | Every overnight branch + every merge to `main` is unsigned. Either retro-sign now or set the rule for merge-to-`main` going forward. | Accept this run; sign future commits |
| **Q-12** | Brand voice register | "Clever friend who already read the wiki" is the chosen voice. Tom can override or refine; copy already uses it. | Calm + dry, rare ribbing |
| **Q-4** | Use Wiki sprites commercially? (now Q-18 supersedes) | Resolved by Q-18 default + `packages/osrs-assets` shipping only BSD-2 + CC0 sources. Confirm Tom is happy with "no Wiki sprites in the paid product." | No Wiki sprites |
| **Q-5** | Network presence -- opt-in or opt-out? | "Opt-in, default off" is on disk. Marketing's "X agents online" widget will look anaemic at launch with everyone opted out by default. Pick a hook (e.g. "show me on the map" toggle in dashboard onboarding). | Opt-in |
| **Q-6** | OSRS account binding -- trust plugin-reported player name? | Currently yes. If we want a verification step (one-time chat-message proof), it adds a friction point but kills account-name spoofing. Resolve before paid launch. | Trust the plugin; add later |
| **Q-1** | Domain name (`osrsllm.app` placeholder) | Default placeholder is wired into `.env`. Decide actual domain before DNS + Stripe production checkout setup. | osrsllm.app |
| **Q-15** | License: MIT or BSD-2-Clause for plugin? | The hub README says BSD-2; in practice both ship. If a reviewer enforces literally, we re-license in the PR cycle. | MIT |
| **Q-16** | Kotlin or Java for plugin? | Same risk profile -- Kotlin works in practice but is on the rejected list literally. | Kotlin, port if pushed |
| **Q-17** | Open-source the backend repo or just the protocol spec? | Goodwill vs. leaking the routing logic. Affects the hub PR's "are these people good actors" read. | Protocol spec only |
| **Q-3** | RuneLite plugin distribution | "Eventually hub; for now sideload via external-plugin-hub config." If Q-18 is "no Jagex ask," then hub-first is even more important. | Hub eventually |
| **Q-8** | Multiple agents share one git checkout | Operational; affects next overnight, not this launch. | Each agent in its own worktree |

---

## Sanity check on the milestone progress numbers

- Linear says M1 is **69.4%** done, M2 is **81.3%**, M3 is **90.9%**.
- Done count (Linear): **23 issues** in this project at state `Done` as of
  this PR's filing. Open issues across all states (excluding the four
  built-in Linear "tour" tasks RAI-1..4): **8** -- RAI-5, RAI-9, RAI-12,
  RAI-16, RAI-21, RAI-23, RAI-24, RAI-31, RAI-32. (This PR closes 9, 12,
  and 32.)
- Open PRs at filing: **PR #24 (RAI-21)** and **PR #22 (RAI-23)**.

## What this PR closes

- **RAI-9** -- memory systems research (see `docs/research/memory-systems/`).
- **RAI-12** -- this file.
- **RAI-32** -- STATUS.md final-state append (see
  `docs/agents/STATUS.md`).
