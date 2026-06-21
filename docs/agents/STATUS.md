# Live agent status board

*Maintained by the operating loop. Last updated: Loop M+2, 2026-06-21.*

## Headline

- **Milestones:** M1 (productized client) ~85% · M2 (marketing) ~85% · M3
  (backend) ~92%. All three are structurally complete; remaining work is
  hardening + the RAI-5 catalog tier-0 unblockers.
- **PRs merged on `main`:** 32 (overnight + loop M+1 deepening).
- **Open PRs:** 0 at this snapshot (one agent worktree in flight — see
  *In flight* below).
- **Cron:** session-only job `8e5a4446` firing every 20 minutes
  (`7,27,47 * * * *`). Auto-expires after 7 days.
- **Recent loops:**
  - Loop 0–N: overnight build (30 Linear issues Done, 26 PRs).
  - Loop M+1: 6 PRs (#27 → #32) — GAPS verify, og:image, Gradle guard,
    RAI-16 maxRetries, RAI-5 catalog (100+ tools), ToolFamily +6 family
    additions. 7 GAPS entries closed. RAI-5 + RAI-16 marked Done.
  - Loop M+2 (current): top-5 RAI-5 unblockers agent dispatched +
    STATUS.md rebuild.

## Milestones — current state

### M1 · Productized client (plugin ↔ backend chat over WS) — ~85%

| Issue | State | Note |
|---|---|---|
| RAI-13 monorepo | ✅ Done | PR merged overnight |
| RAI-17 WS protocol | ✅ Done | PR #16 |
| RAI-22 plugin BackendClient + CloudChatRunner | ✅ Done | PR #21 |
| RAI-23 plugin pairing UI | ✅ Done | PR #22 |
| RAI-25 tool gating | ✅ Done | PR #17 (turn-1 ≤1.5K tokens) |
| RAI-16 OpenRouter proxy | ✅ Done | Fold-merge of RAI-17; maxRetries added loop M+1 (PR #28) |
| RAI-35 RuneLite Hub submission package | ✅ Done | Submission PR to upstream not yet filed |
| RAI-36 plugin security audit | ✅ Done | PR #23 |
| RAI-38 egress-gate pivot | ✅ Done | PR #11 |
| RAI-5 catalog | ✅ Done | PR #29 (100+ tools) |
| RAI-5 unblockers (5 new tools) | ⏳ In flight (loop M+2 agent) | `agent/rai-5-unblockers/top-5-tools` |
| RAI-24 token-budget HUD in chat sidebar | ⏸ Backlog | Plugin UI |
| M1.6 hub submission PR opened upstream | ⏸ Not started | needs final review of plugin source |

### M2 · Marketing page — ~85%

| Issue | State | Note |
|---|---|---|
| RAI-21 public presence WS + REST | ✅ Done | PR #24 |
| RAI-28 marketing skeleton | ✅ Done | PR merged overnight |
| RAI-29 Hero / ProblemSolution / Demo | ✅ Done | PR #19 |
| RAI-30 Pricing + FeatureGrid + FAQ + Footer + LiveCounter | ✅ Done | PR #19 |
| og:image polish | ✅ Done | PR #27 (loop M+1) |
| Lighthouse perf/a11y/SEO (≥85/95/95) | ⏸ Not verified | M2.3, M2.4 |
| Stripe Checkout link in PricingTiers | ⏸ Not started | Marketing → backend redirect |

### M3 · Backend (auth, credits, billing, token tracking) — ~92%

| Issue | State | Note |
|---|---|---|
| RAI-14 Hono + Drizzle + PGLite skeleton | ✅ Done | PR merged overnight |
| RAI-15 11-table schema + migration | ✅ Done | PR #14 |
| RAI-18 pairing-code auth | ✅ Done | PR #18 |
| RAI-19 Stripe webhook receiver | ✅ Done | PR #20 |
| RAI-20 token meter + hard cap | ✅ Done | PR #20 |
| RAI-26 dashboard skeleton | ✅ Done | PR merged overnight |
| RAI-27 dashboard wiring | ✅ Done | PR #25 |
| RAI-34 legal stack + GDPR endpoints | ✅ Done | needs lawyer review |
| RAI-37 event-driven analytics + admin ops | ✅ Done | PR #13 |
| M3.5 GDPR Art. 17 real deletion | ⏸ Not started | `apps/backend/src/api/me.ts` has 8 TS errors (stub references non-existent modules) |
| M3.6 Postgres prod migration story | ⏸ Not started | `infra/` mostly empty |

### M0 · Research & spec — Done

RAI-5 (RuneLite API catalog, loop M+1), RAI-6 (community), RAI-7 (assets),
RAI-8 (OpenRouter economics), RAI-9 (memory systems), RAI-10 (competitor),
RAI-11 (brand voice + name), RAI-12 (gaps), RAI-32 (memory steward),
RAI-33 (libraries), RAI-34 (legal).

## In flight (at this snapshot)

- **Agent:** `top-5 RAI-5 unblockers` in worktree
  `agent/rai-5-unblockers/top-5-tools`. Implements `get_account_identity`,
  `get_raid_layout`, `get_target_projectiles`, `get_farming_state`,
  `get_active_prayers` per catalog §3. Will open PR with tests,
  ContextRouter keyword updates, and `DATA_DISCLOSURE.md` additions.

## Open questions still live for Tom

Top 5 from `OPEN_QUESTIONS.md`:

1. **Q-18** — Jagex Legal proactive contact? (default: no, ship with mitigations)
2. **Q-7** — Unsigned commits — accept the overnight set or retro-sign?
3. **Q-13** — Product name "Tibbly" — USPTO TESS check before logo spend
4. **Q-2** — Pricing tiers ($7 / $19 / $49) — locked in code; willingness-to-pay unvalidated
5. **Q-12** — Brand voice ("clever friend who already read the wiki")

## How to use this board

- **At loop start:** read `HANDOFF.md` first (it's the one-page wake-up
  briefing). Then this board for milestone-level state. Then `GAPS.md`
  for unfinished work. Then `OPEN_QUESTIONS.md` for Tom-only decisions.
- **At loop end:** update this board's headline + in-flight section.
  Don't list every PR — `gh pr list --state merged --base main` is the
  source of truth.
