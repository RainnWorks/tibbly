/**
 * Unit tests for the token-bucket rate-limit middleware.
 *
 * Drives the clock manually so refill behaviour is deterministic and
 * fast — no `setTimeout`, no fragile wall-clock assertions.
 */
import { describe, expect, it } from "bun:test";
import { Hono } from "hono";

import {
  createMemoryStorage,
  defaultKeyOf,
  rateLimit,
} from "../../src/middleware/rateLimit";

function makeApp(opts: {
  capacity: number;
  refillPerSecond: number;
  clock: () => number;
  keyOf?: (c: any) => string;
  storage?: ReturnType<typeof createMemoryStorage>;
}): Hono {
  const app = new Hono();
  app.use(
    "/*",
    rateLimit({
      capacity: opts.capacity,
      refillPerSecond: opts.refillPerSecond,
      clock: opts.clock,
      keyOf: opts.keyOf,
      storage: opts.storage,
      name: "test",
    }),
  );
  app.get("/", (c) => c.json({ ok: true }));
  return app;
}

function get(app: Hono, key = "1.2.3.4"): Promise<Response> {
  return app.fetch(new Request("http://localhost/", { headers: { "x-real-ip": key } }));
}

describe("rateLimit middleware", () => {
  it("allows requests up to capacity in one burst", async () => {
    let now = 1_000;
    const app = makeApp({ capacity: 3, refillPerSecond: 1, clock: () => now });

    for (let i = 0; i < 3; i++) {
      const res = await get(app);
      expect(res.status).toBe(200);
      expect(res.headers.get("X-RateLimit-Limit")).toBe("3");
    }
  });

  it("returns 429 with Retry-After when capacity is exhausted", async () => {
    let now = 1_000;
    const app = makeApp({ capacity: 2, refillPerSecond: 1, clock: () => now });

    await get(app);
    await get(app);
    const blocked = await get(app);

    expect(blocked.status).toBe(429);
    const retry = blocked.headers.get("Retry-After");
    expect(retry).not.toBeNull();
    expect(Number(retry)).toBeGreaterThanOrEqual(1);
    expect(blocked.headers.get("X-RateLimit-Remaining")).toBe("0");

    const body = (await blocked.json()) as { ok: boolean; error: string; retryAfterSeconds: number };
    expect(body).toMatchObject({ ok: false, error: "rate_limited" });
    expect(body.retryAfterSeconds).toBeGreaterThanOrEqual(1);
  });

  it("refills tokens over time at refillPerSecond", async () => {
    let now = 1_000;
    const app = makeApp({ capacity: 2, refillPerSecond: 1, clock: () => now });

    await get(app);
    await get(app);
    expect((await get(app)).status).toBe(429);

    // Advance just under one second — still blocked.
    now += 900;
    expect((await get(app)).status).toBe(429);

    // Cross the second boundary — one token refilled, one request allowed.
    now += 200;
    expect((await get(app)).status).toBe(200);
    // Next one drains it again.
    expect((await get(app)).status).toBe(429);
  });

  it("uses independent buckets per key", async () => {
    let now = 1_000;
    const app = makeApp({ capacity: 1, refillPerSecond: 0.1, clock: () => now });

    expect((await get(app, "1.1.1.1")).status).toBe(200);
    // Same key → blocked.
    expect((await get(app, "1.1.1.1")).status).toBe(429);
    // Different key → fresh bucket.
    expect((await get(app, "2.2.2.2")).status).toBe(200);
  });

  it("isolates buckets between mounts even when sharing storage", async () => {
    let now = 1_000;
    const storage = createMemoryStorage();
    const a = new Hono();
    a.use("/*", rateLimit({ capacity: 1, refillPerSecond: 0.1, clock: () => now, storage, name: "A" }));
    a.get("/", (c) => c.json({ ok: true }));
    const b = new Hono();
    b.use("/*", rateLimit({ capacity: 1, refillPerSecond: 0.1, clock: () => now, storage, name: "B" }));
    b.get("/", (c) => c.json({ ok: true }));

    // A drains.
    expect((await a.fetch(new Request("http://h/", { headers: { "x-real-ip": "1" } }))).status).toBe(200);
    expect((await a.fetch(new Request("http://h/", { headers: { "x-real-ip": "1" } }))).status).toBe(429);
    // B with the same key is independent.
    expect((await b.fetch(new Request("http://h/", { headers: { "x-real-ip": "1" } }))).status).toBe(200);
  });

  it("accepts a custom keyOf", async () => {
    let now = 1_000;
    const app = new Hono();
    app.use(
      "/*",
      rateLimit({
        capacity: 1,
        refillPerSecond: 0.1,
        clock: () => now,
        // Bucket per route, not per IP.
        keyOf: (c) => c.req.path,
        name: "route",
      }),
    );
    app.get("/a", (c) => c.json({ ok: true }));
    app.get("/b", (c) => c.json({ ok: true }));

    expect((await app.fetch(new Request("http://h/a"))).status).toBe(200);
    expect((await app.fetch(new Request("http://h/a"))).status).toBe(429);
    expect((await app.fetch(new Request("http://h/b"))).status).toBe(200);
  });

  it("throws on invalid configuration", () => {
    expect(() => rateLimit({ capacity: 0, refillPerSecond: 1 })).toThrow();
    expect(() => rateLimit({ capacity: 1, refillPerSecond: 0 })).toThrow();
  });

  describe("defaultKeyOf", () => {
    const makeCtx = (headers: Record<string, string>): any => ({
      req: { header: (h: string) => headers[h.toLowerCase()] },
    });

    it("prefers cf-connecting-ip", () => {
      expect(
        defaultKeyOf(
          makeCtx({
            "cf-connecting-ip": "203.0.113.4",
            "x-forwarded-for": "10.0.0.1",
            "x-real-ip": "192.168.0.1",
          }),
        ),
      ).toBe("ip:203.0.113.4");
    });

    it("falls back to x-forwarded-for first hop", () => {
      expect(
        defaultKeyOf(
          makeCtx({ "x-forwarded-for": "203.0.113.7, 10.0.0.1, 192.168.0.1" }),
        ),
      ).toBe("ip:203.0.113.7");
    });

    it("falls back to x-real-ip", () => {
      expect(defaultKeyOf(makeCtx({ "x-real-ip": "203.0.113.9" }))).toBe("ip:203.0.113.9");
    });

    it("returns ip:unknown when no header is present", () => {
      expect(defaultKeyOf(makeCtx({}))).toBe("ip:unknown");
    });
  });
});
