/**
 * Token-bucket rate limit middleware for Hono.
 *
 * One bucket per (route, key) pair. Each request costs 1 token; tokens
 * refill at `refillPerSecond`. Empty bucket → 429 + `Retry-After`. The
 * default `keyOf` extracts the client IP from CF / XFF / X-Real-IP
 * headers and falls back to `"unknown"` — fine for dev where everything
 * shares a single bucket.
 *
 * Why a custom impl: we run on Bun + Hono with no Redis in this project
 * yet. An in-process token bucket is enough for the launch shape (single
 * backend instance) and lets us swap in a `RateLimitStorage` backed by
 * Redis later without touching the call sites.
 */
import type { Context, MiddlewareHandler } from "hono";

export interface BucketState {
  /** Float; can be fractional during refill. */
  tokens: number;
  /** Last refill timestamp in ms (monotonic, from `clock()`). */
  lastRefillMs: number;
}

export interface RateLimitStorage {
  get(key: string): BucketState | undefined;
  set(key: string, state: BucketState): void;
  /** Total entries — exposed so the GC can decide when to sweep. */
  size(): number;
  /** Drop entries whose bucket is full and idle longer than `idleMs`. */
  gc(idleMs: number, nowMs: number): number;
}

/**
 * Default in-memory storage with periodic GC. Entries that have refilled
 * to capacity AND been idle longer than `idleMs` are dropped, so the map
 * doesn't grow without bound on bursty traffic with many unique keys.
 */
export function createMemoryStorage(): RateLimitStorage {
  const map = new Map<string, BucketState>();
  return {
    get: (k) => map.get(k),
    set: (k, s) => {
      map.set(k, s);
    },
    size: () => map.size,
    gc(idleMs, nowMs) {
      let dropped = 0;
      for (const [k, v] of map) {
        if (nowMs - v.lastRefillMs > idleMs) {
          map.delete(k);
          dropped++;
        }
      }
      return dropped;
    },
  };
}

export interface RateLimitOptions {
  /** Bucket capacity AND burst limit. */
  capacity: number;
  /** Sustained rate. `capacity / refillPerSecond` = full-refill seconds. */
  refillPerSecond: number;
  /**
   * Maps a request to a bucket key. Default keys by client IP from
   * standard proxy headers. Return distinct strings to mean distinct
   * buckets — prefixing with the route name keeps mounts isolated.
   */
  keyOf?: (c: Context) => string;
  /** Bucket storage. Default: in-memory with idle GC. */
  storage?: RateLimitStorage;
  /** Clock — overridable for tests. Default: `Date.now`. */
  clock?: () => number;
  /**
   * GC: bucket idle threshold. Buckets at full capacity AND idle longer
   * than this get dropped. Default: 10× full-refill seconds.
   */
  gcIdleMs?: number;
  /**
   * GC trigger: run the sweep when `storage.size() >= gcSizeThreshold`.
   * Default: 1000.
   */
  gcSizeThreshold?: number;
  /**
   * Tag prepended to every key so two mounts sharing one storage stay
   * isolated. Set to the route name for readability in tests.
   */
  name?: string;
}

/** Default key: client IP from standard proxy headers, route-prefixed. */
export function defaultKeyOf(c: Context): string {
  const cf = c.req.header("cf-connecting-ip");
  if (cf && cf.length > 0) return `ip:${cf.trim()}`;
  const xff = c.req.header("x-forwarded-for");
  if (xff && xff.length > 0) return `ip:${xff.split(",")[0]!.trim()}`;
  const xri = c.req.header("x-real-ip");
  if (xri && xri.length > 0) return `ip:${xri.trim()}`;
  return "ip:unknown";
}

export function rateLimit(opts: RateLimitOptions): MiddlewareHandler {
  const {
    capacity,
    refillPerSecond,
    keyOf = defaultKeyOf,
    storage = createMemoryStorage(),
    clock = Date.now,
    gcSizeThreshold = 1000,
    name = "rl",
  } = opts;
  if (capacity <= 0) throw new Error("rateLimit: capacity must be > 0");
  if (refillPerSecond <= 0) throw new Error("rateLimit: refillPerSecond must be > 0");

  const fullRefillMs = (capacity / refillPerSecond) * 1000;
  const gcIdleMs = opts.gcIdleMs ?? Math.max(fullRefillMs * 10, 60_000);

  return async (c, next) => {
    const raw = keyOf(c);
    const key = `${name}|${raw}`;
    const now = clock();

    // Periodic GC — cheap O(n) sweep, only when the map crosses the
    // threshold. Storage decides what counts as idle vs active.
    if (storage.size() >= gcSizeThreshold) {
      storage.gc(gcIdleMs, now);
    }

    const prev = storage.get(key);
    let tokens: number;
    if (prev === undefined) {
      tokens = capacity - 1;
    } else {
      const elapsedMs = Math.max(0, now - prev.lastRefillMs);
      const refilled = Math.min(capacity, prev.tokens + (elapsedMs / 1000) * refillPerSecond);
      tokens = refilled - 1;
    }

    if (tokens < 0) {
      // Restore the bucket to its pre-decrement value (don't penalise the
      // already-empty bucket further) and compute when the next token
      // arrives. Floor 1s so polite clients still back off audibly.
      const carried = prev ? Math.min(capacity, prev.tokens + ((now - prev.lastRefillMs) / 1000) * refillPerSecond) : 0;
      storage.set(key, { tokens: carried, lastRefillMs: now });

      const msUntilNextToken = Math.max(0, ((1 - carried) / refillPerSecond) * 1000);
      const retryAfterSec = Math.max(1, Math.ceil(msUntilNextToken / 1000));

      c.header("Retry-After", String(retryAfterSec));
      c.header("X-RateLimit-Limit", String(capacity));
      c.header("X-RateLimit-Remaining", "0");
      c.header(
        "X-RateLimit-Reset",
        String(Math.ceil((now + msUntilNextToken) / 1000)),
      );
      return c.json(
        { ok: false, error: "rate_limited", retryAfterSeconds: retryAfterSec },
        429,
      );
    }

    storage.set(key, { tokens, lastRefillMs: now });
    c.header("X-RateLimit-Limit", String(capacity));
    c.header("X-RateLimit-Remaining", String(Math.floor(tokens)));
    c.header(
      "X-RateLimit-Reset",
      String(Math.ceil((now + ((capacity - tokens) / refillPerSecond) * 1000) / 1000)),
    );
    await next();
  };
}
