/**
 * Shared in-memory PGLite fixture for analytics tests (RAI-37).
 *
 * Builds an isolated PGLite instance and applies the RAI-37 schema
 * migrations against it. Each test file can call `makeTestDb()` once
 * per `beforeAll` / `beforeEach` block to get a fresh DB.
 *
 * We read the SQL straight from `migrations/0000_rai37_analytics.sql`
 * rather than re-deriving DDL by hand — that way the test is honest
 * about what production will run.
 */
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

import { PGlite } from "@electric-sql/pglite";
import { drizzle } from "drizzle-orm/pglite";

import * as schema from "../src/db/schema";

const MIGRATIONS_DIR = join(import.meta.dir, "..", "migrations");

export interface TestDbHandle {
  db: ReturnType<typeof drizzle<typeof schema>>;
  raw: PGlite;
  close(): Promise<void>;
}

export async function makeTestDb(): Promise<TestDbHandle> {
  const raw = new PGlite();
  const sqlFiles = readdirSync(MIGRATIONS_DIR)
    .filter((f) => f.endsWith(".sql"))
    .sort();

  for (const file of sqlFiles) {
    const contents = readFileSync(join(MIGRATIONS_DIR, file), "utf8");
    const statements = contents
      .split(/-->\s*statement-breakpoint/g)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    for (const stmt of statements) {
      await raw.exec(stmt);
    }
  }

  const db = drizzle(raw, { schema });
  return {
    db,
    raw,
    close: async () => {
      await raw.close();
    },
  };
}
