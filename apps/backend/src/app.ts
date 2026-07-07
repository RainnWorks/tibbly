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
import path from "node:path";

import { Hono } from "hono";
import { secureHeaders } from "hono/secure-headers";

import { createAccountRouter } from "./api/account";
import type { CreateAccountRouterOptions } from "./api/account";
import { createAccountsRouter } from "./api/accounts";
import type { CreateAccountsRouterOptions } from "./api/accounts";
import { createAuthEmailRouter, type CreateAuthEmailRouterOptions } from "./api/auth/email";
import { createAdminCatalogRouter, type CreateAdminCatalogOptions } from "./api/admin/catalog";
import { createAdminLoginRouter, type CreateAdminLoginOptions } from "./api/admin/login";
import { createAdminOpenRouterRouter, type CreateAdminOpenRouterOptions } from "./api/admin/openrouter";
import { createAdminUsageRouter } from "./api/admin/usage";
import type { CreateAdminUsageOptions } from "./api/admin/usage";
import { createAdminUsersRouter, type CreateAdminUsersOptions } from "./api/admin/users";
import { createBillingCheckoutRouter } from "./api/billing-checkout";
import type { CreateBillingCheckoutRouterOptions } from "./api/billing-checkout";
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

/**
 * Default `apps/ops/dist` location, resolved relative to this source file
 * so it doesn't depend on the process cwd. From `apps/backend/src/app.ts`
 * the relative climb is `../../../ops/dist` (src → backend → apps → ops).
 */
function resolveOpsStaticRoot(override?: string): string {
  if (override) return override;
  return path.resolve(import.meta.dir, "..", "..", "ops", "dist");
}

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
   * Admin user-management router (ops console). Pass `{ db, stripe?,
   * adminEmails }`. Sits under `/admin/users/*`.
   */
  adminUsers?: CreateAdminUsersOptions;
  /**
   * Admin OpenRouter spend + revenue router. Sits under
   * `/admin/openrouter/*`.
   */
  adminOpenRouter?: CreateAdminOpenRouterOptions;
  /**
   * Admin model-catalog router (model platform step 1). Sits under
   * `/admin/catalog/*`. Pass `{ db, adminEmails }` to enable.
   */
  adminCatalog?: CreateAdminCatalogOptions;
  /**
   * Admin login + session router. Sits under `/admin/login`,
   * `/admin/session`, `/admin/logout`.
   */
  adminLogin?: CreateAdminLoginOptions | "auto";
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
   * Account-panel feed for the in-RuneLite Tibbly panel (D-8 pivot).
   * Pass `{ db }` to mount `/v1/account/summary` + `/v1/account/usage-proxy`.
   * Omit in tests that don't exercise it.
   */
  account?: CreateAccountRouterOptions;
  /**
   * Stripe customer portal router (RAI-27). Pass `{ db, stripe }` to
   * mount `/v1/billing/*`. Omit in tests that don't exercise billing.
   */
  billing?: CreateBillingPortalRouterOptions;
  /**
   * Stripe Checkout session router for new signups. Pass `{ stripe,
   * successUrl, cancelUrl }` to mount `POST /v1/billing/checkout/:tier`.
   * Anonymous (no requireUser) — this is the entry point from the
   * marketing PricingTiers CTAs.
   */
  billingCheckout?: CreateBillingCheckoutRouterOptions;
  /**
   * GDPR Art. 15 / Art. 17 router (M3.5). Pass `{}` to mount the default,
   * or `{ db, stripe }` to override. Omit in tests that don't exercise
   * deletion/export. Requires `requireUser` auth (header-gated for now).
   */
  me?: CreateMeRouterOptions | "auto";
  /**
   * End-user magic-link auth (Resend + JWT session cookie). Mounts at
   * `/api/auth/*`. Omit in tests that don't exercise auth.
   */
  authEmail?: CreateAuthEmailRouterOptions;
  /**
   * Serve the built ops SPA at `/ops/*`. Defaults to ON; pass `false`
   * for tests that just exercise the API.
   */
  opsStatic?: boolean;
  /**
   * Absolute path to the built ops `dist/` directory. Defaults to the
   * checked-in monorepo location (`apps/ops/dist`) resolved relative to
   * this source file.
   */
  opsStaticRoot?: string;
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

  // adminLogin MUST mount before any gated `/admin/*` router so the
  // unauthenticated /admin/login + /admin/session + /admin/logout routes
  // win Hono's first-match. The admin usage router below applies
  // `adminGate` on `/admin/*`, which would otherwise 401 the login path
  // before the login handler could run.
  if (options.adminLogin) {
    const loginOpts = options.adminLogin === "auto" ? {} : options.adminLogin;
    app.route("/admin", createAdminLoginRouter(loginOpts));
  }

  if (options.admin) {
    const adminOpts = options.admin === "auto" ? {} : options.admin;
    app.route("/admin", createAdminUsageRouter(adminOpts));
  }

  if (options.adminUsers) {
    app.route("/admin/users", createAdminUsersRouter(options.adminUsers));
  }

  if (options.adminOpenRouter) {
    app.route("/admin/openrouter", createAdminOpenRouterRouter(options.adminOpenRouter));
  }

  if (options.adminCatalog) {
    app.route("/admin/catalog", createAdminCatalogRouter(options.adminCatalog));
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

  if (options.account) {
    app.route("/v1/account", createAccountRouter(options.account));
  }

  if (options.billing) {
    app.route("/v1/billing", createBillingPortalRouter(options.billing));
  }

  if (options.billingCheckout) {
    app.route("/v1/billing", createBillingCheckoutRouter(options.billingCheckout));
  }

  if (options.me) {
    const meOpts = options.me === "auto" ? {} : options.me;
    app.route("/v1/me", createMeRouter(meOpts));
  }

  if (options.authEmail) {
    app.route("/api/auth", createAuthEmailRouter(options.authEmail));
  }

  // Ops SPA — served same-origin so cookies + admin API just work.
  // Resolves the dist dir relative to THIS source file (not cwd) so it
  // works whether bun is started from the repo root or apps/backend/.
  if (options.opsStatic !== false) {
    const opsRoot = resolveOpsStaticRoot(options.opsStaticRoot);
    app.get("/ops", (c) => c.redirect("/ops/", 308));
    app.get("/ops/*", async (c) => {
      const rel = c.req.path.replace(/^\/ops\/?/, "") || "index.html";
      const filePath = path.join(opsRoot, rel);
      // SPA fallback: only assets get served as-is; anything missing or
      // not a static asset returns index.html so client-side routing wins.
      const file = Bun.file(filePath);
      if (await file.exists()) {
        return new Response(file);
      }
      const indexFile = Bun.file(path.join(opsRoot, "index.html"));
      if (await indexFile.exists()) {
        return new Response(indexFile, {
          headers: { "content-type": "text/html; charset=utf-8" },
        });
      }
      return c.json(
        { ok: false, error: "ops_dist_missing", hint: "run `bun run -F @osrs-llm-helper/ops build`" },
        503,
      );
    });
  }

  app.notFound((c) => c.json({ ok: false, error: "not_found" }, 404));

  app.onError((err, c) => {
    log.error({ err: { message: err.message, stack: err.stack } }, "unhandled");
    return c.json({ ok: false, error: "internal_error" }, 500);
  });

  return app;
}
