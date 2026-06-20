# Token-usage analytics — research summary

Sources researched (read via WebFetch on 2026-06-21):

- **Langfuse** — https://langfuse.com/docs/observability + https://langfuse.com/docs/tracing-data-model
- **Helicone** — https://docs.helicone.ai/getting-started/quick-start
- **Vercel AI SDK Telemetry** — https://ai-sdk.dev/docs/ai-sdk-core/telemetry

Detailed notes: [`langfuse.md`](./langfuse.md), [`helicone.md`](./helicone.md), [`vercel-ai-sdk.md`](./vercel-ai-sdk.md).

## What we should mimic

| Idea | From | How it lands in our `events`/`metrics_*` stack |
|---|---|---|
| **Trace / observation hierarchy** | Langfuse | Our `chat.message.sent` is the trace root; `chat.tool_call.*` are observations beneath it. Join by `chatId` + `messageId`. |
| **OTel GenAI attributes** (`gen_ai.usage.input_tokens` / `output_tokens`, `gen_ai.request.model`, `gen_ai.response.finish_reasons`) | Vercel AI SDK | We don't ship OTel tonight, but our `chat.message.sent` payload mirrors the same fields (`tokens.in`, `tokens.out`, `model`). Easy to bridge to OTel later. |
| **User + session grouping** | All three | Envelope already carries `userId`. `chatId` is our session key. |
| **Async, non-blocking ingestion** | Langfuse, Helicone | Our in-process bus is best-effort; persistence errors never throw back into the request handler. |
| **Cost calculation at ingest** | Helicone | `chat.message.sent.payload.costMicroUsd` is computed by the orchestrator before publish, so the metrics rollup only sums precomputed integers — no surprise compute cost in the dashboard query. |
| **Real-time vs aggregated views** | Helicone, Langfuse | `/admin/realtime` reads raw `events` for the last 60s. Other dashboards read from `metrics_*_daily`. |
| **Provider-agnostic field names** | Helicone, Vercel AI SDK | We log `model` as the OpenRouter slug (`openrouter/anthropic/claude-haiku-4.5`); when we add other providers the field is still the same. |
| **Tagging** | Langfuse | We add `feature.tool_family.exposed.payload.reason` (`keyword` / `meta-tool` / `default`) so we can answer "is the tool router actually winning?". |

## What we explicitly DON'T do tonight

- **Streaming time-series DB.** Langfuse + Helicone scale by pushing into ClickHouse. Our per-day Postgres rollups are fine until ~100M raw events; we'll cut over then.
- **Eval scoring.** Langfuse models LLM-as-judge scores as first-class observations. Out of scope.
- **OTel exporter.** Optional later — every attribute we record maps cleanly to OTel GenAI semantic conventions, so the swap is mechanical.
- **Per-prompt cost prediction.** Helicone offers prompt simulation; we'd rather invest in tool gating (the actual cost lever) than predict cost.

## Recommended dashboard widgets (M3)

1. **Daily tool family stacked bar** — `metrics_tool_usage_daily` grouped by `family`.
2. **Cost vs revenue line** — `sum(metrics_chat_daily.cost_micro_usd)` vs Stripe MRR.
3. **Funnel sankey/bar** — `metrics_funnel_daily` ordered by step.
4. **Errors by kind** — `metrics_errors_daily` grouped by `kind`.
5. **Realtime ribbon** — `/admin/realtime` last-minute event rate.
6. **Top spenders table** — `metrics_chat_daily` ordered by `tokens_in + tokens_out`.
