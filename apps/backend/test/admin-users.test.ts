/**
 * Contract for /admin/users/* (ops console).
 *
 * Covers:
 *   - auth gate (mirrors admin-usage.test.ts pattern)
 *   - GET /admin/users: empty list, filter by status, search by email
 *   - GET /admin/users/:id: full payload, 404 path
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import {
  chats,
  devices,
  messages,
  osrsAccounts,
  subscriptions,
  tokenBalances,
  users,
} from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
const ADMIN = "tom@rowm.co";

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function app() {
  return createApp({
    adminUsers: { db: handle.db, adminEmails: [ADMIN] },
  });
}

describe("/admin/users auth gate", () => {
  it("401s without the admin header", async () => {
    const res = await app().fetch(new Request("http://localhost/admin/users"));
    expect(res.status).toBe(401);
  });

  it("401s when email is not in the allow-list", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/users", {
        headers: { "x-admin-email": "evil@example.com" },
      }),
    );
    expect(res.status).toBe(401);
  });
});

describe("GET /admin/users", () => {
  it("returns empty rows + zero total on empty DB", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/users", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    const body = (await res.json()) as { rows: unknown[]; total: number };
    expect(res.status).toBe(200);
    expect(body.rows).toHaveLength(0);
    expect(body.total).toBe(0);
  });

  it("lists users with balance + tier joined", async () => {
    await handle.db.insert(users).values([
      { id: "u1", email: "alice@example.com", status: "active" },
      { id: "u2", email: "bob@example.com", status: "banned" },
    ]);
    await handle.db.insert(tokenBalances).values([
      { userId: "u1", balanceTokens: 5000 },
    ]);
    await handle.db.insert(subscriptions).values([
      {
        id: "sub1",
        userId: "u1",
        stripeSubscriptionId: "sub_stripe_1",
        tier: "pro",
        status: "active",
        monthlyQuotaTokens: 1_000_000,
        currentPeriodStart: new Date(),
        currentPeriodEnd: new Date(Date.now() + 30 * 86_400_000),
      },
    ]);

    const res = await app().fetch(
      new Request("http://localhost/admin/users", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    const body = (await res.json()) as {
      rows: Array<{
        id: string;
        email: string;
        tier: string;
        balanceTokens: number;
        status: string;
      }>;
      total: number;
    };
    expect(res.status).toBe(200);
    expect(body.total).toBe(2);
    const alice = body.rows.find((r) => r.id === "u1");
    const bob = body.rows.find((r) => r.id === "u2");
    expect(alice?.tier).toBe("pro");
    expect(alice?.balanceTokens).toBe(5000);
    expect(bob?.tier).toBe("free");
    expect(bob?.balanceTokens).toBe(0);
  });

  it("filters by status=banned", async () => {
    await handle.db.insert(users).values([
      { id: "u1", email: "a@example.com", status: "active" },
      { id: "u2", email: "b@example.com", status: "banned" },
    ]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users?status=banned", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    const body = (await res.json()) as { rows: Array<{ id: string }>; total: number };
    expect(body.rows).toHaveLength(1);
    expect(body.rows[0]!.id).toBe("u2");
  });

  it("searches by email substring", async () => {
    await handle.db.insert(users).values([
      { id: "u1", email: "alice@example.com", status: "active" },
      { id: "u2", email: "bob@other.com", status: "active" },
    ]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users?q=other", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    const body = (await res.json()) as { rows: Array<{ id: string }> };
    expect(body.rows).toHaveLength(1);
    expect(body.rows[0]!.id).toBe("u2");
  });
});

describe("GET /admin/users/:id", () => {
  it("404s on unknown id", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/users/missing", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    expect(res.status).toBe(404);
  });

  it("returns user + devices + osrs accounts + recent chats + balance", async () => {
    await handle.db.insert(users).values([
      { id: "u1", email: "alice@example.com", status: "active" },
    ]);
    await handle.db.insert(devices).values([
      {
        id: "d1",
        userId: "u1",
        deviceKeyHash: "hash1",
        displayName: "Tom's laptop",
        playerName: "Zezima",
      },
    ]);
    await handle.db.insert(osrsAccounts).values([
      {
        id: "o1",
        userId: "u1",
        displayName: "Zezima",
        accountType: "main",
        status: "verified",
      },
    ]);
    const c1 = { id: "c1", userId: "u1", title: "Quest help" };
    await handle.db.insert(chats).values([c1]);
    await handle.db.insert(messages).values([
      {
        id: "m1",
        chatId: "c1",
        role: "user",
        content: "Hi",
        promptTokens: 10,
        completionTokens: 0,
      },
    ]);
    await handle.db.insert(tokenBalances).values([{ userId: "u1", balanceTokens: 2000 }]);

    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      user: { id: string; email: string };
      devices: Array<{ id: string; playerName: string }>;
      osrsAccounts: Array<{ id: string }>;
      recentChats: Array<{ id: string }>;
      balance: { balanceTokens: number };
    };
    expect(body.user.id).toBe("u1");
    expect(body.devices).toHaveLength(1);
    expect(body.devices[0]!.playerName).toBe("Zezima");
    expect(body.osrsAccounts).toHaveLength(1);
    expect(body.recentChats).toHaveLength(1);
    expect(body.balance.balanceTokens).toBe(2000);
  });
});
