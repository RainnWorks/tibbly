# Strategic consistency review 001

Reviewer: independent strategy hat, loop M+11.
Date: 2026-06-21.
Scope: full strategic + product corpus produced during the overnight build.
Status: review only. No fixes applied.

## Executive summary

The worst inconsistency is a currency split between the source-of-truth tier
table (USD cents in `apps/backend/src/billing/tiers.ts`) and every other
canonical strategic doc that now quotes the same tiers in GBP; a paying
customer who follows the marketing page to checkout will be billed in dollars
against a Stripe price the backend resolves by env var, which is a real
revenue-correctness hazard. Twenty-three findings total across direct
contradictions, stale claims, missing decisions, and decisions that have been
made in conversation but never reflected in docs. Recommended action: pause
any new strategy work until the contradictions in section 2 below are
reconciled, because every downstream agent that reads two of these docs will
make a different call.

## Direct contradictions

### C1. Tier pricing currency: USD vs GBP (Blocker)

- `apps/backend/src/billing/tiers.ts:50` declares `monthlyPriceCents: 700`
  with no currency field and inline doc strings that refer to the spec as
  USD. `apps/backend/src/billing/tiers.ts:30` calls it "Display price in
  cents USD."
- `apps/marketing/src/sections/PricingTiers.tsx:44` renders `price: "£7"`.
- `docs/architecture/HUB_RELEASE_STRATEGY.md:111` says "What the £7 / £19 /
  £49 tiers buy". `docs/marketing/IA.md:265` says "Hobbyist holds at £7".
- `docs/agents/NORTH_STAR.md` and `docs/agents/OPEN_QUESTIONS.md:9` both
  quote `$7 / $19 / $49`. `docs/research/llm-providers/cost-model.md:174`
  locks the prices as `$7 / $19 / $49`.
- `docs/architecture/LICENSING.md:11` introduces "Tibbly Limited" (a UK
  entity, which implies GBP) as the rights holder.

  **Canonical answer:** GBP is the more recent decision (HUB_RELEASE_STRATEGY
  is loop M+9 and IA.md is loop M+9 synthesis; the cost-model and the backend
  tiers file pre-date the rebrand). The marketing page is what the customer
  sees first; the backend has to match it. The entire stack (Stripe price
  ids, margin math in `cost-model.md`, the budget tables in
  `docs/product/SOCIAL_COMPANION.md` section 3) needs to be re-stated in
  GBP, and the cost model has to be re-checked because OpenRouter charges
  in USD while revenue would come in in GBP. The £/$ conversion at the
  current rate is close enough that margins do not flip sign, but the
  word "USD" in the source of truth file is wrong.

### C2. "Most picked" badge on Hobbyist tier (Important)

- `docs/marketing/IA.md:268` says under verdict 5: "**Killed (#49).**
  Principle 2: honesty over polish. We have no install data yet. The badge
  is a fake until it is real."
- `apps/marketing/src/sections/PricingTiers.tsx:53` still sets the Hobbyist
  tier's `highlighted: true` and `apps/marketing/src/sections/PricingTiers.tsx:162`
  still renders the literal "Most picked" badge on the highlighted card.

  **Canonical answer:** IA.md wins. The canonical synthesis explicitly killed
  the badge on honesty grounds and the file post-dates the live React code.
  The badge has to come out of `PricingTiers.tsx` before the next marketing
  PR.

### C3. Pricing tier card count: three vs four (Important)

- `docs/marketing/IA.md:113` mandates "Three visible tiers (Free, Hobbyist,
  Pro) plus Iron as a footer note". `IA.md:269` cites verdict 6 spelling
  out the placement: "Section 7, after the inventory grid."
- `apps/marketing/src/sections/PricingTiers.tsx:27` declares an array of
  four tier objects (`Free`, `Hobbyist`, `Pro`, `Iron`) and
  `PricingTiers.tsx:147` renders all four as equal cards in a
  `lg:grid-cols-4` layout.

  **Canonical answer:** IA.md is canonical. The current React component
  contradicts the synthesis on a load-bearing layout decision. The Iron tier
  has to be relocated to a footer note, leaving three visible cards.

### C4. Plugin source repo name: `tibbly-plugin` vs `osrs-llm-helper-plugin` (Important)

- `docs/architecture/LICENSING.md:10` and `docs/architecture/REPO_SPLIT.md:33`
  both name the plugin repo `RainnWorks/tibbly-plugin`.
- `docs/product/EMBODIED_COMPANION.md:145` says "all open MIT under the
  existing RainnWorks/osrs-llm-helper-plugin repo as defined in REPO_SPLIT.md."

  **Canonical answer:** REPO_SPLIT.md is canonical for repo naming because
  it is the migration plan that LICENSING.md cites at LICENSING.md:170. The
  embodied companion spec is wrong on the repo name and is citing the wrong
  reference for the backend repo name as well.

### C5. Backend repo name: `tibbly-platform` vs `osrs-llm-helper-backend` (Important)

- `docs/architecture/REPO_SPLIT.md:41` names the backend repo
  `RainnWorks/tibbly-platform`. `docs/architecture/LICENSING.md:11` agrees.
- `docs/product/EMBODIED_COMPANION.md:154` says "Backend-side, closed
  proprietary under the existing rowm/osrs-llm-helper-backend repo as
  defined in REPO_SPLIT.md."

  **Canonical answer:** REPO_SPLIT.md wins. The Embodied Companion spec
  invents a `rowm/osrs-llm-helper-backend` repo path that does not exist in
  the canonical split and that uses the wrong org (`rowm` vs `RainnWorks`).
  Same fix as C4.

### C6. Default companion tier placement (Q-30 vs Q-32) (Important)

- `docs/product/EMBODIED_COMPANION.md:199` recommends Q-30 default: "ship
  the companion to all paid tiers including Hobbyist (£7). The companion
  is the relationship product and gating it above Hobbyist would weaken the
  funnel."
- `docs/product/SOCIAL_COMPANION.md:158` says "Free / Hobbyist tier: ~10
  social interactions per day. S0 is always free... S1 is metered against
  this bucket. S2+ are not available at this tier."
- `docs/agents/OPEN_QUESTIONS.md:264` Q-32 default: "Pro and above.
  Hobbyist gets S0 (passive presence rendering) only."

  **Canonical answer:** This is consistent only if you treat solo as "all
  tiers" and social as "Pro and above". The two specs are individually
  consistent but a casual reader sees a Hobbyist tier that gets the solo
  companion but no S1 social interaction, despite SOCIAL_COMPANION.md
  promising "S1 is metered against this bucket" for Hobbyist. Section 3 of
  SOCIAL_COMPANION needs to be re-stated to match Q-32's "S0 only at
  Hobbyist" verdict, or Q-32 needs to be revised to allow S1 metered at
  Hobbyist. Pick one. Right now the bucket allowance description and the
  open-question default disagree.

### C7. Model defaults: hardcoded vs catalog-driven (Important)

- `docs/agents/DECISION_LOG.md:228` D-9: "no model id is ever hardcoded in
  the plugin, backend, or marketing copy."
- `docs/architecture/HUB_RELEASE_STRATEGY.md:116` says paid tier buys
  "Managed routing, Haiku to Sonnet to Opus escalation per tier".
- `apps/marketing/src/sections/PricingTiers.tsx:48` Hobbyist features
  literally include "Haiku 4.5 fast turns". `PricingTiers.tsx:64` Pro
  includes "Sonnet 4.6 deeper reasoning". `PricingTiers.tsx:79` Iron
  includes "Opus 4.7 on the hardest steps".
- `docs/agents/NORTH_STAR.md:72` hardcodes "Default models: Haiku 4.5
  routing, Sonnet 4.6 chats, Opus 4.7 premium-tier deep reasoning."

  **Canonical answer:** D-9 wins on every code path. North Star and the
  marketing component were written before D-9 landed and now violate it.
  The marketing pricing component is the most damaging violation because
  it ships those model ids to public traffic; if Anthropic renames or
  retires any of those slugs, the marketing page is wrong and the customer
  has paid for the wrong thing. Replace model id strings in marketing copy
  with tier-language ("routing tier", "deeper-reasoning tier", "long-horizon
  tier") and put the actual id under the catalog table per D-9.

### C8. North Star top-level pivot acknowledgement (Important)

- `docs/agents/NORTH_STAR.md:95` lists "What 'we succeeded tonight' looks
  like" including "Dashboard has at least: login, usage chart, account
  binding, Stripe portal link."
- `docs/agents/DECISION_LOG.md:58` D-8 says "User dashboard
  de-prioritised... The web app at `apps/dashboard` is repurposed as
  Tibbly's internal ops console" and "token-spend visibility removed from
  user UI."
- `docs/agents/HANDOFF.md:26` confirms PR #37 merged the rename from
  `apps/dashboard` to `apps/ops`.

  **Canonical answer:** D-8 wins. North Star is now stale on the dashboard
  question; the file still treats the user dashboard as a deliverable. A
  future agent who reads NORTH_STAR first will try to build a usage chart
  with token-spend visibility, which D-8 explicitly bans. NORTH_STAR needs
  a top-of-file callout pointing at D-8.

### C9. Hub-version compatibility: BYOK vs tools-only vs cloud-paid (Important)

- `docs/architecture/HUB_RELEASE_STRATEGY.md:48` defines a "three-tier
  value floor inside the same plugin binary": Tier 1 tools-only, Tier 2
  BYO key, Tier 3 paid Tibbly cloud.
- `docs/marketing/IA.md:323` Free strip copy says "Thirty messages a day
  on the routing-tier model. All the live tools work, every quest, every
  clue, every slayer task. No card, no login. Pair the plugin to your
  account when you want more."
- `apps/marketing/src/sections/PricingTiers.tsx:33` Free tier features list:
  "30 messages / day · Haiku model · routing-tier only · Watermarked
  replies · Core tool surface (find_item, ge_price, wiki)".

  **Canonical answer:** HUB_RELEASE_STRATEGY.md is the more recent strategic
  thinking (loop M+9). It draws a hard distinction between "free tier on
  the Tibbly cloud" (which is what the marketing page advertises) and
  "BYOK in the hub-version plugin" (which is what the hub PR depends on).
  The marketing page does not currently describe the BYOK mode at all; the
  free tier copy implies free is Tibbly cloud with our money paying for
  the OpenRouter call. The hub strategy doc says the honest pitch is
  "Tibbly works for free with your own OpenAI key. Upgrade to a Tibbly
  subscription when you want it to just work" (HUB_RELEASE_STRATEGY.md:127).
  Either the marketing page needs a BYOK mention, or the hub strategy needs
  to acknowledge that the free tier on Tibbly cloud is the public-facing
  free, and BYOK is an additional hub-eligibility lever that is not
  marketed. Right now a hub reviewer reading both will see a strategic
  conflict.

### C10. Q-15 plugin license: MIT default vs hub README BSD-2 (Nit)

- `docs/agents/OPEN_QUESTIONS.md:91` Q-15: default MIT, alternative BSD-2.
  Not closed.
- `docs/agents/DECISION_LOG.md:156` D-10 locks MIT.
- `docs/architecture/LICENSING.md:62` reasons "MIT matches the precedent
  set by player-stats-sync, LeaguesSync, and RuneGPT (Q-15 default)."

  **Canonical answer:** Q-15 is effectively resolved by D-10 but
  OPEN_QUESTIONS.md still lists it as open. Cheap fix: mark Q-15 as
  "RESOLVED 2026-06-21 (loop M+9), see D-10" the same way Q-17 was already
  marked.

### C11. Token surface line item: "72 MCP tools" vs "72 + 1 meta-tool" (Nit)

- `docs/agents/NORTH_STAR.md` removed the line entirely but `CLAUDE.md`
  (the project root file Claude reads first every loop) at the top of
  the worktree's reminder block says "72 game-state MCP tools + 1
  `enable_tools` meta-tool".
- `docs/agents/GAPS.md:147` notes: "CLAUDE.md says '72 MCP tools' but
  `ToolRegistry.kt` has 73 entries (the 73rd is the `enable_tools`
  meta-tool). Not corrected in this PR; logged as Q-RAI5-1 in the
  catalog's open-questions section."

  **Canonical answer:** CLAUDE.md was updated by RAI-5 / PR #31 to read
  "72 game-state tools + 1 meta-tool". GAPS.md row A7's text is stale and
  contradicts itself ("Not corrected in this PR" while CLAUDE.md was in
  fact corrected). Cheap fix: edit GAPS.md A7 to mark the inconsistency as
  resolved.

### C12. Sonnet version on Pro tier: 4.5 vs 4.6 (Nit)

- `docs/research/llm-providers/_SUMMARY.md:9` references "Sonnet 4.5 at
  $3/$15".
- `docs/research/llm-providers/cost-model.md:70` uses "Sonnet 4.5: IN
  $0.00300/K".
- `docs/agents/NORTH_STAR.md:72` says "Default models: ... Sonnet 4.6
  chats".
- `apps/marketing/src/sections/PricingTiers.tsx:64` Pro feature includes
  "Sonnet 4.6 · deeper reasoning on hard questions".
- `docs/agents/HANDOFF.md:177` summary line: "97.4 / 88.0 / 79.1% gross
  margin locked in at quota" presumes the cost-model numbers, which were
  computed on Sonnet 4.5 list prices.

  **Canonical answer:** D-9 should make this moot ("no hardcoded model
  ids"). Until D-9 lands in the marketing component, the Sonnet 4.5 vs
  4.6 split is a real margin-math inconsistency. Sonnet 4.6 list prices
  may differ from 4.5 by the time the cost-model re-runs; HANDOFF's "88%
  margin" line is asserting precision against numbers that have already
  shifted. Flag the cost-model for re-run on 4.6.

## Stale claims

### S1. STATUS.md milestone percentages (Important)

- `docs/agents/STATUS.md:5` says "Loop M+2, 2026-06-21" with M1 ~85%, M2
  ~85%, M3 ~92%.
- `docs/agents/HANDOFF.md:1` says "Refresh: loop M+10" and HANDOFF
  describes 26 merged PRs, the pivot landed at M+2 to M+3, ops console
  re-cast complete, and "All three must-ship milestones structurally
  complete" (HANDOFF.md:175).

  **Canonical answer:** HANDOFF wins on freshness. STATUS.md was last
  updated at M+2 and is now ~8 loops behind. The milestone percentages and
  the "in flight" section are both stale. STATUS.md was supposed to be
  "live status board, updated each loop" per CLAUDE.md but has not been
  maintained since the pivot.

### S2. GAPS.md M1.4/M3.1 (Nit)

- `docs/agents/GAPS.md:161` row M1.4 says "resolved loop M+1" and so does
  M3.1 at GAPS.md:180. The strikethroughs are correct but the row is still
  listed under "Missing pieces" with a Severity column.

  **Canonical answer:** Move resolved rows out of "Missing pieces" into a
  separate "Resolved" section, or delete them. Right now an agent reading
  GAPS.md sees a "High" severity row that is actually closed; high signal
  to noise.

### S3. NORTH_STAR "supporting must-ships" list (Important)

- `docs/agents/NORTH_STAR.md:50` lists "Monorepo restructure", "Token
  economy", "Stripe wiring", "Presence/network feed". All four are now
  shipped per HANDOFF.md:204.

  **Canonical answer:** NORTH_STAR has not been updated since launch
  night. The "supporting must-ships" frame is now historical and reading
  the file leaves a fresh agent confused about what is open vs closed.
  Add a "Status (loop M+10)" callout near the top or move the section
  into past tense.

### S4. DECISION_LOG D-9 references router.ts constants that should be removed by step 2 (Nit)

- `docs/agents/DECISION_LOG.md:241` says "The legacy `MODEL_HAIKU` /
  `MODEL_SONNET` / `MODEL_OPUS` constants in `apps/backend/src/llm/router.ts`
  stay in place".
- The same paragraph in `docs/architecture/MODEL_PLATFORM.md:39` says the
  constants "become a step-2 seed-load only".

  **Canonical answer:** Consistent if you read both together; potentially
  confusing if you read only one. MODEL_PLATFORM.md is more precise. Cheap
  fix: cross-reference the same exact framing in both files.

## Missing decisions

### M1. Currency designation for the Stripe price table (Blocker)

The split between USD and GBP (C1 above) is a missing decision wearing a
contradiction's clothes. We need an explicit decision row in DECISION_LOG:
"All public pricing in GBP; backend tier table re-labelled; Stripe price ids
will be GBP price ids in production." Or the inverse if Tom decides USD is
the production currency.

### M2. Whether BYOK is a public-facing free tier or a hub-only tier (Important)

C9 above. We have not picked between the two stories:

- Story A: the public marketing free tier is "30 messages a day on Tibbly
  cloud" (which is what PricingTiers.tsx and IA.md currently say); BYOK
  is an unmarketed feature that exists for hub eligibility only.
- Story B: the public marketing free tier IS BYOK and the cloud free tier
  does not exist (which is what HUB_RELEASE_STRATEGY.md implies).

This needs a decision row.

### M3. Currency for the social budget (Nit)

`docs/product/SOCIAL_COMPANION.md` section 3 describes budgets as integer
"social interactions per day" with no money attached. That dodges C1, but
the cost commentary in the same section ("roughly half a personal turn")
is also currency-agnostic. Decision needed: are social interactions
metered against a USD-denominated cost pool or a UK-denominated cost pool?
Probably the same answer as M1 but should be stated.

### M4. Embodied companion (Q-29 roadmap commitment) is unresolved (Important)

`docs/product/EMBODIED_COMPANION.md:198` Q-29 explicitly asks "greenlight
M-COMP-1 as a one-week experiment" vs "commit to the full five-to-seven
week roadmap up front". HANDOFF.md M+10 lists the embodied companion as
"in flight" but does not say which Q-29 path the in-flight work is
following. SOCIAL_COMPANION.md section 7 stacks M-COMP-4 through M-COMP-7
on top of M-COMP-3 (SOCIAL_COMPANION.md:412), which assumes the full
roadmap commitment. The social spec depends on a Q-29 answer that has not
been formally given.

### M5. Whether the licence covers the embodied companion's commissioned art (Nit)

`docs/product/EMBODIED_COMPANION.md:167` says the art will be "under the
same MIT plus art-license model already in LICENSING.md." LICENSING.md does
not actually have an art-license model; it covers code only. This is a
missing decision pretending to be a citation.

## Decisions made in conversation but not reflected in docs

### V1. OPEN_QUESTIONS still shows Q-15 as open; D-10 implicitly closes it (Nit)

See C10. The conversation that produced D-10 also locked the MIT decision
for the plugin. OPEN_QUESTIONS.md should mark Q-15 as RESOLVED with a
pointer to D-10, the same way Q-17 was marked.

### V2. Tom called the user dashboard "LLM slop" and pivoted to ops; OPEN_QUESTIONS Q-19 default ("keep `apps/dashboard`") was overridden by HANDOFF (Important)

- `docs/agents/OPEN_QUESTIONS.md:148` Q-19 picked default: "keep
  `apps/dashboard` (rename in place rather than move)."
- `docs/agents/HANDOFF.md:24` says PR #37 merged "`apps/dashboard` →
  `apps/ops` rename", which is option (a) of Q-19.

  **Verdict:** Q-19 was answered in conversation (Tom's M+3 review) and
  the answer was option (a), not the picked default. OPEN_QUESTIONS.md
  should mark Q-19 as RESOLVED (option a) with a pointer to PR #37 and
  D-8.

### V3. Q-20 PR #34 merge-and-forget (Nit)

- `docs/agents/OPEN_QUESTIONS.md:160` Q-20 picked default: "pause".
- `docs/agents/HANDOFF.md:79` says "Q-20 -> merge for compliance (PR #34
  merged)."

  **Verdict:** Q-20 was answered in conversation. OPEN_QUESTIONS.md should
  mark Q-20 as RESOLVED.

### V4. Q-21 plugin account panel vs ops console (Nit)

- `docs/agents/OPEN_QUESTIONS.md:171` Q-21 picked default: "ops console
  first".
- `docs/agents/HANDOFF.md:80` says "Q-21 -> ops + plugin panel as parallel
  tracks (both agents spawned in M+5, in flight)."

  **Verdict:** Q-21 was answered in conversation with a third option not
  in the picked-default ladder. OPEN_QUESTIONS.md should mark Q-21 as
  RESOLVED (parallel tracks).

### V5. Tom committed to RainnWorks as the github org (Nit)

LICENSING.md and REPO_SPLIT.md both encode `RainnWorks/*` as the org
prefix without a corresponding open question or decision row that names
the org. The EMBODIED_COMPANION.md typo uses `rowm/*` (C5 above) which
suggests the org name decision was made in conversation but never logged.
Either add a D-11 row that names RainnWorks as the canonical org, or
fold it into a footnote on D-10.

### V6. Tom approved Tibbly as the brand name (Nit)

OPEN_QUESTIONS.md Q-13 still shows Tibbly as "picked default" awaiting
USPTO check. Every doc downstream of Q-13 (HUB_RELEASE_STRATEGY.md,
LICENSING.md, REPO_SPLIT.md, IA.md, the React component, the social spec)
treats Tibbly as a fixed brand. The brand is fixed in everyone's head
except the OPEN_QUESTIONS.md row. Either resolve Q-13 with a TM-check
caveat, or mark every downstream doc as provisional.

### V7. D-8 banned token-spend visibility in user UI; the marketing page still uses tier-aware copy that complies (Wins)

(Inversion: this is not a missing decision, this is a discipline that
held. Logged in section 5.)

## Internal-consistency wins

Worth preserving while fixing the contradictions above.

### W1. D-10 / LICENSING.md / REPO_SPLIT.md are tightly coupled and cite each other

The licensing/repo-split corpus is the cleanest part of this review. D-10
cites LICENSING.md and REPO_SPLIT.md; both files cite D-10 in their
cross-references section. OPEN_QUESTIONS.md Q-17 is correctly marked as
RESOLVED by D-10. Q-25/Q-26/Q-27 hang off D-10 and are linked in both
directions. This is the model the rest of the corpus should follow.

### W2. D-8 / OPS_DESIGN / GAPS / HANDOFF are coherent on the pivot

The dashboard-to-ops pivot is consistently described across DECISION_LOG
D-8, OPS_DESIGN.md, HANDOFF.md, and the PR #37 reference. The
taste-skill mirror, the auth wall, the ban on three-equal-card layouts
and the no-em-dash rule all repeat across the four files with no
disagreement. The pivot's only stale residue is in NORTH_STAR.md
(S3 / C8 above), which is the older file.

### W3. The egress-gate posture is consistent across hub strategy, data flow, and licensing

`docs/architecture/DATA_FLOW.md`, `docs/architecture/HUB_RELEASE_STRATEGY.md`,
and `docs/architecture/LICENSING.md` all describe the single outbound
WebSocket egress, the no-localhost-HTTP rule, and the ScapeGPT precedent
in compatible language. The architectural story for hub eligibility is
the strongest internal narrative in the corpus.

### W4. Brand voice and IA agree on tone

`docs/marketing/BRAND_VOICE.md` ("the clever friend who already read the
wiki") and `docs/marketing/IA.md` section 6 (the hero copy) speak the
same register with no internal contradiction. The hard bans (no emoji,
no exclamation marks on factual answers, no "as an AI" disclaim) are
mirrored across both docs.

### W5. Hard cap at zero is consistent across DECISION_LOG D-4, NORTH_STAR, cost-model, PricingTiers, and SOCIAL_COMPANION

The "stop at zero unless customer enables auto-top-up" rule is one of the
most-repeated invariants in the corpus and every doc that references it
states it the same way.

## Recommended fix order

What breaks if it's not fixed before the next agent reads it.

1. **C1 currency designation** (`apps/backend/src/billing/tiers.ts:30`
   comment + every downstream cost-model, social budget, pricing copy
   doc). Add D-11 to DECISION_LOG. Without this, a Stripe production
   checkout could bill in the wrong currency.
2. **C2 "Most picked" badge** (`apps/marketing/src/sections/PricingTiers.tsx:53,162`).
   IA.md killed it on honesty grounds. Leaving it live makes the page
   asserts a fact we cannot defend on Reddit.
3. **C3 four-tier pricing layout** (`apps/marketing/src/sections/PricingTiers.tsx`
   structure). IA.md mandates three visible cards plus an Iron footnote.
   Same blast radius as C2 because it ships the wrong message above the
   fold.
4. **C7 hardcoded model ids in marketing** (`apps/marketing/src/sections/PricingTiers.tsx:48,64,79`
   plus `docs/agents/NORTH_STAR.md:72`). D-9 forbids this. Replace ids
   with tier-language and put ids behind the catalog.
5. **C8 NORTH_STAR pivot acknowledgement** (top-of-file callout in
   `docs/agents/NORTH_STAR.md`). A fresh agent reads NORTH_STAR first and
   will build the wrong dashboard otherwise.
6. **C9 BYOK story** (decision row + harmonise HUB_RELEASE_STRATEGY,
   IA.md, PricingTiers free strip copy). The hub PR fails if the marketing
   page contradicts the hub strategy.
7. **S1 STATUS.md rebuild** (`docs/agents/STATUS.md` regenerated from
   `gh pr list --state merged` + HANDOFF.md). Status is the agent's
   primary live signal and it has been stale since M+2.
8. **C4 / C5 / V5 repo-name typos in EMBODIED_COMPANION.md** (3 lines in
   `docs/product/EMBODIED_COMPANION.md:145,154`). Quick fix; without it,
   the embodied companion code will land in the wrong repo path.
9. **C6 social tier alignment** (`docs/product/SOCIAL_COMPANION.md`
   section 3 paragraph on Free / Hobbyist budget). Pick S0-only or S1-metered.
10. **V1-V6 open-question resolutions** (`docs/agents/OPEN_QUESTIONS.md`
    Q-13/Q-15/Q-19/Q-20/Q-21 marked RESOLVED with pointers). Low-risk
    janitorial work but every loop wastes cycles re-litigating answered
    questions.
11. **M4 Q-29 roadmap commitment** (`docs/product/EMBODIED_COMPANION.md`
    + DECISION_LOG row). The social spec is currently committing to a
    roadmap that has not been greenlit.
12. **C10 / C11 / C12 / S2 / S4 / M5 nits** (cleanup as time permits).

End of review.
