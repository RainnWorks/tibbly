/**
 * DB client — chooses driver based on DATABASE_URL.
 *
 * - `postgres://…` or `postgresql://…` → `drizzle-orm/postgres-js` (prod).
 * - anything else (including `file:./local.db`, `memory://`, bare paths) →
 *   `drizzle-orm/pglite` (dev / tests). PGLite paths beginning with `file:`
 *   are normalised to filesystem paths; `memory://` becomes an ephemeral
 *   in-memory instance.
 *
 * The real schema lands in RAI-15. Until then the typed `db` instance has an
 * empty schema and only the raw drivers are useful.
 */
import path from "node:path";

import { PGlite } from "@electric-sql/pglite";
import { drizzle as drizzlePglite } from "drizzle-orm/pglite";
import { migrate as migratePglite } from "drizzle-orm/pglite/migrator";
import { drizzle as drizzlePostgresJs } from "drizzle-orm/postgres-js";
import { migrate as migratePostgresJs } from "drizzle-orm/postgres-js/migrator";
import postgres from "postgres";

import { env } from "../env";
import { log } from "../lib/log";
import * as schema from "./schema";

export type AppSchema = typeof schema;

export type DbClient =
  | ReturnType<typeof drizzlePglite<AppSchema>>
  | ReturnType<typeof drizzlePostgresJs<AppSchema>>;

export interface DbHandle {
  /** The Drizzle ORM client with the app schema attached. */
  db: DbClient;
  /** Underlying driver kind — useful for runtime branches and logging. */
  driver: "pglite" | "postgres-js";
  /** Closes the underlying connection / WASM instance. */
  close: () => Promise<void>;
}

function isPostgresUrl(url: string): boolean {
  return url.startsWith("postgres://") || url.startsWith("postgresql://");
}

/** Convert the DATABASE_URL into a PGLite-friendly data dir or `memory://`. */
function pgliteLocation(url: string): string {
  if (url.startsWith("memory://") || url === ":memory:") return "memory://";
  if (url.startsWith("file:")) return url.slice("file:".length);
  return url;
}

export function createDb(databaseUrl: string = env.DATABASE_URL): DbHandle {
  if (isPostgresUrl(databaseUrl)) {
    const client = postgres(databaseUrl, { prepare: false });
    const db = drizzlePostgresJs(client, { schema });
    log.info({ driver: "postgres-js" }, "db: connected");
    return {
      db,
      driver: "postgres-js",
      close: async () => {
        await client.end({ timeout: 5 });
      },
    };
  }

  const location = pgliteLocation(databaseUrl);
  const client = new PGlite(location);
  const db = drizzlePglite(client, { schema });
  log.info({ driver: "pglite", location }, "db: connected");
  return {
    db,
    driver: "pglite",
    close: async () => {
      await client.close();
    },
  };
}

/**
 * Process-wide singleton. Tests can call `createDb(...)` directly with a
 * `memory://` URL to get an isolated instance per test file.
 */
let cached: DbHandle | undefined;
export function getDb(): DbHandle {
  if (!cached) cached = createDb();
  return cached;
}

/**
 * Resolve the on-disk `migrations/` directory relative to this source
 * file so it works regardless of the runtime cwd. From
 * `apps/backend/src/db/client.ts` the climb is `../../migrations`.
 */
function resolveMigrationsFolder(): string {
  return path.resolve(import.meta.dir, "..", "..", "migrations");
}

/**
 * Apply pending Drizzle migrations. Safe to call on every boot —
 * `drizzle.__drizzle_migrations` tracks the journal so already-applied
 * files are skipped. Returns the absolute folder it scanned so logs
 * stay self-explaining.
 */
export async function applyMigrations(handle: DbHandle = getDb()): Promise<string> {
  const folder = resolveMigrationsFolder();
  if (handle.driver === "pglite") {
    await migratePglite(handle.db as ReturnType<typeof drizzlePglite<AppSchema>>, {
      migrationsFolder: folder,
    });
  } else {
    await migratePostgresJs(handle.db as ReturnType<typeof drizzlePostgresJs<AppSchema>>, {
      migrationsFolder: folder,
    });
  }
  return folder;
}
