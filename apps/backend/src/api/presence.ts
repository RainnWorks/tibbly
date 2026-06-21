/**
 * REST presence endpoint (RAI-21).
 *
 *   GET /v1/presence → 200 { count, byRegion }
 *
 * Same shape as the WS `snapshot` frame. Useful for clients that don't want
 * to hold a socket open (curl, status pages, prerendered marketing
 * snippets). Cached for a few seconds in the response headers so a viral
 * spike doesn't hammer us.
 *
 * Privacy: the same constraints as the WS feed apply — no PII leaves this
 * route. See `src/ws/presence-tracker.ts` for the data path.
 */
import { Hono } from "hono";

import type { PresenceTracker } from "../ws/presence-tracker";

export interface CreatePresenceRouterOptions {
  tracker: PresenceTracker;
  /**
   * Max-age (seconds) advertised on the response. Default 5 — same cadence
   * as the WS broadcast, so REST clients see comparable freshness.
   */
  cacheMaxAgeSeconds?: number;
}

export function createPresenceRouter(options: CreatePresenceRouterOptions): Hono {
  const { tracker } = options;
  const maxAge = options.cacheMaxAgeSeconds ?? 5;

  const app = new Hono();

  app.get("/", (c) => {
    const snap = tracker.snapshot();
    c.header("Cache-Control", `public, max-age=${maxAge}`);
    // CORS — marketing + dashboard apps live on separate origins.
    c.header("Access-Control-Allow-Origin", "*");
    return c.json({
      count: snap.count,
      byRegion: snap.byRegion,
    });
  });

  return app;
}
