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
import { bridgeEventLoggerToBus, pluginWsHandler } from "./ws/plugin";
import {
  assertNotDevStub,
  devStubBalanceMeter,
  devStubDeviceLookup,
} from "./ws/stubs";

const app = createApp({ admin: "auto" });

// RAI-37: spin up the analytics pipeline. The bus + persister + crons are
// in-process; an external bus swap-in stays a future concern.
const { db } = getDb();
attachEventPersister(getDefaultBus(), { db });
const aggregationCron = startAggregationCron({ db });
const retentionCron = startRetentionCron({ db });

// RAI-17: plugin↔backend chat WebSocket. RAI-15/RAI-20 will replace the
// dev stubs with the real device + balance impls.
assertNotDevStub(env.NODE_ENV);
const pluginWs = pluginWsHandler({
  deviceLookup: devStubDeviceLookup,
  balanceMeter: devStubBalanceMeter,
  eventLogger: bridgeEventLoggerToBus(getDefaultBus()),
});

const server = Bun.serve({
  port: env.PORT,
  fetch(req, srv) {
    const url = new URL(req.url);
    if (url.pathname === "/ws/plugin") {
      const ok = srv.upgrade(req, { data: pluginWs.makeSocketData() });
      return ok ? undefined : new Response("upgrade failed", { status: 400 });
    }
    return app.fetch(req);
  },
  websocket: pluginWs.websocket,
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
