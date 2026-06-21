/**
 * Hono app factory.
 *
 * Kept separate from `server.ts` so tests can `createApp()` and `app.fetch()`
 * straight against a Web Standards Request without binding a TCP port.
 *
 * Routes (RAI-14 skeleton):
 *   GET /health  — liveness probe, returns {ok, version, uptime}.
 *   GET /version — version-only probe for deploy attribution.
 *
 * Real routes (auth, chat WS, billing webhooks) attach in later issues.
 */
import { Hono } from "hono";
import { secureHeaders } from "hono/secure-headers";

import { createAdminUsageRouter } from "./api/admin/usage";
import type { CreateAdminUsageOptions } from "./api/admin/usage";
import { createPairingRouter } from "./api/pairing";
import type { CreatePairingRouterOptions } from "./api/pairing";
import { env } from "./env";
import { log } from "./lib/log";

/** Process boot time in ms — uptime is computed from this. */
const BOOT_AT = Date.now();

export interface CreateAppOptions {
  /** Override version (handy for tests). Defaults to `env.VERSION`. */
  version?: string;
  /** Override boot time (handy for tests). Defaults to module-load time. */
  bootAt?: number;
  /**
   * Admin analytics router config (RAI-37). Pass `{ db, adminEmails }` to
   * enable. When omitted, /admin/* routes are NOT mounted — tests that
   * don't care about admin can keep using the bare app.
   */
  admin?: CreateAdminUsageOptions | "auto";
  /**
   * Pairing-code router config (RAI-18). Pass `{ db }` to mount the
   * `/v1/pairing/*` endpoints. Omit in tests that don't exercise pairing
   * so the bare app stays DB-free.
   */
  pairing?: CreatePairingRouterOptions;
}

export function createApp(options: CreateAppOptions = {}): Hono {
  const version = options.version ?? env.VERSION;
  const bootAt = options.bootAt ?? BOOT_AT;

  const app = new Hono();

  app.use("*", secureHeaders());

  app.use("*", async (c, next) => {
    const start = Date.now();
    await next();
    log.info(
      {
        method: c.req.method,
        path: c.req.path,
        status: c.res.status,
        ms: Date.now() - start,
      },
      "req",
    );
  });

  app.get("/health", (c) =>
    c.json({
      ok: true,
      version,
      uptime: (Date.now() - bootAt) / 1000,
    }),
  );

  app.get("/version", (c) => c.json({ version }));

  if (options.admin) {
    const adminOpts = options.admin === "auto" ? {} : options.admin;
    app.route("/admin", createAdminUsageRouter(adminOpts));
  }

  if (options.pairing) {
    app.route("/v1/pairing", createPairingRouter(options.pairing));
  }

  app.notFound((c) => c.json({ ok: false, error: "not_found" }, 404));

  app.onError((err, c) => {
    log.error({ err: { message: err.message, stack: err.stack } }, "unhandled");
    return c.json({ ok: false, error: "internal_error" }, 500);
  });

  return app;
}
