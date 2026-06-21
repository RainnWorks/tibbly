/**
 * Admin login endpoint (ops console stopgap).
 *
 *   POST /admin/login   body: { email }
 *
 * Backend checks `email` against `ADMIN_EMAILS`. On match it mints a
 * short-lived JWT (HMAC-SHA256, via `jose`) and sets it as a hardened
 * cookie: httpOnly, Secure, SameSite=Strict, 12h expiry. The cookie
 * value is the JWT; subsequent admin requests carry it back as
 * `ops_session` and the frontend reads the email claim to attach
 * `x-admin-email` to admin fetches (matching the existing pattern).
 *
 * This is a stopgap. Production wants Google Workspace OIDC; we do not
 * try to be a real auth system tonight. See OPS_DESIGN.md section 10.
 *
 *   GET  /admin/session  -> { email } when cookie is valid, 401 otherwise.
 *   POST /admin/logout   -> clears cookie.
 *
 * JWT secret is read from `OPS_JWT_SECRET` if present; otherwise we
 * derive a stable per-process secret so dev does not crash. We log a
 * warning when falling back so it is impossible to silently miss this
 * before the prod cut.
 */
import { Hono } from "hono";
import { jwtVerify, SignJWT } from "jose";

import { env } from "../../env";
import { log } from "../../lib/log";
import { parseAdminEmails } from "./_gate";

const COOKIE_NAME = "ops_session";
const JWT_LIFETIME_SEC = 12 * 60 * 60;

export interface CreateAdminLoginOptions {
  /** Override admin emails for tests. Defaults to env.ADMIN_EMAILS. */
  adminEmails?: readonly string[];
  /**
   * Symmetric secret used to sign the cookie JWT. Production should set
   * `OPS_JWT_SECRET`; in dev/test we fall back to a per-process key.
   */
  jwtSecret?: Uint8Array;
  /** Override cookie attributes for non-https dev. */
  cookieSecure?: boolean;
}

function deriveDevSecret(): Uint8Array {
  // Deterministic per-process. The warning is loud on boot so this can
  // never silently become production.
  const base = `dev-only-ops-jwt-${process.pid}-${env.DATABASE_URL}`;
  return new TextEncoder().encode(base);
}

function readSecret(opt?: Uint8Array): Uint8Array {
  if (opt) return opt;
  const fromEnv = process.env["OPS_JWT_SECRET"];
  if (fromEnv && fromEnv.length >= 32) return new TextEncoder().encode(fromEnv);
  log.warn(
    "OPS_JWT_SECRET unset or < 32 chars; using a dev-only fallback. Set OPS_JWT_SECRET for prod.",
  );
  return deriveDevSecret();
}

export function createAdminLoginRouter(options: CreateAdminLoginOptions = {}): Hono {
  const app = new Hono();
  const secret = readSecret(options.jwtSecret);
  const allow = new Set(
    (options.adminEmails ?? parseAdminEmails(env.ADMIN_EMAILS)).map((e) =>
      e.toLowerCase(),
    ),
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
      `${COOKIE_NAME}=${jwt}`,
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
    const cookie = readCookie(c.req.header("cookie"), COOKIE_NAME);
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
      `${COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0${cookieSecure ? "; Secure" : ""}`,
    );
    return c.json({ ok: true });
  });

  return app;
}

function readCookie(headerValue: string | undefined, name: string): string | null {
  if (!headerValue) return null;
  const parts = headerValue.split(/;\s*/);
  for (const p of parts) {
    const eq = p.indexOf("=");
    if (eq === -1) continue;
    if (p.slice(0, eq) === name) return p.slice(eq + 1);
  }
  return null;
}
