# Companion brain (backend half of the embodied companion)

*Status: shipping as RAI-67 (loop M+15).*
*Owner: backend.*
*Companion product spec: `docs/product/EMBODIED_COMPANION.md`.*

The plugin renders the sprite. The backend decides what the companion
knows, remembers, and says. This document covers the backend half: the
personality archetypes, the memory store, the reactive-dialogue WS
handler, the end-of-session extraction pass, and the nightly maintenance
jobs.

## North star

Three moments must land (verbatim from `EMBODIED_COMPANION.md` §1):

1. **Recognition on log-in.** The companion's first line back references
   something specific from the player's last session.
2. **Quiet competence mid-task.** When the plugin fires a contextual
   trigger ("you hovered a herb you don't recognise"), the backend can
   respond in one sentence that uses live snapshot data.
3. **A shaped relationship.** After a month, the companion sounds like
   itself, not like a generic assistant. The player's nickname, voice
   notes, and remembered beats all show up in the prompt.

## Storage shape

Two new tables, both keyed off `users.id` and cascade-deleted by the
GDPR Art. 17 endpoint:

### `companion_profile`

| column                 | type        | purpose                                       |
|------------------------|-------------|-----------------------------------------------|
| `id`                   | text PK     | nanoid21                                      |
| `user_id`              | text FK     | billing principal                              |
| `osrs_account_id`      | text FK?    | nullable; one companion per OSRS character    |
| `starter_archetype`    | text        | visual: hooded / fox / wisp / golem           |
| `personality_archetype`| text        | voice id (see §Archetypes)                    |
| `companion_name`       | text?       | player-given nickname                         |
| `voice_style_notes`    | jsonb[]     | capped at 20 newest explicit corrections      |
| `relationship_age_days`| int         | bumps on calendar-day roll                    |
| `last_session_ended_at`| timestamptz | extraction-job marker                         |

`(user_id, osrs_account_id)` is unique. Postgres treats NULL as distinct,
so a user with no paired character still has at most one anonymous row.

### `companion_memories`

| column               | type        | purpose                                |
|----------------------|-------------|----------------------------------------|
| `id`                 | text PK     | nanoid21                               |
| `profile_id`         | text FK     | cascade on profile delete              |
| `body`               | text (≤160) | one-sentence memorable beat            |
| `category`           | text        | pve_progress / goal / preference / ... |
| `weight`             | float       | 0.1-5.0 at insert, decays nightly      |
| `evidence`           | jsonb[]     | probe pointers that justified the row  |
| `first_seen_at`      | timestamptz | insert time                            |
| `last_referenced_at` | timestamptz | bumped when the prompt builder surfaces |
| `forgotten_at`       | timestamptz?| soft-delete by the decay job           |

Indexes: `profile_idx`, `weight_idx`, `last_referenced_idx`.

Migration: `apps/backend/migrations/0004_companion.sql`. Plain CREATE
INDEX (no CONCURRENTLY) because both tables are new and empty.

## Archetypes

Four authored voices in `apps/backend/src/companion/archetypes.ts`:

| id                       | voice anchor                              | when to pick                |
|--------------------------|-------------------------------------------|-----------------------------|
| `dry_wiki_veteran`       | Settled-flavoured documentary calm        | DEFAULT                     |
| `soft_confused_friend`   | warm, slightly soft-spoken, gentle offers | player wants kindness       |
| `sardonic_veteran`       | deadpan, dry beats, never punches down    | player wants the dry beats  |
| `earnest_helper`         | warm, attentive, no sarcasm               | player asked for no sarcasm |

Every prompt prefix carries:

1. The archetype's baseline voice paragraph.
2. The companion's chosen nickname (or the "not given a name yet" line).
3. The relationship age in days.
4. The accumulated voice-style notes, as bullets.
5. The top 5-10 surviving memories ordered by weight × recency.
6. The current world snapshot (`summary` + `triggerType`).
7. A shared closing rule block (no AI-disclaim, no Jagex ToS breaches,
   one short sentence by default).

Closing-rule unit assertions live in `companion-archetypes.test.ts`.

## Reactive-dialogue WS handler

`apps/backend/src/ws/companion.ts` exposes a `CompanionService` that the
plugin WS handler delegates to once a socket is authed. Three frames:

### `companion_trigger`

The plugin asks for a proactive line. The backend:

1. Loads or creates the companion profile.
2. Loads the top-N non-forgotten memories.
3. Builds the archetype prompt.
4. Picks a model via `chooseCheapModel(tier)` (D-9 compliant - see
   §Model selection).
5. Streams the LLM response, capped at ~150 output tokens.
6. Applies the turn cost through the shared `BalanceMeter`.
7. Replies with a `companion_line` frame the plugin renders in the
   speech bubble.

### `companion_interaction_event`

Four event types currently:

| `eventType`           | effect                                                    |
|-----------------------|-----------------------------------------------------------|
| `name_companion`      | sets `companion_profile.companionName` (truncated to 32)  |
| `style_note`          | appends to `voice_style_notes`, capped at 20 newest       |
| `forget`              | soft-forgets memories scoped to `last_session` or `all`   |
| `clicked_companion`   | runs a `companion_trigger` with `clicked_companion` type  |

Each event answers with a `companion_ack(ok)` frame; `clicked_companion`
answers with a `companion_line` instead.

### `companion_memory_hint`

The plugin marks a moment as memorable. The backend pushes it onto the
in-memory hint queue and answers with an ack. The extractor decides at
session-end whether it's worth keeping.

## End-of-session memory extraction

`apps/backend/src/companion/extract-memories.ts`. Runs when:

- The plugin's WS disconnects (player closed RuneLite), OR
- A 30-minute idle timer fires on a still-open socket, OR
- An admin triggers it from the ops console.

Inputs: the queued hints + chat transcript + game-state probes.

Output: up to 10 single-sentence memories, each scored 0.1-5.0. The
output is Zod-validated (`ExtractedMemoriesSchema`) - a malformed LLM
response is dropped (not inserted) and logged. The job also bumps
`relationship_age_days` if the UTC calendar day rolled over since
`last_session_ended_at`.

Model selection happens once per pass via
`chooseCheapModelFromCatalog(db, { provider: "anthropic" })`. No model
id is hardcoded. The fallback (empty catalog) is `chooseCheapModel`.

## Memory decay

`apps/backend/src/companion/decay-memories.ts`. Nightly. For each
non-forgotten memory whose `last_referenced_at` is older than the
30-day window, multiply `weight` by 0.5. When `weight` drops below
0.1, set `forgotten_at = now()` so the prompt builder stops surfacing
the row. The companion's recall is naturally bounded by this - old
beats fade unless they keep getting brought up.

## Voice-style adaptation

`apps/backend/src/companion/voice-adaptation.ts`. Nightly. For each
profile with at least one ended session and at least one style note,
asks the cheap-tier LLM to compact + de-contradict the
`voice_style_notes` list (cap 20). The result replaces the column.

Same D-9 catalog lookup as the extractor; same Zod-guarded output.

## Model selection (D-9 compliance)

No model id is hardcoded in the companion path. The summary:

- **Reactive lines (hot path).** `chooseCheapModel(tier)` from
  `src/llm/router.ts`. Returns the cheapest Anthropic id. Step-2 routing
  policies (the future `routing_policies` table) will replace this with a
  catalog lookup keyed by `(segment, intent="companion_line")`.
- **Offline jobs (extraction, voice adaptation).**
  `chooseCheapModelFromCatalog(db, { provider: "anthropic" })`. Reads
  `model_catalog`, picks the cheapest non-retired row, falls back to
  `chooseCheapModel` only if the catalog is empty (first-ever boot).

The Iron tier still gets Opus on its interactive turns through
`chooseModel(tier)` - the companion path is a separate concern, not a
tier downgrade.

## GDPR cascade

`apps/backend/src/api/me.ts` (Art. 15 export + Art. 17 delete):

- **Export.** `companion_profiles` + `companion_memories` are added to
  the export JSON (`exportVersion: 3`). Existing fields are unchanged.
- **Delete.** The cascade explicitly drops `companion_memories` then
  `companion_profile` inside the transaction. The schema's
  `ON DELETE CASCADE` FKs would handle this anyway; we delete explicitly
  so the audit log reads in the same shape as the rest of the cascade.

Tests in `apps/backend/test/me.test.ts` assert both the export shape and
the cascade.

## Token-cost discipline

| path                     | model           | output cap | notes                       |
|--------------------------|-----------------|------------|-----------------------------|
| reactive line            | cheap-tier      | 150 tokens | one-sentence reactions only |
| `clicked_companion`      | cheap-tier      | 150 tokens | same path                   |
| extraction (per session) | cheap-tier      | one-shot   | structured-output JSON      |
| voice adaptation         | cheap-tier      | one-shot   | per profile per night       |

Companion paths never hit Opus regardless of tier.

## Open work

- Wire the boot-time + nightly scheduler for `decayMemories` and
  `refineVoiceStyleNotes` into `server.ts` (RAI-68 follow-up).
- Surface companion-state metrics on the ops console.
- Build the `STILL_TODO_*` E2E tests once the plugin lands the new
  outbound payloads.
