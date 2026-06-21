/**
 * RAI-21 — presence tracker + WS + REST tests.
 *
 * Coverage:
 *   1. Tracker counts open sessions and slices by region.
 *   2. Disconnect respects the grace window — count stays high until grace
 *      elapses, then drops.
 *   3. WS subscribers receive an initial snapshot on connect and another
 *      after a tracked connect/disconnect.
 *   4. REST `/v1/presence` returns the same shape.
 *
 * The "simulate 2 plugin WS connections" scenario is exercised through the
 * shared tracker — the plugin WS handler is already tested in ws.test.ts;
 * here we focus on the tracker contract end-to-end against the public
 * presence surface.
 */
import { afterEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import { presenceWsHandler } from "../src/ws/presence";
import { PresenceTracker } from "../src/ws/presence-tracker";

// ─── tracker ──────────────────────────────────────────────────────────────

describe("PresenceTracker", () => {
  it("counts active sessions and slices by resolved region", () => {
    const tracker = new PresenceTracker({
      graceMs: 0,
      regionResolver: (ip) => {
        if (ip === "1.1.1.1") return "AU";
        if (ip === "2.2.2.2") return "US";
        return null;
      },
    });

    tracker.connect("s1", "1.1.1.1");
    tracker.connect("s2", "2.2.2.2");
    tracker.connect("s3", "3.3.3.3"); // no region → GLOBAL

    const snap = tracker.snapshot();
    expect(snap.count).toBe(3);
    expect(snap.byRegion).toEqual({ AU: 1, US: 1, GLOBAL: 1 });
  });

  it("falls back to GLOBAL when geoip returns null for every IP", () => {
    const tracker = new PresenceTracker({
      graceMs: 0,
      regionResolver: () => null,
    });
    tracker.connect("a", "8.8.8.8");
    tracker.connect("b", "9.9.9.9");
    const snap = tracker.snapshot();
    expect(snap.count).toBe(2);
    expect(snap.byRegion).toEqual({ GLOBAL: 2 });
  });

  it("treats loopback / RFC1918 IPs as GLOBAL without calling the resolver", () => {
    let calls = 0;
    const tracker = new PresenceTracker({
      graceMs: 0,
      regionResolver: () => {
        calls += 1;
        return "US";
      },
    });
    tracker.connect("a", "127.0.0.1");
    tracker.connect("b", "::1");
    tracker.connect("c", "10.0.0.5");
    tracker.connect("d", "192.168.1.10");
    tracker.connect("e", "172.20.0.1");
    expect(calls).toBe(0);
    expect(tracker.snapshot().byRegion).toEqual({ GLOBAL: 5 });
  });

  it("drops a session only after the grace window elapses", async () => {
    const tracker = new PresenceTracker({
      graceMs: 25, // tiny grace so the test is fast
      regionResolver: () => "EU",
    });
    tracker.connect("p1", "1.1.1.1");
    tracker.connect("p2", "1.1.1.1");
    expect(tracker.snapshot().count).toBe(2);

    tracker.disconnect("p1");
    // Still counted during grace.
    expect(tracker.snapshot().count).toBe(2);

    await new Promise((r) => setTimeout(r, 60));
    expect(tracker.snapshot().count).toBe(1);
    expect(tracker.snapshot().byRegion).toEqual({ EU: 1 });
  });

  it("reconnecting the same session id cancels a pending grace removal", async () => {
    const tracker = new PresenceTracker({
      graceMs: 25,
      regionResolver: () => "US",
    });
    tracker.connect("p1", "1.1.1.1");
    tracker.disconnect("p1");
    // Reconnect before grace fires.
    tracker.connect("p1", "1.1.1.1");
    await new Promise((r) => setTimeout(r, 60));
    expect(tracker.snapshot().count).toBe(1);
  });

  it("subscribers receive an initial snapshot and updates on connect", () => {
    const tracker = new PresenceTracker({ graceMs: 0, regionResolver: () => "US" });
    const seen: number[] = [];
    const unsub = tracker.subscribe((snap) => seen.push(snap.count));
    expect(seen).toEqual([0]);

    tracker.connect("a", "1.1.1.1");
    tracker.connect("b", "1.1.1.1");
    expect(seen).toEqual([0, 1, 2]);
    unsub();

    tracker.connect("c", "1.1.1.1");
    expect(seen).toEqual([0, 1, 2]); // unsubscribed
  });
});

// ─── REST ─────────────────────────────────────────────────────────────────

describe("GET /v1/presence", () => {
  it("returns the current snapshot in the documented shape", async () => {
    const tracker = new PresenceTracker({
      graceMs: 0,
      regionResolver: (ip) => (ip === "1.1.1.1" ? "EU" : "US"),
    });
    tracker.connect("s1", "1.1.1.1");
    tracker.connect("s2", "2.2.2.2");

    const app = createApp({ presence: { tracker } });
    const res = await app.fetch(new Request("http://localhost/v1/presence"));
    expect(res.status).toBe(200);
    const body = (await res.json()) as { count: number; byRegion: Record<string, number> };
    expect(body.count).toBe(2);
    expect(body.byRegion).toEqual({ EU: 1, US: 1 });
    expect(res.headers.get("cache-control")).toContain("max-age=5");
  });

  it("returns { count: 0, byRegion: {} } when no sessions are open", async () => {
    const tracker = new PresenceTracker({ graceMs: 0 });
    const app = createApp({ presence: { tracker } });
    const res = await app.fetch(new Request("http://localhost/v1/presence"));
    const body = (await res.json()) as { count: number; byRegion: Record<string, number> };
    expect(body.count).toBe(0);
    expect(body.byRegion).toEqual({});
  });
});

// ─── WS feed ──────────────────────────────────────────────────────────────

describe("presence WebSocket feed", () => {
  let activeServer: ReturnType<typeof Bun.serve> | undefined;
  let activeHandler: ReturnType<typeof presenceWsHandler> | undefined;

  afterEach(() => {
    activeHandler?.stop();
    activeServer?.stop(true);
    activeServer = undefined;
    activeHandler = undefined;
  });

  function bootServer(tracker: PresenceTracker, intervalMs = 50): number {
    const handler = presenceWsHandler({ tracker, broadcastIntervalMs: intervalMs });
    const server = Bun.serve({
      port: 0,
      fetch(req, srv) {
        if (new URL(req.url).pathname === "/ws/presence") {
          const ok = srv.upgrade(req, { data: handler.makeSocketData() });
          return ok ? undefined : new Response("upgrade failed", { status: 400 });
        }
        return new Response("not found", { status: 404 });
      },
      websocket: handler.websocket,
    });
    activeServer = server;
    activeHandler = handler;
    return server.port;
  }

  function connect(port: number): Promise<WebSocket> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(`ws://localhost:${port}/ws/presence`);
      ws.addEventListener("open", () => resolve(ws), { once: true });
      ws.addEventListener("error", (e) => reject(e), { once: true });
    });
  }

  async function readNext(ws: WebSocket, timeoutMs = 1000): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        ws.removeEventListener("message", onMsg);
        reject(new Error("timeout waiting for presence frame"));
      }, timeoutMs);
      function onMsg(ev: MessageEvent): void {
        clearTimeout(timer);
        ws.removeEventListener("message", onMsg);
        try {
          resolve(JSON.parse(ev.data as string));
        } catch (err) {
          reject(err);
        }
      }
      ws.addEventListener("message", onMsg);
    });
  }

  it("sends an immediate snapshot on connect", async () => {
    const tracker = new PresenceTracker({ graceMs: 0, regionResolver: () => "EU" });
    tracker.connect("plugin-1", "1.1.1.1");
    tracker.connect("plugin-2", "1.1.1.1");

    const port = bootServer(tracker);
    const ws = await connect(port);
    const first = (await readNext(ws)) as {
      type: string;
      count: number;
      byRegion: Record<string, number>;
    };
    expect(first.type).toBe("snapshot");
    expect(first.count).toBe(2);
    expect(first.byRegion).toEqual({ EU: 2 });
    ws.close();
  });

  it("pushes a new snapshot when a plugin session connects", async () => {
    const tracker = new PresenceTracker({ graceMs: 0, regionResolver: () => "US" });
    const port = bootServer(tracker);
    const ws = await connect(port);
    // Drain initial snapshot.
    await readNext(ws);
    // Trigger an update.
    tracker.connect("plugin-late", "9.9.9.9");
    const update = (await readNext(ws)) as {
      type: string;
      count: number;
      byRegion: Record<string, number>;
    };
    expect(update.count).toBe(1);
    expect(update.byRegion).toEqual({ US: 1 });
    ws.close();
  });

  it("drops counts after the grace window when a plugin disconnects (2 connect → 1 left after grace)", async () => {
    const tracker = new PresenceTracker({ graceMs: 30, regionResolver: () => "US" });
    const port = bootServer(tracker, 500); // slow steady timer; rely on event-driven pushes
    const ws = await connect(port);
    // Drain initial snapshot (count=0).
    const init = (await readNext(ws)) as { count: number };
    expect(init.count).toBe(0);

    // Two plugin sessions connect.
    tracker.connect("plugin-A", "1.1.1.1");
    const afterA = (await readNext(ws)) as { count: number };
    expect(afterA.count).toBe(1);
    tracker.connect("plugin-B", "2.2.2.2");
    const afterB = (await readNext(ws)) as { count: number };
    expect(afterB.count).toBe(2);

    // Disconnect one. Within grace the count stays at 2.
    tracker.disconnect("plugin-A");
    expect(tracker.snapshot().count).toBe(2);

    // Wait past the grace window — count drops to 1, and the WS receives
    // a fresh snapshot.
    const eventual = (await readNext(ws, 500)) as { count: number };
    expect(eventual.count).toBe(1);

    ws.close();
  });
});
