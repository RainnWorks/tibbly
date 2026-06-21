# SQL migration safety review 001

- Reviewer: SQL migration safety hat (hat 8).
- Date: 2026-06-21.
- Branch: `agent/loop-mplus12/review-sql-migration-safety`.
- Linear: RAI-58.
- Scope: every file under `apps/backend/migrations/`, cross-referenced with `apps/backend/src/db/schema.ts` and `apps/backend/test/_db-fixture.ts`.
- Files reviewed:
  - `apps/backend/migrations/0000_merged_schema.sql` (RAI-15 + RAI-37 merged baseline, 223 lines, 14 tables, 7 enums, 36 indexes, 14 foreign keys).
  - `apps/backend/migrations/0001_rai18_auth.sql` (no-op `SELECT 1;` stub kept to stabilise the drizzle journal).
  - `apps/backend/migrations/0002_processed_stripe_events.sql` (one table for Stripe webhook idempotency).
  - `apps/backend/migrations/0003_model_catalog.sql` (one table plus two indexes for the OpenRouter model catalog).
- Postgres version assumed: 16+ (no version is pinned anywhere in `infra/` or `apps/backend/package.json`; we should pin).
- PGLite compatibility checked: yes (the test fixture at `apps/backend/test/_db-fixture.ts:30-42` reads `migrations/*.sql` in name order and splits on `--> statement-breakpoint`).
- Authority: https://squawkhq.com/docs/rules (Squawk core rules list).

## Verdict

Would-merge-with-fixes. Nothing in the four migrations is data-destroying, nothing drops a column, nothing changes a column type, and the schema is greenfield so the "adding NOT NULL to a populated table" class does not apply yet. The blockers below are about the production runner, not the SQL itself: the drizzle-kit migrator wraps every migration in a transaction by default and no migration sets `lock_timeout` / `statement_timeout`, which is the single fix that takes the next CONCURRENTLY-shaped change out of "incident" territory. The migrations also lean on Postgres semantics PGLite silently accepts (partial unique indexes with double-quoted column references, `'{}'::jsonb` casts in defaults) that will continue to work but should be guarded with a CI parse against real Postgres before we have a real users table.

## Hard blockers

### B1. `processed_stripe_events.event_id` is `text PRIMARY KEY` with no length cap and no FK semantics declared

Squawk rule: `prefer-text-field` (https://squawkhq.com/docs/prefer-text-field). `text` is fine. But: the field is the Stripe `event.id`, which is a stable 30-char `evt_*` token, and there is no unique constraint outside of the PK and no `CHECK (length(event_id) BETWEEN 10 AND 64)` guarding against an attacker-controlled webhook payload writing a multi-megabyte string into the dedup table and OOM-ing the row cache.

File: `apps/backend/migrations/0002_processed_stripe_events.sql:2`. Schema: `apps/backend/src/db/schema.ts:516`.

Why this is a blocker: the entire purpose of the table is "we trusted Stripe enough to write the id"; in practice the handler does `await db.insert(processedStripeEvents).values(...)` with whatever the webhook sent. A misconfigured forwarder or a malicious replay aimed at a debug endpoint can balloon row size to the 1 GB toast limit, and `PRIMARY KEY` on an unbounded text column means a single oversized row can split a btree page hot path. This is also the only billing-critical table without a sanity-check constraint.

Fix: add `CONSTRAINT processed_stripe_events_event_id_shape CHECK (event_id ~ '^evt_[A-Za-z0-9]{10,64}$') NOT VALID`, then a follow-up `VALIDATE CONSTRAINT` step. Squawk rule: `constraint-missing-not-valid` (https://squawkhq.com/docs/constraint-missing-not-valid). Cheap to add now because the table is empty.

### B2. Drizzle-kit migrator wraps each file in a single transaction; no `lock_timeout` or `statement_timeout` is set anywhere

Squawk rules: `require-concurrent-index-creation` (https://squawkhq.com/docs/require-concurrent-index-creation), `ban-concurrent-index-creation-in-transaction` (https://squawkhq.com/docs/ban-concurrent-index-creation-in-transaction), `require-timeout-settings` (cited in `docs/agents/CODE_QUALITY_PROMPTS.md:933`; see Squawk note on lock_timeout in the rule index).

File: `apps/backend/drizzle.config.ts:14-20` declares `drizzle-kit migrate` as the prod runner. drizzle-kit applies each `.sql` file inside a transaction by default. `apps/backend/migrations/0000_merged_schema.sql:183-223` then issues 36 non-concurrent `CREATE INDEX` statements. On an empty greenfield DB this is fine; on the day we run a follow-up migration that touches a populated `messages` or `usage_records` table, the next `CREATE INDEX` will take ACCESS EXCLUSIVE for the duration of the build and there is no timeout to bail us out.

Why this is a blocker today: not because the current four files break, but because the runner contract is baked in. The first migration after we ship will inherit the same shape and the first index on `messages(chat_id, created_at)` over a real customer dataset will lock the table for the full build. We need the timeout floor and the CONCURRENTLY pattern documented and enforced before that day. Today the four migrations would all succeed against a populated database only because the database is empty; that is a one-time grace.

Fix: prepend every future migration that touches a populated table with `SET lock_timeout = '2s'; SET statement_timeout = '60s';`. For index work, emit the new index in two phases: a no-op file that runs `--> statement-breakpoint` only, then a wrapper script (or a drizzle-kit `breakpoints` config tweak) that runs `CREATE INDEX CONCURRENTLY` outside any transaction. The Squawk rule `ban-concurrent-index-creation-in-transaction` explicitly calls this out; we should add a project-level note in `docs/architecture/` so the next agent does not regress this.

### B3. Test fixture and production runner do not apply migrations identically

File: `apps/backend/test/_db-fixture.ts:30-42` reads `migrations/*.sql` in name order, splits each file on `/-->\s*statement-breakpoint/g`, and `raw.exec()`s each statement individually with no enclosing transaction. drizzle-kit's `migrate` command, by contrast, reads `migrations/meta/_journal.json`, applies each file inside one transaction, and uses the `--> statement-breakpoint` marker only as a hint for splitting when the driver requires single statements.

Concrete drift: any failure inside the middle of `0000_merged_schema.sql` in production rolls back the whole file; in the test fixture, prior statements in the file persist. That means a test can pass against a partially-applied schema that production will never see, and vice versa. The journal file (`apps/backend/migrations/meta/_journal.json`) is not consulted by the test fixture at all, so any migration the journal marks as already-applied still runs from scratch in tests.

Why this is a blocker: it is exactly the class of bug Hat 8's brief calls out (`docs/agents/CODE_QUALITY_PROMPTS.md:943`, "the test fixture splits on it; does production?"). We are running tests against a fundamentally different replay model than the prod migrator.

Fix: replace `_db-fixture.ts:30-42` with `drizzle-orm/pglite/migrator`'s `migrate()` helper, which honours `meta/_journal.json` and uses the same wrapping discipline. Or, at minimum, wrap the loop in a single `BEGIN; ... COMMIT;` per file so a partial-failure test reproduces prod rollback semantics.

## Important findings

### I1. `cancel_at_period_end` is `integer DEFAULT 0 NOT NULL` instead of `boolean`

Squawk rule: `prefer-bigint-over-int` is the closest formal Squawk rule (https://squawkhq.com/docs/prefer-bigint-over-int), but the deeper issue is the absence of a `boolean` column type for what is semantically a flag.

File: `apps/backend/migrations/0000_merged_schema.sql:124`. Schema: `apps/backend/src/db/schema.ts:465`. Drizzle does not have a built-in PGLite-safe boolean coercion path, which appears to be the reason this was modelled as `integer`. The cost: every reader has to know that `0` is false and any nonzero value is true (no `CHECK (cancel_at_period_end IN (0, 1))` exists, so a bad write can insert `42` and reads silently see truthy).

Fix: either migrate to `boolean NOT NULL DEFAULT false` (correct), or add `CHECK (cancel_at_period_end IN (0, 1))` to lock the invariant. Cheap because the table is empty.

### I2. `metrics_*_pk` indexes are non-unique on a tuple meant to be the primary key

Squawk rule: `disallowed-unique-constraint` is the inverse rule; the issue here is the *absence* of a unique constraint where the column name announces one.

Files:
- `apps/backend/migrations/0000_merged_schema.sql:194` (`metrics_chat_daily_pk` on `(date, user_id)`).
- `apps/backend/migrations/0000_merged_schema.sql:196` (`metrics_errors_daily_pk` on `(date, kind)`).
- `apps/backend/migrations/0000_merged_schema.sql:197` (`metrics_funnel_daily_pk` on `(date, step)`).
- `apps/backend/migrations/0000_merged_schema.sql:198` (`metrics_tool_usage_daily_pk` on `(date, tool_name, family, user_id)`).

Each is declared `CREATE INDEX`, not `CREATE UNIQUE INDEX`, and no `PRIMARY KEY (...)` exists on these tables at all. The aggregator at `src/events/aggregate.ts` (referenced in the schema docstring at `apps/backend/src/db/schema.ts:566-570`) is described as "ON CONFLICT upsert trivially handles" the partial-counts case. ON CONFLICT requires a unique constraint or unique index. Without one, the upsert will fail or silently duplicate rows.

Fix: change each `CREATE INDEX "metrics_*_pk"` to `CREATE UNIQUE INDEX "metrics_*_pk"` (matches the Drizzle schema's clearly-stated intent) or, better, declare a real composite `PRIMARY KEY` on each table and drop the redundant `*_pk` btree index. The drizzle schema at `apps/backend/src/db/schema.ts:584-643` uses `index(...)` rather than `uniqueIndex(...)`, so the schema source itself is wrong; fix both sides.

### I3. `model_catalog` has no foreign key from `usage_records.model` despite that being the obvious join

Squawk rule: `constraint-missing-not-valid` (applied if we add the FK later: https://squawkhq.com/docs/constraint-missing-not-valid). The current shape is just missing the constraint.

File: `apps/backend/migrations/0003_model_catalog.sql:1-13`. Schema: `apps/backend/src/db/schema.ts:710-760`. `usage_records.model` is `text` (0000:151, schema:428) and `messages.model` is `text` (0000:41, schema:353). Both columns are documented as referring to the model id that is now the PK of `model_catalog`. There is no FK declared either direction.

Fix when we have a populated `usage_records`: add `ALTER TABLE usage_records ADD CONSTRAINT usage_records_model_fk FOREIGN KEY (model) REFERENCES model_catalog(id) NOT VALID;` then a separate `VALIDATE CONSTRAINT` in a follow-up. Today, while the table is empty, we can add it in one step. Pick a direction: ideally `ON DELETE RESTRICT` so retiring a catalog row never silently nulls the billing history.

### I4. `events.id` is `text` with no `$defaultFn` on the Drizzle side; production inserts must supply the id explicitly

File: `apps/backend/migrations/0000_merged_schema.sql:29` declares `"id" text PRIMARY KEY NOT NULL` with no default. Schema: `apps/backend/src/db/schema.ts:543` declares `id: text("id").primaryKey()` with no `.$defaultFn(newId)` despite every other table using that pattern (`users` at line 135, `devices` at 169, `osrsAccounts` at 202, etc.).

Why this is important: every other table's insert path can omit the id and Drizzle fills it. `events` writes must remember to supply one. A future contributor reading the Drizzle file will reasonably assume the `.$defaultFn(newId)` is implicit and skip the id, getting a `NULL violates not-null constraint` failure only at runtime.

Fix: add `.$defaultFn(newId)` to the `events.id` column in the schema (no SQL change needed; the column is already `text PRIMARY KEY NOT NULL`).

### I5. Partial unique indexes use a `WHERE` clause that references the column with a table prefix that drizzle-kit emits but real-world Postgres tolerates only because of permissive quoting

File: `apps/backend/migrations/0000_merged_schema.sql:206` emits:

```
CREATE UNIQUE INDEX "sessions_active_per_device_unique" ON "sessions" USING btree ("device_id") WHERE "sessions"."ended_at" IS NULL;
```

Same pattern at lines 220-221 (`users_email_unique`, `users_stripe_customer_unique`). Postgres accepts the `"sessions"."ended_at"` form. PGLite accepts it. The fragility: any partial-index predicate that references columns with the table-qualified form will break if drizzle-kit ever swaps to the unqualified form in a re-emit, and `IMMUTABLE`-shaped predicate evaluators inside Postgres can be sensitive to qualifier changes (an unrelated `ALTER TABLE ... RENAME` would silently invalidate the index because the predicate names the old table).

Fix: in the Drizzle schema (`apps/backend/src/db/schema.ts:145`, `:148`, `:292`), replace `sql`${t.email} IS NOT NULL`` with `sql`email IS NOT NULL`` (unqualified), so re-emit produces a portable predicate. The current form works but is hostile to a future table rename.

### I6. `subscriptions.monthly_quota_tokens` and `token_balances.balance_tokens` are `bigint` but client code reads them as `number`

Schema: `apps/backend/src/db/schema.ts:462` declares `bigint("monthly_quota_tokens", { mode: "number" })` and `:496` declares the same for `balance_tokens`. Bun/postgres-js will deliver a JS `number` for values up to 2^53 - 1; beyond that the value is silently truncated.

Why this is important even though SQL is correct: the column is `bigint` precisely because we expect lifetime token totals to exceed 32-bit limits. The TypeScript path reading it as `number` ceiling-caps at 9 PB tokens, which is safe today but defeats the migration's purpose. Squawk rule: `prefer-bigint-over-int` (https://squawkhq.com/docs/prefer-bigint-over-int) wants the column type right; we got that right, but the consumer needs `mode: "bigint"` or an explicit conversion at the boundary.

Fix: switch to `mode: "bigint"` and update the few callers (per-call decrement SQL and the dashboard read path) to use `BigInt` arithmetic, or document an explicit cap and add `CHECK (balance_tokens <= 9007199254740992)`.

### I7. `pairing_codes` has no TTL-driven sweep or expiry index that the lookup actually uses

File: `apps/backend/migrations/0000_merged_schema.sql:204` adds `pairing_codes_expires_idx ON (expires_at)`. The handler at `auth/pairing.ts` filters on `expires_at > now() AND used_at IS NULL`, which is a range predicate on the indexed column combined with an unindexed null filter. A partial index `WHERE used_at IS NULL` would let the planner pick a much tighter scan.

Why this is important: as soon as we ship the pairing flow to real users, this becomes the hot lookup on every dashboard claim. The current `pairing_codes_code_unique` index at line 202 handles the code-equality lookup, but the sweep that wipes expired-unused codes will full-scan.

Fix: replace `index("pairing_codes_expires_idx").on(t.expiresAt)` with `index("pairing_codes_expires_unused_idx").on(t.expiresAt).where(sql\`used_at IS NULL\`)`. Cheap now, painful at scale.

### I8. Two indexes on `chats` overlap on `(user_id, ...)` lookups

File: `apps/backend/migrations/0000_merged_schema.sql:183-185`:

```
CREATE INDEX "chats_user_created_idx" ON "chats" USING btree ("user_id","created_at");
CREATE INDEX "chats_session_idx" ON "chats" USING btree ("session_id");
CREATE INDEX "chats_deleted_at_idx" ON "chats" USING btree ("deleted_at");
```

Not strictly a Squawk-flagged rule, but a soft duplication: the chat-list query for the dashboard filters by `user_id AND deleted_at IS NULL ORDER BY created_at DESC`. The `chats_user_created_idx` will not be used optimally with the `deleted_at IS NULL` predicate; the `chats_deleted_at_idx` is a low-cardinality non-partial index (always nullable, dominated by NULL values) and will rarely be used.

Fix: drop `chats_deleted_at_idx` and add `chats_user_created_live_idx ON (user_id, created_at) WHERE deleted_at IS NULL`. Same treatment for `messages_deleted_at_idx` at line 193.

## Nits

### N1. `0001_rai18_auth.sql` is a no-op stub with `SELECT 1;`

File: `apps/backend/migrations/0001_rai18_auth.sql:1-7`. Keeping the file is correct (drizzle-kit's journal expects the slot), but `SELECT 1;` is more honest as a comment-only file plus a single `-- intentional no-op` line. The current shape works in both PGLite and Postgres; the nit is that future readers will scan the file for an empty block, find a query, and second-guess.

Fix: replace `SELECT 1;` with `DO $$ BEGIN NULL; END $$;` (no result row, explicit no-op intent) or just leave the comments and remove the trailing `SELECT`.

### N2. Public schema is referenced inconsistently. Sometimes `"public"."..."`, sometimes bare

Squawk rule: `require-table-schema` (https://squawkhq.com/docs/require-table-schema). `CREATE TYPE` lines at `0000_merged_schema.sql:1-7` are `public.` qualified; `CREATE TABLE` lines are bare; `ALTER TABLE ... REFERENCES "public"."..."` is mixed. PGLite tolerates both; Postgres tolerates both. The nit: consistency. Pick one. Drizzle-kit will keep regenerating the same shape so we cannot just hand-edit; if we care, raise an issue against drizzle-kit and pin the version.

### N3. `metrics_*_daily.user_id` defaults to `''` instead of being nullable

Files: `apps/backend/migrations/0000_merged_schema.sql:50`, `:76`. Schema: `apps/backend/src/db/schema.ts:576`, `:606`. The empty string is a sentinel for "this row aggregates across all users". A nullable column with `WHERE user_id IS NULL` is the more conventional shape and removes the magic `''` value from query writers' mental model.

### N4. Comment style in 0001 is fine, but no `LICENSE` / `COPYRIGHT` header anywhere in `migrations/`

Not a Squawk rule. RuneLite plugin code carries a header; the SQL files do not. Cosmetic.

### N5. Index naming is inconsistent: `_idx` vs `_unique` vs `_pk`

Mixed style across `0000_merged_schema.sql`. Not a Squawk rule. Cosmetic.

## PGLite-vs-Postgres delta lens

Each item is a case where the migration would pass the PGLite-backed test in `apps/backend/test/_db-fixture.ts` but is at risk under real Postgres.

- `apps/backend/migrations/0000_merged_schema.sql:206, :220, :221`: partial unique indexes with `WHERE "table"."column" IS NOT NULL`. PGLite parses the table-qualified predicate fine; real Postgres parses it but flags any subsequent rename of the table because the predicate hard-codes the name. Fix per I5.
- `apps/backend/migrations/0000_merged_schema.sql:53-55, :77-79, :121-126`: `bigint` columns. PGLite returns these as JS `number` regardless of the driver setting; postgres-js will return a string unless configured. The fixture's tests at `_db-fixture.ts` will pass; production reads can silently differ. Fix per I6.
- `apps/backend/migrations/0003_model_catalog.sql:8-9`: `jsonb DEFAULT '[]'::jsonb` and `jsonb DEFAULT '{}'::jsonb`. PGLite accepts the cast. Postgres 11+ treats this as a non-volatile default for the fast-path ADD COLUMN. On future migrations that add a jsonb column to an existing table, the default must remain non-volatile or we lose the fast-path; document this.
- `apps/backend/test/_db-fixture.ts:36-41`: splits on `--> statement-breakpoint` and runs each chunk through `raw.exec()` outside a transaction. drizzle-kit's `migrate` wraps the whole file. The replay semantics drift; tests that depend on a partial application of `0000_merged_schema.sql` will succeed where prod would roll back. Blocker B3 above.
- `apps/backend/migrations/0000_merged_schema.sql:194-198`: `CREATE INDEX` (not `CREATE UNIQUE INDEX`) on tuples Drizzle calls the "pk". PGLite tolerates ON CONFLICT against a non-unique index by inserting duplicates; Postgres rejects ON CONFLICT without a unique constraint. The first test that exercises the aggregator's upsert path will silently double-write in PGLite and fail outright in prod. Blocker-shaped, captured as I2.
- `apps/backend/migrations/0000_merged_schema.sql:1-7`: `CREATE TYPE ... AS ENUM` is supported by PGLite for definition but PGLite's enum value comparison is more permissive than Postgres in some edge cases involving subsequent `ALTER TYPE ... ADD VALUE` inside a transaction. We will hit this the first time we add a value to `subscription_status` or `osrs_account_type`. Postgres forbids `ALTER TYPE ADD VALUE` inside a transaction; drizzle-kit's transaction-per-file model will fail at that point. Document the fix: emit the ALTER TYPE in a `.sql` file whose first line is `COMMIT;` (drizzle-kit's escape hatch) or do the swap via a parallel column.

## Squawk rule pass matrix

| Rule | Applies? | Passes? | Findings reference |
|---|---|---|---|
| adding-required-field (https://squawkhq.com/docs/adding-required-field) | no | n/a | greenfield baseline; no existing rows to backfill |
| adding-serial-primary-key-field (https://squawkhq.com/docs/adding-serial-primary-key-field) | no | n/a | all PKs are `text` nanoids, not `serial` |
| ban-char-field (https://squawkhq.com/docs/ban-char-field) | yes | pass | no `char(n)` anywhere |
| ban-create-domain (https://squawkhq.com/docs/ban-create-domain) | yes | pass | no `CREATE DOMAIN` |
| ban-drop-column (https://squawkhq.com/docs/ban-drop-column) | no | n/a | no drops |
| ban-drop-database (https://squawkhq.com/docs/ban-drop-database) | yes | pass | no `DROP DATABASE` |
| ban-drop-not-null (https://squawkhq.com/docs/ban-drop-not-null) | no | n/a | no `DROP NOT NULL` |
| ban-drop-table (https://squawkhq.com/docs/ban-drop-table) | yes | pass | no `DROP TABLE` |
| ban-truncate-cascade (https://squawkhq.com/docs/ban-truncate-cascade) | yes | pass | no truncate |
| changing-column-type (https://squawkhq.com/docs/changing-column-type) | no | n/a | no `ALTER COLUMN TYPE` |
| constraint-missing-not-valid (https://squawkhq.com/docs/constraint-missing-not-valid) | yes | partial | B1 missing CHECK; I3 missing FK |
| disallowed-unique-constraint (https://squawkhq.com/docs/disallowed-unique-constraint) | yes | partial | I2 metrics_* indexes are not unique despite name |
| prefer-big-int (alias of prefer-bigint-over-int, https://squawkhq.com/docs/prefer-bigint-over-int) | yes | pass | all token / cost / quota columns are `bigint` |
| prefer-bigint-over-int (https://squawkhq.com/docs/prefer-bigint-over-int) | yes | pass with note | `cancel_at_period_end` is `integer` deliberately (I1); not an id |
| prefer-bigint-over-smallint (https://squawkhq.com/docs/prefer-bigint-over-smallint) | yes | pass | no smallints |
| prefer-identity (https://squawkhq.com/docs/prefer-identity) | no | n/a | no `serial` / `bigserial` columns; all PKs are nanoid text |
| prefer-robust-stmts (https://squawkhq.com/docs/prefer-robust-stmts) | yes | partial | no `IF NOT EXISTS` guards on `CREATE TYPE` / `CREATE TABLE`; drizzle-kit elides them by design but a hand re-run after a failed apply will conflict |
| prefer-text-field (https://squawkhq.com/docs/prefer-text-field) | yes | pass | every string column is `text`, no `varchar(N)` |
| prefer-timestamptz (https://squawkhq.com/docs/prefer-timestamptz) | yes | pass | every timestamp is `timestamp with time zone` |
| renaming-column (https://squawkhq.com/docs/renaming-column) | no | n/a | no renames |
| renaming-table (https://squawkhq.com/docs/renaming-table) | no | n/a | no renames |
| require-concurrent-index-creation (https://squawkhq.com/docs/require-concurrent-index-creation) | yes | fail | B2: 36 non-concurrent `CREATE INDEX` in 0000 |
| require-concurrent-index-deletion (https://squawkhq.com/docs/require-concurrent-index-deletion) | no | n/a | no index drops |
| transaction-nesting (https://squawkhq.com/docs/transaction-nesting) | yes | pass | no explicit `BEGIN` inside files |
| ban-concurrent-index-creation-in-transaction (https://squawkhq.com/docs/ban-concurrent-index-creation-in-transaction) | yes | pass with note | no `CONCURRENTLY` today; B2 documents the contract for when we add it |
| require-table-schema (https://squawkhq.com/docs/require-table-schema) | yes | partial | N2: inconsistent `public.` qualification |
| identifier-too-long (https://squawkhq.com/docs/identifier-too-long) | yes | pass | longest identifier is `metrics_tool_usage_daily_date_idx` at 33 chars, well under 63 |

## Recommended fix order

1. B3. Replace `_db-fixture.ts:30-42`'s ad hoc loop with `drizzle-orm/pglite/migrator`. One-file change. Closes the test-vs-prod replay drift before any further migration lands.
2. I2. Promote the four `metrics_*_pk` indexes to `CREATE UNIQUE INDEX` and update the Drizzle schema accordingly (`apps/backend/src/db/schema.ts:584-643`). One commit. Unblocks ON CONFLICT upserts.
3. B2. Add a project-level migration template in `docs/architecture/` and `infra/` that prepends `SET lock_timeout = '2s'; SET statement_timeout = '60s';` to every future migration touching a populated table, and documents the `CREATE INDEX CONCURRENTLY` escape hatch (drizzle-kit's `breakpoints: false` per-file flag).
4. B1. Add the `CHECK` constraint on `processed_stripe_events.event_id` shape. Cheap because the table is empty.
5. I1. Decide on `boolean` vs `CHECK (cancel_at_period_end IN (0, 1))` and ship the constraint.

End.
