/**
 * Drizzle schema barrel — production tables for the backend.
 *
 * Implements RAI-15: users, devices, osrs_accounts, pairing_codes, sessions,
 * chats, messages, tool_calls, usage_records, subscriptions, token_balances.
 *
 * RAI-37 (Analytics) extends this with:
 *   - `events`              : append-only raw event log (jsonb payload).
 *   - `metrics_tool_usage_daily`  : per-day tool/family usage counters.
 *   - `metrics_chat_daily`        : per-day chat volume + tokens + cost.
 *   - `metrics_funnel_daily`      : per-day funnel step counts.
 *   - `metrics_errors_daily`      : per-day error category counters.
 *
 * Conventions:
 * - **IDs** are 21-char nanoid strings stored as `text`. Avoids the Postgres
 *   UUID type so PGLite + Postgres behave identically, and so logs/URLs stay
 *   URL-safe. Generated via `newId()` (helper exported below).
 * - **Timestamps** use `timestamp({ withTimezone: true })` and default to
 *   `now()` so retention sweepers and billing periods all share a single
 *   clock semantics.
 * - **Soft delete** lives on long-lived, user-owned content (users, chats,
 *   messages) per docs/legal/DATA_RETENTION.md §"Soft delete vs hard delete".
 *   `deleted_at IS NULL` is the canonical "live row" predicate.
 * - **JSONB** is used for unbounded structured blobs (tool I/O); typed via
 *   `$type<...>()` so reads return the right shape without a cast.
 * - **Enums** are declared via `pgEnum` so migrations include CHECK-like
 *   constraints and Drizzle types narrow properly.
 *
 * Stripe-customer preservation (see DATA_RETENTION.md §"Stripe-side billing
 * carve-out"): when a user soft-deletes their account we keep the
 * `stripe_customer_id` on the row so the retention sweeper can later detach
 * PII on the Stripe object without re-resolving by email. The Stripe customer
 * record itself is NOT deleted until the 6y/7y accounting window expires —
 * that lifecycle is owned by the billing service, not this schema.
 *
 * Raw `events` are retained for 30 days (see `src/jobs/retention-sweeper.ts`).
 * Materialised `metrics_*` rows are retained for 13 months — they're
 * already aggregated and free of raw payload PII, so the privacy budget
 * is small.
 */
import { sql } from "drizzle-orm";
import {
  bigint,
  date,
  index,
  integer,
  jsonb,
  pgEnum,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core";
import { customAlphabet } from "nanoid";

/* -------------------------------------------------------------------------- */
/* ID generator                                                                */
/* -------------------------------------------------------------------------- */

/**
 * URL-safe alphabet (no `-` / `_`) so IDs can be dropped into shell args, URL
 * paths, and log lines without quoting. 21 chars → ~149 bits of entropy,
 * collision probability < 1 in a billion at 1B IDs.
 */
const ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const nanoid21 = customAlphabet(ID_ALPHABET, 21);
export const newId = (): string => nanoid21();

/* -------------------------------------------------------------------------- */
/* Enums                                                                       */
/* -------------------------------------------------------------------------- */

export const userStatusEnum = pgEnum("user_status", ["active", "banned"]);

export const osrsAccountTypeEnum = pgEnum("osrs_account_type", [
  "main",
  "ironman",
  "hardcore_ironman",
  "ultimate_ironman",
  "group_ironman",
  "hardcore_group_ironman",
  "unranked_group_ironman",
  "deadman",
  "seasonal",
  "fresh_start",
  "unknown",
]);

export const osrsAccountStatusEnum = pgEnum("osrs_account_status", [
  "pending",
  "verified",
  "revoked",
]);

export const messageRoleEnum = pgEnum("message_role", ["user", "assistant", "tool", "system"]);

export const toolCallStatusEnum = pgEnum("tool_call_status", [
  "pending",
  "ok",
  "error",
  "timeout",
]);

export const subscriptionTierEnum = pgEnum("subscription_tier", ["hobbyist", "pro", "iron"]);

export const subscriptionStatusEnum = pgEnum("subscription_status", [
  "trialing",
  "active",
  "past_due",
  "canceled",
  "incomplete",
  "incomplete_expired",
  "unpaid",
  "paused",
]);

/* -------------------------------------------------------------------------- */
/* users                                                                       */
/* -------------------------------------------------------------------------- */

/**
 * The billing principal. May be created lazily on first chat — `email` is
 * nullable to support the "frictionless" path where a device-key alone gates
 * usage until the user pairs through the dashboard.
 *
 * **Stripe carve-out**: `stripe_customer_id` is preserved through soft-delete
 * (the row's `deleted_at` is set, but `stripe_customer_id` is NOT cleared)
 * so the retention sweeper can call `stripe.customers.update` to scrub PII
 * on the Stripe side, then hard-delete this row once the accounting window
 * (6y UK / 7y US, per docs/legal/DATA_RETENTION.md) elapses.
 */
export const users = pgTable(
  "users",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    stripeCustomerId: text("stripe_customer_id"),
    email: text("email"),
    status: userStatusEnum("status").notNull().default("active"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
    /** Soft delete marker — see RAI-34 retention sweeper. */
    deletedAt: timestamp("deleted_at", { withTimezone: true }),
  },
  (t) => [
    uniqueIndex("users_email_unique").on(t.email).where(sql`${t.email} IS NOT NULL`),
    uniqueIndex("users_stripe_customer_unique")
      .on(t.stripeCustomerId)
      .where(sql`${t.stripeCustomerId} IS NOT NULL`),
    index("users_status_idx").on(t.status),
    index("users_deleted_at_idx").on(t.deletedAt),
  ],
);

export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

/* -------------------------------------------------------------------------- */
/* devices                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * A plugin install. `device_key_hash` is the Argon2id-hashed long-lived key
 * the plugin generates on first launch (see docs/architecture/IDENTITY.md).
 * Raw keys never live in this DB.
 */
export const devices = pgTable(
  "devices",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    deviceKeyHash: text("device_key_hash").notNull(),
    displayName: text("display_name"),
    lastSeenAt: timestamp("last_seen_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex("devices_user_key_unique").on(t.userId, t.deviceKeyHash),
    index("devices_user_idx").on(t.userId),
    index("devices_last_seen_idx").on(t.lastSeenAt),
  ],
);

export type Device = typeof devices.$inferSelect;
export type NewDevice = typeof devices.$inferInsert;

/* -------------------------------------------------------------------------- */
/* osrs_accounts                                                               */
/* -------------------------------------------------------------------------- */

/**
 * OSRS character bound to a billing user. A single user may pair multiple
 * characters (mains + alts + GIM members). `display_name` is the in-game
 * player name read from `Client.localPlayer.name`.
 */
export const osrsAccounts = pgTable(
  "osrs_accounts",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    displayName: text("display_name").notNull(),
    accountType: osrsAccountTypeEnum("account_type").notNull().default("unknown"),
    lastVerifiedAt: timestamp("last_verified_at", { withTimezone: true }),
    status: osrsAccountStatusEnum("status").notNull().default("pending"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index("osrs_accounts_user_idx").on(t.userId),
    uniqueIndex("osrs_accounts_user_name_unique").on(t.userId, t.displayName),
  ],
);

export type OsrsAccount = typeof osrsAccounts.$inferSelect;
export type NewOsrsAccount = typeof osrsAccounts.$inferInsert;

/* -------------------------------------------------------------------------- */
/* pairing_codes                                                               */
/* -------------------------------------------------------------------------- */

/**
 * One-time 6-digit code shown in-game so the user can pair a device to their
 * billing account from the web dashboard. TTL 10 minutes; the API treats any
 * row whose `expires_at < now()` OR whose `used_at IS NOT NULL` as spent.
 */
export const pairingCodes = pgTable(
  "pairing_codes",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    code: text("code").notNull(),
    deviceId: text("device_id")
      .notNull()
      .references(() => devices.id, { onDelete: "cascade" }),
    expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
    usedAt: timestamp("used_at", { withTimezone: true }),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex("pairing_codes_code_unique").on(t.code),
    index("pairing_codes_device_idx").on(t.deviceId),
    index("pairing_codes_expires_idx").on(t.expiresAt),
  ],
);

export type PairingCode = typeof pairingCodes.$inferSelect;
export type NewPairingCode = typeof pairingCodes.$inferInsert;

/* -------------------------------------------------------------------------- */
/* sessions                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * A live WebSocket session between the plugin and the backend. At most one
 * active session per device — enforced via the partial unique index on
 * `(device_id) WHERE ended_at IS NULL`.
 */
export const sessions = pgTable(
  "sessions",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    deviceId: text("device_id")
      .notNull()
      .references(() => devices.id, { onDelete: "cascade" }),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    startedAt: timestamp("started_at", { withTimezone: true }).notNull().defaultNow(),
    endedAt: timestamp("ended_at", { withTimezone: true }),
    lastMessageAt: timestamp("last_message_at", { withTimezone: true }),
  },
  (t) => [
    uniqueIndex("sessions_active_per_device_unique")
      .on(t.deviceId)
      .where(sql`${t.endedAt} IS NULL`),
    index("sessions_device_idx").on(t.deviceId),
    index("sessions_user_idx").on(t.userId),
    index("sessions_started_at_idx").on(t.startedAt),
  ],
);

export type Session = typeof sessions.$inferSelect;
export type NewSession = typeof sessions.$inferInsert;

/* -------------------------------------------------------------------------- */
/* chats                                                                       */
/* -------------------------------------------------------------------------- */

/**
 * A chat thread within a session. Soft-deletable so the retention sweeper can
 * archive chats older than the user's retention class (30d default, 90d if
 * opted in — see docs/legal/DATA_RETENTION.md `chat-default` / `chat-extended`).
 */
export const chats = pgTable(
  "chats",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    sessionId: text("session_id").references(() => sessions.id, { onDelete: "set null" }),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    title: text("title"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    archivedAt: timestamp("archived_at", { withTimezone: true }),
    /** Soft delete marker — retention sweeper hard-deletes by age. */
    deletedAt: timestamp("deleted_at", { withTimezone: true }),
  },
  (t) => [
    index("chats_user_created_idx").on(t.userId, t.createdAt),
    index("chats_session_idx").on(t.sessionId),
    index("chats_deleted_at_idx").on(t.deletedAt),
  ],
);

export type Chat = typeof chats.$inferSelect;
export type NewChat = typeof chats.$inferInsert;

/* -------------------------------------------------------------------------- */
/* messages                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * One message in a chat. Token counts live here per-turn so usage_records can
 * be aggregated lazily. Soft-deletable so retention by chat-age cascades
 * cleanly (sweeper marks `deleted_at`, hard-delete runs after grace).
 */
export const messages = pgTable(
  "messages",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    chatId: text("chat_id")
      .notNull()
      .references(() => chats.id, { onDelete: "cascade" }),
    role: messageRoleEnum("role").notNull(),
    content: text("content").notNull().default(""),
    model: text("model"),
    promptTokens: integer("prompt_tokens").notNull().default(0),
    completionTokens: integer("completion_tokens").notNull().default(0),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    deletedAt: timestamp("deleted_at", { withTimezone: true }),
  },
  (t) => [
    index("messages_chat_created_idx").on(t.chatId, t.createdAt),
    index("messages_deleted_at_idx").on(t.deletedAt),
  ],
);

export type Message = typeof messages.$inferSelect;
export type NewMessage = typeof messages.$inferInsert;

/* -------------------------------------------------------------------------- */
/* tool_calls                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * A single MCP-style tool invocation attached to an assistant message. Inputs
 * are structured (jsonb); outputs are either a text blob OR a structured
 * payload — we keep both columns so callers can pick the cheapest read.
 */
export interface ToolCallInput {
  /** Arbitrary JSON the tool was invoked with. */
  [key: string]: unknown;
}

export interface ToolCallOutput {
  [key: string]: unknown;
}

export const toolCalls = pgTable(
  "tool_calls",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    messageId: text("message_id")
      .notNull()
      .references(() => messages.id, { onDelete: "cascade" }),
    toolName: text("tool_name").notNull(),
    input: jsonb("input").$type<ToolCallInput>().notNull().default({}),
    outputText: text("output_text"),
    outputJson: jsonb("output_json").$type<ToolCallOutput>(),
    durationMs: integer("duration_ms"),
    status: toolCallStatusEnum("status").notNull().default("pending"),
    errorMessage: text("error_message"),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index("tool_calls_message_idx").on(t.messageId),
    index("tool_calls_tool_name_idx").on(t.toolName),
    index("tool_calls_status_idx").on(t.status),
  ],
);

export type ToolCall = typeof toolCalls.$inferSelect;
export type NewToolCall = typeof toolCalls.$inferInsert;

/* -------------------------------------------------------------------------- */
/* usage_records                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Per-turn usage rollup for billing. Cost is stored in micro-USD (1e-6 USD)
 * as a `bigint` to avoid float drift across millions of low-cost turns.
 */
export const usageRecords = pgTable(
  "usage_records",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    chatId: text("chat_id").references(() => chats.id, { onDelete: "set null" }),
    model: text("model").notNull(),
    promptTokens: integer("prompt_tokens").notNull().default(0),
    completionTokens: integer("completion_tokens").notNull().default(0),
    costMicroUsd: bigint("cost_micro_usd", { mode: "number" }).notNull().default(0),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    index("usage_records_user_created_idx").on(t.userId, t.createdAt),
    index("usage_records_chat_idx").on(t.chatId),
    index("usage_records_model_idx").on(t.model),
  ],
);

export type UsageRecord = typeof usageRecords.$inferSelect;
export type NewUsageRecord = typeof usageRecords.$inferInsert;

/* -------------------------------------------------------------------------- */
/* subscriptions                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Mirror of the Stripe subscription objects we care about. `stripe_subscription_id`
 * is unique. Billing webhooks upsert into this table; nothing else writes here.
 */
export const subscriptions = pgTable(
  "subscriptions",
  {
    id: text("id").primaryKey().$defaultFn(newId),
    userId: text("user_id")
      .notNull()
      .references(() => users.id, { onDelete: "cascade" }),
    stripeSubscriptionId: text("stripe_subscription_id").notNull(),
    tier: subscriptionTierEnum("tier").notNull(),
    status: subscriptionStatusEnum("status").notNull(),
    monthlyQuotaTokens: bigint("monthly_quota_tokens", { mode: "number" }).notNull().default(0),
    currentPeriodStart: timestamp("current_period_start", { withTimezone: true }).notNull(),
    currentPeriodEnd: timestamp("current_period_end", { withTimezone: true }).notNull(),
    cancelAtPeriodEnd: integer("cancel_at_period_end").notNull().default(0),
    createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [
    uniqueIndex("subscriptions_stripe_id_unique").on(t.stripeSubscriptionId),
    index("subscriptions_user_idx").on(t.userId),
    index("subscriptions_status_idx").on(t.status),
    index("subscriptions_period_end_idx").on(t.currentPeriodEnd),
  ],
);

export type Subscription = typeof subscriptions.$inferSelect;
export type NewSubscription = typeof subscriptions.$inferInsert;

/* -------------------------------------------------------------------------- */
/* token_balances                                                              */
/* -------------------------------------------------------------------------- */

/**
 * Live token budget per user. Decremented per OpenRouter call; topped up on
 * Stripe webhook (`invoice.payment_succeeded`) or on monthly renewal.
 * One row per user — `user_id` is the PK.
 *
 * `balance_tokens` is `bigint` so per-call decrements via atomic SQL won't
 * overflow even after months of pro-tier use.
 */
export const tokenBalances = pgTable("token_balances", {
  userId: text("user_id")
    .primaryKey()
    .references(() => users.id, { onDelete: "cascade" }),
  balanceTokens: bigint("balance_tokens", { mode: "number" }).notNull().default(0),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
});

export type TokenBalance = typeof tokenBalances.$inferSelect;
export type NewTokenBalance = typeof tokenBalances.$inferInsert;

/* -------------------------------------------------------------------------- */
/* processed_stripe_events                                                     */
/* -------------------------------------------------------------------------- */

/**
 * Idempotency log for Stripe webhook deliveries (RAI-19).
 *
 * Stripe retries failed deliveries up to 3 days; the same `event.id` may
 * therefore land more than once. The webhook handler does an INSERT-OR-NOTHING
 * against this table before crediting tokens; a double-replay returns "already
 * processed" and credits exactly zero. PK on `event_id` enforces the contract.
 */
export const processedStripeEvents = pgTable("processed_stripe_events", {
  eventId: text("event_id").primaryKey(),
  eventType: text("event_type").notNull(),
  processedAt: timestamp("processed_at", { withTimezone: true }).notNull().defaultNow(),
});

export type ProcessedStripeEvent = typeof processedStripeEvents.$inferSelect;
export type NewProcessedStripeEvent = typeof processedStripeEvents.$inferInsert;

/* -------------------------------------------------------------------------- */
/*  Raw event log (RAI-37 analytics)                                          */
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
/*  Materialised daily metrics (RAI-37 analytics)                             */
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
