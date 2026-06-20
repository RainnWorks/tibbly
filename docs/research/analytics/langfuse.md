# Langfuse — what to mimic

Sources, fetched 2026-06-21:
- https://langfuse.com/docs/observability
- https://langfuse.com/docs/tracing-data-model

## Data model

- **Trace** — a single request/operation. Carries `userId`, `sessionId`, `tags`, `metadata`, `releases`, `versions`, `environment`.
- **Observation** — a step within a trace. Nestable. Specialised into:
  - `generation` — an LLM call (`model`, `input`, `output`, `usage` token counts, cost).
  - `tool call`
  - `RAG retrieval step`
  - `event` / `span` — generic timing units.
- **Session** — multi-trace grouping for multi-turn conversations.
- **Score** — evaluator output attached to a trace, observation, or session.

## What they emphasise

- "Structured logs of every request that capture the exact prompt sent, the model's response, token usage, latency, and any tools or retrieval steps in between."
- LLM-native: `token usage`, `model parameters`, `prompt/completion pairs`, `evaluation scores` are first-class.
- SDKs ship trace data **asynchronously** to avoid blocking the request path.

## Mapped to our taxonomy

| Langfuse | Ours |
|---|---|
| Trace | `chat.message.sent` envelope (the message is the unit) |
| Generation observation | `chat.message.sent.payload.tokens` + `model` + `costMicroUsd` |
| Tool call observation | `chat.tool_call.started` → `.completed` / `.failed` pair |
| Session | `chatId` correlates messages |
| `userId` | envelope `userId` |
| Tags | `feature.tool_family.exposed.payload.reason` |
| Score | (out of scope tonight; would be a separate `score.*` event family) |

## What we copy verbatim

- **Per-trace user + session correlation.** Already in the envelope.
- **Async ingestion.** Bus handlers run "fire and forget" — persistence errors are logged but never throw back to the request thread.
- **Numeric `usage` decomposed into input + output.** Our `payload.tokens = { in, out }` matches Langfuse exactly.

## What we leave for later

- Multi-language SDKs — we only run TS for tonight.
- Score/evaluator framework.
- A separate `metadata` jsonb column on every event — we have one global `payload` jsonb and rely on per-type Zod schemas to enforce shape.
