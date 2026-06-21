/**
 * Backend entrypoint.
 *
 * Boots the Hono app on `env.PORT` via Bun's native HTTP server. The skeleton
 * exposes `/health` and `/version` only — chat, auth, billing land in later
 * RAI issues but plug into the app factory in `app.ts`.
 *
 * Run: `bun --hot src/server.ts`
 */
import { createApp } from "./app";
import { getDb } from "./db/client";
import { env } from "./env";
import { startAggregationCron } from "./events/aggregate";
import { attachEventPersister, getDefaultBus } from "./events";
import { startRetentionCron } from "./jobs/retention-sweeper";
import { log } from "./lib/log";

const app = createApp({ admin: "auto" });

// RAI-37: spin up the analytics pipeline. The bus + persister + crons are
// in-process; an external bus swap-in stays a future concern.
const { db } = getDb();
attachEventPersister(getDefaultBus(), { db });
const aggregationCron = startAggregationCron({ db });
const retentionCron = startRetentionCron({ db });

const server = Bun.serve({
  port: env.PORT,
  fetch: app.fetch,
});

log.info({ port: server.port, version: env.VERSION, env: env.NODE_ENV }, "backend: listening");

if (!env.OPENROUTER_API_KEY && env.NODE_ENV !== "test") {
  log.warn("OPENROUTER_API_KEY missing — chat routes will fail when they land (RAI-17).");
}

const shutdown = (signal: string): void => {
  log.info({ signal }, "backend: shutting down");
  aggregationCron.stop();
  retentionCron.stop();
  server.stop();
  process.exit(0);
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

export { app, server };
