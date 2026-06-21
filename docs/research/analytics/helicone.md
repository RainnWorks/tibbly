# Helicone — what to mimic

Source, fetched 2026-06-21:
- https://docs.helicone.ai/getting-started/quick-start

## What Helicone is

Helicone is a managed gateway in front of 100+ LLM models (OpenAI, Anthropic, Google, Groq, Vertex). Every request is logged, with cost computed at the provider's published price.

## Architecture takeaways

- **Real-time visibility.** "You'll see your request appear in the Requests tab within seconds." Their dashboard streams new rows; not a batch ETL.
- **Two pathways for keys.** Bring-your-own-keys (BYOK) or Helicone-managed keys. Same observability data flows either way. Ours is "all OpenRouter, all the time" so we don't need this duality.
- **Provider-uniform fields.** Their unified API stores model + cost + tokens with the same names regardless of which provider served the request. We do the same by keying on the OpenRouter model slug.

## Mapped to our taxonomy

| Helicone | Ours |
|---|---|
| Per-request log | `chat.message.sent` event |
| Per-tool log | `chat.tool_call.*` events |
| Cost markup | We log `costMicroUsd` (0% markup; matches OpenRouter cost exactly) |
| Properties / tags | We use specific event types (`feature.tool_family.exposed`) instead of free-form tags — easier to write SQL against |

## What we copy

- **Real-time first.** `/admin/realtime` reads raw events from the last 60s, not the materialised view. The dashboard ribbon updates immediately on every event.
- **Cost as integer micro-USD.** Helicone keeps full provider precision; we use `bigint` micro-USD so we never lose a cent to float rounding.

## What we leave for later

- Caching layer (Helicone's killer feature is response-cache analytics). We may add a `cache.hit` / `cache.miss` event family once we have a cache.
- Multi-provider abstraction. OpenRouter already is the abstraction.
- Tagging at the request body level — out of scope.
