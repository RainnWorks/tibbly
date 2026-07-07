/**
 * Integration tests: pairing router's rate-limit gate.
 *
 * Confirms the three pairing endpoints are 429-able under burst, can be
 * disabled per-route or globally for tests, and produce sane diagnostic
 * headers.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import { type DbHandle, createDb } from "../src/db/client";
import { applyMigrations } from "../src/db/client";

let handle: DbHandle;

beforeEach(async () => {
  handle = createDb("memory://");
  await applyMigrations(handle);
});

afterEach(async () => {
  await handle.close();
});

function reqRequest(body: { deviceKey: string; playerName?: string }): Request {
  return new Request("http://localhost/v1/pairing/request", {
    method: "POST",
    headers: { "content-type": "application/json", "x-real-ip": "203.0.113.10" },
    body: JSON.stringify(body),
  });
}

function reqClaim(code: string): Request {
  return new Request("http://localhost/v1/pairing/claim", {
    method: "POST",
    headers: { "content-type": "application/json", "x-real-ip": "203.0.113.11" },
    body: JSON.stringify({ code, email: "claimer@example.com" }),
  });
}

describe("pairing rate limits", () => {
  it("rejects the 6th burst /request from one IP within the same instant", async () => {
    let now = 1_000;
    const app = createApp({
      pairing: {
        db: handle.db,
        rateLimits: { request: { capacity: 5, refillPerSecond: 0.1 } },
        rateLimitClock: () => now,
      },
    });

    for (let i = 0; i < 5; i++) {
      const ok = await app.fetch(reqRequest({ deviceKey: `dev_${i}_long_enough_string` }));
      expect(ok.status).toBe(200);
      expect(ok.headers.get("X-RateLimit-Limit")).toBe("5");
    }

    const blocked = await app.fetch(reqRequest({ deviceKey: "dev_burst_one_too_many" }));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("Retry-After")).not.toBeNull();
  });

  it("refills /claim tokens after the configured wait", async () => {
    let now = 1_000;
    const app = createApp({
      pairing: {
        db: handle.db,
        rateLimits: { claim: { capacity: 2, refillPerSecond: 1 } },
        rateLimitClock: () => now,
      },
    });

    // Drain the bucket on invalid codes; we only care about the gate.
    const drain1 = await app.fetch(reqClaim("AAAAAA"));
    expect(drain1.status).toBe(404);
    const drain2 = await app.fetch(reqClaim("BBBBBB"));
    expect(drain2.status).toBe(404);
    const blocked = await app.fetch(reqClaim("CCCCCC"));
    expect(blocked.status).toBe(429);

    // Advance past one full refill — should let one more through.
    now += 1_100;
    const unblocked = await app.fetch(reqClaim("DDDDDD"));
    expect(unblocked.status).toBe(404);
  });

  it("globally disables rate limits when rateLimits === 'off'", async () => {
    const app = createApp({
      pairing: { db: handle.db, rateLimits: "off" },
    });

    // 20 requests in a single tick — would 429 under any sane default.
    for (let i = 0; i < 20; i++) {
      const res = await app.fetch(reqRequest({ deviceKey: `dev_off_${i}_padding_padding` }));
      expect(res.status).toBe(200);
      expect(res.headers.get("X-RateLimit-Limit")).toBeNull();
    }
  });

  it("disables a single route via 'off' while keeping the others gated", async () => {
    let now = 1_000;
    const app = createApp({
      pairing: {
        db: handle.db,
        rateLimits: {
          request: "off",
          claim: { capacity: 1, refillPerSecond: 0.001 },
        },
        rateLimitClock: () => now,
      },
    });

    // /request is open.
    for (let i = 0; i < 10; i++) {
      const res = await app.fetch(reqRequest({ deviceKey: `dev_x_${i}_padding_padding_pad` }));
      expect(res.status).toBe(200);
    }

    // /claim is single-bucket.
    const first = await app.fetch(reqClaim("AAAAAA"));
    expect(first.status).toBe(404);
    const second = await app.fetch(reqClaim("BBBBBB"));
    expect(second.status).toBe(429);
  });

  it("applies sensible defaults when no rateLimits option is passed", async () => {
    let now = 1_000;
    const app = createApp({
      pairing: { db: handle.db, rateLimitClock: () => now },
    });

    // Defaults: request capacity 5. The 6th in the same instant 429s.
    for (let i = 0; i < 5; i++) {
      const ok = await app.fetch(reqRequest({ deviceKey: `dev_default_${i}_padding_padd` }));
      expect(ok.status).toBe(200);
    }
    const blocked = await app.fetch(reqRequest({ deviceKey: "dev_default_overflow_pad" }));
    expect(blocked.status).toBe(429);
  });

  it("counts successful /claim requests against the bucket too", async () => {
    // Successful claims should also drain a token — a real user only
    // claims once per pair so this is fine, and it stops a leak where
    // valid codes don't count toward the limit. This test pins that.
    let now = 1_000;
    const app = createApp({
      pairing: {
        db: handle.db,
        rateLimits: { claim: { capacity: 2, refillPerSecond: 1 } },
        rateLimitClock: () => now,
      },
    });

    // Mint a real code so /claim has something to find.
    const minted = await app.fetch(reqRequest({ deviceKey: "dev_real_pad_for_request_ok" }));
    const { code } = (await minted.json()) as { code: string };

    // First claim succeeds, second claim is the replay (410), third
    // claim hits the bucket floor and 429s — proving both ok and gone
    // responses drain.
    const ok = await app.fetch(reqClaim(code));
    expect(ok.status).toBe(201);
    const replay = await app.fetch(reqClaim(code));
    expect(replay.status).toBe(410);
    const blocked = await app.fetch(reqClaim("ZZZZZZ"));
    expect(blocked.status).toBe(429);
  });
});
