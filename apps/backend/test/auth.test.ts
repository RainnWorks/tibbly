/**
 * RAI-18 — pairing-code auth.
 *
 * Covers the full happy path:
 *   plugin POSTs /v1/pairing/request → 6-char code
 *   dashboard POSTs /v1/pairing/claim → userId + deviceId
 *   second claim with the same code → 410 (replay protection)
 *   WS auth handler (authenticateDeviceKey) verifies the bound device
 *
 * Plus edge cases:
 *   - bad input → 400
 *   - unknown code → 404
 *   - expired code → 410
 *   - redeem-link path with a stub verifier
 *   - argon2 hash + verify round-trip
 *   - sweepExpiredPairingCodes drops only unclaimed-and-stale rows
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import {
  authenticateDeviceKey,
  claimPairingCode,
  createPairingRequest,
  generatePairingCode,
  PAIRING_TTL_MS,
  sweepExpiredPairingCodes,
} from "../src/auth/pairing";
import { hashDeviceKey, verifyDeviceKey } from "../src/auth/device-key";
import { devices, pairingCodes, users } from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

const DEVICE_KEY = "test-device-key-0123456789abcdef-RAI18";
const PLAYER_NAME = "zezima";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});

afterEach(async () => {
  await handle.close();
});

describe("device-key hashing", () => {
  it("hashes + verifies round-trip", async () => {
    const hash = await hashDeviceKey(DEVICE_KEY);
    expect(hash.startsWith("$argon2id$")).toBe(true);
    expect(await verifyDeviceKey(hash, DEVICE_KEY)).toBe(true);
    expect(await verifyDeviceKey(hash, "wrong-key")).toBe(false);
  });

  it("rejects empty keys", async () => {
    await expect(hashDeviceKey("")).rejects.toThrow();
    expect(await verifyDeviceKey("$argon2id$bogus", "")).toBe(false);
    expect(await verifyDeviceKey("", "anything")).toBe(false);
  });

  it("verify returns false on malformed hash rather than throwing", async () => {
    expect(await verifyDeviceKey("not-a-hash", DEVICE_KEY)).toBe(false);
  });
});

describe("pairing code generator", () => {
  it("emits 6-char codes from the safe alphabet", () => {
    const re = /^[2-9ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/;
    for (let i = 0; i < 100; i++) {
      const code = generatePairingCode();
      expect(code).toHaveLength(6);
      expect(re.test(code)).toBe(true);
    }
  });

  it("does not emit ambiguous glyphs (0,O,1,I)", () => {
    for (let i = 0; i < 200; i++) {
      const code = generatePairingCode();
      expect(code).not.toMatch(/[01OI]/);
    }
  });
});

describe("pairing flow — direct unit", () => {
  it("request → claim → bind", async () => {
    const { code, expiresAt } = await createPairingRequest(handle.db, {
      deviceKey: DEVICE_KEY,
      playerName: PLAYER_NAME,
    });
    expect(code).toHaveLength(6);
    expect(expiresAt.getTime()).toBeGreaterThan(Date.now());

    const claim = await claimPairingCode(handle.db, {
      code,
      stripeCustomerId: "cus_TEST1",
    });
    expect(claim.userId).toBeTruthy();
    expect(claim.deviceId).toBeTruthy();
    expect(claim.userCreated).toBe(true);

    const [user] = await handle.db.select().from(users).where(eq(users.id, claim.userId));
    expect(user?.stripeCustomerId).toBe("cus_TEST1");

    const [device] = await handle.db.select().from(devices).where(eq(devices.id, claim.deviceId));
    expect(device?.userId).toBe(claim.userId);
    expect(device?.playerName).toBe(PLAYER_NAME);
    expect(await verifyDeviceKey(device!.deviceKeyHash, DEVICE_KEY)).toBe(true);
  });

  it("second claim returns PairingGoneError", async () => {
    const { code } = await createPairingRequest(handle.db, {
      deviceKey: DEVICE_KEY,
    });
    await claimPairingCode(handle.db, { code, stripeCustomerId: "cus_TEST2" });

    await expect(
      claimPairingCode(handle.db, { code, stripeCustomerId: "cus_TEST2" }),
    ).rejects.toThrow(/already_claimed/);
  });

  it("expired claim is gone", async () => {
    // Insert directly with an already-expired row so we don't have to time-travel.
    const past = new Date(Date.now() - 60_000);
    await createPairingRequest(
      handle.db,
      { deviceKey: DEVICE_KEY },
      new Date(past.getTime() - PAIRING_TTL_MS),
    );
    const [row] = await handle.db.select().from(pairingCodes);
    expect(row).toBeDefined();
    expect(row!.expiresAt.getTime()).toBeLessThan(Date.now());

    await expect(claimPairingCode(handle.db, { code: row!.code })).rejects.toThrow(/expired/);
  });

  it("reuses an existing user when the same stripeCustomerId claims twice", async () => {
    const first = await createPairingRequest(handle.db, { deviceKey: "key-A1234567890abcdef" });
    const claimA = await claimPairingCode(handle.db, {
      code: first.code,
      stripeCustomerId: "cus_DUPE",
    });

    const second = await createPairingRequest(handle.db, { deviceKey: "key-B1234567890abcdef" });
    const claimB = await claimPairingCode(handle.db, {
      code: second.code,
      stripeCustomerId: "cus_DUPE",
    });

    expect(claimB.userId).toBe(claimA.userId);
    expect(claimB.userCreated).toBe(false);
    expect(claimB.deviceId).not.toBe(claimA.deviceId);
  });

  it("authenticateDeviceKey finds the bound device after a claim", async () => {
    const { code } = await createPairingRequest(handle.db, { deviceKey: DEVICE_KEY });
    const claim = await claimPairingCode(handle.db, { code, stripeCustomerId: "cus_WS" });

    const session = await authenticateDeviceKey(handle.db, DEVICE_KEY);
    expect(session).not.toBeNull();
    expect(session!.userId).toBe(claim.userId);
    expect(session!.deviceId).toBe(claim.deviceId);

    expect(await authenticateDeviceKey(handle.db, "wrong-key-xxxxxxxxxxxxxxxxx")).toBeNull();
    expect(await authenticateDeviceKey(handle.db, "")).toBeNull();
  });

  it("sweepExpiredPairingCodes drops only unclaimed expired rows", async () => {
    // 1. Unclaimed and expired — should be swept.
    await createPairingRequest(
      handle.db,
      { deviceKey: "key-expired-aaaaaaaaaaaaaaaaa" },
      new Date(Date.now() - PAIRING_TTL_MS - 60_000),
    );
    // 2. Unclaimed but live — should stay.
    await createPairingRequest(handle.db, { deviceKey: "key-live-bbbbbbbbbbbbbbbbb" });
    // 3. Claimed (then expired implicitly because we wait). Should stay (auditable).
    const { code } = await createPairingRequest(handle.db, {
      deviceKey: "key-claimed-cccccccccccccccccc",
    });
    await claimPairingCode(handle.db, { code, stripeCustomerId: "cus_KEEP" });

    const removed = await sweepExpiredPairingCodes(handle.db);
    expect(removed).toBe(1);

    const remaining = await handle.db.select().from(pairingCodes);
    expect(remaining).toHaveLength(2);
  });
});

describe("/v1/pairing HTTP endpoints", () => {
  it("full happy path: request → claim → replay-protected → WS auth", async () => {
    const app = createApp({ pairing: { db: handle.db } });

    /* 1. Plugin requests a code. */
    const reqRes = await app.fetch(
      new Request("http://localhost/v1/pairing/request", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ deviceKey: DEVICE_KEY, playerName: PLAYER_NAME }),
      }),
    );
    expect(reqRes.status).toBe(200);
    const requested = (await reqRes.json()) as { code: string; expiresAt: string };
    expect(requested.code).toHaveLength(6);
    expect(new Date(requested.expiresAt).getTime()).toBeGreaterThan(Date.now());

    /* 2. Dashboard claims it. */
    const claimRes = await app.fetch(
      new Request("http://localhost/v1/pairing/claim", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          code: requested.code,
          stripeCustomerId: "cus_HAPPY",
        }),
      }),
    );
    expect(claimRes.status).toBe(201);
    const claim = (await claimRes.json()) as {
      userId: string;
      deviceId: string;
      userCreated: boolean;
    };
    expect(claim.userCreated).toBe(true);
    expect(claim.userId).toBeTruthy();
    expect(claim.deviceId).toBeTruthy();

    /* 3. Replay — same code, second time, 410 Gone. */
    const replayRes = await app.fetch(
      new Request("http://localhost/v1/pairing/claim", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          code: requested.code,
          stripeCustomerId: "cus_HAPPY",
        }),
      }),
    );
    expect(replayRes.status).toBe(410);
    const replayBody = (await replayRes.json()) as { error: string; reason: string };
    expect(replayBody.error).toBe("gone");
    expect(replayBody.reason).toBe("already_claimed");

    /* 4. WS auth shim — verify the deviceKey resolves to the same user/device. */
    const session = await authenticateDeviceKey(handle.db, DEVICE_KEY);
    expect(session).toEqual({ userId: claim.userId, deviceId: claim.deviceId });
  });

  it("rejects malformed request body with 400", async () => {
    const app = createApp({ pairing: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/request", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ deviceKey: "short" }),
      }),
    );
    expect(res.status).toBe(400);
  });

  it("rejects malformed claim code with 400", async () => {
    const app = createApp({ pairing: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/claim", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code: "0OI1XY" }), // forbidden alphabet
      }),
    );
    expect(res.status).toBe(400);
  });

  it("unknown code → 404", async () => {
    const app = createApp({ pairing: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/claim", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ code: "ABCDEF", stripeCustomerId: "cus_NA" }),
      }),
    );
    expect(res.status).toBe(404);
  });

  it("redeem-link route forwards to claim via the verifier shim", async () => {
    const app = createApp({
      pairing: {
        db: handle.db,
        verifyMagicLink: async (token) => {
          // Pretend the token is just `magic:<code>` for test purposes.
          const m = token.match(/^magic:(.+)$/);
          if (!m) return null;
          return { code: m[1]!, email: "buyer@example.com" };
        },
      },
    });

    const { code } = await createPairingRequest(handle.db, {
      deviceKey: "key-magic-link-xxxxxxxxxxxxxxxxx",
    });

    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/redeem-link", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          token: `magic:${code}`,
          stripeCustomerId: "cus_MAGIC",
        }),
      }),
    );
    expect(res.status).toBe(201);
    const body = (await res.json()) as { userId: string; userCreated: boolean };
    expect(body.userCreated).toBe(true);

    const [user] = await handle.db.select().from(users).where(eq(users.id, body.userId));
    expect(user?.email).toBe("buyer@example.com");
    expect(user?.stripeCustomerId).toBe("cus_MAGIC");
  });

  it("redeem-link returns 401 when the verifier rejects the token", async () => {
    const app = createApp({
      pairing: {
        db: handle.db,
        verifyMagicLink: async () => null,
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/redeem-link", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ token: "anything" }),
      }),
    );
    expect(res.status).toBe(401);
  });

  it("redeem-link returns 503 when no verifier is configured", async () => {
    const app = createApp({ pairing: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/pairing/redeem-link", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ token: "magic:ABCDEF" }),
      }),
    );
    expect(res.status).toBe(503);
  });
});
