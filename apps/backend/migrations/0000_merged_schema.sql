CREATE TYPE "public"."message_role" AS ENUM('user', 'assistant', 'tool', 'system');--> statement-breakpoint
CREATE TYPE "public"."osrs_account_status" AS ENUM('pending', 'verified', 'revoked');--> statement-breakpoint
CREATE TYPE "public"."osrs_account_type" AS ENUM('main', 'ironman', 'hardcore_ironman', 'ultimate_ironman', 'group_ironman', 'hardcore_group_ironman', 'unranked_group_ironman', 'deadman', 'seasonal', 'fresh_start', 'unknown');--> statement-breakpoint
CREATE TYPE "public"."subscription_status" AS ENUM('trialing', 'active', 'past_due', 'canceled', 'incomplete', 'incomplete_expired', 'unpaid', 'paused');--> statement-breakpoint
CREATE TYPE "public"."subscription_tier" AS ENUM('hobbyist', 'pro', 'iron');--> statement-breakpoint
CREATE TYPE "public"."tool_call_status" AS ENUM('pending', 'ok', 'error', 'timeout');--> statement-breakpoint
CREATE TYPE "public"."user_status" AS ENUM('active', 'banned');--> statement-breakpoint
CREATE TABLE "chats" (
	"id" text PRIMARY KEY NOT NULL,
	"session_id" text,
	"user_id" text NOT NULL,
	"title" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"archived_at" timestamp with time zone,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "devices" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"device_key_hash" text NOT NULL,
	"display_name" text,
	"player_name" text,
	"last_seen_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "events" (
	"id" text PRIMARY KEY NOT NULL,
	"type" text NOT NULL,
	"user_id" text,
	"payload" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "messages" (
	"id" text PRIMARY KEY NOT NULL,
	"chat_id" text NOT NULL,
	"role" "message_role" NOT NULL,
	"content" text DEFAULT '' NOT NULL,
	"model" text,
	"prompt_tokens" integer DEFAULT 0 NOT NULL,
	"completion_tokens" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "metrics_chat_daily" (
	"date" date NOT NULL,
	"user_id" text DEFAULT '' NOT NULL,
	"message_count" integer DEFAULT 0 NOT NULL,
	"tokens_in" bigint DEFAULT 0 NOT NULL,
	"tokens_out" bigint DEFAULT 0 NOT NULL,
	"cost_micro_usd" bigint DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "metrics_errors_daily" (
	"date" date NOT NULL,
	"kind" text NOT NULL,
	"count" integer DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "metrics_funnel_daily" (
	"date" date NOT NULL,
	"step" text NOT NULL,
	"user_count" integer DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "metrics_tool_usage_daily" (
	"date" date NOT NULL,
	"tool_name" text NOT NULL,
	"family" text NOT NULL,
	"user_id" text DEFAULT '' NOT NULL,
	"count" integer DEFAULT 0 NOT NULL,
	"total_input_bytes" bigint DEFAULT 0 NOT NULL,
	"total_output_bytes" bigint DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "osrs_accounts" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"display_name" text NOT NULL,
	"account_type" "osrs_account_type" DEFAULT 'unknown' NOT NULL,
	"last_verified_at" timestamp with time zone,
	"status" "osrs_account_status" DEFAULT 'pending' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "pairing_codes" (
	"id" text PRIMARY KEY NOT NULL,
	"code" text NOT NULL,
	"device_id" text,
	"device_key_hash" text,
	"player_name" text,
	"user_id" text,
	"expires_at" timestamp with time zone NOT NULL,
	"used_at" timestamp with time zone,
	"claimed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "sessions" (
	"id" text PRIMARY KEY NOT NULL,
	"device_id" text NOT NULL,
	"user_id" text NOT NULL,
	"started_at" timestamp with time zone DEFAULT now() NOT NULL,
	"ended_at" timestamp with time zone,
	"last_message_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "subscriptions" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"stripe_subscription_id" text NOT NULL,
	"tier" "subscription_tier" NOT NULL,
	"status" "subscription_status" NOT NULL,
	"monthly_quota_tokens" bigint DEFAULT 0 NOT NULL,
	"current_period_start" timestamp with time zone NOT NULL,
	"current_period_end" timestamp with time zone NOT NULL,
	"cancel_at_period_end" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "token_balances" (
	"user_id" text PRIMARY KEY NOT NULL,
	"balance_tokens" bigint DEFAULT 0 NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "tool_calls" (
	"id" text PRIMARY KEY NOT NULL,
	"message_id" text NOT NULL,
	"tool_name" text NOT NULL,
	"input" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"output_text" text,
	"output_json" jsonb,
	"duration_ms" integer,
	"status" "tool_call_status" DEFAULT 'pending' NOT NULL,
	"error_message" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "usage_records" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"chat_id" text,
	"model" text NOT NULL,
	"prompt_tokens" integer DEFAULT 0 NOT NULL,
	"completion_tokens" integer DEFAULT 0 NOT NULL,
	"cost_micro_usd" bigint DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" text PRIMARY KEY NOT NULL,
	"stripe_customer_id" text,
	"email" text,
	"status" "user_status" DEFAULT 'active' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
ALTER TABLE "chats" ADD CONSTRAINT "chats_session_id_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."sessions"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chats" ADD CONSTRAINT "chats_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "devices" ADD CONSTRAINT "devices_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "messages" ADD CONSTRAINT "messages_chat_id_chats_id_fk" FOREIGN KEY ("chat_id") REFERENCES "public"."chats"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "osrs_accounts" ADD CONSTRAINT "osrs_accounts_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "pairing_codes" ADD CONSTRAINT "pairing_codes_device_id_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "pairing_codes" ADD CONSTRAINT "pairing_codes_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_device_id_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "subscriptions" ADD CONSTRAINT "subscriptions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "token_balances" ADD CONSTRAINT "token_balances_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "tool_calls" ADD CONSTRAINT "tool_calls_message_id_messages_id_fk" FOREIGN KEY ("message_id") REFERENCES "public"."messages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "usage_records" ADD CONSTRAINT "usage_records_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "usage_records" ADD CONSTRAINT "usage_records_chat_id_chats_id_fk" FOREIGN KEY ("chat_id") REFERENCES "public"."chats"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "chats_user_created_idx" ON "chats" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "chats_session_idx" ON "chats" USING btree ("session_id");--> statement-breakpoint
CREATE INDEX "chats_deleted_at_idx" ON "chats" USING btree ("deleted_at");--> statement-breakpoint
CREATE UNIQUE INDEX "devices_user_key_unique" ON "devices" USING btree ("user_id","device_key_hash");--> statement-breakpoint
CREATE INDEX "devices_user_idx" ON "devices" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "devices_last_seen_idx" ON "devices" USING btree ("last_seen_at");--> statement-breakpoint
CREATE INDEX "events_type_created_idx" ON "events" USING btree ("type","created_at");--> statement-breakpoint
CREATE INDEX "events_user_created_idx" ON "events" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "events_created_idx" ON "events" USING btree ("created_at");--> statement-breakpoint
CREATE INDEX "messages_chat_created_idx" ON "messages" USING btree ("chat_id","created_at");--> statement-breakpoint
CREATE INDEX "messages_deleted_at_idx" ON "messages" USING btree ("deleted_at");--> statement-breakpoint
CREATE INDEX "metrics_chat_daily_pk" ON "metrics_chat_daily" USING btree ("date","user_id");--> statement-breakpoint
CREATE INDEX "metrics_chat_daily_date_idx" ON "metrics_chat_daily" USING btree ("date");--> statement-breakpoint
CREATE INDEX "metrics_errors_daily_pk" ON "metrics_errors_daily" USING btree ("date","kind");--> statement-breakpoint
CREATE INDEX "metrics_funnel_daily_pk" ON "metrics_funnel_daily" USING btree ("date","step");--> statement-breakpoint
CREATE INDEX "metrics_tool_usage_daily_pk" ON "metrics_tool_usage_daily" USING btree ("date","tool_name","family","user_id");--> statement-breakpoint
CREATE INDEX "metrics_tool_usage_daily_date_idx" ON "metrics_tool_usage_daily" USING btree ("date");--> statement-breakpoint
CREATE INDEX "osrs_accounts_user_idx" ON "osrs_accounts" USING btree ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "osrs_accounts_user_name_unique" ON "osrs_accounts" USING btree ("user_id","display_name");--> statement-breakpoint
CREATE UNIQUE INDEX "pairing_codes_code_unique" ON "pairing_codes" USING btree ("code");--> statement-breakpoint
CREATE INDEX "pairing_codes_device_idx" ON "pairing_codes" USING btree ("device_id");--> statement-breakpoint
CREATE INDEX "pairing_codes_expires_idx" ON "pairing_codes" USING btree ("expires_at");--> statement-breakpoint
CREATE INDEX "pairing_codes_claimed_idx" ON "pairing_codes" USING btree ("claimed_at");--> statement-breakpoint
CREATE UNIQUE INDEX "sessions_active_per_device_unique" ON "sessions" USING btree ("device_id") WHERE "sessions"."ended_at" IS NULL;--> statement-breakpoint
CREATE INDEX "sessions_device_idx" ON "sessions" USING btree ("device_id");--> statement-breakpoint
CREATE INDEX "sessions_user_idx" ON "sessions" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "sessions_started_at_idx" ON "sessions" USING btree ("started_at");--> statement-breakpoint
CREATE UNIQUE INDEX "subscriptions_stripe_id_unique" ON "subscriptions" USING btree ("stripe_subscription_id");--> statement-breakpoint
CREATE INDEX "subscriptions_user_idx" ON "subscriptions" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "subscriptions_status_idx" ON "subscriptions" USING btree ("status");--> statement-breakpoint
CREATE INDEX "subscriptions_period_end_idx" ON "subscriptions" USING btree ("current_period_end");--> statement-breakpoint
CREATE INDEX "tool_calls_message_idx" ON "tool_calls" USING btree ("message_id");--> statement-breakpoint
CREATE INDEX "tool_calls_tool_name_idx" ON "tool_calls" USING btree ("tool_name");--> statement-breakpoint
CREATE INDEX "tool_calls_status_idx" ON "tool_calls" USING btree ("status");--> statement-breakpoint
CREATE INDEX "usage_records_user_created_idx" ON "usage_records" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "usage_records_chat_idx" ON "usage_records" USING btree ("chat_id");--> statement-breakpoint
CREATE INDEX "usage_records_model_idx" ON "usage_records" USING btree ("model");--> statement-breakpoint
CREATE UNIQUE INDEX "users_email_unique" ON "users" USING btree ("email") WHERE "users"."email" IS NOT NULL;--> statement-breakpoint
CREATE UNIQUE INDEX "users_stripe_customer_unique" ON "users" USING btree ("stripe_customer_id") WHERE "users"."stripe_customer_id" IS NOT NULL;--> statement-breakpoint
CREATE INDEX "users_status_idx" ON "users" USING btree ("status");--> statement-breakpoint
CREATE INDEX "users_deleted_at_idx" ON "users" USING btree ("deleted_at");