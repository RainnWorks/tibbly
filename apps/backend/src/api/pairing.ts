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

export interface CreatePairingRouterOptions {
  db: DbClient;
  /**
   * Hook for the redeem-link path to verify the magic-link token. Production
   * wiring (Stripe checkout success URL) signs `{code, exp}` with a shared
   * secret; for testing we accept a plain `{ verifyMagicLink: async (t) =>
   * ({ code }) }` shim. Returning `null` triggers a 401 from the route.
   */
  verifyMagicLink?: (token: string) => Promise<{ code: string; email?: string } | null>;
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
