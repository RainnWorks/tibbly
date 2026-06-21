# Loop log — append only

## Loop 0 — 2026-06-21 (handoff start)

### Read
- All user messages this session captured in `docs/product/RAW_INSTRUCTIONS.md`.
- Existing CLAUDE.md context (productize phase).

### Worked on
- Created `CLAUDE.md` operating instructions with NORTH STAR directive.
- Created `docs/agents/MEMORY_ARCHITECTURE.md`.
- Created `docs/product/RAW_INSTRUCTIONS.md` (verbatim user input).
- Created `docs/agents/NORTH_STAR.md` (with three deliverable priorities).
- Created `docs/agents/SCOPE_GUARD.md`.
- Created `docs/agents/ASSIGNMENTS.md` (13 agents across 3 stages).
- Created `docs/INDEX.md`.
- Created `docs/agents/STATUS.md`.

### Spawned
- (none yet — about to spawn the Stage 1+2 workflow)

### Deferred to next loop
- Spawning Stage 3 (building) agents — first need Stage 1 monorepo skeleton.
- Auto-memory updates for cross-conversation persistence.
- Initial commit of the planning docs.

### Next loop should
1. Verify Stage 1 monorepo workflow finished (A1) and committed.
2. Read Stage 2 research outputs; refresh GAPS.md.
3. Spawn Stage 3 builders (B1, B2, B3, B4, F1, F2, F3).
4. Update STATUS.md.
5. Schedule next loop wakeup at 1200s (20 minutes).

## Loop M+1 — 2026-06-21 (post-premature-stop recovery)

### Read
- `NORTH_STAR.md`, `GAPS.md`, `HANDOFF.md`, `OPEN_QUESTIONS.md`, `INDEX.md`.
- Verified cron `8e5a4446` still firing on `7,27,47 * * * *`.
- Verified `feedback_autonomous_never_stop.md` HARD RULE memory is in place.

### Worked on
- Verified three GAPS.md entries were stale and marked them resolved:
  A1 (`McpServerService` gating already in place at
  `OsrsLlmHelperPlugin.kt:198`), A3 (Stripe webhook `unclaimEvent` on
  catch at `stripe.ts:109`), A4 (`/admin/*` deny-by-default middleware
  with 3 explicit 401 tests).
- Added `:checkMcpServerGated` Gradle task as a regression guard.
- Built the marketing og:image stack — `og-card.svg` (1200x630 OSRS
  chat-window), `scripts/build-og.mjs` PNG renderer via `@resvg/resvg-js`
  with `sharp` fallback, full og/twitter metadata in `index.html`.
- Confirmed `apps/backend/src/llm/openrouter.ts` is NOT a stub —
  full `runStream`, `buildRemoteTools`, tier-aware `chooseModel` are
  shipped; only missing piece is explicit jittered retry/backoff for
  transient 429/5xx (Vercel SDK has built-in retries already).

### Spawned
- RAI-5 RuneLite API depth catalog agent in worktree
  `agent/rai-5/runelite-api-catalog`. Will land
  `docs/research/runelite-api/catalog.md` + `_SUMMARY.md`, open PR.

### Merged
- PR #27 — `loop M+1: gaps verify + og:image + Gradle guard` (squashed).

### Deferred to next loop
- Pick up the RAI-5 agent's PR and merge if green.
- Retroactively close RAI-16 in Linear (folded into RAI-17).
- M3.5 — turn GDPR Art. 17 right-to-erasure stub into real deletion.
- M2.2 — sweep `apps/marketing/src/` for stray `osrs-llm-helper` strings.
- M2.3/M2.4 — run Lighthouse against the marketing dev server.
- Add explicit jittered retry/backoff to `openrouter.ts` for transient
  OpenRouter 429/5xx on the streaming path.
- Update `STATUS.md` from "Loop 0" → a real live state view (GAPS A5).

### Next loop should
1. Check PR for RAI-5 catalog; merge if green; refresh `ToolFamily.kt`
   enum order against the catalog's family suggestions.
2. Close RAI-16 in Linear (PR-less retroactive — comment + state flip).
3. Tackle whichever of M3.5 / M2.2 / Lighthouse fits the time budget.
4. Update STATUS.md once at least one of the above completes.
