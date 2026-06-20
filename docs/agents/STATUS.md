# Live agent status board

*Maintained by the operating loop. Last updated: Loop 0 — initial setup.*

## Loop counter: 0

## Stage 1 — foundations

| ID | Agent | Status | Branch | Notes |
|----|-------|--------|--------|-------|
| A1 | Architect | pending | — | Will set up monorepo |
| A2 | Memory/docs steward | running (this loop) | main | Initial docs done; spawn after Stage 2 begins |

## Stage 2 — research + spec

| ID | Agent | Status | Branch | Notes |
|----|-------|--------|--------|-------|
| R1 | RuneLite API explorer | pending | — | |
| R2 | Community / market research | pending | — | |
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
