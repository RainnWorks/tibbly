# Routing strategy — osrs-llm-helper, v1

This is the routing strategy the backend agent (RAI-2 et al.) must implement.
It is the contract; treat changes as a versioned migration.

## Goals

1. **Hobbyist** tier ships at a margin even on chatty users (Haiku-only).
2. **Pro** tier feels meaningfully smarter than hobbyist on combat / quest /
   gear questions — Sonnet does the answer, Haiku does the routing.
3. **Iron** tier handles long deep-thinking questions (quest walkthroughs,
   raid prep, GIM strategy) — Sonnet by default, Opus on intent-matched
   "deep" turns.

All three tiers share the same OpenRouter client, the same tool-gating
preamble, the same per-customer token meter. The difference is *which slug*
the backend hits for the answering turn.

## Tier defaults

| Tier     | Routing/classifier turn        | Main answer turn                         | Deep-think escalation     |
|----------|--------------------------------|------------------------------------------|---------------------------|
| Hobbyist | `anthropic/claude-haiku-latest` | `anthropic/claude-haiku-latest`         | not available             |
| Pro      | `anthropic/claude-haiku-latest` | `anthropic/claude-sonnet-latest`        | not available             |
| Iron     | `anthropic/claude-haiku-latest` | `anthropic/claude-sonnet-latest`        | `anthropic/claude-opus-latest` on intent match |

All slugs use OpenRouter `~latest` aliases so 4.6 / 4.7 roll in automatically
when Anthropic ships them. Sources: per-model pages on
[openrouter.ai](https://openrouter.ai/models).

## The router (turn-1, all tiers)

Every user message goes through a **router turn** before the main answer turn.
Router turn responsibilities:

1. Classify intent → tool family to unlock (combat / skilling / quest / clue /
   GIM / general / off-topic).
2. Decide whether to escalate to Opus (Iron tier only; intent must be one of
   `quest_walkthrough`, `raid_prep`, `complex_gear_setup`, `long_horizon_plan`).
3. Emit a compact JSON action: `{"family": "...", "escalate": true|false}`.

Router model: **Haiku 4.5** on every tier (cheap, fast, JSON-stable, 200K ctx
is plenty). Reasoning: paying for Sonnet on a structured-output classifier turn
is wasted margin; Haiku JSON-mode handles this at ~5% of Sonnet's cost.

Router input is bounded: `system (router brief, ~400 tok) + last user msg
(~500 tok) + last 2 assistant turns trimmed (~500 tok)` ≈ **1.4K tokens in**,
**~80 tokens out**.

Router cost per call:

- Haiku: 1.4K × $0.00100 + 0.08K × $0.00500 = **$0.0018** per router turn.

## The main answer turn

After the router classifies, the backend:

1. Loads the chosen tool family's manifest (~600 tok) onto the always-on core
   (~900 tok) for ~**1.5K tokens of tool surface** total. See
   [`architecture/TOOL_ECONOMY.md`](../../architecture/TOOL_ECONOMY.md).
2. Builds the preamble (player snapshot: name, combat level, location, gear,
   active task) ≈ **1K tokens**.
3. Appends user message (~0.5K) and recent assistant context (~1K).
4. Calls the tier's main model.

Main turn input on turn 1 ≈ **4K tokens** (tool surface + preamble + user +
short history). Output 500–1000 tokens. We model 750 tokens output as average.

Subsequent turns: tool surface + preamble cache-hits at ~25% of list price.

## Escalation (Iron tier)

The router emits `escalate: true` when intent ∈ {quest_walkthrough, raid_prep,
complex_gear_setup, long_horizon_plan}. The backend then routes that one turn
to `anthropic/claude-opus-latest`. Subsequent turns fall back to Sonnet unless
the router escalates again.

Hard caps to protect margin:

- Iron tier: max **10 Opus turns / day / customer**. After that the system
  silently falls back to Sonnet and surfaces a "Opus quota refreshes at
  midnight UTC" pill in the UI.
- Pro tier: Opus is **not** wired. Pro upgrade prompt in UI if the router
  thinks the user is asking for deep-think on a Pro tier.

## Fallback chain (OpenRouter `models` array)

We pass `models` to OpenRouter so the router cascades on rate-limit / 5xx
instead of us hand-rolling retries.

| Primary                           | Fallback 1               | Fallback 2                |
|-----------------------------------|--------------------------|---------------------------|
| `anthropic/claude-haiku-latest`   | `openai/gpt-5-mini`      | `google/gemini-2.5-flash` |
| `anthropic/claude-sonnet-latest`  | `openai/gpt-5`           | `google/gemini-2.5-pro`   |
| `anthropic/claude-opus-latest`    | `openai/o3`              | `anthropic/claude-sonnet-latest` |

Rationale:

- **Haiku → GPT-5 Mini → Gemini Flash:** all three are <$0.50/Mtok input,
  all three return JSON cleanly. Gemini Flash is last because the JSON-mode
  reliability historically lags Anthropic / OpenAI.
- **Sonnet → GPT-5 → Gemini Pro:** GPT-5 is the closest non-Anthropic
  Sonnet-class. Gemini 2.5 Pro is the long-context safety net.
- **Opus → o3 → Sonnet:** o3 is the cheapest reasoning peer. If both
  Anthropic and OpenAI reasoning capacity is gone, we'd rather give the
  user Sonnet than nothing.

Source: [OpenRouter quickstart](https://openrouter.ai/docs/quickstart) describes
the `models` array fallback behavior.

## Caching strategy

For Anthropic models we set `cache_control: { type: "ephemeral" }` on:

- The tool surface block (1.5K tokens — recompiles only on tool gating change).
- The static portion of the preamble (system prompt + style guide, ~400 tokens).

We do **not** cache the player-state portion of the preamble (changes every
turn) or the conversation history (cheap relative to the cache write cost).

Expected cache hit rate on turn 2+ ≈ 90% of input tokens. We model **25% of
list input price** for cached tokens. OpenRouter advertises 60–80% savings on
cached input via per-model pricing notes; 25% of list is the conservative
floor.

## Telemetry hooks the backend MUST emit

Each LLM call emits a row:

```ts
{
  customer_id, tier, turn_idx,
  model, fallback_used: boolean,
  input_tokens, cached_input_tokens, output_tokens,
  router_decision: "haiku" | "sonnet" | "opus",
  escalated: boolean,
  intent_family: string,
  latency_ms,
  cost_estimate_usd, // computed from list price + cache discount
}
```

This feeds:

- the dashboard token-usage chart,
- per-customer billing,
- the routing tuning loop (we re-tune escalation thresholds weekly).

## Open questions punted to v2

- Per-customer model preference override (Pro/Iron paying to use Opus on every
  turn). Defer until we see demand.
- Streaming token cost amortization for cancelled streams. Today we bill the
  full stream; revisit if abuse appears.
- Auto-promotion of a Pro user to Iron-trial when the router escalates 3x in a
  session. Marketing-side decision.

## TL;DR for the backend agent

```ts
const TIER_ROUTER = {
  hobbyist: { main: "anthropic/claude-haiku-latest", opus: false },
  pro:      { main: "anthropic/claude-sonnet-latest", opus: false },
  iron:     { main: "anthropic/claude-sonnet-latest", opus: true  },
};

// Step 1 — always Haiku router
const decision = await openrouter.chat({
  model: "anthropic/claude-haiku-latest",
  models: ["openai/gpt-5-mini", "google/gemini-2.5-flash"],
  messages: ROUTER_MESSAGES,
  response_format: { type: "json_object" },
});

// Step 2 — answer
const cfg = TIER_ROUTER[tier];
const useOpus = cfg.opus && decision.escalate && withinDailyOpusCap(customer);
const answerModel = useOpus
  ? "anthropic/claude-opus-latest"
  : cfg.main;

const fallbacks = answerModel.includes("opus")
  ? ["openai/o3", "anthropic/claude-sonnet-latest"]
  : answerModel.includes("sonnet")
    ? ["openai/gpt-5", "google/gemini-2.5-pro"]
    : ["openai/gpt-5-mini", "google/gemini-2.5-flash"];

const reply = await openrouter.chat({
  model: answerModel,
  models: fallbacks,
  messages: assembleMessages(decision.family, preamble, history, userMsg),
  stream: true,
});
```
