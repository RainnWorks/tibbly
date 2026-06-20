CREATE TABLE "events" (
	"id" text PRIMARY KEY NOT NULL,
	"type" text NOT NULL,
	"user_id" text,
	"payload" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
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
CREATE INDEX "events_type_created_idx" ON "events" USING btree ("type","created_at");--> statement-breakpoint
CREATE INDEX "events_user_created_idx" ON "events" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "events_created_idx" ON "events" USING btree ("created_at");--> statement-breakpoint
CREATE INDEX "metrics_chat_daily_pk" ON "metrics_chat_daily" USING btree ("date","user_id");--> statement-breakpoint
CREATE INDEX "metrics_chat_daily_date_idx" ON "metrics_chat_daily" USING btree ("date");--> statement-breakpoint
CREATE INDEX "metrics_errors_daily_pk" ON "metrics_errors_daily" USING btree ("date","kind");--> statement-breakpoint
CREATE INDEX "metrics_funnel_daily_pk" ON "metrics_funnel_daily" USING btree ("date","step");--> statement-breakpoint
CREATE INDEX "metrics_tool_usage_daily_pk" ON "metrics_tool_usage_daily" USING btree ("date","tool_name","family","user_id");--> statement-breakpoint
CREATE INDEX "metrics_tool_usage_daily_date_idx" ON "metrics_tool_usage_daily" USING btree ("date");