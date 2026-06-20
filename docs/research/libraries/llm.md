# LLM orchestration — recommendation

## Decision

**Use the Vercel AI SDK (`ai`) v6 with `@openrouter/ai-sdk-provider`.**

This is the well-trodden path for OpenRouter + TypeScript + tool-calling in 2026.
It standardises streaming, tool calls, and provider switching, and is what the
NORTH_STAR mandates.

| Library | Verdict | Why |
|---|---|---|
| **Vercel AI SDK (`ai`)** | ✅ PICK | Provider-agnostic, streaming-first, first-class tool-call API, OpenRouter has an official provider package |
| LangChain.js | ❌ Skip | Heavier abstraction, opinionated chains/agents we don't need, slower release cadence for raw LLM features |
| Mastra | ⚠️ Watch | Newer agent framework on top of AI SDK; useful if we later need workflow primitives, but extra surface area we don't need tonight |

## Packages

- `ai` — Vercel AI SDK core. v6 (latest as of 2026-06).
  - https://ai-sdk.dev/docs/introduction (official)
  - https://github.com/vercel/ai
- `@openrouter/ai-sdk-provider` — Official OpenRouter provider for AI SDK. v1.2.1+.
  - https://www.npmjs.com/package/@openrouter/ai-sdk-provider
  - https://openrouter.ai/docs (provider mentioned in OpenRouter integrations docs)
- `zod` — required peer for tool schemas.

## Install

```bash
bun add ai @openrouter/ai-sdk-provider zod
```

## Hello-world: streaming text

Source: https://ai-sdk.dev/docs/introduction (paraphrased to use OpenRouter)

```ts
import { streamText } from 'ai';
import { createOpenRouter } from '@openrouter/ai-sdk-provider';

const openrouter = createOpenRouter({
  apiKey: process.env.OPENROUTER_API_KEY!,
});

const result = await streamText({
  model: openrouter.chat('anthropic/claude-sonnet-4.6'),
  prompt: 'Give me a one-line OSRS tip.',
});

for await (const chunk of result.textStream) {
  process.stdout.write(chunk);
}
```

## Hello-world: streaming + tool calls

Source: https://ai-sdk.dev/docs (tool-call pattern is identical across providers
because the SDK normalises it).

```ts
import { streamText, tool, stepCountIs } from 'ai';
import { createOpenRouter } from '@openrouter/ai-sdk-provider';
import { z } from 'zod';

const openrouter = createOpenRouter({ apiKey: process.env.OPENROUTER_API_KEY! });

const result = streamText({
  model: openrouter.chat('anthropic/claude-sonnet-4.6'),
  prompt: 'How much XP for level 99 Slayer?',
  tools: {
    xpForLevel: tool({
      description: 'Return total XP required to reach an OSRS level.',
      inputSchema: z.object({ level: z.number().int().min(1).max(99) }),
      execute: async ({ level }) => {
        // tool body — could call into the plugin over WebSocket
        return { totalXp: Math.floor(level ** 3 * 1.1) };
      },
    }),
  },
  stopWhen: stepCountIs(5), // cap multi-step tool loops
});

for await (const part of result.fullStream) {
  if (part.type === 'text-delta') process.stdout.write(part.text);
  if (part.type === 'tool-call') console.log('tool', part.toolName, part.input);
}
```

## When NOT to use

- **Hard real-time per-token budgeting mid-stream.** The SDK abstracts the byte
  stream; if we need to inject a kill switch *between* tokens to enforce a
  credit cap, we wrap `streamText` with our own AbortController and track usage
  from `result.usage` on completion. Acceptable for v1; revisit if a tier
  abuses streaming.
- **Direct OpenRouter feature access.** A handful of OpenRouter-specific knobs
  (provider routing, fallbacks list, transforms) are exposed through extra
  body params on the provider; if we ever need a feature the provider doesn't
  passthrough we'd drop to the raw OpenRouter HTTP API for that call only.
- **Non-LLM streaming.** Don't use this for our plugin↔backend WebSocket; that's
  Bun's native WS. See `realtime.md`.

## Maintenance signals (verified 2026-06)

- `ai` — v6 line is current. https://ai-sdk.dev/docs/introduction
- `@openrouter/ai-sdk-provider` — actively published; v1.2.x line current per
  the npm page. https://www.npmjs.com/package/@openrouter/ai-sdk-provider
- Vercel ships the SDK as a core product so cadence is fast — minor releases
  multiple times per month historically.

## Default models (per NORTH_STAR)

- `anthropic/claude-haiku-4.5` — cheap routing turns, classifiers.
- `anthropic/claude-sonnet-4.6` — default chat.
- `anthropic/claude-opus-4.7` — premium tier deep reasoning.
