/**
 * Pairing-code flow (RAI-18).
 *
 * The plugin generates a long-lived deviceKey on install, then on first chat
 * request asks the backend for a 6-char pairing code which it surfaces in-game.
 * The user enters the code on the dashboard — or arrives at the dashboard via
 * the Stripe checkout success URL with a pre-attached customer id — and the
 * backend then:
 *
 *   1. Creates (or finds) a `users` row for the Stripe customer.
 *   2. Creates a `devices` row tying the deviceKeyHash to that user.
 *   3. Marks the pairing row `claimed_at = now()`.
 *
 * Replay protection: a code that has been claimed OR has expired returns 410
 * Gone on subsequent claim attempts. Codes are single-shot.
 *
 * The WebSocket `authenticate` handler (RAI-17) calls `authenticateDeviceKey`
 * to bind an inbound socket to a user — it argon2-verifies the supplied raw
 * key against every device's hash for that user (typically 1, rarely >5).
 */
import { customAlphabet } from "nanoid";
import { and, eq, isNull, lt, sql } from "drizzle-orm";

import type { DbClient } from "../db/client";
import { devices, pairingCodes, users } from "../db/schema";
import { hashDeviceKey, verifyDeviceKey } from "./device-key";

/* -------------------------------------------------------------------------- */
/*  Code generation                                                            */
/* -------------------------------------------------------------------------- */

/**
 * 32-char alphabet, excluding visually ambiguous glyphs:
 *   - `0` / `O`
 *   - `1` / `I`
 *
 * Yields 32^6 ≈ 1.07B codes — collision is astronomically unlikely under the
 * 10-minute TTL even at thousands of pairings per minute, but the request
 * loop guards against it anyway with a small retry.
 */
const PAIRING_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
const PAIRING_CODE_LENGTH = 6;
const nanoidCode = customAlphabet(PAIRING_CODE_ALPHABET, PAIRING_CODE_LENGTH);

/** 21-char URL-safe id for `users`, `devices`, `pairing_codes` PKs. */
const ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const nanoidId = customAlphabet(ID_ALPHABET, 21);

/** TTL for an unclaimed pairing code. */
export const PAIRING_TTL_MS = 10 * 60 * 1000;

/** Number of insert retries on accidental code collision. */
const COLLISION_RETRIES = 5;

/** Generate a 6-char pairing code from the safe alphabet. */
export function generatePairingCode(): string {
  return nanoidCode();
}

/* -------------------------------------------------------------------------- */
/*  Request                                                                    */
/* -------------------------------------------------------------------------- */

export interface PairingRequestInput {
  /** Raw device key as the plugin holds it locally. Hashed before storage. */
  deviceKey: string;
  /** In-game player name — `Client.localPlayer.name`. May be absent on logout. */
  playerName?: string | undefined;
}

export interface PairingRequestResult {
  code: string;
  expiresAt: Date;
}

/**
 * Insert a fresh pairing code for the device-key claim. Retries on the rare
 * race that two requests generate identical 6-char strings.
 */
export async function createPairingRequest(
  db: DbClient,
  input: PairingRequestInput,
  now: Date = new Date(),
): Promise<PairingRequestResult> {
  const deviceKeyHash = await hashDeviceKey(input.deviceKey);
  const expiresAt = new Date(now.getTime() + PAIRING_TTL_MS);

  let lastErr: unknown;
  for (let attempt = 0; attempt < COLLISION_RETRIES; attempt++) {
    const code = generatePairingCode();
    try {
      await db.insert(pairingCodes).values({
        id: nanoidId(),
        code,
        deviceKeyHash,
        playerName: input.playerName ?? null,
        userId: null,
        deviceId: null,
        expiresAt,
        claimedAt: null,
      });
      return { code, expiresAt };
    } catch (err) {
      lastErr = err;
      // PG / PGLite both throw on unique violation; just retry with a new code.
      continue;
    }
  }
  throw new Error(
    `createPairingRequest: exhausted ${COLLISION_RETRIES} retries: ${String(lastErr)}`,
  );
}

/* -------------------------------------------------------------------------- */
/*  Claim                                                                      */
/* -------------------------------------------------------------------------- */

export interface PairingClaimInput {
  code: string;
  /**
   * Stripe customer id (`cus_…`) from the dashboard or the checkout success
   * URL. When present the backend either finds the existing user row OR
   * creates a fresh one. When absent we still mint an anonymous user so the
   * device can be bound — but anonymous users have no payment ability.
   */
  stripeCustomerId?: string | undefined;
  /** Optional email to attach if we are creating the user row. */
  email?: string | undefined;
}

export interface PairingClaimResult {
  userId: string;
  deviceId: string;
  /** Whether the user row was created on this claim (vs. found). */
  userCreated: boolean;
}

/**
 * Error variants distinguish the "no row" case (404) from the "row exists but
 * is spent" case (410). The Hono route maps these to HTTP status codes.
 */
export class PairingNotFoundError extends Error {
  constructor() {
    super("pairing code not found");
    this.name = "PairingNotFoundError";
  }
}

export class PairingGoneError extends Error {
  constructor(public reason: "expired" | "already_claimed") {
    super(`pairing code ${reason}`);
    this.name = "PairingGoneError";
  }
}

/**
 * Atomically claim a pairing code, creating user + device as needed and
 * marking the row spent. Surfaces typed errors for the API layer to map to
 * 404 / 410 / 200.
 */
export async function claimPairingCode(
  db: DbClient,
  input: PairingClaimInput,
  now: Date = new Date(),
): Promise<PairingClaimResult> {
  return db.transaction(async (tx) => {
    const [row] = await tx
      .select()
      .from(pairingCodes)
      .where(eq(pairingCodes.code, input.code))
      .limit(1);

    if (!row) throw new PairingNotFoundError();
    if (row.claimedAt !== null) throw new PairingGoneError("already_claimed");
    if (row.expiresAt.getTime() <= now.getTime()) {
      throw new PairingGoneError("expired");
    }

    /* ------- resolve / create user ------------------------------------ */
    let userId: string;
    let userCreated = false;

    if (input.stripeCustomerId) {
      const [existing] = await tx
        .select()
        .from(users)
        .where(eq(users.stripeCustomerId, input.stripeCustomerId))
        .limit(1);
      if (existing) {
        userId = existing.id;
      } else {
        userId = nanoidId();
        await tx.insert(users).values({
          id: userId,
          stripeCustomerId: input.stripeCustomerId,
          email: input.email ?? null,
        });
        userCreated = true;
      }
    } else if (input.email) {
      const [existing] = await tx.select().from(users).where(eq(users.email, input.email)).limit(1);
      if (existing) {
        userId = existing.id;
      } else {
        userId = nanoidId();
        await tx.insert(users).values({
          id: userId,
          email: input.email,
          stripeCustomerId: null,
        });
        userCreated = true;
      }
    } else {
      // Anonymous user — payment must be attached later via /v1/pairing/redeem-link.
      userId = nanoidId();
      await tx.insert(users).values({
        id: userId,
        stripeCustomerId: null,
        email: null,
      });
      userCreated = true;
    }

    /* ------- create device -------------------------------------------- */
    const deviceId = nanoidId();
    await tx.insert(devices).values({
      id: deviceId,
      userId,
      deviceKeyHash: row.deviceKeyHash,
      playerName: row.playerName,
      lastSeenAt: now,
    });

    /* ------- mark code spent ------------------------------------------ */
    await tx
      .update(pairingCodes)
      .set({ claimedAt: now, userId, deviceId })
      .where(eq(pairingCodes.code, input.code));

    return { userId, deviceId, userCreated };
  });
}

/* -------------------------------------------------------------------------- */
/*  WS authentication                                                          */
/* -------------------------------------------------------------------------- */

export interface AuthenticatedSession {
  userId: string;
  deviceId: string;
}

/**
 * Verify an inbound deviceKey against the stored hashes. Used by the WebSocket
 * handler (RAI-17) to bind a live socket to a user record.
 *
 * Implementation: we cannot index by hash (argon2 includes a random salt so
 * the same input → different hash) so we scan devices and verify in turn.
 * In practice the search space is small — typical user has 1-3 devices —
 * and this path is only hit at WS connect, never per-message.
 *
 * Returns `null` when no device matches. Callers must treat that as a
 * 401-equivalent disconnect.
 */
export async function authenticateDeviceKey(
  db: DbClient,
  deviceKey: string,
): Promise<AuthenticatedSession | null> {
  if (!deviceKey) return null;

  const rows = await db.select().from(devices);

  for (const device of rows) {
    if (await verifyDeviceKey(device.deviceKeyHash, deviceKey)) {
      return { userId: device.userId, deviceId: device.id };
    }
  }
  return null;
}

/* -------------------------------------------------------------------------- */
/*  Maintenance                                                                */
/* -------------------------------------------------------------------------- */

/**
 * Delete unclaimed expired pairing codes. Called by the retention sweeper.
 * Returns the number of rows removed. Claimed rows are retained so we can
 * audit the device → user binding history.
 */
export async function sweepExpiredPairingCodes(
  db: DbClient,
  now: Date = new Date(),
): Promise<number> {
  // Count first, then delete — Drizzle's union type for `delete` doesn't
  // expose `.returning()` consistently across PGLite/postgres-js. Cheap
  // because we keep this table tiny.
  const predicate = and(isNull(pairingCodes.claimedAt), lt(pairingCodes.expiresAt, now));
  const [{ c }] = await db
    .select({ c: sql<number>`count(*)::int` })
    .from(pairingCodes)
    .where(predicate);
  await db.delete(pairingCodes).where(predicate);
  return c;
}
