# OpenRouter model catalog — June 2026

Snapshot of OpenRouter list pricing for the models we plan to evaluate for
osrs-llm-helper. Cited where verified directly from per-model pages on
openrouter.ai. List prices are pre-cache; OpenRouter notes that effective
pricing on cached input can be 60–80% cheaper for repeated context, which is
the lever that makes our ~3K token tool+preamble preamble cheap on turn ≥2.

> **NOTE ON MODEL NAMING.** The product North Star refers to "Haiku 4.5 / Sonnet
> 4.6 / Opus 4.7." OpenRouter as of 2026-06-21 ships **Haiku 4.5 / Sonnet 4.5 /
> Opus 4.5** as the current Anthropic generation. We map our tier defaults to
> these slugs and rely on OpenRouter's `~anthropic/claude-*-latest` aliases so
> that when 4.6 / 4.7 land, the backend auto-rolls without a deploy.
> See [routing-strategy.md](./routing-strategy.md) for the alias plan.

## Comparison table

| Model (OpenRouter slug)           | Input $/Mtok | Output $/Mtok | Ctx     | Notes                                      | Source |
|-----------------------------------|-------------:|--------------:|--------:|--------------------------------------------|--------|
| `anthropic/claude-haiku-4.5`      | $1.00        | $5.00         | 200K    | Our hobbyist default + Pro classifier      | [openrouter.ai/anthropic/claude-haiku-4.5](https://openrouter.ai/anthropic/claude-haiku-4.5) |
| `anthropic/claude-sonnet-4.5`     | $3.00        | $15.00        | 1M      | Pro main chat, Iron default                | [openrouter.ai/anthropic/claude-sonnet-4.5](https://openrouter.ai/anthropic/claude-sonnet-4.5) |
| `anthropic/claude-opus-4.5`       | $5.00        | $25.00        | 200K    | Iron premium burst (quests, raids)         | [openrouter.ai/anthropic/claude-opus-4.5](https://openrouter.ai/anthropic/claude-opus-4.5) |
| `openai/gpt-5`                    | $1.25        | $10.00        | 400K    | Sonnet-class alternative; long context     | [openrouter.ai/openai/gpt-5](https://openrouter.ai/openai/gpt-5) |
| `openai/gpt-5-mini`               | $0.25        | $2.00         | 400K    | Cheapest credible fallback for hobbyist    | [openrouter.ai/openai/gpt-5-mini](https://openrouter.ai/openai/gpt-5-mini) |
| `openai/o3`                       | $2.00        | $8.00         | 200K    | Reasoning fallback; ~Opus quality, cheaper | [openrouter.ai/openai/o3](https://openrouter.ai/openai/o3) |
| `openai/o4-mini`                  | $1.10        | $4.40         | 200K    | Reasoning fallback for Pro                 | [openrouter.ai/openai/o4-mini](https://openrouter.ai/openai/o4-mini) |
| `google/gemini-2.5-flash`         | $0.30        | $2.50         | 1M      | Cheap, long-context option for routing     | [openrouter.ai/google/gemini-2.5-flash](https://openrouter.ai/google/gemini-2.5-flash) |
| `google/gemini-2.5-pro`           | $1.25        | $10.00        | 1M      | Long-context Sonnet alternative            | [openrouter.ai/google/gemini-2.5-pro](https://openrouter.ai/google/gemini-2.5-pro) |
| `deepseek/deepseek-chat-v3`       | $0.20        | $0.80         | 131K    | Floor-pricing tool routing / batch         | [openrouter.ai/deepseek/deepseek-chat-v3](https://openrouter.ai/deepseek/deepseek-chat-v3) |
| `mistralai/mistral-large`         | $2.00        | $6.00         | 128K    | EU-host fallback, decent JSON/tool calls   | [openrouter.ai/mistralai/mistral-large](https://openrouter.ai/mistralai/mistral-large) |
| `qwen/qwen3-max`                  | $0.78        | $3.90         | 262K    | Solid open-weights mid-tier fallback       | [openrouter.ai/qwen/qwen3-max](https://openrouter.ai/qwen/qwen3-max) |

## Per-1K-token unit reference (used in cost-model.md)

Divide the per-Mtok prices by 1000:

| Model              | $/1K in   | $/1K out  |
|--------------------|----------:|----------:|
| Haiku 4.5          | $0.00100  | $0.00500  |
| Sonnet 4.5         | $0.00300  | $0.01500  |
| Opus 4.5           | $0.00500  | $0.02500  |
| GPT-5              | $0.00125  | $0.01000  |
| GPT-5 Mini         | $0.00025  | $0.00200  |
| o3                 | $0.00200  | $0.00800  |
| o4-mini            | $0.00110  | $0.00440  |
| Gemini 2.5 Flash   | $0.00030  | $0.00250  |
| Gemini 2.5 Pro     | $0.00125  | $0.01000  |
| DeepSeek V3        | $0.00020  | $0.00080  |
| Mistral Large 2    | $0.00200  | $0.00600  |
| Qwen3 Max          | $0.00078  | $0.00390  |

## API behavior, briefly

- **Endpoint:** `POST https://openrouter.ai/api/v1/chat/completions` — OpenAI-shape body.
  Streaming via `stream: true` SSE. Tool-calling via standard `tools` / `tool_choice`.
  Source: [openrouter.ai/docs/quickstart](https://openrouter.ai/docs/quickstart).
- **Aliases:** OpenRouter exposes `~latest` aliases (e.g.
  `anthropic/claude-haiku-latest`, `anthropic/claude-sonnet-latest`,
  `anthropic/claude-opus-latest`). We bind our tier defaults to those slugs so we
  auto-track Anthropic's 4.6/4.7 generation when it ships.
  Source: [openrouter.ai/docs/quickstart](https://openrouter.ai/docs/quickstart).
- **Fallbacks:** OpenRouter supports a `models` array on a single request — the
  router cascades down on rate-limit / 5xx. We use this to fall Sonnet → GPT-5,
  Opus → o3 if Anthropic capacity is squeezed. Source:
  [openrouter.ai/docs/quickstart](https://openrouter.ai/docs/quickstart).
- **Prompt caching:** Anthropic Sonnet/Opus/Haiku support cached input on
  OpenRouter. Per-model pages note "for repeated context, this can be 60–80%
  cheaper" — i.e. cached input ≈ 20–40% of list. Our cost model uses a
  conservative **25% of input list price** for cached tool surface + preamble
  on turn ≥ 2. Source: per-model pricing notes on openrouter.ai pages above.
- **Transforms / middle-out:** OpenRouter accepts `transforms: ["middle-out"]`
  on the request body to compress overly-long prompts by dropping the middle
  N% of messages. We do NOT plan to enable this — our token budget is bounded
  by tool gating, and middle-out drops history the player cares about.
  Documented in OpenRouter quickstart.

## Decisions locked

1. **Tier defaults** map to the `~latest` Anthropic aliases (Haiku → Sonnet → Opus).
2. **Fallback chain** uses OpenRouter's `models` array — never a hand-rolled retry loop.
3. **Cached input pricing assumption** = 25% of list (i.e. 75% discount), conservative
   vs. the 60–80% claimed by OpenRouter's own copy.
4. **No middle-out transform** — degrades player UX, doesn't help margin.
5. **DeepSeek V3 / Gemini Flash** reserved as future cheap-router experiments;
   not in v1 routing.
