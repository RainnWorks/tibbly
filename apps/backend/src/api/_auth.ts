/**
 * Real session middleware for the v1 user surface (RAI-39, closes audit C1).
 *
 * Trust root: the plugin holds the raw device key on disk. The backend only
 * persists `devices.deviceKeyHash`, an argon2id digest with a per-row salt.
 * Each HTTP request carries the raw key as
 *
 *   Authorization: Bearer <raw-device-key>
 *
 * The middleware looks up the row whose argon2 verify passes against the
 * presented key. Cache by `sha256(rawKey)` so the per-request cost is one
 * hash lookup; misses fall back to the scan-and-verify path that the WS
 * handshake already uses (`authenticateDeviceKey` in `auth/pairing.ts`).
 *
 * Why scan-and-verify rather than `argon2(raw) == hash`: argon2 includes a
 * random salt in the digest, so the same input deterministically yields a
 * different hash. We cannot index by digest. The scan is bounded — typical
 * user has 1–3 devices, and the cache wraps it in O(1) for warm traffic.
 *
 * Header trust paths removed in this revision:
 *   - `x-user-id` (no verification) — DELETED. Audit C1 attack closed.
 *   - `Authorization: Bearer <raw-userId>` (no verification) — DELETED.
 *
 * Dev-only escape hatch (`x-dev-user-id`): accepted when
 * `env.NODE_ENV !== "production"` AND `env.ALLOW_DEV_HEADERS === "true"`.
 * Off by default. The boot path logs a loud warning when on so it cannot
 * silently leak into a production image.
 *
 * Tests reach for `requireUserWith({ db })` so a PGLite fixture and an
 * empty cache can be passed in; the production binding `requireUser` is
 * the same factory bound to the singleton DB + a module-scoped cache.
 */
import { createHash } from "node:crypto";

import type { Context, MiddlewareHandler } from "hono";

import { verifyDeviceKey } from "../auth/device-key";
import type { DbClient } from "../db/client";
import { getDb } from "../db/client";
import { devices } from "../db/schema";
import { env } from "../env";
import { log } from "../lib/log";

/**
 * Hono variables added by `requireUser`. Route handlers read
 * `c.var.userId` once the middleware has accepted them; `c.var.deviceId`
 * is the device row that authenticated the request (or `null` on the
 * dev-only fallback path).
 */
export interface AuthedVars {
  userId: string;
  deviceId: string | null;
}

/**
 * Read the raw device key from the request, or `null` if none was
 * presented. Only `Authorization: Bearer <raw-device-key>` is honoured.
 */
function readBearerDeviceKey(c: Context): string | null {
  const auth = c.req.header("authorization")?.trim();
  if (!auth) return null;
  if (!auth.toLowerCase().startsWith("bearer ")) return null;
  const token = auth.slice("bearer ".length).trim();
  return token.length > 0 ? token : null;
}

/**
 * Read the dev-only user id hint, or `null` if the env gate is closed.
 * Returns the trimmed value only when BOTH `NODE_ENV !== "production"`
 * AND `ALLOW_DEV_HEADERS === "true"`.
 */
function readDevUserIdIfAllowed(c: Context): string | null {
  if (env.NODE_ENV === "production") return null;
  if (env.ALLOW_DEV_HEADERS !== "true") return null;
  const direct = c.req.header("x-dev-user-id")?.trim();
  return direct && direct.length > 0 ? direct : null;
}

/**
 * Cache key for the device-key lookup. We hash with sha256 (not store the
 * raw key) so a heap dump of the cache cannot replay the credential.
 */
function deviceKeyCacheKey(rawKey: string): string {
  return createHash("sha256").update(rawKey, "utf8").digest("hex");
}

/**
 * Bounded in-memory LRU. Production traffic is dominated by repeat
 * (raw-key → user) lookups so the cache hit rate is high; misses pay
 * one argon2 verify per device row for the user, identical to the WS
 * handshake.
 *
 * Eviction is "delete oldest insertion" via `Map` iteration order. Not a
 * true LRU but cheap and deterministic, which is enough for an in-process
 * gate. Capacity bounds memory; a smaller value here just means more cache
 * misses, never wrong answers.
 */
class DeviceKeyCache {
  private readonly capacity: number;
  private readonly map: Map<string, { userId: string; deviceId: string }> = new Map();

  public constructor(capacity = 5000) {
    this.capacity = capacity;
  }

  public get(key: string): { userId: string; deviceId: string } | undefined {
    return this.map.get(key);
  }

  public set(key: string, value: { userId: string; deviceId: string }): void {
    if (this.map.size >= this.capacity) {
      const first = this.map.keys().next().value;
      if (typeof first === "string") this.map.delete(first);
    }
    this.map.set(key, value);
  }

  /**
   * Drop every cached row whose mapped userId or deviceId matches the
   * argument. Called when a device is unpaired or a user is banned so a
   * stale cache entry never resurrects the credential.
   */
  public invalidateByUserOrDevice(targetUserId: string | null, targetDeviceId: string | null): void {
    for (const [k, v] of this.map) {
      if (
        (targetUserId !== null && v.userId === targetUserId) ||
        (targetDeviceId !== null && v.deviceId === targetDeviceId)
      ) {
        this.map.delete(k);
      }
    }
  }

  public clear(): void {
    this.map.clear();
  }

  public size(): number {
    return this.map.size;
  }
}

export interface RequireUserOptions {
  /** Override DB for tests. Defaults to the process-wide singleton. */
  db?: DbClient;
  /** Override cache for tests. Defaults to a module-scoped cache. */
  cache?: DeviceKeyCache;
}

/**
 * Module-scoped cache for the production middleware. Tests can pass their
 * own via `requireUserWith({ cache })`.
 */
const defaultCache = new DeviceKeyCache();

/** Test helper — exported so the negative-test path can prove a stale cache hit cannot replay. */
export function getDefaultDeviceKeyCache(): DeviceKeyCache {
  return defaultCache;
}

/** Resolve `(userId, deviceId)` for a raw device key. Returns `null` on miss. */
async function resolveDeviceKey(
  db: DbClient,
  cache: DeviceKeyCache,
  rawKey: string,
): Promise<{ userId: string; deviceId: string } | null> {
  const cacheKey = deviceKeyCacheKey(rawKey);
  const hit = cache.get(cacheKey);
  if (hit) return hit;

  // Miss — scan the device table. Argon2's per-row salt makes a hash-equality
  // index impossible; verification must be linear in the number of devices.
  // The WS handshake uses the same shape; see `authenticateDeviceKey`.
  const rows = await db
    .select({ id: devices.id, userId: devices.userId, deviceKeyHash: devices.deviceKeyHash })
    .from(devices);

  for (const row of rows) {
    if (await verifyDeviceKey(row.deviceKeyHash, rawKey)) {
      const value = { userId: row.userId, deviceId: row.id };
      cache.set(cacheKey, value);
      return value;
    }
  }
  return null;
}

/**
 * Build a `requireUser` middleware bound to the supplied DB + cache. The
 * production export `requireUser` is `requireUserWith({})` — the same
 * factory, defaults applied.
 */
export function requireUserWith(
  options: RequireUserOptions = {},
): MiddlewareHandler<{ Variables: AuthedVars }> {
  const cache = options.cache ?? defaultCache;
  let dbRef: DbClient | null = options.db ?? null;

  // Lazy DB resolution: when no override is supplied, defer `getDb()` to
  // first request so test imports that do `createApp()` without touching
  // the singleton DB never trigger PGLite boot.
  const resolveDb = (): DbClient => {
    if (dbRef) return dbRef;
    dbRef = getDb().db;
    return dbRef;
  };

  return async (c, next) => {
    const rawKey = readBearerDeviceKey(c);
    if (rawKey) {
      const resolved = await resolveDeviceKey(resolveDb(), cache, rawKey);
      if (!resolved) return c.json({ ok: false, error: "unauthorized" }, 401);
      c.set("userId", resolved.userId);
      c.set("deviceId", resolved.deviceId);
      await next();
      return;
    }

    const devUserId = readDevUserIdIfAllowed(c);
    if (devUserId) {
      c.set("userId", devUserId);
      c.set("deviceId", null);
      await next();
      return;
    }

    return c.json({ ok: false, error: "unauthorized" }, 401);
  };
}

/**
 * Production middleware. Routes that need an authenticated user mount this
 * via `app.use("*", requireUser)`.
 */
export const requireUser: MiddlewareHandler<{ Variables: AuthedVars }> = requireUserWith();

/**
 * Boot-time announce of the dev-headers gate. The server entrypoint calls
 * this once so a misconfigured production image cannot silently accept
 * the bypass.
 */
export function warnIfDevHeadersOn(): void {
  if (env.ALLOW_DEV_HEADERS === "true") {
    if (env.NODE_ENV === "production") {
      // Defence in depth: even if the env loader allowed this through, the
      // middleware ignores the header in production. Still scream so ops
      // notice the misconfiguration.
      log.error(
        { NODE_ENV: env.NODE_ENV },
        "ALLOW_DEV_HEADERS=true in production — header is IGNORED but the config is wrong",
      );
    } else {
      log.warn(
        { NODE_ENV: env.NODE_ENV },
        "ALLOW_DEV_HEADERS=true — x-dev-user-id will bypass real auth. DO NOT enable in production.",
      );
    }
  }
}

/** Exported so the device CRUD paths can punch the cache when a device is unpaired. */
export { DeviceKeyCache };
