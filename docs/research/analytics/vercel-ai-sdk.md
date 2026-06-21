# Vercel AI SDK telemetry — what to mimic

Source, fetched 2026-06-21:
- https://ai-sdk.dev/docs/ai-sdk-core/telemetry

## Spans they instrument

- `ai.generateText` / `ai.generateText.doGenerate`
- `ai.streamText` / `ai.streamText.doStream` (tracks first-chunk latency)
- `ai.toolCall` (per-tool invocation)
- `ai.embed` / `ai.embed.doEmbed`
- `ai.embedMany` / `ai.embedMany.doEmbed`

## Attributes per span

- `ai.model.id`, `ai.model.provider`
- `ai.usage.promptTokens`, `ai.usage.completionTokens`
- `ai.prompt`, `ai.response.text`, `ai.response.finishReason`
- `ai.toolCall.name`, `ai.toolCall.args`, `ai.toolCall.result`

## OTel GenAI semantic conventions

The SDK exports OTel attributes under `gen_ai.*`:

- `gen_ai.system` (provider)
- `gen_ai.request.model`, `gen_ai.response.model`
- `gen_ai.request.temperature`, `gen_ai.request.max_tokens`, `gen_ai.request.top_k`, `gen_ai.request.top_p`
- `gen_ai.response.finish_reasons`
- `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`

## What we copy

- **Token usage split into input + output.** Our `chat.message.sent.payload.tokens = { in, out }` maps 1:1.
- **First-chunk latency**. Not in tonight's M3, but the event taxonomy reserves room: `chat.message.first_chunk_ms` would be a future event.
- **Tool calls as their own spans.** Our `chat.tool_call.started` → `.completed`/`.failed` pair mirrors the SDK's `ai.toolCall` lifecycle.
- **Optional input/output recording (`recordInputs`, `recordOutputs`).** Privacy-first. Our SCOPE_GUARD already says raw prompts never enter the `events` table — only token counts + model.

## What we leave for later

- OTel exporter. Easy follow-up: write a wildcard subscriber that maps every event to a span and pushes to a configured exporter.
- Embedding spans. Not used yet.
- Multi-step (agentic) `onStepFinish` callbacks. Our tool-loop is shallow; if we go agentic we add `chat.step.finished`.

## Why this matters

If we ever flip the chat orchestrator to run on top of the Vercel AI SDK, the SDK's OTel output already carries every attribute we need — we just teach our bus to consume OTel spans instead of our hand-rolled types. The taxonomy was deliberately chosen to be the smallest sealed superset of what the SDK emits.
