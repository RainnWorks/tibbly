/**
 * Public presence WebSocket feed (RAI-21).
 *
 * Anonymous, world-readable. Pushes `{ count, byRegion }` every
 * `broadcastIntervalMs` (default 5s) to every subscriber, plus an immediate
 * snapshot on connect so the marketing site can paint without waiting a
 * tick.
 *
 * Privacy contract — anything that touches this feed must NEVER carry:
 *   - Player names
 *   - Device keys
 *   - User ids
 *   - Precise IPs / lat-long
 * Region is coarse country-level (or `GLOBAL` when geoip fails).
 *
 * Wire shape from server → client:
 *   { type: "snapshot", count: number, byRegion: Record<string, number> }
 *
 * The handler accepts no client → server messages today; future iterations
 * may add a region filter ("just push EU counts"). For now we ignore any
 * incoming frames so the feed stays a strict broadcast.
 */
import type { ServerWebSocket, WebSocketHandler } from "bun";

import { log } from "../lib/log";
import type { PresenceSnapshot, PresenceTracker } from "./presence-tracker";

export interface PresenceWsOptions {
  tracker: PresenceTracker;
  /**
   * How often to push a snapshot to subscribers. Default 5000ms — the
   * marketing site's design target. Lower in tests for fast assertions.
   */
  broadcastIntervalMs?: number;
}

interface PresenceSocketData {
  /** No per-socket state today; placeholder for future region filters. */
  subscribedAt: number;
}

const DEFAULT_INTERVAL_MS = 5_000;

/**
 * Build a Bun WS handler + a shared broadcast loop. The loop is started
 * lazily on the first subscriber and torn down when the last one leaves —
 * we don't burn a timer when no one's watching.
 */
export function presenceWsHandler(options: PresenceWsOptions): {
  websocket: WebSocketHandler<PresenceSocketData>;
  makeSocketData(): PresenceSocketData;
  /** Test helper — stop the broadcast loop immediately. */
  stop(): void;
} {
  const tracker = options.tracker;
  const intervalMs = options.broadcastIntervalMs ?? DEFAULT_INTERVAL_MS;
  const sockets = new Set<ServerWebSocket<PresenceSocketData>>();
  let timer: ReturnType<typeof setInterval> | null = null;
  let lastSerialized = "";

  function serialize(snap: PresenceSnapshot): string {
    return JSON.stringify({
      type: "snapshot",
      count: snap.count,
      byRegion: snap.byRegion,
    });
  }

  function broadcast(snap: PresenceSnapshot): void {
    const frame = serialize(snap);
    lastSerialized = frame;
    for (const ws of sockets) {
      if (ws.readyState !== 1) continue; // 1 === OPEN
      try {
        ws.send(frame);
      } catch (err) {
        log.warn({ err }, "presence-ws: send failed");
      }
    }
  }

  function ensureTimer(): void {
    if (timer) return;
    timer = setInterval(() => {
      broadcast(tracker.snapshot());
    }, intervalMs);
    if (typeof timer === "object" && timer && "unref" in timer) {
      try {
        (timer as { unref?: () => void }).unref?.();
      } catch {
        // ignore
      }
    }
  }

  function maybeStopTimer(): void {
    if (!timer) return;
    if (sockets.size > 0) return;
    clearInterval(timer);
    timer = null;
  }

  // Listen on the tracker so connect/disconnect events push a snapshot
  // out immediately — the 5s timer is a steady heartbeat, but anyone
  // watching should see real-time changes too.
  const unsubscribeFromTracker = tracker.subscribe((snap) => {
    // Avoid spamming identical frames when the snapshot didn't change.
    const frame = serialize(snap);
    if (frame === lastSerialized) return;
    broadcast(snap);
  });

  const websocket: WebSocketHandler<PresenceSocketData> = {
    open(ws): void {
      sockets.add(ws);
      ensureTimer();
      // Initial snapshot so the client doesn't wait up to `intervalMs`
      // for its first paint.
      const snap = tracker.snapshot();
      try {
        ws.send(serialize(snap));
      } catch (err) {
        log.warn({ err }, "presence-ws: initial send failed");
      }
    },

    message(_ws, _raw): void {
      // No client→server protocol yet. Silently drop.
    },

    close(ws): void {
      sockets.delete(ws);
      maybeStopTimer();
    },

    drain(_ws): void {
      // Bun backpressure hook — small JSON frames, nothing to do.
    },
  };

  return {
    websocket,
    makeSocketData(): PresenceSocketData {
      return { subscribedAt: Date.now() };
    },
    stop(): void {
      unsubscribeFromTracker();
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
      sockets.clear();
    },
  };
}
