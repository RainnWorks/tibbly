/**
 * Drizzle schema barrel.
 *
 * RAI-37 (Analytics) extends this with:
 *   - `events`              : append-only raw event log (jsonb payload).
 *   - `metrics_tool_usage_daily`  : per-day tool/family usage counters.
 *   - `metrics_chat_daily`        : per-day chat volume + tokens + cost.
 *   - `metrics_funnel_daily`      : per-day funnel step counts.
 *   - `metrics_errors_daily`      : per-day error category counters.
 *
 * Raw `events` are retained for 30 days (see `src/jobs/retention-sweeper.ts`).
 * Materialised `metrics_*` rows are retained for 13 months — they're
 * already aggregated and free of raw payload PII, so the privacy budget
 * is small.
 *
 * NOTE: Other agents (RAI-15) add the canonical `users`, `chats`, etc.
 * tables to this same module. Keep table names unique and re-export
 * everything so `import * as schema from "./db/schema"` keeps working.
 */
import { sql } from "drizzle-orm";
import {
  bigint,
  date,
  index,
  integer,
  jsonb,
  pgTable,
  text,
  timestamp,
} from "drizzle-orm/pg-core";

/* -------------------------------------------------------------------------- */
/*  Raw event log                                                             */
/* -------------------------------------------------------------------------- */

/**
 * Every domain event published on the in-process event bus is mirrored here.
 *
 * Indexes:
 *   - (type, created_at) drives "what happened of kind X recently?".
 *   - (user_id, created_at) drives per-user audit + GDPR export.
 *   - (created_at) drives the retention sweeper's range delete.
 *
 * `payload` is jsonb so analytics SQL can `payload ->> 'toolName'` directly
 * without an extra column per event type. Schema enforcement happens at
 * the Zod boundary in `src/events/types.ts`.
 */
export const events = pgTable(
  "events",
  {
    id: text("id").primaryKey(),
    type: text("type").notNull(),
    userId: text("user_id"),
    payload: jsonb("payload").$type<Record<string, unknown>>().notNull(),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    typeCreatedIdx: index("events_type_created_idx").on(table.type, table.createdAt),
    userCreatedIdx: index("events_user_created_idx").on(table.userId, table.createdAt),
    createdIdx: index("events_created_idx").on(table.createdAt),
  }),
);

export type EventRow = typeof events.$inferSelect;
export type NewEventRow = typeof events.$inferInsert;

/* -------------------------------------------------------------------------- */
/*  Materialised daily metrics                                                */
/* -------------------------------------------------------------------------- */

/**
 * One row per (date, user, tool). Updated by the once-per-minute aggregator
 * (`src/events/aggregate.ts`). Keys are wide enough that ON CONFLICT upsert
 * trivially handles the "we already wrote partial counts for today" case.
 */
export const metricsToolUsageDaily = pgTable(
  "metrics_tool_usage_daily",
  {
    date: date("date").notNull(),
    toolName: text("tool_name").notNull(),
    family: text("family").notNull(),
    userId: text("user_id").notNull().default(""),
    count: integer("count").notNull().default(0),
    totalInputBytes: bigint("total_input_bytes", { mode: "number" }).notNull().default(0),
    totalOutputBytes: bigint("total_output_bytes", { mode: "number" }).notNull().default(0),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    pk: index("metrics_tool_usage_daily_pk").on(
      table.date,
      table.toolName,
      table.family,
      table.userId,
    ),
    dateIdx: index("metrics_tool_usage_daily_date_idx").on(table.date),
  }),
);

export type MetricsToolUsageDailyRow = typeof metricsToolUsageDaily.$inferSelect;

/**
 * One row per (date, user). Aggregates chat.message.sent — i.e. how many
 * messages, how many tokens in/out, and the rolled-up OpenRouter cost in
 * micro-USD (1e-6 USD) so we never lose precision in integer math.
 */
export const metricsChatDaily = pgTable(
  "metrics_chat_daily",
  {
    date: date("date").notNull(),
    userId: text("user_id").notNull().default(""),
    messageCount: integer("message_count").notNull().default(0),
    tokensIn: bigint("tokens_in", { mode: "number" }).notNull().default(0),
    tokensOut: bigint("tokens_out", { mode: "number" }).notNull().default(0),
    costMicroUsd: bigint("cost_micro_usd", { mode: "number" }).notNull().default(0),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    pk: index("metrics_chat_daily_pk").on(table.date, table.userId),
    dateIdx: index("metrics_chat_daily_date_idx").on(table.date),
  }),
);

export type MetricsChatDailyRow = typeof metricsChatDaily.$inferSelect;

/**
 * One row per (date, step). `step` is one of the funnel.* event suffixes
 * (e.g. `installed`, `consent_accepted`, `first_message`, `first_paid`).
 * `user_count` is the distinct number of users (or device keys for the
 * anonymous step) that crossed the step on that date.
 */
export const metricsFunnelDaily = pgTable(
  "metrics_funnel_daily",
  {
    date: date("date").notNull(),
    step: text("step").notNull(),
    userCount: integer("user_count").notNull().default(0),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    pk: index("metrics_funnel_daily_pk").on(table.date, table.step),
  }),
);

export type MetricsFunnelDailyRow = typeof metricsFunnelDaily.$inferSelect;

/**
 * One row per (date, kind) where kind is the error.* event suffix
 * (e.g. `openrouter`, `plugin_disconnect`).
 */
export const metricsErrorsDaily = pgTable(
  "metrics_errors_daily",
  {
    date: date("date").notNull(),
    kind: text("kind").notNull(),
    count: integer("count").notNull().default(0),
    updatedAt: timestamp("updated_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    pk: index("metrics_errors_daily_pk").on(table.date, table.kind),
  }),
);

export type MetricsErrorsDailyRow = typeof metricsErrorsDaily.$inferSelect;
