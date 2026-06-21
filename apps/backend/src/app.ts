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

import { createAccountsRouter } from "./api/accounts";
import type { CreateAccountsRouterOptions } from "./api/accounts";
import { createAdminUsageRouter } from "./api/admin/usage";
import type { CreateAdminUsageOptions } from "./api/admin/usage";
import { createBillingPortalRouter } from "./api/billing-portal";
import type { CreateBillingPortalRouterOptions } from "./api/billing-portal";
import { createMeRouter } from "./api/me";
import type { CreateMeRouterOptions } from "./api/me";
import { createPairingRouter } from "./api/pairing";
import type { CreatePairingRouterOptions } from "./api/pairing";
import { createPresenceRouter } from "./api/presence";
import type { CreatePresenceRouterOptions } from "./api/presence";
import { createStripeWebhookRouter } from "./api/webhooks/stripe";
import type { StripeWebhookDeps } from "./api/webhooks/stripe";
import { createUsageRouter } from "./api/usage";
import type { CreateUsageRouterOptions } from "./api/usage";
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
  /**
   * Presence router config (RAI-21). Pass `{ tracker }` to mount the
   * public `/v1/presence` endpoint. Omit when the bare app is fine.
   */
  presence?: CreatePresenceRouterOptions;
  /**
   * Stripe webhook router (RAI-19). Pass the meter + db to mount
   * `POST /api/webhooks/stripe`. Omitted in dev/test by default so a
   * missing webhook secret doesn't crash boot.
   */
  stripeWebhook?: StripeWebhookDeps;
  /**
   * Usage summary router (RAI-27). Pass `{ db }` to mount
   * `/v1/usage/*`. Omit in tests that don't exercise usage.
   */
  usage?: CreateUsageRouterOptions;
  /**
   * Linked-accounts router (RAI-27). Pass `{ db }` to mount
   * `/v1/accounts/*`. Omit in tests that don't exercise it.
   */
  accounts?: CreateAccountsRouterOptions;
  /**
   * Stripe customer portal router (RAI-27). Pass `{ db, stripe }` to
   * mount `/v1/billing/*`. Omit in tests that don't exercise billing.
   */
  billing?: CreateBillingPortalRouterOptions;
  /**
   * GDPR Art. 15 / Art. 17 router (M3.5). Pass `{}` to mount the default,
   * or `{ db, stripe }` to override. Omit in tests that don't exercise
   * deletion/export. Requires `requireUser` auth (header-gated for now).
   */
  me?: CreateMeRouterOptions | "auto";
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

  if (options.presence) {
    app.route("/v1/presence", createPresenceRouter(options.presence));
  }

  if (options.stripeWebhook) {
    app.route("/api/webhooks", createStripeWebhookRouter(options.stripeWebhook));
  }

  if (options.usage) {
    app.route("/v1/usage", createUsageRouter(options.usage));
  }

  if (options.accounts) {
    app.route("/v1/accounts", createAccountsRouter(options.accounts));
  }

  if (options.billing) {
    app.route("/v1/billing", createBillingPortalRouter(options.billing));
  }

  if (options.me) {
    const meOpts = options.me === "auto" ? {} : options.me;
    app.route("/v1/me", createMeRouter(meOpts));
  }

  app.notFound((c) => c.json({ ok: false, error: "not_found" }, 404));

  app.onError((err, c) => {
    log.error({ err: { message: err.message, stack: err.stack } }, "unhandled");
    return c.json({ ok: false, error: "internal_error" }, 500);
  });

  return app;
}
