/**
 * Backend entrypoint.
 *
 * Boots the Hono app on `env.PORT` via Bun's native HTTP server. The skeleton
 * exposes `/health` and `/version` only — chat, auth, billing land in later
 * RAI issues but plug into the app factory in `app.ts`.
 *
 * Run: `bun --hot src/server.ts`
 */
import type { WebSocketHandler } from "bun";

import { warnIfDevHeadersOn } from "./api/_auth";
import { createApp } from "./app";
import { createTokenMeter } from "./billing/meter";
import { meterToBalancePort } from "./billing/ws-adapter";
import { getDb } from "./db/client";
import { env } from "./env";
import { startAggregationCron } from "./events/aggregate";
import { attachEventPersister, getDefaultBus } from "./events";
import { startRetentionCron } from "./jobs/retention-sweeper";
import { log } from "./lib/log";
import { startModelCatalogScheduler } from "./llm/catalog/scheduler";
import type { BalanceMeter } from "./ws/plugin";
import { bridgeEventLoggerToBus, pluginWsHandler } from "./ws/plugin";
import { presenceWsHandler } from "./ws/presence";
import { getDefaultPresenceTracker } from "./ws/presence-tracker";
import {
  assertNotDevStub,
  devStubBalanceMeter,
  devStubDeviceLookup,
} from "./ws/stubs";

const presenceTracker = getDefaultPresenceTracker();

// RAI-37: spin up the analytics pipeline. The bus + persister + crons are
// in-process; an external bus swap-in stays a future concern.
const { db } = getDb();
attachEventPersister(getDefaultBus(), { db });
const aggregationCron = startAggregationCron({ db });
const retentionCron = startRetentionCron({ db });

// Model platform step 1: live OpenRouter catalog. Boot refresh (5s
// deferred) + nightly 03:17 UTC tick. See docs/architecture/MODEL_PLATFORM.md.
const catalogScheduler = startModelCatalogScheduler({ db });

// RAI-20: real token meter. When STRIPE_WEBHOOK_SECRET is configured we trust
// the meter to be live and use it as the WS BalanceMeter port; otherwise we
// fall back to the dev stub so local agents can iterate without Stripe.
const meter = createTokenMeter({ db, bus: getDefaultBus() });
const useRealMeter = !!env.STRIPE_WEBHOOK_SECRET;
const balanceMeter: BalanceMeter = useRealMeter ? meterToBalancePort(meter) : devStubBalanceMeter;

// RAI-19: mount the Stripe webhook router when keys are configured.
// RAI-21: mount the public presence router for /v1/presence.
// RAI-39: mount admin login alongside the admin gate so the ops_session
//   JWT cookie is the only trust root; without this mount the dashboard
//   has no way to issue a cookie and the admin surface stays locked.
const app = createApp({
  admin: "auto",
  adminLogin: "auto",
  presence: { tracker: presenceTracker },
  adminCatalog: { db },
  ...(useRealMeter ? { stripeWebhook: { db, meter, bus: getDefaultBus() } } : {}),
});

// RAI-39: announce the dev-headers escape hatch at boot. Critical in
// production — the middleware ignores `x-dev-user-id` there, but a
// misconfigured image deserves a loud diagnostic.
warnIfDevHeadersOn();

// RAI-17: plugin↔backend chat WebSocket. RAI-15/RAI-20 will replace the
// dev stubs with the real device + balance impls.
assertNotDevStub(env.NODE_ENV);
const pluginWs = pluginWsHandler({
  deviceLookup: devStubDeviceLookup,
  balanceMeter,
  eventLogger: bridgeEventLoggerToBus(getDefaultBus()),
  presence: presenceTracker,
});

// RAI-21: public presence WS feed. Same Bun.serve, distinct path.
const presenceWs = presenceWsHandler({ tracker: presenceTracker });

// Bun.serve takes a single websocket handler per server. We tag each
// upgrade with a discriminator and dispatch to the right per-route handler
// inside each callback. To keep the per-handler typing intact, we swap
// `ws.data` to the handler's expected payload on entry and restore the
// envelope on exit.
type SocketKind = "plugin" | "presence";
interface DispatchData {
  kind: SocketKind;
  plugin?: ReturnType<typeof pluginWs.makeSocketData>;
  presence?: ReturnType<typeof presenceWs.makeSocketData>;
}

async function dispatch<R>(
  ws: { data: unknown },
  fn: () => R | Promise<R>,
  inner: unknown,
): Promise<R> {
  const envelope = ws.data;
  ws.data = inner;
  try {
    return await fn();
  } finally {
    ws.data = envelope;
  }
}

const websocket: WebSocketHandler<DispatchData> = {
  open(ws) {
    const data = ws.data;
    const target = data.kind === "plugin" ? pluginWs.websocket : presenceWs.websocket;
    const inner = data.kind === "plugin" ? data.plugin : data.presence;
    void dispatch(ws as unknown as { data: unknown }, () => target.open?.(ws as never), inner);
  },
  async message(ws, raw) {
    const data = ws.data;
    const target = data.kind === "plugin" ? pluginWs.websocket : presenceWs.websocket;
    const inner = data.kind === "plugin" ? data.plugin : data.presence;
    await dispatch(ws as unknown as { data: unknown }, () => target.message?.(ws as never, raw), inner);
  },
  close(ws, code, reason) {
    const data = ws.data;
    const target = data.kind === "plugin" ? pluginWs.websocket : presenceWs.websocket;
    const inner = data.kind === "plugin" ? data.plugin : data.presence;
    void dispatch(ws as unknown as { data: unknown }, () => target.close?.(ws as never, code, reason), inner);
  },
  drain(ws) {
    const data = ws.data;
    const target = data.kind === "plugin" ? pluginWs.websocket : presenceWs.websocket;
    const inner = data.kind === "plugin" ? data.plugin : data.presence;
    void dispatch(ws as unknown as { data: unknown }, () => target.drain?.(ws as never), inner);
  },
};

const server = Bun.serve<DispatchData, never>({
  port: env.PORT,
  fetch(req, srv) {
    const url = new URL(req.url);
    if (url.pathname === "/ws/plugin") {
      const data: DispatchData = { kind: "plugin", plugin: pluginWs.makeSocketData() };
      const ok = srv.upgrade(req, { data });
      return ok ? undefined : new Response("upgrade failed", { status: 400 });
    }
    if (url.pathname === "/ws/presence") {
      const data: DispatchData = { kind: "presence", presence: presenceWs.makeSocketData() };
      const ok = srv.upgrade(req, { data });
      return ok ? undefined : new Response("upgrade failed", { status: 400 });
    }
    return app.fetch(req);
  },
  websocket,
});

log.info({ port: server.port, version: env.VERSION, env: env.NODE_ENV }, "backend: listening");

if (!env.OPENROUTER_API_KEY && env.NODE_ENV !== "test") {
  log.warn("OPENROUTER_API_KEY missing — chat routes will fail when they land (RAI-17).");
}

const shutdown = (signal: string): void => {
  log.info({ signal }, "backend: shutting down");
  aggregationCron.stop();
  retentionCron.stop();
  catalogScheduler.stop();
  presenceWs.stop();
  server.stop();
  process.exit(0);
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

export { app, server };
