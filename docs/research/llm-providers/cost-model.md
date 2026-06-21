# Cost model — per-tier chat economics, v1

All numbers in USD. List prices pulled from
[`openrouter-catalog.md`](./openrouter-catalog.md) — verified against
openrouter.ai per-model pages on 2026-06-21.

## Inputs (from RAI-8 brief)

- **Turn 1 input**: ~1.5K tool surface + 1K preamble + 0.5K user msg = **3K input tokens**
- **Average turn output**: 750 tokens (midpoint of 500–1000)
- **Average conversation**: 6 turns
- **Cached input price**: 25% of list (conservative vs. OpenRouter's 60–80%
  savings claim). Applied to the 1.9K-ish "tool surface + static preamble"
  block on turns 2–6.

## Per-turn cost math

Let `IN_list = X $/Ktok`, `OUT_list = Y $/Ktok`.

### Turn 1 (no cache hits)
- 3.0 K input × IN_list
- 0.75 K output × OUT_list

### Turns 2–6 (cache hits on 1.9K of input)
Effective input per turn:
- 1.9 K × IN_list × 0.25 (cached)  = 0.475 K · IN_list
- 1.1 K × IN_list (fresh user + history)
- ⇒ **1.575 K effective input** per turn at IN_list price
- 0.75 K output × OUT_list

Across 5 follow-up turns: **7.875 K effective input + 3.75 K output**

### Whole conversation
- Effective input (answer model): 3.0 + 7.875 = **10.875 K**
- Output (answer model): 0.75 × 6 = **4.5 K**
- Router (Haiku, every turn): 1.4 K input + 0.08 K output × 6 turns
  = **8.4 K router input + 0.48 K router output**

## Router cost (all tiers)

Router = `anthropic/claude-haiku-latest`:
- 8.4 × $0.00100 + 0.48 × $0.00500 = **$0.0108 per conversation**

## Tier 1 — Hobbyist ($7/mo, Haiku-only)

Answer model = Haiku 4.5: IN $0.00100/K, OUT $0.00500/K

- Answer input cost: 10.875 × $0.00100 = $0.01088
- Answer output cost: 4.5 × $0.00500 = $0.02250
- Router: $0.01080
- **Cost per 6-turn conversation: $0.04418**

### Token-quota math (100K tokens/month soft cap)

Total tokens billed per conversation ≈ answer effective input (10.875K) +
answer output (4.5K) + router in/out (8.88K) ≈ **24.3K tokens/conversation**.

At 100K quota: **~4.1 conversations/month** before they hit cap.

- Max LLM cost at quota: 4.1 × $0.04418 = **$0.18 / customer / month**
- Revenue: **$7.00 / customer / month**
- Margin: **$6.82 / customer / month (97.4%)**

We are NOT margin-limited on Hobbyist — we are *adoption*-limited. The 100K
cap is a soft enforcement mechanism more than a cost ceiling. Reasonable to
raise to 250K once we're confident on retention.

## Tier 2 — Pro ($19/mo, Sonnet main + Haiku router)

Answer model = Sonnet 4.5: IN $0.00300/K, OUT $0.01500/K

- Answer input cost: 10.875 × $0.00300 = $0.03263
- Answer output cost: 4.5 × $0.01500 = $0.06750
- Router: $0.01080
- **Cost per 6-turn conversation: $0.11093**

### Token-quota math (500K tokens/month soft cap)

Tokens/conv ≈ 24.3K. At 500K quota: **~20.6 conversations/month**.

- Max LLM cost at quota: 20.6 × $0.11093 = **$2.28 / customer / month**
- Revenue: **$19.00 / customer / month**
- Margin: **$16.72 / customer / month (88.0%)**

Pro is the workhorse. If a customer averages 10 conversations/month (realistic
median for an engaged player) cost ≈ $1.11, margin ≈ **$17.89 (94%)**.

## Tier 3 — Iron ($49/mo, Sonnet default + Opus escalation)

We model **20% of answer turns escalate to Opus** (router signals deep-think:
quest, raid, complex gear). This is the long-term steady-state target after
the daily Opus cap nudges escalation behavior toward "save it for the hard
question." On heavy weeks an Iron customer can hit the 10/day Opus cap; this
20% blended ratio is what we expect over a full month.

Sonnet share (80% of answer turns):
- 0.80 × (10.875 × $0.00300 + 4.5 × $0.01500) = 0.80 × $0.10013 = $0.08010

Opus share (20% of answer turns) — Opus 4.5: IN $0.00500/K, OUT $0.02500/K:
- 0.20 × (10.875 × $0.00500 + 4.5 × $0.02500) = 0.20 × $0.16688 = $0.03338

Router: $0.01080

- **Cost per 6-turn conversation: $0.12428**

### Token-quota math (2M tokens/month soft cap)

Tokens/conv ≈ 24.3K. At 2M quota: **~82.3 conversations/month**.

- Max LLM cost at quota: 82.3 × $0.12428 = **$10.23 / customer / month**
- Revenue: **$49.00 / customer / month**
- Margin: **$38.77 / customer / month (79.1%)**

Hard daily Opus cap (10 turns/day) protects us from a worst-case where every
turn escalates. At full Opus saturation a single conversation costs:
- 10.875 × $0.005 + 4.5 × $0.025 + router = $0.18 — still survivable per
  conversation, but the 10/day cap means a customer can't sustain >300
  Opus turns / month even on the most aggressive use. That keeps the
  worst-case at roughly **$0.18 × 50 = $9** in pure-Opus-conversation cost,
  which is still inside margin.

## Sensitivity table — what if our cache discount assumption is wrong?

| Cache discount on input | Hobbyist cost/conv | Pro cost/conv | Iron cost/conv |
|------------------------:|-------------------:|--------------:|---------------:|
| 0% (no caching)         | $0.0539            | $0.1409       | $0.1592        |
| 25% (our assumption)    | $0.0442            | $0.1109       | $0.1243        |
| 75% (OR's high-end)     | $0.0252            | $0.0510       | $0.0556        |

Even in the worst case (no caching at all), Pro and Iron tiers are >85% margin
at quota cap. Hobbyist holds >95%.

## Comparison: if we *didn't* gate tools (turn-1 input = 8K instead of 3K)

This is the counter-factual for the tool-economy work (RAI-3 / RAI-7):

| Tier     | With gating | Without gating | Δ        |
|----------|------------:|---------------:|---------:|
| Hobbyist | $0.0442     | $0.0832        | +88% cost|
| Pro      | $0.1109     | $0.2280        | +106% cost|
| Iron     | $0.1243     | $0.2563        | +106% cost|

Tool gating roughly **halves our per-chat cost**. This is the single
biggest margin lever the backend ships tonight.

## Break-even reference

How many conversations/month at each tier would *eat* the entire monthly fee?

- Hobbyist ($7): 7 / 0.0442 = **158 conv/mo before margin → 0**
- Pro ($19): 19 / 0.1109 = **171 conv/mo before margin → 0**
- Iron ($49): 49 / 0.1243 = **394 conv/mo before margin → 0**

These thresholds are all well above the token-quota cap, so the quota is
already the effective ceiling. Good.

## Open questions

- **Cache write cost.** Anthropic charges a one-time write premium for the
  first cache hit (typically 1.25x list). We model this as absorbed into the
  turn-1 input cost — accurate enough at 6-turn averages, would matter for
  single-turn-then-leave users. RAI-2 should track empirically.
- **Streaming-cancel billing.** We bill the entire stream today; need to
  audit OpenRouter behavior for partial streams.
- **Per-1K-turn variance.** A clue-scroll session averages 12 turns, a price
  check averages 2. Need real-world distribution before locking quotas.
- **Opus-tier upsell economics.** If we let Iron customers pay $0.10/turn
  to unlock unlimited Opus, what's the take-rate? Defer to v2.

## Decision: ship with these prices

Locking in:

- **Hobbyist $7 / 100K tokens / Haiku-only** — 97% margin at quota.
- **Pro $19 / 500K tokens / Sonnet + Haiku router** — 88% margin at quota.
- **Iron $49 / 2M tokens / Sonnet + Opus escalation (10/day cap)** — 79% margin at quota.

All three margins survive the no-cache worst case at >75% margin.
