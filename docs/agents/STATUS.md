# Live agent status board

*Maintained by the operating loop. Last updated: Loop M+10/M+11 boundary,
2026-06-21.*

## Headline

- **Milestones (post-pivot framing per NORTH_STAR + D-8):**
  - M1 productized client + WSS egress + hub-eligibility -- structurally
    complete.
  - M2 marketing page (IA-canon + Stripe Checkout wired + free-tier
    strip) -- structurally complete, IA Phase 1+2 landed in PR #62.
  - M3 Tibbly ops + in-plugin account panel -- both tracks shipped (ops
    in PR #43, plugin panel in PR #40).
- **PRs merged on `main` (62 total).** Use
  `git log --oneline -50` for the live list -- do not duplicate it here.
- **Open PRs at this snapshot:** 0 (this branch will become the next).
- **Strategic-thread swarm:** in flight loops M+9 to M+11 -- embodied
  companion spec (#52), social fabric (#55), licensing split (#53),
  IA synthesis (#54), HUB_RELEASE_STRATEGY (#45), model platform step 1
  (#57), DirectChatRunner BYOK (#58), marketing IA phases 1+2 (#62),
  three review hats (#59 #60 #61).
- **Cron:** session-only job `8e5a4446` firing every 20 minutes,
  healthy.
- **Open questions Q-22 through Q-35:** 14 entries open, mix of hub
  strategy (Q-22–Q-24), repo split timing (Q-25–Q-27), embodied
  companion (Q-28–Q-31), and social fabric (Q-32–Q-35). See
  `docs/agents/OPEN_QUESTIONS.md`.
- **Resolved this loop (by PR #62 + RAI-41 / this PR):**
  Q-13 (Tibbly brand), Q-15 (MIT plugin per D-10), Q-19 (ops rename
  PR #37), Q-20 (PR #34 merged), Q-21 (parallel tracks PR #40 + PR #43).

## What changed since the last STATUS update (PR #33, loop M+1)

- Pivot D-8 landed: dashboard de-prioritised, `apps/dashboard` →
  `apps/ops` (PR #37), token-spend visibility removed from user UI
  (PR #42), taste-skill mirrored (PR #35), Tibbly account panel inside
  RuneLite (PR #40), full ops console recast (PR #43).
- Marketing IA phases 1+2 (PR #62): three visible tiers + Iron footnote,
  no Most-picked badge, model ids removed from copy, BYOK strip added.
- Legal/positioning corpus: hub release strategy 3-tier value floor
  (PR #45), licensing split D-10 + LICENSING.md + REPO_SPLIT.md
  (PR #53), canonical IA synthesis from four proposals (PR #54).
- Product specs: embodied companion (PR #52), social fabric (PR #55),
  mobile companion research (PR #56).
- Engineering: Stripe Checkout wired to PricingTiers (PR #44), model
  platform step 1 with live OpenRouter catalog (PR #57), DirectChatRunner
  BYOK runtime (PR #58), `/v1/me` real implementation (PR #34, #41).
- Three independent review hats produced audits:
  security-skeptic (PR #59), strategic-consistency (PR #60),
  hub-maintainer (PR #61). The strategic-consistency findings are being
  resolved on the current branch (RAI-41).

## Milestones -- current state

### M1 · Productized client (plugin <-> backend chat over WSS) -- done

| Track | State |
|---|---|
| Egress gate + single outbound WSS (no localhost HTTP) | done (PR #11 + audit guard) |
| Backend client + CloudChatRunner | done (PR #21) |
| In-plugin pairing UI | done (PR #22) |
| Tool gating + turn-1 <=1.5K cap | done (PR #17) |
| OpenRouter proxy + maxRetries | done (PR #28) |
| 100+ tool RuneLite catalog + Tier 0 probes | done (PRs #29 #36 #38) |
| BYOK config + DirectChatRunner runtime | done (PRs #51 #58) |
| Hub submission package | drafted; PR upstream not yet filed (M1.6) |
| Plugin account panel inside RuneLite | done (PR #40) |
| Token-budget HUD in chat sidebar | backlog (RAI-24) |

### M2 · Marketing page -- done

| Track | State |
|---|---|
| Vite + Tailwind v4 skeleton | done |
| Hero / ProblemSolution / Demo / Pricing / FeatureGrid / FAQ / Footer / LiveCounter | done |
| og:image polish | done (PR #27) |
| Stripe Checkout wired | done (PR #44) |
| Token-spend strip removed per D-8 | done (PR #42) |
| Canonical IA synthesis | done (PR #54) |
| IA Phase 1+2 (copy + structure + type/palette) | done (PR #62) |
| Lighthouse perf/a11y/SEO (>=85/95/95) | unverified |
| Sprite catalog usage in >=3 sections | done |

### M3 · Tibbly ops + in-plugin account panel -- done

| Track | State |
|---|---|
| Hono + Drizzle + PGLite skeleton + 11-table schema | done |
| Pairing-code auth | done |
| Stripe webhook receiver + idempotency | done (PR #20) |
| Token meter + hard cap at zero | done (PR #20) |
| GDPR Art. 15/17 endpoints (`/v1/me`) | done (PRs #34 #41) |
| Event-driven analytics + admin ops | done (PR #13) |
| Ops console recast (auth-walled) | done (PR #43) |
| Model platform step 1 + ops `/catalog` route | done (PR #57) |
| Postgres prod migration story | not started (M3.6) |

### M0 · Research & spec -- ongoing

R6 community, R7 brand voice + name (Tibbly), R8 OpenRouter economics,
R9 memory systems, R10 competitor + hub precedent, R12 GAPS,
RAI-5 RuneLite API catalog, RAI-32 memory steward, RAI-33 library
scout, RAI-34 legal stack. Loop M+9–M+11 added: embodied companion
spec, social fabric spec, mobile companion research, hub release
strategy, licensing/repo split, marketing IA synthesis + Phase 1+2,
DirectChatRunner BYOK, model platform step 1, three independent
review hats.

## In flight (at this snapshot)

- **This PR (RAI-41)** -- reconciling strategic-consistency findings on
  `agent/loop-mplus11/fix-strategic-consistency`: currency rename, NORTH_STAR
  rebuild, EMBODIED_COMPANION repo paths, STATUS rebuild, Q-status
  updates.

## Open questions still live for Tom

Top of the queue from `OPEN_QUESTIONS.md`:

1. **Q-18** -- Jagex Legal proactive contact (default: no).
2. **Q-7** -- Unsigned commits -- accept the overnight set or retro-sign?
3. **Q-22 / Q-23 / Q-24** -- Hub strategy follow-ups (start BYOK build
   now? when to start the 12-week hub clock? rebrand before
   submission?).
4. **Q-28 / Q-29 / Q-30 / Q-31** -- Embodied companion shape, roadmap
   commitment, tier placement, parasocial posture.
5. **Q-32 / Q-33 / Q-34 / Q-35** -- Social fabric tier placement,
   launch mode, moderation staffing, chat-line surface.

## How to use this board

- At loop start: read `HANDOFF.md` first (one-page wake-up briefing),
  then this board for milestone-level state, then `GAPS.md` for
  unfinished work, then `OPEN_QUESTIONS.md` for Tom-only decisions.
- At loop end: update this board's headline + in-flight section. Don't
  list every PR -- `git log --oneline -50` is the source of truth.
