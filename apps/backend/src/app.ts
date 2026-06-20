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

import { env } from "./env";
import { log } from "./lib/log";

/** Process boot time in ms — uptime is computed from this. */
const BOOT_AT = Date.now();

export interface CreateAppOptions {
  /** Override version (handy for tests). Defaults to `env.VERSION`. */
  version?: string;
  /** Override boot time (handy for tests). Defaults to module-load time. */
  bootAt?: number;
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

  app.notFound((c) => c.json({ ok: false, error: "not_found" }, 404));

  app.onError((err, c) => {
    log.error({ err: { message: err.message, stack: err.stack } }, "unhandled");
    return c.json({ ok: false, error: "internal_error" }, 500);
  });

  return app;
}
