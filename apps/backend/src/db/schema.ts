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
  uniqueIndex,
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

/* -------------------------------------------------------------------------- */
/*  Identity — users, devices, pairing codes (RAI-18)                         */
/* -------------------------------------------------------------------------- */

/**
 * Billing principal. Created lazily on first successful pairing claim. A
 * single user can have many devices (one per RuneLite install) and many
 * OSRS characters (via `devices.player_name`).
 *
 * `stripe_customer_id` is what marries us to the billing world. The pairing
 * claim endpoint accepts a Stripe customer id (from the checkout success URL
 * redirect) and either finds the matching user or creates a fresh one.
 *
 * NOTE: RAI-15 lands a richer `users` table (status enum, soft delete,
 * timestamps). We provide the minimum shape here so RAI-18 ships standalone;
 * RAI-15 should ADD nullable columns rather than rename, so the merge is clean.
 */
export const users = pgTable(
  "users",
  {
    id: text("id").primaryKey(),
    stripeCustomerId: text("stripe_customer_id"),
    email: text("email"),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    stripeCustomerUnique: uniqueIndex("users_stripe_customer_unique")
      .on(table.stripeCustomerId)
      .where(sql`${table.stripeCustomerId} IS NOT NULL`),
    emailUnique: uniqueIndex("users_email_unique")
      .on(table.email)
      .where(sql`${table.email} IS NOT NULL`),
  }),
);

export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

/**
 * A plugin install. `device_key_hash` is the Argon2id digest of the long-lived
 * device key the plugin generates on first launch (see
 * docs/architecture/IDENTITY.md). Raw keys are never persisted.
 *
 * `player_name` is the in-game name read from `Client.localPlayer.name` at
 * pairing request time. One device row per (user, device_key_hash) — a player
 * who reinstalls the plugin and pairs again will simply create a new device.
 */
export const devices = pgTable(
  "devices",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    deviceKeyHash: text("device_key_hash").notNull(),
    playerName: text("player_name"),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    userIdx: index("devices_user_idx").on(table.userId),
    keyHashIdx: index("devices_key_hash_idx").on(table.deviceKeyHash),
  }),
);

export type Device = typeof devices.$inferSelect;
export type NewDevice = typeof devices.$inferInsert;

/**
 * One-time pairing code, generated by the plugin and surfaced in-game. The
 * user then enters it on the dashboard (or completes Stripe checkout) to
 * bind their freshly-paid account to the device.
 *
 * Lifecycle:
 *   1. `POST /v1/pairing/request` — plugin sends `{ deviceKey, playerName }`.
 *      Row inserted with `device_key_hash`, `player_name`, `code`,
 *      `expires_at = now() + 10m`. `user_id` + `device_id` are NULL.
 *   2. `POST /v1/pairing/claim` — dashboard sends `{ code, stripeCustomerId? }`.
 *      Backend creates/finds user, creates device, then sets `user_id`,
 *      `device_id`, `claimed_at` on the pairing row.
 *   3. Replay protection: any subsequent claim with the same code returns 410
 *      (`claimed_at IS NOT NULL`). Expired rows return 410 too.
 *
 * The plugin's raw deviceKey is intentionally not stored. Verification at
 * claim time happens against `device_key_hash` via argon2.
 */
export const pairingCodes = pgTable(
  "pairing_codes",
  {
    id: text("id").primaryKey(),
    code: text("code").notNull(),
    deviceKeyHash: text("device_key_hash").notNull(),
    playerName: text("player_name"),
    userId: text("user_id").references(() => users.id, { onDelete: "cascade" }),
    deviceId: text("device_id").references(() => devices.id, { onDelete: "cascade" }),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    claimedAt: timestamp("claimed_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true })
      .notNull()
      .default(sql`now()`),
  },
  (table) => ({
    codeUnique: uniqueIndex("pairing_codes_code_unique").on(table.code),
    expiresIdx: index("pairing_codes_expires_idx").on(table.expiresAt),
    claimedIdx: index("pairing_codes_claimed_idx").on(table.claimedAt),
  }),
);

export type PairingCodeRow = typeof pairingCodes.$inferSelect;
export type NewPairingCodeRow = typeof pairingCodes.$inferInsert;
