/**
 * Shared admin auth middleware (RAI-39, closes audit C2).
 *
 * Trust root: the `ops_session` HMAC-SHA256 JWT cookie issued by
 * `admin/login.ts`. Every admin route mounts this middleware, which:
 *
 *   1. Reads the `ops_session` cookie from the inbound request.
 *   2. `jose.jwtVerify`s it against the configured secret.
 *   3. Verifies the embedded email claim is in `ADMIN_EMAILS`.
 *   4. Stores the email on `c.var.adminEmail` for downstream handlers.
 *
 * Anything missing, expired, malformed, or for a revoked email → 401.
 *
 * The legacy `x-admin-email` header path is gone — it was forgeable by any
 * caller who knew an operator email. Audit C2 attack closed.
 *
 * Tests pass `{ jwtSecret, adminEmails }` to the factory so they can mint
 * their own cookies via `signOpsSession` (also exported here).
 */
import type { Context, MiddlewareHandler } from "hono";
import { jwtVerify, SignJWT } from "jose";

import { env } from "../../env";
import { resolveOpsJwtSecret } from "./jwt-secret";

/**
 * Hono variables attached to `c.var` once `adminGate` accepts a request.
 * Downstream handlers (ban / refund / credit) read `c.var.adminEmail` for
 * the audit `events.payload.by` field.
 */
export interface AdminGateVars {
  adminEmail: string;
}

export interface AdminGateOptions {
  /** Override admin email allow-list for tests. Defaults to env.ADMIN_EMAILS. */
  adminEmails?: readonly string[];
  /**
   * Override the JWT secret. Tests pass an explicit `Uint8Array`; production
   * pulls from `OPS_JWT_SECRET` via `resolveOpsJwtSecret`.
   */
  jwtSecret?: Uint8Array;
}

/** Cookie name shared with `admin/login.ts`. */
export const OPS_COOKIE_NAME = "ops_session";

export function adminGate(
  options: AdminGateOptions = {},
): MiddlewareHandler<{ Variables: AdminGateVars }> {
  const allow = new Set(
    (options.adminEmails ?? parseAdminEmails(env.ADMIN_EMAILS)).map((e) => e.toLowerCase()),
  );
  const secret = options.jwtSecret ?? resolveOpsJwtSecret();

  return async (c, next) => {
    if (allow.size === 0) {
      return c.json({ ok: false, error: "unauthorized" }, 401);
    }

    const cookie = readCookie(c.req.header("cookie"), OPS_COOKIE_NAME);
    if (!cookie) {
      return c.json({ ok: false, error: "unauthorized", reason: "no_session" }, 401);
    }

    let email: string;
    try {
      const { payload } = await jwtVerify(cookie, secret);
      const claim = typeof payload["email"] === "string" ? payload["email"].toLowerCase() : null;
      if (!claim) {
        return c.json({ ok: false, error: "unauthorized", reason: "invalid_session" }, 401);
      }
      email = claim;
    } catch {
      return c.json({ ok: false, error: "unauthorized", reason: "invalid_session" }, 401);
    }

    if (!allow.has(email)) {
      return c.json({ ok: false, error: "unauthorized", reason: "revoked" }, 401);
    }

    c.set("adminEmail", email);
    await next();
    return;
  };
}

export function parseAdminEmails(raw: string): string[] {
  return raw
    .split(",")
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

/**
 * Test helper — mint a valid ops_session JWT against the supplied secret.
 * Use it to seed the `cookie` header on a request fixture.
 */
export async function signOpsSession(
  email: string,
  secret: Uint8Array,
  options: { lifetimeSeconds?: number } = {},
): Promise<string> {
  const lifetime = options.lifetimeSeconds ?? 12 * 60 * 60;
  return new SignJWT({ email: email.toLowerCase() })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime(`${lifetime}s`)
    .sign(secret);
}

/**
 * Read a single named cookie from a `Cookie` header string. Shared with
 * `admin/login.ts`; lives here so the gate has zero dependency on the
 * login router import (avoids a circular import).
 */
export function readCookie(headerValue: string | undefined, name: string): string | null {
  if (!headerValue) return null;
  const parts = headerValue.split(/;\s*/);
  for (const p of parts) {
    const eq = p.indexOf("=");
    if (eq === -1) continue;
    if (p.slice(0, eq) === name) return p.slice(eq + 1);
  }
  return null;
}

/** Test convenience — build a `Set-Cookie`-shaped cookie header for fixtures. */
export function buildCookieHeader(name: string, value: string): string {
  return `${name}=${value}`;
}

/** Re-export utility so route handlers can build their own audit context. */
export function readAdminEmail(c: Context<{ Variables: AdminGateVars }>): string {
  return c.var.adminEmail;
}
