/**
 * Pairing API (RAI-18).
 *
 * Routes:
 *   POST /v1/pairing/request      — plugin asks for a 6-char code.
 *   POST /v1/pairing/claim        — dashboard or Stripe success page claims it.
 *   POST /v1/pairing/redeem-link  — magic-link variant used by Stripe checkout
 *                                   success URL: same as `claim` but accepts
 *                                   a signed `token` carrying the code, so the
 *                                   user only has to click a link.
 *
 * The route module is exposed via `createPairingRouter({ db })` so tests
 * (and the future composition root) can inject an isolated PGLite instance
 * instead of touching the singleton.
 */
import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";

import type { DbClient } from "../db/client";
import {
  claimPairingCode,
  createPairingRequest,
  PairingGoneError,
  PairingNotFoundError,
} from "../auth/pairing";
import { rateLimit, type RateLimitOptions } from "../middleware/rateLimit";

/**
 * Defaults tuned for the launch traffic shape:
 *  - `request` — plugin pairs once per install; legitimate IPs hit this
 *    very rarely. Tight enough to choke a code-flooding attacker without
 *    affecting a real user.
 *  - `claim`   — dashboard / success-page submits one code per pair. A
 *    real user might retry on fat-finger. Tightish so a brute-force
 *    crawl from one IP can't sweep the 6-char keyspace inside any
 *    individual code's 10-min window.
 *  - `redeemLink` — Stripe success URL click. One per checkout; same
 *    profile as `claim`.
 *
 * Numbers are intentionally per-route: a user hitting `claim` shouldn't
 * burn their `request` budget and vice-versa.
 */
const DEFAULT_REQUEST_LIMIT = { capacity: 5, refillPerSecond: 0.1 } as const;
const DEFAULT_CLAIM_LIMIT = { capacity: 10, refillPerSecond: 0.2 } as const;
const DEFAULT_REDEEM_LIMIT = { capacity: 10, refillPerSecond: 0.2 } as const;

export interface PairingRateLimits {
  /** POST /v1/pairing/request — plugin-side. */
  request?: Pick<RateLimitOptions, "capacity" | "refillPerSecond"> | "off";
  /** POST /v1/pairing/claim — dashboard / web-side. */
  claim?: Pick<RateLimitOptions, "capacity" | "refillPerSecond"> | "off";
  /** POST /v1/pairing/redeem-link — Stripe magic-link click. */
  redeemLink?: Pick<RateLimitOptions, "capacity" | "refillPerSecond"> | "off";
}

export interface CreatePairingRouterOptions {
  db: DbClient;
  /**
   * Hook for the redeem-link path to verify the magic-link token. Production
   * wiring (Stripe checkout success URL) signs `{code, exp}` with a shared
   * secret; for testing we accept a plain `{ verifyMagicLink: async (t) =>
   * ({ code }) }` shim. Returning `null` triggers a 401 from the route.
   */
  verifyMagicLink?: (token: string) => Promise<{ code: string; email?: string } | null>;
  /**
   * Per-route rate-limit overrides. Pass `"off"` per-route to disable in
   * tests, or `"off"` at top level to disable all three. Defaults are
   * tuned for prod traffic; see comments above for the rationale.
   */
  rateLimits?: PairingRateLimits | "off";
  /**
   * Clock override forwarded to the rate-limit buckets. Tests use this
   * to drive refill behaviour without `setTimeout`.
   */
  rateLimitClock?: () => number;
}

const requestSchema = z.object({
  deviceKey: z.string().min(16, "deviceKey too short"),
  playerName: z.string().min(1).max(64).optional(),
});

/**
 * 6 chars from `[A-Z0-9]` excluding visually ambiguous `0` / `O` / `1` / `I`.
 * Matches the alphabet in `auth/pairing.ts`.
 */
const PAIRING_CODE_REGEX = /^[2-9ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/;

const claimSchema = z.object({
  code: z.string().length(6).regex(PAIRING_CODE_REGEX, "invalid code format"),
  stripeCustomerId: z.string().startsWith("cus_").optional(),
  email: z.string().email().optional(),
});

const redeemSchema = z.object({
  token: z.string().min(8),
  stripeCustomerId: z.string().startsWith("cus_").optional(),
});

export function createPairingRouter(options: CreatePairingRouterOptions): Hono {
  const { db } = options;
  const app = new Hono();

  // Rate-limit gates. Resolved per-route so a single `"off"` at the top
  // wipes all three but per-route overrides are still possible.
  const rl = options.rateLimits;
  const requestCfg = rl === "off" ? "off" : (rl?.request ?? DEFAULT_REQUEST_LIMIT);
  const claimCfg = rl === "off" ? "off" : (rl?.claim ?? DEFAULT_CLAIM_LIMIT);
  const redeemCfg = rl === "off" ? "off" : (rl?.redeemLink ?? DEFAULT_REDEEM_LIMIT);
  const clock = options.rateLimitClock;
  const buildLimiter = (cfg: typeof requestCfg, name: string) =>
    cfg === "off"
      ? null
      : rateLimit({ ...cfg, name, ...(clock ? { clock } : {}) });
  const requestLimiter = buildLimiter(requestCfg, "pairing.request");
  const claimLimiter = buildLimiter(claimCfg, "pairing.claim");
  const redeemLimiter = buildLimiter(redeemCfg, "pairing.redeemLink");
  if (requestLimiter) app.use("/request", requestLimiter);
  if (claimLimiter) app.use("/claim", claimLimiter);
  if (redeemLimiter) app.use("/redeem-link", redeemLimiter);

  /* ----- POST /request -------------------------------------------------- */
  app.post("/request", zValidator("json", requestSchema), async (c) => {
    const body = c.req.valid("json");
    const result = await createPairingRequest(db, body);
    return c.json({
      code: result.code,
      expiresAt: result.expiresAt.toISOString(),
    });
  });

  /* ----- POST /claim ---------------------------------------------------- */
  app.post("/claim", zValidator("json", claimSchema), async (c) => {
    const body = c.req.valid("json");
    try {
      const result = await claimPairingCode(db, body);
      return c.json(
        {
          userId: result.userId,
          deviceId: result.deviceId,
          userCreated: result.userCreated,
        },
        201,
      );
    } catch (err) {
      if (err instanceof PairingNotFoundError) {
        return c.json({ error: "not_found" }, 404);
      }
      if (err instanceof PairingGoneError) {
        return c.json({ error: "gone", reason: err.reason }, 410);
      }
      throw err;
    }
  });

  /* ----- POST /redeem-link --------------------------------------------- */
  app.post("/redeem-link", zValidator("json", redeemSchema), async (c) => {
    const body = c.req.valid("json");
    const verify = options.verifyMagicLink;
    if (!verify) {
      return c.json({ error: "redeem_link_disabled" }, 503);
    }
    const parsed = await verify(body.token);
    if (!parsed) return c.json({ error: "invalid_token" }, 401);

    try {
      const result = await claimPairingCode(db, {
        code: parsed.code,
        stripeCustomerId: body.stripeCustomerId,
        email: parsed.email,
      });
      return c.json(
        {
          userId: result.userId,
          deviceId: result.deviceId,
          userCreated: result.userCreated,
        },
        201,
      );
    } catch (err) {
      if (err instanceof PairingNotFoundError) {
        return c.json({ error: "not_found" }, 404);
      }
      if (err instanceof PairingGoneError) {
        return c.json({ error: "gone", reason: err.reason }, 410);
      }
      throw err;
    }
  });

  return app;
}
