CREATE TABLE "model_catalog" (
	"id" text PRIMARY KEY NOT NULL,
	"provider" text NOT NULL,
	"display_name" text NOT NULL,
	"context_length" integer NOT NULL,
	"input_price_micro_usd_per_million" bigint NOT NULL,
	"output_price_micro_usd_per_million" bigint NOT NULL,
	"input_modalities" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"capabilities" jsonb DEFAULT '{}'::jsonb NOT NULL,
	"first_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"retired_at" timestamp with time zone
);
--> statement-breakpoint
CREATE INDEX "model_catalog_provider_idx" ON "model_catalog" USING btree ("provider");--> statement-breakpoint
CREATE INDEX "model_catalog_retired_idx" ON "model_catalog" USING btree ("retired_at");