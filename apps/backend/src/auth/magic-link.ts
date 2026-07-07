/**
 * Magic-link sign-in core.
 *
 * Pure logic: take an email + IP + (optionally) a device-flow user code,
 * mint a single-use URL token, persist its SHA-256 hash, and hand back
 * everything the caller needs to send the email and set the
 * `pending_auth` cookie.
 *
 * The caller (Hono route) owns the actual cookie write, the email send,
 * and the URL stitching — keeping this file free of Hono / Resend lets
 * tests exercise the mint/verify pair against a real DB without booting
 * any of that infra.
 */
import { and, eq, gt, isNull, lt, sql } from "drizzle-orm";

import type { DbClient } from "../db/client";
import { authMagicLinks, users, type AuthMagicLink } from "../db/schema";

/** Default TTL — 10 minutes is the industry baseline for magic links. */
export const DEFAULT_LINK_TTL_MS = 10 * 60 * 1000;

/** Token byte length. 32 bytes = 256 bits → ~52 base64url characters. */
const TOKEN_BYTES = 32;

export interface IssueMagicLinkInput {
  email: string;
  /** Source IP — surfaced for triage logs; rate limiting is upstream. */
  requestIp?: string | null;
  /**
   * Cookie value set by `/start` that the verifier will check. Pass
   * `null` to opt out — used by the cross-device fallback where the
   * browser that clicks the link is intentionally different from the
   * one that requested it.
   */
  pendingSessionId: string | null;
  /**
   * Optional device-flow chain: when the magic link came from the
   * `/device` page, this is the `user_code` the verify path should
   * auto-approve once the user is signed in.
   */
  userCode?: string | null;
  /** TTL override; defaults to 10 minutes. */
  ttlMs?: number;
  /** Clock override; defaults to `Date.now`. */
  now?: () => number;
}

export interface IssuedMagicLink {
  /** Raw URL token. Goes in the email link; never logged, never persisted raw. */
  token: string;
  /** Persisted row id — handy for tests + observability. */
  id: string;
  /** Absolute expiry timestamp. */
  expiresAt: Date;
}

/** Hash a raw token to its persisted form. SHA-256 hex is fine here. */
export async function hashToken(token: string): Promise<string> {
  const data = new TextEncoder().encode(token);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return toHex(new Uint8Array(digest));
}

/** Random URL-safe token. Uses Node's standard `crypto.getRandomValues`. */
export function mintToken(bytes: number = TOKEN_BYTES): string {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return base64Url(buf);
}

/**
 * Normalise an email for lookup + persistence. Lower-cases, trims, and
 * collapses common typos like trailing dots. We do NOT strip `+suffix`
 * Gmail-style aliases — those are real and users rely on them.
 */
export function normaliseEmail(raw: string): string {
  return raw.trim().toLowerCase().replace(/\.+$/, "");
}

export async function issueMagicLink(
  db: DbClient,
  input: IssueMagicLinkInput,
): Promise<IssuedMagicLink> {
  const now = input.now?.() ?? Date.now();
  const ttl = input.ttlMs ?? DEFAULT_LINK_TTL_MS;
  const email = normaliseEmail(input.email);
  const expiresAt = new Date(now + ttl);
  const token = mintToken();
  const tokenHash = await hashToken(token);

  const [row] = await db
    .insert(authMagicLinks)
    .values({
      email,
      tokenHash,
      pendingSessionId: input.pendingSessionId,
      requestIp: input.requestIp ?? null,
      userCode: input.userCode ?? null,
      expiresAt,
    })
    .returning({ id: authMagicLinks.id });

  return { token, id: row.id, expiresAt };
}

export type ConsumeError =
  | { kind: "not_found" }
  | { kind: "expired" }
  | { kind: "already_consumed" }
  | { kind: "wrong_browser" };

export interface ConsumedMagicLink {
  email: string;
  userId: string;
  userCreated: boolean;
  userCode: string | null;
}

export interface ConsumeInput {
  token: string;
  /** Value of the `pending_auth` cookie on the verifying request. */
  pendingSessionId: string | null;
  /** Clock override; defaults to `Date.now`. */
  now?: () => number;
}

/**
 * Verify + consume a magic link.
 *
 * Atomically:
 *  - Hashes the raw token from the URL.
 *  - Looks up the row by hash.
 *  - Rejects on missing / expired / already-consumed / browser-mismatch.
 *  - Marks the row consumed (single-use).
 *  - Upserts the `users` row keyed by email, returning {userId,
 *    userCreated}.
 *
 * Wrapped in a single transaction so a race between two simultaneous
 * clicks ends with only one of them seeing `userCreated`. PGLite +
 * postgres-js both support `db.transaction(...)` identically.
 */
export async function consumeMagicLink(
  db: DbClient,
  input: ConsumeInput,
): Promise<{ ok: true; value: ConsumedMagicLink } | { ok: false; error: ConsumeError }> {
  const now = input.now?.() ?? Date.now();
  const tokenHash = await hashToken(input.token);

  return await db.transaction(async (tx) => {
    const [row] = await tx
      .select()
      .from(authMagicLinks)
      .where(eq(authMagicLinks.tokenHash, tokenHash))
      .limit(1);
    if (!row) return { ok: false as const, error: { kind: "not_found" as const } };
    if (row.consumedAt !== null)
      return { ok: false as const, error: { kind: "already_consumed" as const } };
    if (row.expiresAt.getTime() <= now)
      return { ok: false as const, error: { kind: "expired" as const } };
    if (row.pendingSessionId !== null && row.pendingSessionId !== input.pendingSessionId)
      return { ok: false as const, error: { kind: "wrong_browser" as const } };

    await tx
      .update(authMagicLinks)
      .set({ consumedAt: new Date(now) })
      .where(eq(authMagicLinks.id, row.id));

    // Upsert by email. Concurrent issuers for the same email race here —
    // we resolve to the existing row by checking before inserting.
    const existing = await tx
      .select({ id: users.id })
      .from(users)
      .where(eq(users.email, row.email))
      .limit(1);
    let userId: string;
    let userCreated: boolean;
    if (existing.length > 0) {
      userId = existing[0].id;
      userCreated = false;
      await tx.update(users).set({ updatedAt: new Date(now) }).where(eq(users.id, userId));
    } else {
      const [inserted] = await tx
        .insert(users)
        .values({ email: row.email })
        .returning({ id: users.id });
      userId = inserted.id;
      userCreated = true;
    }

    return {
      ok: true as const,
      value: {
        email: row.email,
        userId,
        userCreated,
        userCode: row.userCode,
      },
    };
  });
}

/**
 * GC pass — drop links that are expired AND consumed, or expired by
 * `idleGraceMs` (default: 7 days). Safe to run on a cron tick.
 */
export async function sweepExpiredMagicLinks(
  db: DbClient,
  opts: { now?: () => number; idleGraceMs?: number } = {},
): Promise<number> {
  const now = opts.now?.() ?? Date.now();
  const idleGrace = opts.idleGraceMs ?? 7 * 24 * 60 * 60 * 1000;
  const cutoff = new Date(now - idleGrace);
  const result = await db
    .delete(authMagicLinks)
    .where(lt(authMagicLinks.expiresAt, cutoff))
    .returning({ id: authMagicLinks.id });
  return result.length;
}

/** Lightweight count of outstanding links for a given email; for tests. */
export async function outstandingFor(
  db: DbClient,
  email: string,
  now: () => number = Date.now,
): Promise<AuthMagicLink[]> {
  const ts = new Date(now());
  return db
    .select()
    .from(authMagicLinks)
    .where(
      and(
        eq(authMagicLinks.email, normaliseEmail(email)),
        gt(authMagicLinks.expiresAt, ts),
        isNull(authMagicLinks.consumedAt),
      ),
    );
}

/* ------------------------------------------------------------------ */
/* Encoding helpers                                                    */
/* ------------------------------------------------------------------ */

function toHex(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i]!.toString(16).padStart(2, "0");
  }
  return out;
}

function base64Url(bytes: Uint8Array): string {
  // Bun + Node both expose Buffer.
  const b64 = Buffer.from(bytes).toString("base64");
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// `sql` is imported above so drizzle's tagged-template re-exports stay live
// even after tree-shaking; without this reference some bundlers drop it.
void sql;
