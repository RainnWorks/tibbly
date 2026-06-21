/**
 * Admin login endpoint (ops console stopgap, RAI-39 hardening).
 *
 *   POST /admin/login   body: { email }
 *
 * Backend checks `email` against `ADMIN_EMAILS`. On match it mints a
 * short-lived JWT (HMAC-SHA256, via `jose`) and sets it as a hardened
 * cookie: httpOnly, Secure, SameSite=Strict, 12h expiry. The cookie
 * value is the JWT; subsequent admin requests carry it back as
 * `ops_session` and the SHARED `adminGate` middleware (`_gate.ts`)
 * verifies it server-side. No `x-admin-email` trust path exists anymore.
 *
 *   GET  /admin/session  -> { email } when cookie is valid, 401 otherwise.
 *   POST /admin/logout   -> clears cookie.
 *
 * JWT secret resolution lives in `jwt-secret.ts`:
 *   - production: `OPS_JWT_SECRET` is required, or boot refuses to start.
 *   - dev / test: per-process derived fallback with a loud warning.
 *
 * This is a stopgap. Production wants Google Workspace OIDC; we do not
 * try to be a real auth system tonight. See OPS_DESIGN.md section 10.
 */
import { Hono } from "hono";
import { jwtVerify, SignJWT } from "jose";

import { env } from "../../env";
import { parseAdminEmails, OPS_COOKIE_NAME, readCookie } from "./_gate";
import { resolveOpsJwtSecret } from "./jwt-secret";

const JWT_LIFETIME_SEC = 12 * 60 * 60;

export interface CreateAdminLoginOptions {
  /** Override admin emails for tests. Defaults to env.ADMIN_EMAILS. */
  adminEmails?: readonly string[];
  /**
   * Symmetric secret used to sign the cookie JWT. Production should set
   * `OPS_JWT_SECRET`; in dev/test we fall back to a per-process key
   * (see `jwt-secret.ts`). Test callers always pass an explicit value.
   */
  jwtSecret?: Uint8Array;
  /** Override cookie attributes for non-https dev. */
  cookieSecure?: boolean;
}

export function createAdminLoginRouter(options: CreateAdminLoginOptions = {}): Hono {
  const app = new Hono();
  const secret = options.jwtSecret ?? resolveOpsJwtSecret();
  const allow = new Set(
    (options.adminEmails ?? parseAdminEmails(env.ADMIN_EMAILS)).map((e) => e.toLowerCase()),
  );
  const cookieSecure = options.cookieSecure ?? env.NODE_ENV === "production";

  app.post("/login", async (c) => {
    const body = (await c.req.json().catch(() => ({}))) as { email?: string };
    const email = body.email?.trim().toLowerCase();
    if (!email || allow.size === 0 || !allow.has(email)) {
      return c.json({ ok: false, error: "unauthorized" }, 401);
    }

    const jwt = await new SignJWT({ email })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuedAt()
      .setExpirationTime(`${JWT_LIFETIME_SEC}s`)
      .sign(secret);

    const cookieAttrs = [
      `${OPS_COOKIE_NAME}=${jwt}`,
      "Path=/",
      "HttpOnly",
      "SameSite=Strict",
      `Max-Age=${JWT_LIFETIME_SEC}`,
      cookieSecure ? "Secure" : "",
    ]
      .filter(Boolean)
      .join("; ");

    c.header("Set-Cookie", cookieAttrs);
    return c.json({ ok: true, email });
  });

  app.get("/session", async (c) => {
    const cookie = readCookie(c.req.header("cookie"), OPS_COOKIE_NAME);
    if (!cookie) return c.json({ ok: false, error: "no_session" }, 401);
    try {
      const { payload } = await jwtVerify(cookie, secret);
      const email = typeof payload["email"] === "string" ? payload["email"] : null;
      if (!email || !allow.has(email)) {
        return c.json({ ok: false, error: "revoked" }, 401);
      }
      return c.json({ ok: true, email });
    } catch {
      return c.json({ ok: false, error: "invalid_session" }, 401);
    }
  });

  app.post("/logout", (c) => {
    c.header(
      "Set-Cookie",
      `${OPS_COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0${cookieSecure ? "; Secure" : ""}`,
    );
    return c.json({ ok: true });
  });

  return app;
}
