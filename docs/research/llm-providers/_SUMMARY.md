# LLM-providers research — summary

Owner: RAI-8 (model-economics research). Status: v1 locked, ready for backend
agent (RAI-2) to implement.

## TL;DR

- **Anthropic via OpenRouter is the cheapest credible stack.** Haiku 4.5 at
  $1/$5 per Mtok, Sonnet 4.5 at $3/$15, Opus 4.5 at $5/$25. Latest-aliased so
  4.6 / 4.7 land automatically when Anthropic ships them. Source per-model
  pages on [openrouter.ai/models](https://openrouter.ai/models).
- **Three-tier routing**:
  - Hobbyist → Haiku-only.
  - Pro → Haiku router + Sonnet answer.
  - Iron → Haiku router + Sonnet default + Opus escalation on
    `quest_walkthrough | raid_prep | complex_gear_setup | long_horizon_plan`,
    capped at 10 Opus turns/day.
- **Cost per 6-turn conversation** (with our 1.5K-tool-surface + 25%-cached-input
  assumption): **Hobbyist $0.044, Pro $0.111, Iron $0.124.**
- **Margin at full-quota utilization**:
  - Hobbyist $7 / 100K tokens → **97.4%**
  - Pro $19 / 500K tokens → **88.0%**
  - Iron $49 / 2M tokens → **79.1%**
- **Tool gating is the #1 margin lever** — halves per-chat cost vs. ungated.
  Confirms the RAI-3 / RAI-7 priority.

## Files in this folder

- [`openrouter-catalog.md`](./openrouter-catalog.md) — 12-model pricing /
  context table + API behavior summary + fallback decisions.
- [`routing-strategy.md`](./routing-strategy.md) — the routing contract for
  the backend agent: tier defaults, router prompt, escalation rules,
  fallback chain, caching strategy, telemetry hooks, TS pseudo-code.
- [`cost-model.md`](./cost-model.md) — per-tier per-conversation math,
  sensitivity to cache-discount assumption, ungated-vs-gated comparison,
  break-even reference.
- [`_SUMMARY.md`](./_SUMMARY.md) — this file.

## Hand-off to other agents

- **Backend Core (RAI-2):** implement the `TIER_ROUTER` map and the two-call
  loop from `routing-strategy.md#tl-dr-for-the-backend-agent`. The fallback
  chain uses OpenRouter's `models` array — do NOT hand-roll retries.
- **Backend Billing (RAI-4):** the per-call telemetry shape in
  `routing-strategy.md#telemetry-hooks` is the cost-attribution event. Stripe
  metered events fire on that row's `cost_estimate_usd`.
- **Token Optimizer (RAI-3 / RAI-7):** validate the 1.5K tool-surface budget.
  `cost-model.md#comparison-if-we-didnt-gate-tools` shows the margin penalty
  for slipping above that — the single biggest margin lever in the system.
- **Marketing (RAI-5):** the $7 / $19 / $49 tier pricing in this doc is
  locked. Use [`product/PRICING.md`](../../product/PRICING.md) as the public
  copy source — this folder is internal margin math.
- **Dashboard (RAI-6):** surface `cost_estimate_usd` and the cached-input
  ratio per session so users see why their token usage looks how it does.

## Sources (all verified 2026-06-21)

- [openrouter.ai/anthropic/claude-haiku-4.5](https://openrouter.ai/anthropic/claude-haiku-4.5)
- [openrouter.ai/anthropic/claude-sonnet-4.5](https://openrouter.ai/anthropic/claude-sonnet-4.5)
- [openrouter.ai/anthropic/claude-opus-4.5](https://openrouter.ai/anthropic/claude-opus-4.5)
- [openrouter.ai/openai/gpt-5](https://openrouter.ai/openai/gpt-5)
- [openrouter.ai/openai/gpt-5-mini](https://openrouter.ai/openai/gpt-5-mini)
- [openrouter.ai/openai/o3](https://openrouter.ai/openai/o3)
- [openrouter.ai/openai/o4-mini](https://openrouter.ai/openai/o4-mini)
- [openrouter.ai/google/gemini-2.5-flash](https://openrouter.ai/google/gemini-2.5-flash)
- [openrouter.ai/google/gemini-2.5-pro](https://openrouter.ai/google/gemini-2.5-pro)
- [openrouter.ai/deepseek/deepseek-chat-v3](https://openrouter.ai/deepseek/deepseek-chat-v3)
- [openrouter.ai/mistralai/mistral-large](https://openrouter.ai/mistralai/mistral-large)
- [openrouter.ai/qwen/qwen3-max](https://openrouter.ai/qwen/qwen3-max)
- [openrouter.ai/docs/quickstart](https://openrouter.ai/docs/quickstart)

## Re-validate when

- Anthropic ships Haiku 4.6 / Sonnet 4.6 / Opus 4.7 — re-check pricing on
  the `~latest` slugs (aliases may carry new list prices).
- OpenRouter changes its cache-discount default or transforms behavior.
- Our actual per-turn input token distribution (measured in production)
  drifts from the 3K / 1.5K-effective model used here.
