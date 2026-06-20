/**
 * drizzle-kit config — emits migration SQL into ./migrations and reads schema
 * from ./src/db/schema.ts. The dialect stays `postgresql` for both dev (PGLite,
 * which speaks PG SQL) and prod (postgres-js).
 *
 * Usage:
 *   bunx drizzle-kit generate   # diff schema -> new SQL migration
 *   bunx drizzle-kit migrate    # apply pending migrations to DATABASE_URL
 */
import { defineConfig } from "drizzle-kit";

export default defineConfig({
  schema: "./src/db/schema.ts",
  out: "./migrations",
  dialect: "postgresql",
  dbCredentials: {
    url: process.env["DATABASE_URL"] ?? "file:./local.db",
  },
  strict: true,
  verbose: true,
});
