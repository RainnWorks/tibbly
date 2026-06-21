/**
 * Presence tracker (RAI-21).
 *
 * Tracks how many plugin WS sessions are currently connected, sliced by
 * region. The plugin WS handler calls `connect(sessionId, ip)` on open and
 * `disconnect(sessionId)` on close. To smooth over brief reconnects (Wi-Fi
 * hiccups, world hops), disconnects respect a configurable grace period
 * before they reduce the count.
 *
 * Region inference happens via `geoip-lite`. The dependency is loaded
 * dynamically and best-effort — if the lookup table is missing or the
 * module isn't present (e.g. trimmed in some deploys), every session
 * collapses to the `GLOBAL` bucket.
 *
 * Privacy: this module is the ONLY place player IPs touch — they're hashed
 * down to a country code immediately and never persisted, never logged.
 * The presence broadcast carries only counts.
 */
import { log } from "../lib/log";

export interface PresenceSnapshot {
  /** Total number of active plugin sessions, including those in grace. */
  count: number;
  /** Per-region counts keyed by ISO 3166-1 alpha-2 country, or "GLOBAL". */
  byRegion: Record<string, number>;
}

export type PresenceListener = (snapshot: PresenceSnapshot) => void;

export interface PresenceTrackerOptions {
  /**
   * Milliseconds a disconnected session lingers before it's actually
   * removed. Defaults to 30 seconds — masks normal Wi-Fi blips and avoids
   * "0 online" flicker during a wave of reconnects. Tests pass a tiny
   * value to verify the grace path.
   */
  graceMs?: number;
  /**
   * Override the IP → region lookup. Production uses geoip-lite (loaded
   * lazily). Tests can inject a deterministic function.
   *
   * Return `null` to fall back to the `GLOBAL` bucket.
   */
  regionResolver?: (ip: string) => string | null;
  /** Override clock for deterministic tests. */
  now?: () => number;
}

interface SessionRecord {
  region: string;
  /** When set, this session is in the grace window and will purge at this ms. */
  graceUntil: number | null;
}

const DEFAULT_GRACE_MS = 30_000;
const GLOBAL_REGION = "GLOBAL";

/**
 * Try to load `geoip-lite` and return a resolver. If the package is missing
 * or its database isn't bundled, return `null` so the tracker collapses to
 * the GLOBAL bucket without crashing the server.
 */
function loadGeoipResolver(): ((ip: string) => string | null) | null {
  try {
    // Dynamic require — avoids hard linking the module type into call sites
    // and keeps it optional. Bun resolves CJS exports transparently.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const geoip = require("geoip-lite") as {
      lookup: (ip: string) => { country?: string } | null;
    };
    if (typeof geoip?.lookup !== "function") return null;
    return (ip: string) => {
      try {
        const result = geoip.lookup(ip);
        const country = result?.country;
        return country && country.length === 2 ? country : null;
      } catch {
        return null;
      }
    };
  } catch (err) {
    log.warn({ err: err instanceof Error ? err.message : String(err) }, "presence: geoip-lite unavailable, falling back to GLOBAL");
    return null;
  }
}

export class PresenceTracker {
  private readonly sessions = new Map<string, SessionRecord>();
  private readonly listeners = new Set<PresenceListener>();
  private readonly graceMs: number;
  private readonly now: () => number;
  private readonly regionResolver: (ip: string) => string | null;
  /**
   * Active grace timers keyed by sessionId. We hold them so a reconnect of
   * the same id can cancel the pending purge.
   */
  private readonly graceTimers = new Map<string, ReturnType<typeof setTimeout>>();

  constructor(options: PresenceTrackerOptions = {}) {
    this.graceMs = options.graceMs ?? DEFAULT_GRACE_MS;
    this.now = options.now ?? (() => Date.now());
    // If the caller didn't inject a resolver, try geoip-lite once. We keep
    // the result on the instance — the require() is the expensive part.
    this.regionResolver = options.regionResolver ?? (loadGeoipResolver() ?? (() => null));
  }

  /**
   * Register a new session. Safe to call repeatedly with the same id — a
   * second `connect` cancels any pending grace timer and overwrites region.
   */
  connect(sessionId: string, ip: string | null | undefined): void {
    // Cancel any pending grace removal for this id (reconnect path).
    const pending = this.graceTimers.get(sessionId);
    if (pending) {
      clearTimeout(pending);
      this.graceTimers.delete(sessionId);
    }
    const region = this.resolveRegion(ip);
    this.sessions.set(sessionId, { region, graceUntil: null });
    this.broadcast();
  }

  /**
   * Begin the grace window for a session. After `graceMs` it's removed and
   * a new snapshot is broadcast.
   */
  disconnect(sessionId: string): void {
    const record = this.sessions.get(sessionId);
    if (!record) return;
    // Mark grace; count still reflects the session until the timer fires.
    record.graceUntil = this.now() + this.graceMs;
    const timer = setTimeout(() => {
      this.sessions.delete(sessionId);
      this.graceTimers.delete(sessionId);
      this.broadcast();
    }, this.graceMs);
    this.graceTimers.set(sessionId, timer);
    // Don't keep the process alive just for a presence purge timer.
    if (typeof timer === "object" && timer && "unref" in timer) {
      try {
        (timer as { unref?: () => void }).unref?.();
      } catch {
        // ignore — unref is best-effort
      }
    }
  }

  /** Current snapshot — both REST and WS feed read through this. */
  snapshot(): PresenceSnapshot {
    const byRegion: Record<string, number> = {};
    let count = 0;
    for (const record of this.sessions.values()) {
      count += 1;
      byRegion[record.region] = (byRegion[record.region] ?? 0) + 1;
    }
    return { count, byRegion };
  }

  /**
   * Subscribe to snapshot changes. Returns an unsubscribe function.
   * The listener fires immediately with the current snapshot so callers
   * don't have to seed their own state.
   */
  subscribe(listener: PresenceListener): () => void {
    this.listeners.add(listener);
    try {
      listener(this.snapshot());
    } catch (err) {
      log.warn({ err }, "presence: listener threw on initial snapshot");
    }
    return () => {
      this.listeners.delete(listener);
    };
  }

  /** Total active session count (including grace). */
  size(): number {
    return this.sessions.size;
  }

  /** Test helper — wipe state and drop timers. */
  reset(): void {
    for (const t of this.graceTimers.values()) clearTimeout(t);
    this.graceTimers.clear();
    this.sessions.clear();
    this.listeners.clear();
  }

  private broadcast(): void {
    const snap = this.snapshot();
    for (const listener of this.listeners) {
      try {
        listener(snap);
      } catch (err) {
        log.warn({ err }, "presence: listener threw");
      }
    }
  }

  private resolveRegion(ip: string | null | undefined): string {
    if (!ip) return GLOBAL_REGION;
    const trimmed = ip.trim();
    if (!trimmed) return GLOBAL_REGION;
    // IPv6 loopback / IPv4 loopback / private ranges all skip the lookup —
    // geoip-lite would return null anyway, and we save a hash hit.
    if (isLoopbackOrPrivate(trimmed)) return GLOBAL_REGION;
    const resolved = this.regionResolver(trimmed);
    return resolved ?? GLOBAL_REGION;
  }
}

/**
 * Detect addresses that won't have a meaningful geoip mapping. Keeping this
 * minimal — we don't need full RFC 1918 coverage, just the common cases we
 * see in dev / behind proxies.
 */
function isLoopbackOrPrivate(ip: string): boolean {
  if (ip === "::1" || ip === "127.0.0.1") return true;
  if (ip.startsWith("10.")) return true;
  if (ip.startsWith("192.168.")) return true;
  if (ip.startsWith("172.")) {
    const second = Number.parseInt(ip.split(".")[1] ?? "", 10);
    if (Number.isFinite(second) && second >= 16 && second <= 31) return true;
  }
  if (ip.startsWith("fc") || ip.startsWith("fd")) return true; // ULA
  if (ip.startsWith("fe80:")) return true; // link-local
  return false;
}

/** Process-wide tracker. Tests should prefer `new PresenceTracker()`. */
let defaultTracker: PresenceTracker | undefined;
export function getDefaultPresenceTracker(): PresenceTracker {
  if (!defaultTracker) defaultTracker = new PresenceTracker();
  return defaultTracker;
}

export function resetDefaultPresenceTracker(): void {
  defaultTracker?.reset();
  defaultTracker = undefined;
}
