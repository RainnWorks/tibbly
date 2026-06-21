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

## Loop M+2 — 2026-06-21 (RAI-5 catalog harvest + STATUS rebuild)

### Read
`NORTH_STAR.md`, `GAPS.md`, `HANDOFF.md`, `OPEN_QUESTIONS.md`,
`INDEX.md`. Verified cron `8e5a4446` still firing.

### Worked on
- Reviewed RAI-5 catalog agent's PR #29 (100+ tools, 776 lines), merged.
- Marked RAI-5 Done in Linear.
- Pulled the additive subset of catalog §6 into PR #31 — added
  RAIDS / LEAGUES / FARMING / APPEARANCE / AMBIENT / PETS to
  `ToolFamily.kt`. Deferred PARTY→SOCIAL + FISHING/QUEST_ITEMS folds.
- Fixed CLAUDE.md "72 MCP tools" typo → "72 game-state tools + 1 meta-tool".
- Rebuilt `STATUS.md` as a live milestone board (resolved GAPS A5).

### Spawned
- Top-5 RAI-5 unblockers agent in worktree
  `agent/rai-5-unblockers/top-5-tools`.

### Merged
- PR #29 — RAI-5 full catalog
- PR #31 — RAI-5 follow-up (additive families)
- PR #32 — HANDOFF refresh
- PR #33 — STATUS.md rebuild

### In flight at loop close
- PR #34 — M3.5 `/v1/me` GDPR rewrite, opened but NOT merged.
- RAI-5 unblockers agent still running.

## Loop M+2.5 — 2026-06-21 (Tom woke up, pivot)

Mid-loop Tom returned and gave significant product direction:
- Dashboard reads as LLM slop visually + conceptually
- Most user-facing account mgmt → in RuneLite, not web
- Hide token spend from users entirely
- Build internal Tibbly ops platform (user search, ban, refund)
- Apply taste-skill (mirrored to `.claude/skills/taste-skill/SKILL.md`)

Memories saved:
- `project-dashboard-pivot-2026-06-21`
- `feedback-hide-token-spend-from-users`
- `feedback-taste-skill-no-llm-slop`

Pivot logged as D-8 in `DECISION_LOG.md` + Q-19/Q-20/Q-21 in
`OPEN_QUESTIONS.md`. Asked Tom three questions; no answer yet so I'm
queuing the pivot work and continuing on safe documentation tasks.

### Next loop should
1. Check for Tom's response to Q-19/Q-20/Q-21 before starting
   irreversible pivot work.
2. Pick up RAI-5 unblockers PR if it lands; merge if clean.
3. Skip user-dashboard improvement work — Tom may scrap that whole
   surface.
4. Safe deepening: marketing copy review, additional backend tests,
   documentation polish, taste-skill pre-flight checklist study.
