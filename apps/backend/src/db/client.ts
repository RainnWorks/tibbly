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
import { PGlite } from "@electric-sql/pglite";
import { drizzle as drizzlePglite } from "drizzle-orm/pglite";
import { drizzle as drizzlePostgresJs } from "drizzle-orm/postgres-js";
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
