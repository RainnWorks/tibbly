-- RAI-67: embodied companion brain (personality + memory)
--
-- Adds two tables that back the always-on Tibbly companion:
--   * companion_profile   : per-(user, osrs_account) personality + nickname
--   * companion_memories  : extracted beats that the prompt-builder recalls
--
-- Migration safety:
--   * Both tables are brand new and empty, so we use plain CREATE INDEX
--     (NOT CONCURRENTLY) per the Squawk discipline notes from PR #57.
--   * Foreign keys cascade on user delete so the GDPR Art. 17 cascade in
--     /v1/me sweeps the companion state without an explicit DELETE.
--   * osrs_account_id is SET NULL on parent delete because a companion
--     should survive the player un-linking a character.
CREATE TABLE "companion_profile" (
	"id" text PRIMARY KEY NOT NULL,
	"user_id" text NOT NULL,
	"osrs_account_id" text,
	"starter_archetype" text NOT NULL,
	"personality_archetype" text NOT NULL,
	"companion_name" text,
	"voice_style_notes" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"relationship_age_days" integer DEFAULT 0 NOT NULL,
	"last_session_ended_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "companion_profile_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE cascade ON UPDATE no action,
	CONSTRAINT "companion_profile_osrs_account_id_osrs_accounts_id_fk" FOREIGN KEY ("osrs_account_id") REFERENCES "osrs_accounts"("id") ON DELETE set null ON UPDATE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX "companion_profile_user_account_unique" ON "companion_profile" USING btree ("user_id","osrs_account_id");--> statement-breakpoint
CREATE INDEX "companion_profile_user_idx" ON "companion_profile" USING btree ("user_id");--> statement-breakpoint
CREATE TABLE "companion_memories" (
	"id" text PRIMARY KEY NOT NULL,
	"profile_id" text NOT NULL,
	"body" text NOT NULL,
	"category" text NOT NULL,
	"weight" double precision DEFAULT 1 NOT NULL,
	"evidence" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"first_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_referenced_at" timestamp with time zone DEFAULT now() NOT NULL,
	"forgotten_at" timestamp with time zone,
	CONSTRAINT "companion_memories_profile_id_companion_profile_id_fk" FOREIGN KEY ("profile_id") REFERENCES "companion_profile"("id") ON DELETE cascade ON UPDATE no action
);
--> statement-breakpoint
CREATE INDEX "companion_memories_profile_idx" ON "companion_memories" USING btree ("profile_id");--> statement-breakpoint
CREATE INDEX "companion_memories_weight_idx" ON "companion_memories" USING btree ("weight");--> statement-breakpoint
CREATE INDEX "companion_memories_last_referenced_idx" ON "companion_memories" USING btree ("last_referenced_at");
