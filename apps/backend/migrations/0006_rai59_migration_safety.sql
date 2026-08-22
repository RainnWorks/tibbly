-- RAI-59: migration + constraint safety follow-ups from the SQL review hat.
--
-- 1. Session guards. `SET LOCAL` binds a lock_timeout and statement_timeout to
--    the migrator's transaction, so a hung lock or a runaway statement bails
--    out instead of stalling the deploy. Production picks up the discipline
--    without touching the DB-wide defaults. Outside a transaction (the test
--    fixture replays each statement standalone) Postgres treats these as a
--    no-op with a warning, which is harmless.
--
-- 2. The four `metrics_*_pk` indexes were created as plain `CREATE INDEX`
--    despite the `_pk` name, so nothing stopped a duplicate (date, ...) row.
--    `src/events/aggregate.ts` does select-then-insert-or-update, which races
--    under concurrency: two workers can both miss the SELECT and both INSERT.
--    Rebuild the indexes as UNIQUE so the second INSERT fails loudly instead
--    of silently double-counting. The tables are greenfield, so a plain
--    `CREATE UNIQUE INDEX` is instant. On a populated table the discipline is
--    `CREATE UNIQUE INDEX CONCURRENTLY` outside any transaction.
--
-- 3. A CHECK constraint on `processed_stripe_events.event_id` so a malformed
--    webhook payload cannot write a multi-megabyte string into the dedup PK
--    btree. Real Stripe ids are `evt_` + a short token; the bound is the point,
--    the exact alphabet is deliberately loose so test and fixture ids pass.
--
-- Idempotent: every step is guarded, so a re-run is a no-op.

SET LOCAL lock_timeout = '5s';--> statement-breakpoint
SET LOCAL statement_timeout = '60s';--> statement-breakpoint

DROP INDEX IF EXISTS "metrics_tool_usage_daily_pk";--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "metrics_tool_usage_daily_pk"
  ON "metrics_tool_usage_daily" USING btree ("date", "tool_name", "family", "user_id");--> statement-breakpoint

DROP INDEX IF EXISTS "metrics_chat_daily_pk";--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "metrics_chat_daily_pk"
  ON "metrics_chat_daily" USING btree ("date", "user_id");--> statement-breakpoint

DROP INDEX IF EXISTS "metrics_funnel_daily_pk";--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "metrics_funnel_daily_pk"
  ON "metrics_funnel_daily" USING btree ("date", "step");--> statement-breakpoint

DROP INDEX IF EXISTS "metrics_errors_daily_pk";--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "metrics_errors_daily_pk"
  ON "metrics_errors_daily" USING btree ("date", "kind");--> statement-breakpoint

ALTER TABLE "processed_stripe_events"
  DROP CONSTRAINT IF EXISTS "processed_stripe_events_event_id_shape";--> statement-breakpoint
ALTER TABLE "processed_stripe_events"
  ADD CONSTRAINT "processed_stripe_events_event_id_shape"
  CHECK ("event_id" ~ '^evt_[A-Za-z0-9_-]+$' AND length("event_id") <= 64);
