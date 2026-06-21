/**
 * E2E orchestrator boot.
 *
 * Spins up an in-process backend (Hono + Bun.serve) on an ephemeral port,
 * wired to a freshly-migrated in-memory PGLite. Optionally launches the
 * marketing and ops Vite dev servers as child processes so the browser
 * scripts have something to drive; programmatic scenarios do not need
 * them and skip the spawn.
 *
 * Why in-process: we want the scenarios to introspect the database
 * (assert balance rows, audit events, etc.) and to be fast (under 60s
 * for the full suite). Running the backend as a subprocess would force
 * us into HTTP-only assertions and add ~1s of boot per scenario.
 *
 * The only thing we genuinely fake here is Stripe (the dev-stub from
 * `apps/backend/src/api/admin/users.ts` plus a stub for checkout). The
 * WSS protocol, auth, pairing, billing-meter (via PGLite), presence,
 * GDPR endpoints, ops gate, and event bus all run for real.
 */
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { spawn, type Subprocess } from "bun";
import { nanoid } from "nanoid";

import { PGlite } from "@electric-sql/pglite";
import { drizzle } from "drizzle-orm/pglite";
import type Stripe from "stripe";

import { z } from "zod";

import { createApp } from "../../apps/backend/src/app";
import { createTokenMeter } from "../../apps/backend/src/billing/meter";
import { meterToBalancePort } from "../../apps/backend/src/billing/ws-adapter";
import {
  attachEventPersister,
  createEventBus,
} from "../../apps/backend/src/events";
import * as schema from "../../apps/backend/src/db/schema";
import { bridgeEventLoggerToBus, pluginWsHandler } from "../../apps/backend/src/ws/plugin";
import { PresenceTracker } from "../../apps/backend/src/ws/presence-tracker";
import { presenceWsHandler } from "../../apps/backend/src/ws/presence";
import type {
  BalanceMeter,
  DeviceLookup,
} from "../../apps/backend/src/ws/plugin";
import type { StripeCheckoutLike } from "../../apps/backend/src/api/billing-checkout";
import type { StripeAdminLike } from "../../apps/backend/src/api/admin/users";
import { authenticateDeviceKey } from "../../apps/backend/src/auth/pairing";
import { hashDeviceKey } from "../../apps/backend/src/auth/device-key";
import { devices, users, subscriptions, tokenBalances } from "../../apps/backend/src/db/schema";
import type { RemoteToolSpec } from "../../apps/backend/src/llm/openrouter";
import { eq } from "drizzle-orm";

import { createFakeLlm, type FakeLlmRunner } from "../harness/fake-llm";
import { buildE2EEnv, type E2EEnv } from "./env";

const MIGRATIONS_DIR = join(import.meta.dir, "..", "..", "apps", "backend", "migrations");

export interface OrchestratorContext {
  env: E2EEnv;
  backendUrl: string;
  wsUrl: string;
  /** Marketing dev-server URL when spawned, otherwise null. */
  marketingUrl: string | null;
  /** Ops dev-server URL when spawned, otherwise null. */
  opsUrl: string | null;
  /** The orchestrator-managed Drizzle handle. Test scenarios introspect it. */
  db: ReturnType<typeof drizzle<typeof schema>>;
  /** Direct PGLite for raw SQL (rarely needed). */
  pg: PGlite;
  /** Track of dev-stub Stripe calls so scenarios can assert. */
  stripeCalls: StripeCallLog;
  /** Presence tracker — exposed so scenarios can wait on snapshots. */
  presence: PresenceTracker;
  /** Fake LLM controller — scenarios can rewrite the next reply / usage. */
  llm: FakeLlmRunner;
  cleanup(): Promise<void>;
}

export interface StripeCallLog {
  checkoutSessions: Array<{ tier: string; priceId: string }>;
  refunds: Array<{ charge: string; amount?: number; reason?: string }>;
  customerUpdates: Array<{ id: string; email: string | null }>;
}

export interface BootOptions {
  /**
   * Launch marketing site as a child Vite process. Default false — only
   * the Claude-in-Chrome browser scripts need it.
   */
  withMarketing?: boolean;
  /** Launch ops site as a child Vite process. Default false. */
  withOps?: boolean;
}

/**
 * Apply every backend migration to a fresh PGLite. Mirrors
 * `apps/backend/test/_db-fixture.ts` so the schema we see in tests is
 * exactly the schema production runs.
 */
async function migrate(pg: PGlite): Promise<void> {
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
      await pg.exec(stmt);
    }
  }
}

/**
 * Real device lookup backed by the PGLite. Mirrors what the production
 * server will use once `apps/backend/src/db/lookup.ts` lands; for now we
 * inline the join from device-key -> users so the WS handler bites on the
 * real argon2 verify path.
 */
function makeDeviceLookup(db: OrchestratorContext["db"]): DeviceLookup {
  return {
    async resolveDeviceKey(deviceKey) {
      const session = await authenticateDeviceKey(db, deviceKey);
      if (!session) return null;
      const [user] = await db.select().from(users).where(eq(users.id, session.userId)).limit(1);
      if (!user || user.status !== "active") return null;
      const [sub] = await db
        .select()
        .from(subscriptions)
        .where(eq(subscriptions.userId, session.userId))
        .limit(1);
      const tier = (sub?.tier ?? "hobbyist") as "hobbyist" | "pro" | "iron";
      return {
        userId: session.userId,
        tier,
        deviceKey,
        playerName: null,
      };
    },
  };
}

function makeStripeStubs(log: StripeCallLog): {
  checkout: StripeCheckoutLike;
  admin: StripeAdminLike;
  full: Stripe;
} {
  const checkout: StripeCheckoutLike = {
    checkout: {
      sessions: {
        async create(params) {
          const tier = (params.line_items?.[0]?.price ?? "unknown") as string;
          log.checkoutSessions.push({ tier, priceId: tier });
          return {
            url: `https://checkout.stripe.test/dev_stub?price=${tier}`,
          };
        },
      },
    },
  };

  const admin: StripeAdminLike = {
    refunds: {
      async create({ charge, amount, reason }) {
        log.refunds.push({
          charge,
          ...(amount !== undefined ? { amount } : {}),
          ...(reason !== undefined ? { reason } : {}),
        });
        return {
          id: `dev_stub_re_${charge.slice(-6)}`,
          amount: amount ?? 0,
          currency: "usd",
          status: "dev_stub",
        };
      },
    },
    invoices: {
      async list() {
        return { data: [] };
      },
    },
  };

  // A minimal Stripe surface for the `/v1/me` delete cascade. We only use
  // `customers.update` (PII detach). Everything else throws if touched.
  const full = {
    customers: {
      async update(id: string, payload: { email?: string; name?: string }) {
        log.customerUpdates.push({ id, email: payload.email ?? null });
        return { id };
      },
    },
  } as unknown as Stripe;

  return { checkout, admin, full };
}

/**
 * Wait for `fetch(url + "/health")` to return ok. Useful when spawning Vite
 * preview as a child process.
 */
async function waitForHttp(url: string, timeoutMs = 15_000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok || res.status === 404) return; // any response = process is up
    } catch {
      // not yet
    }
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error(`waitForHttp: ${url} did not respond in ${timeoutMs}ms`);
}

interface ChildProc {
  proc: Subprocess;
  url: string;
}

/**
 * The tool catalog the WS handler exposes to the (fake) LLM. The names
 * mirror the fixture keys in `harness/game-state-fixtures.ts` so the
 * keyword routing in the fake LLM hits the matching tool. Input schemas
 * are loose on purpose — the fake-plugin never validates them, and the
 * production plugin owns strict schemas on its side.
 */
function defaultToolCatalog(): RemoteToolSpec[] {
  const loose = z.object({}).passthrough();
  const names = [
    "account_identity",
    "combat_stats",
    "inventory",
    "bank_tab",
    "active_prayers",
    "current_quest",
    "slayer_task",
    "farming_summary",
    "farming_patches",
    "raid_layout",
    "target_projectiles",
    "world_state",
  ] as const;
  return names.map((name) => ({
    name,
    description: `Return the player's ${name.replace(/_/g, " ")} from the live RuneLite client.`,
    inputSchema: loose,
  }));
}

async function spawnVite(app: "marketing" | "ops", backendUrl: string): Promise<ChildProc> {
  const port = 0; // let vite pick
  const cwd = join(import.meta.dir, "..", "..", "apps", app);
  const proc = spawn({
    cmd: ["bun", "run", "vite", "--port", String(port), "--strictPort", "false"],
    cwd,
    env: {
      ...process.env,
      VITE_BACKEND_URL: backendUrl,
    },
    stdout: "pipe",
    stderr: "pipe",
  });

  // Vite logs the actual port on stdout. Scan until we see it.
  const reader = proc.stdout.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let found: string | null = null;
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value);
    const match = buffer.match(/Local:\s+(https?:\/\/[^\s]+)/);
    if (match && match[1]) {
      found = match[1].replace(/\/$/, "");
      break;
    }
  }
  reader.releaseLock();
  if (!found) {
    proc.kill();
    throw new Error(`spawnVite(${app}): never saw a Local: URL`);
  }
  return { proc, url: found };
}

/**
 * Boot the orchestrator. Resolves once the backend is listening and
 * (optionally) marketing + ops are reachable.
 */
export async function bootOrchestrator(options: BootOptions = {}): Promise<OrchestratorContext> {
  const env = buildE2EEnv();

  // ---- DB ----------------------------------------------------------------
  const pg = new PGlite();
  await migrate(pg);
  const db = drizzle(pg, { schema });

  // ---- meter + bus -------------------------------------------------------
  const bus = createEventBus();
  attachEventPersister(bus, { db });
  const meter = createTokenMeter({ db, bus });

  // Seed admin user.
  const adminId = `usr_admin_${nanoid(10)}`;
  await db.insert(users).values({
    id: adminId,
    email: env.ADMIN_EMAIL,
    status: "active",
  });
  await db.insert(tokenBalances).values({ userId: adminId, balanceTokens: 0 });

  // ---- presence ----------------------------------------------------------
  const presence = new PresenceTracker({ graceMs: 50 });

  // ---- stripe stubs ------------------------------------------------------
  const stripeCalls: StripeCallLog = {
    checkoutSessions: [],
    refunds: [],
    customerUpdates: [],
  };
  const stripeStubs = makeStripeStubs(stripeCalls);

  // ---- app + WS handlers -------------------------------------------------
  const deviceLookup = makeDeviceLookup(db);
  const balanceMeter: BalanceMeter = meterToBalancePort(meter);
  const eventLogger = bridgeEventLoggerToBus(bus);

  const opsJwtSecret = new TextEncoder().encode(env.OPS_JWT_SECRET);

  const app = createApp({
    admin: { db, adminEmails: [env.ADMIN_EMAIL] },
    adminUsers: { db, adminEmails: [env.ADMIN_EMAIL], stripe: stripeStubs.admin },
    adminLogin: { adminEmails: [env.ADMIN_EMAIL], jwtSecret: opsJwtSecret, cookieSecure: false },
    pairing: { db },
    presence: { tracker: presence },
    me: { db, stripe: stripeStubs.full },
    account: { db },
    accounts: { db },
    billingCheckout: {
      stripe: stripeStubs.checkout,
      successUrl: "https://tibbly.test/welcome",
      cancelUrl: "https://tibbly.test/cancel",
      envSource: {
        STRIPE_PRICE_HOBBYIST: env.STRIPE_PRICE_HOBBYIST,
        STRIPE_PRICE_PRO: env.STRIPE_PRICE_PRO,
        STRIPE_PRICE_IRON: env.STRIPE_PRICE_IRON,
      },
    },
  });

  const llm = createFakeLlm();
  const tools = defaultToolCatalog();
  const pluginWs = pluginWsHandler({
    deviceLookup,
    balanceMeter,
    eventLogger,
    tools,
    llmRunner: llm.runner,
    presence,
  });
  const presenceWs = presenceWsHandler({ tracker: presence });

  // We split the dual-WS dispatcher (plugin + presence) into a single-purpose
  // plugin handler here. The presence WS is exercised via /v1/presence REST
  // in scenarios, which is faithful to how prod clients consume it. Driving
  // both kinds through one Bun.serve worked in production but the envelope
  // swap was racing with the async LLM-stream path: the second inbound
  // frame's `ws.data` got mutated mid-flight, dropping tool_call_result on
  // the floor. One handler per port sidesteps the swap entirely.
  void presenceWs;

  type PluginSocketData = ReturnType<typeof pluginWs.makeSocketData>;

  const server = Bun.serve<PluginSocketData, never>({
    port: 0,
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

  const backendUrl = `http://127.0.0.1:${server.port}`;
  const wsUrl = `ws://127.0.0.1:${server.port}/ws/plugin`;

  // ---- optional vite spawns ---------------------------------------------
  let marketing: ChildProc | null = null;
  let ops: ChildProc | null = null;
  if (options.withMarketing) {
    marketing = await spawnVite("marketing", backendUrl);
    await waitForHttp(marketing.url);
  }
  if (options.withOps) {
    ops = await spawnVite("ops", backendUrl);
    await waitForHttp(ops.url);
  }

  // Avoid unused-var warning for hashDeviceKey in this file; it is the
  // canonical helper the harness re-exports through orchestrator/seed.ts.
  void hashDeviceKey;
  void devices;

  return {
    env,
    backendUrl,
    wsUrl,
    marketingUrl: marketing?.url ?? null,
    opsUrl: ops?.url ?? null,
    db,
    pg,
    stripeCalls,
    presence,
    llm,
    async cleanup() {
      server.stop(true);
      marketing?.proc.kill();
      ops?.proc.kill();
      try {
        await pg.close();
      } catch {
        // ignore
      }
    },
  };
}
