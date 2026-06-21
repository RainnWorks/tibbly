# Live agent status board

*Maintained by the operating loop. Last updated: Loop 0 — initial setup.*

## Loop counter: 0

## Stage 1 — foundations

| ID | Agent | Status | Branch | Notes |
|----|-------|--------|--------|-------|
| A1 | Architect | done (RAI-13) | agent/eng-infra/monorepo | Monorepo restructure committed; bun install, bun run typecheck, and apps/plugin ./gradlew shadowJar all green. Commit unsigned — see OPEN_QUESTIONS Q-7. |
| A2 | Memory/docs steward | running (this loop) | main | Initial docs done; spawn after Stage 2 begins |

## Stage 2 — research + spec

| ID | Agent | Status | Branch | Notes |
|----|-------|--------|--------|-------|
| R1 | RuneLite API explorer | pending | — | |
| R2 | Community / market research | done | agent/r2/community-pain-points | 13 pain points + creators + trends + summary; Reddit reachability flagged for next loop |
| R3 | OSRS Wiki asset catalog | done | agent/r3/osrs-assets | 26 BSD-2 skill icons + 26 small + 3 CC0 fonts cached; licensing.md flags hard NO on wiki sprites for paid SaaS; Q-7 logged. **Use ONLY @osrs-llm-helper/osrs-assets, never wiki URLs.** |
| R4 | Memory system research | pending | — | |
| R5 | LLM provider research | pending | — | |
| R6 | Gaps analyst | pending | — | Runs every loop after Stage 2 starts |

## Stage 3 — building

| ID | Agent | Status | Branch | Notes |
|----|-------|--------|--------|-------|
| B1 | Backend Core | pending | — | Depends on A1 monorepo skeleton |
| B2 | Backend Billing | pending | — | Depends on B1 |
| B3 | Plugin Migration | pending | — | Depends on B1 |
| B4 | Token Optimizer | pending | — | Plugin + backend changes |
| F1 | Dashboard | pending | — | Depends on A1 + B1 contract |
| F2 | Marketing site | pending | — | Can start early; needs R3 |
| F3 | Realtime / Network | pending | — | Depends on B1 |
| Q1 | QA / E2E | pending | — | Runs each loop after F1+F2 boot |

## Final state at end of overnight — 2026-06-21

- Total Done count: **23** (RAI-6, RAI-7, RAI-8, RAI-10, RAI-11, RAI-13, RAI-14, RAI-15, RAI-17, RAI-18, RAI-19, RAI-20, RAI-22, RAI-25, RAI-26, RAI-27, RAI-28, RAI-29, RAI-30, RAI-33, RAI-34, RAI-35, RAI-36, RAI-37, RAI-38). This PR also closes RAI-9, RAI-12, RAI-32 (so 26 once this PR merges).
- Issues still open in-project: **6** — RAI-5 (RuneLite API depth scan), RAI-9, RAI-12, RAI-16 (OpenRouter proxy), RAI-21 (presence WS, in review), RAI-24 (token-budget HUD), RAI-31 (QA E2E smoke), RAI-32. The four built-in Linear tour tasks (RAI-1..4) are out of scope.
- PRs merged this loop (cumulative on `main`): **24** (PR #1 → PR #25). See `gh pr list --state merged --limit 50 --base main`.
- Open PRs at handoff: **PR #24 — RAI-21 public presence WebSocket + REST endpoint** (branch `agent/rai-21/presence`). All other open work (RAI-23 plugin pairing UI, RAI-18 pairing) has merged.
- Open merge conflicts: none observed at handoff.
- Manual-verify items (post-handoff):
  - plugin ↔ backend live chat round-trip (requires logged-in player and a running backend with `OPENROUTER_API_KEY`),
  - Stripe webhook in production (test-mode CLI replay only so far),
  - RuneLite Hub submission (PR to `runelite/plugin-hub` upstream; package ready, not yet filed),
  - OSRS player auth in-game pairing UX (cannot be E2E'd without a logged-in client),
  - **plus the M1.1 / A1 finding from `docs/agents/GAPS.md`: `local/McpServerService` is still injected and started in `OsrsLlmHelperPlugin.kt` despite RAI-38 — this is a hub-PR blocker; sanity-check before opening the upstream PR.**

