# Model platform — 5-layer architecture

*Status: step 1 shipped (loop M+9). Steps 2-5 are scoped but unbuilt.*

## North star

Tom, verbatim:

> the model choice MUST be driven by RECENT research — the landscape changes
> all the time. and i want us focusing on the ability to swap it out, segment
> user base, test.

> no model id is ever hardcoded in the plugin, backend, or marketing copy.
> Every call goes through a routing layer, every routing decision is a DB row,
> and every user can be re-segmented at runtime.

That directive lives in `docs/agents/DECISION_LOG.md` as **D-9**.

## The 5 layers

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5 — Sandbox                                              │
│  Operators replay a recorded chat against any model id, side by │
│  side. The shadow grader scores the diff. Future PR.            │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4 — Experiments                                          │
│  Bandit / holdout tests assigned by segment. Operators ship the │
│  winner via a single DB row change. Future PR.                  │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3 — Segments                                             │
│  A user can be moved between segments at runtime. Segment       │
│  membership is a join row, never a column on the user. Future.  │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2 — Routing policies                                     │
│  A policy is a (segment, intent) -> model_catalog.id mapping    │
│  with optional fallback chain. The chooser consults THIS table; │
│  the legacy `MODEL_HAIKU/SONNET/OPUS` constants in router.ts    │
│  become a step-2 seed-load only. Future PR.                     │
├─────────────────────────────────────────────────────────────────┤
│  Layer 1 — Live catalog (THIS PR)                               │
│  Nightly OpenRouter ingest. Source of truth for every model id, │
│  context window, and current price. Boot refresh + 03:17 UTC.   │
└─────────────────────────────────────────────────────────────────┘
```

## Step 1 — what shipped

### Schema

`apps/backend/src/db/schema.ts` defines `modelCatalog`:

| column                                    | type      | meaning |
|-------------------------------------------|-----------|---------|
| `id`                                      | text PK   | OpenRouter id, e.g. `anthropic/claude-sonnet-4.6` |
| `provider`                                | text      | first slash-segment |
| `display_name`                            | text      | from OpenRouter `name` |
| `context_length`                          | integer   | total context window in tokens |
| `input_price_micro_usd_per_million`       | bigint    | µUSD per 1M prompt tokens |
| `output_price_micro_usd_per_million`      | bigint    | µUSD per 1M completion tokens |
| `input_modalities`                        | jsonb     | `["text"]`, `["text","image"]`, ... |
| `capabilities`                            | jsonb     | architecture / top_provider / per_request_limits |
| `first_seen_at` / `last_seen_at`          | timestamp | lifecycle marks |
| `retired_at`                              | timestamp | non-null = absent from latest run |

Migration: `apps/backend/migrations/0003_model_catalog.sql`.

Prices are stored as integer micro-USD per million tokens. Conversion happens
at ingest:

```
priceMicroUsdPerMillion = round(priceUsdPerToken * 1e12)
// Sonnet at $3/M:  0.000003 USD/token  ->  3_000_000 µUSD/M
```

This means the billing meter and the ops display can both read the same
column and never drift across millions of small turns.

### Ingester

`apps/backend/src/llm/catalog/ingest.ts`:

- `refreshModelCatalog(db, options)` fetches
  `https://openrouter.ai/api/v1/models` with a 30s timeout.
- Upsert by id. Existing row → `lastSeenAt = now()`, `retiredAt = null`.
  Missing row → insert with `firstSeenAt = now()`.
- After the upsert pass, any row whose `lastSeenAt < runStartedAt` is
  marked retired in a second SQL pass.
- Errors do NOT mutate the table. Bad fetch / malformed shape returns
  `{ added: 0, updated: 0, retired: 0 }` and logs the failure.
- Tests cover happy path, add+retire round-trip, re-emerge, malformed
  shape, fetch failure, empty-DB seed, and the price-conversion math.

### Scheduler

`apps/backend/src/llm/catalog/scheduler.ts`:

- Boot refresh fires 5 seconds after `startModelCatalogScheduler({ db })`.
- A minute-by-minute `setInterval` checks for 03:17 UTC and triggers the
  nightly refresh. The 17 in the minute is jitter against the retention
  sweeper (00:00 UTC) and OpenRouter's own pricing-page bulk.
- A `stop()` method clears both timers; wired into the SIGINT/SIGTERM path
  in `apps/backend/src/server.ts`.

### Admin routes

`apps/backend/src/api/admin/catalog.ts` mounts under `/admin/catalog/*`:

- `GET /admin/catalog/models?provider=&retired=false` — list with filters.
- `GET /admin/catalog/diff?since=ISO` — added / retired / price-changed rows.
- `POST /admin/catalog/refresh` — manual refresh, returns the same
  `{ added, updated, retired }` shape as the scheduler.

All three are wrapped by the standard `adminGate` middleware.

### Ops console

`apps/ops/src/routes/catalog.tsx` adds a `/catalog` route to Tibbly Ops:

- Stat strip: live models, providers, added (24h), retired (24h).
- Diff card with three columns (added / retired / price changes) and a
  "refresh now" button.
- Sortable table: provider, context length, input price, output price.
- Sidebar nav entry with keyboard shortcut `g c`, wired into `AppShell`.

## What step 2 (routing policies) needs from this work

1. **Read pattern.** Step 2 lookups will join `(segment_id, intent) ->
   model_catalog.id`. The catalog id is the foreign key. Step 2 must NEVER
   fall back to a hardcoded string if the catalog row is missing — instead
   it surfaces a "no policy for this segment" event so operators can
   diagnose.
2. **Price column.** The chooser reports the per-turn cost using
   `input_price_micro_usd_per_million` from this table, not the legacy
   `MODEL_PRICING` map in `llm/cost.ts`. That map stays as a seeding tool
   for the first run (so the meter doesn't return zero before the first
   ingest succeeds).
3. **Retired guard.** A policy that references a `retired_at IS NOT NULL`
   model is a P1 incident. Step 2 ships a daily lint cron that pages on it.
4. **Capabilities.** Multimodal prompts need `input_modalities` to include
   `"image"`. The chooser can use the jsonb column directly; no separate
   capability table.

## Out of scope for step 1

- Per-model history (we don't yet snapshot price changes for diff replay).
  Step 2 adds `model_catalog_history` when it needs the "price moved by
  X%" diff with confidence.
- Latency / availability scoring. OpenRouter publishes some of this; we'll
  layer it in step 4 (experiments) when the bandit needs the signal.
- Retiring the constants in `router.ts` / `cost.ts`. Step 2 owns that
  removal once the routing-policy reader is in place.
