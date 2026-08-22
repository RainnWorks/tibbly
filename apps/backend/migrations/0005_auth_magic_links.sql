CREATE TABLE "auth_magic_links" (
	"id" text PRIMARY KEY NOT NULL,
	"email" text NOT NULL,
	"token_hash" text NOT NULL,
	"pending_session_id" text,
	"request_ip" text,
	"user_code" text,
	"consumed_at" timestamp with time zone,
	"expires_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX "auth_magic_links_token_hash_unique" ON "auth_magic_links" USING btree ("token_hash");--> statement-breakpoint
CREATE INDEX "auth_magic_links_email_idx" ON "auth_magic_links" USING btree ("email");--> statement-breakpoint
CREATE INDEX "auth_magic_links_expires_idx" ON "auth_magic_links" USING btree ("expires_at");