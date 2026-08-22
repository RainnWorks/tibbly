/**
 * End-user magic-link auth router (mounted at `/api/auth`).
 *
 *   POST /api/auth/email/start    body: { email, userCode? }
 *     → mints + persists a single-use link, sets a `pending_auth` cookie
 *       (browser binding), sends the email via the injected `EmailSender`,
 *       returns 202.
 *
 *   GET  /api/auth/email/verify?token=…
 *     → consumes the link, upserts the user by email, sets a long-lived
 *       `tibbly_session` JWT cookie, 302-redirects to `?next=` (validated)
 *       or `WEB_BASE_URL` root.
 *
 *   GET  /api/auth/session
 *     → 200 { email, userId } when the cookie is valid; 401 otherwise.
 *
 *   POST /api/auth/logout
 *     → clears the cookie.
 *
 * Rate limits: `/start` and `/verify` are wrapped in the token-bucket
 * middleware shipped with the pairing router. Generous on `/verify`
 * (real users will click once); tight on `/start` (the email-sending
 * endpoint is a spam vector).
 */
import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { jwtVerify, SignJWT } from "jose";
import { z } from "zod";

import {
  consumeMagicLink,
  issueMagicLink,
  normaliseEmail,
} from "../../auth/magic-link";
import { resolveAuthJwtSecret } from "../../auth/jwt-secret";
import { ConsoleEmailSender, type EmailSender } from "../../auth/email";
import type { DbClient } from "../../db/client";
import { env } from "../../env";
import { log } from "../../lib/log";
import { rateLimit, type RateLimitOptions } from "../../middleware/rateLimit";

/** Cookie name on the verifying browser. Mirrors `pending_auth` on /start. */
export const PENDING_AUTH_COOKIE = "tibbly_pending_auth";
/** Long-lived session cookie set on successful verify. */
export const SESSION_COOKIE = "tibbly_session";
/** Session TTL — 30 days. Refresh comes from a refresh-token chain (chunk 2). */
const SESSION_TTL_SEC = 30 * 24 * 60 * 60;

const startSchema = z.object({
  email: z.string().email().max(254),
  /**
   * Optional device-flow chain: when the request came from `/device`,
   * verify will auto-approve this user_code on success.
   */
  userCode: z.string().min(4).max(32).optional(),
});

const verifyQuerySchema = z.object({
  token: z.string().min(20).max(256),
  next: z.string().optional(),
});

export interface CreateAuthEmailRouterOptions {
  db: DbClient;
  /** Default: `ConsoleEmailSender`. Production injects `ResendEmailSender`. */
  sender?: EmailSender;
  /** Override for tests; defaults to `resolveAuthJwtSecret()`. */
  jwtSecret?: Uint8Array;
  /** Override the redirect base when not provided in `next`. */
  webBaseUrl?: string;
  /** Override cookie hardening for non-https dev. */
  cookieSecure?: boolean;
  /** Per-route rate-limit configs. Pass `"off"` per-route to disable. */
  rateLimits?: {
    start?: Pick<RateLimitOptions, "capacity" | "refillPerSecond"> | "off";
    verify?: Pick<RateLimitOptions, "capacity" | "refillPerSecond"> | "off";
  } | "off";
  /** Clock override forwarded to the rate-limit buckets. */
  rateLimitClock?: () => number;
  /** Clock override for magic-link issue / consume. */
  clock?: () => number;
}

/**
 * Defaults are deliberately tight on `/start` (email-spam vector) and
 * generous on `/verify` (legit users will click once but a bouncing
 * email client could refetch).
 */
const DEFAULT_START_LIMIT = { capacity: 5, refillPerSecond: 0.05 } as const;
const DEFAULT_VERIFY_LIMIT = { capacity: 30, refillPerSecond: 1 } as const;

export function createAuthEmailRouter(options: CreateAuthEmailRouterOptions): Hono {
  const { db } = options;
  const sender: EmailSender = options.sender ?? new ConsoleEmailSender();
  const secret = options.jwtSecret ?? resolveAuthJwtSecret();
  const webBaseUrl = (options.webBaseUrl ?? env.WEB_BASE_URL).replace(/\/+$/, "");
  const cookieSecure = options.cookieSecure ?? env.NODE_ENV === "production";
  const clock = options.clock;

  const app = new Hono();

  // Rate-limit gates.
  const rl = options.rateLimits;
  const startCfg = rl === "off" ? "off" : (rl?.start ?? DEFAULT_START_LIMIT);
  const verifyCfg = rl === "off" ? "off" : (rl?.verify ?? DEFAULT_VERIFY_LIMIT);
  const buildLimiter = (
    cfg: typeof startCfg,
    name: string,
  ) =>
    cfg === "off"
      ? null
      : rateLimit({ ...cfg, name, ...(options.rateLimitClock ? { clock: options.rateLimitClock } : {}) });
  const startLimiter = buildLimiter(startCfg, "auth.email.start");
  const verifyLimiter = buildLimiter(verifyCfg, "auth.email.verify");
  if (startLimiter) app.use("/email/start", startLimiter);
  if (verifyLimiter) app.use("/email/verify", verifyLimiter);

  /* --------------------- POST /email/start --------------------- */

  app.post("/email/start", zValidator("json", startSchema), async (c) => {
    const body = c.req.valid("json");
    const email = normaliseEmail(body.email);

    // Either reuse the caller's existing pending-auth cookie or mint a
    // fresh one. Reuse lets a single browser re-request a link without
    // losing the binding it already has.
    const existing = readCookie(c.req.header("cookie"), PENDING_AUTH_COOKIE);
    const pendingSessionId = existing ?? randomId();

    const issued = await issueMagicLink(db, {
      email,
      requestIp: clientIp(c),
      pendingSessionId,
      userCode: body.userCode ?? null,
      ...(clock ? { now: clock } : {}),
    });

    const verifyUrl = new URL(`${webBaseUrl}/api/auth/email/verify`);
    verifyUrl.searchParams.set("token", issued.token);
    if (body.userCode) verifyUrl.searchParams.set("next", `/device?user_code=${body.userCode}`);

    try {
      await sender.send({
        to: email,
        link: verifyUrl.toString(),
        ttlMinutes: 10,
        visualCode: body.userCode,
      });
    } catch (err) {
      log.error({ err: (err as Error).message }, "magic-link send failed");
      return c.json({ ok: false, error: "send_failed" }, 502);
    }

    // Mint or refresh the pending-auth cookie. 15-min TTL so a slow
    // user opening the email later still gets matched. The cookie is
    // intentionally NOT `Secure` in dev so the local browser keeps it.
    c.header(
      "Set-Cookie",
      cookie(PENDING_AUTH_COOKIE, pendingSessionId, {
        maxAgeSec: 15 * 60,
        secure: cookieSecure,
      }),
    );
    return c.json({ ok: true }, 202);
  });

  /* --------------------- GET /email/verify --------------------- */

  app.get("/email/verify", zValidator("query", verifyQuerySchema), async (c) => {
    const { token, next } = c.req.valid("query");
    const pendingSessionId = readCookie(c.req.header("cookie"), PENDING_AUTH_COOKIE) ?? null;
    const result = await consumeMagicLink(db, {
      token,
      pendingSessionId,
      ...(clock ? { now: clock } : {}),
    });
    if (!result.ok) {
      return c.json({ ok: false, error: result.error.kind }, errorStatus(result.error.kind));
    }

    const jwt = await new SignJWT({
      sub: result.value.userId,
      email: result.value.email,
    })
      .setProtectedHeader({ alg: "HS256" })
      .setIssuedAt()
      .setExpirationTime(`${SESSION_TTL_SEC}s`)
      .sign(secret);

    // Two cookies: set the session, and clear the now-spent pending one.
    c.header(
      "Set-Cookie",
      cookie(SESSION_COOKIE, jwt, { maxAgeSec: SESSION_TTL_SEC, secure: cookieSecure }),
      { append: true },
    );
    c.header(
      "Set-Cookie",
      cookie(PENDING_AUTH_COOKIE, "", { maxAgeSec: 0, secure: cookieSecure }),
      { append: true },
    );

    const redirectTo = safeRedirect(next, webBaseUrl);
    return c.redirect(redirectTo, 302);
  });

  /* --------------------- GET /session --------------------- */

  app.get("/session", async (c) => {
    const cookieVal = readCookie(c.req.header("cookie"), SESSION_COOKIE);
    if (!cookieVal) return c.json({ ok: false, error: "no_session" }, 401);
    try {
      const { payload } = await jwtVerify(cookieVal, secret);
      const sub = typeof payload["sub"] === "string" ? payload["sub"] : null;
      const email = typeof payload["email"] === "string" ? payload["email"] : null;
      if (!sub || !email) return c.json({ ok: false, error: "invalid_session" }, 401);
      return c.json({ ok: true, userId: sub, email });
    } catch {
      return c.json({ ok: false, error: "invalid_session" }, 401);
    }
  });

  /* --------------------- POST /logout --------------------- */

  app.post("/logout", (c) => {
    c.header(
      "Set-Cookie",
      cookie(SESSION_COOKIE, "", { maxAgeSec: 0, secure: cookieSecure }),
    );
    return c.json({ ok: true });
  });

  return app;
}

/* ------------------------------------------------------------------ */
/* Internal helpers                                                    */
/* ------------------------------------------------------------------ */

function errorStatus(kind: string): 401 | 410 {
  // 410 (Gone) is the right shape for "this link was valid but is no
  // longer usable" — consumed or expired. 401 is for everything else.
  if (kind === "already_consumed" || kind === "expired") return 410;
  return 401;
}

function readCookie(header: string | undefined, name: string): string | undefined {
  if (!header) return undefined;
  const parts = header.split(/;\s*/);
  for (const p of parts) {
    const i = p.indexOf("=");
    if (i === -1) continue;
    if (p.slice(0, i) === name) return p.slice(i + 1);
  }
  return undefined;
}

function cookie(
  name: string,
  value: string,
  opts: { maxAgeSec: number; secure: boolean },
): string {
  const attrs = [
    `${name}=${value}`,
    "Path=/",
    "HttpOnly",
    "SameSite=Lax", // 'Lax' so the redirect after verify carries the cookie.
    `Max-Age=${opts.maxAgeSec}`,
    opts.secure ? "Secure" : "",
  ];
  return attrs.filter(Boolean).join("; ");
}

function clientIp(c: { req: { header: (n: string) => string | undefined } }): string | null {
  return (
    c.req.header("cf-connecting-ip") ??
    c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ??
    c.req.header("x-real-ip") ??
    null
  );
}

/**
 * Validate `next` against the configured web base. Always returns a
 * full URL string. Rejects absolute URLs to other origins to stop
 * the link being turned into an open-redirect.
 */
function safeRedirect(next: string | undefined, webBase: string): string {
  if (!next) return webBase + "/";
  try {
    if (next.startsWith("/")) return webBase + next;
    const u = new URL(next);
    const base = new URL(webBase);
    if (u.origin === base.origin) return u.toString();
  } catch {
    /* fall through */
  }
  return webBase + "/";
}

function randomId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Buffer.from(bytes).toString("base64url");
}
