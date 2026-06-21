/**
 * RAI-27 — /v1/usage/summary contract (RAI-39 auth migration).
 *
 * Confirms:
 *   - 401 without an Authorization Bearer device key.
 *   - 401 when the OLD `x-user-id` header is supplied (audit C1 closed).
 *   - 200 returns balance + lastRenewal + dailyTokens shape.
 *   - dailyTokens groups by day and sums prompt + completion tokens.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import {
  subscriptions,
  tokenBalances,
  usageRecords,
} from "../src/db/schema";
import { bearerHeaders, seedDevice, type SeededDevice } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

async function seedUser(): Promise<SeededDevice> {
  return seedDevice(handle, {
    email: "usage@example.com",
    stripeCustomerId: "cus_USAGE",
  });
}

describe("/v1/usage/summary", () => {
  it("401s without auth", async () => {
    const app = createApp({ usage: { db: handle.db } });
    const res = await app.fetch(new Request("http://localhost/v1/usage/summary"));
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-user-id header is sent (audit C1 closed)", async () => {
    const user = await seedUser();
    const app = createApp({ usage: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/usage/summary", {
        headers: { "x-user-id": user.userId },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("returns zero balance + empty series for a freshly authed user with no records", async () => {
    const user = await seedUser();
    const app = createApp({ usage: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/usage/summary", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      balance: number;
      lastRenewal: string | null;
      dailyTokens: Array<{ date: string; tokens: number }>;
    };
    expect(body.balance).toBe(0);
    expect(body.lastRenewal).toBeNull();
    expect(body.dailyTokens).toEqual([]);
  });

  it("returns balance, last renewal, and per-day totals", async () => {
    const user = await seedUser();

    await handle.db.insert(tokenBalances).values({
      userId: user.userId,
      balanceTokens: 42_000,
    });

    const periodStart = new Date("2026-06-01T00:00:00Z");
    await handle.db.insert(subscriptions).values({
      userId: user.userId,
      stripeSubscriptionId: "sub_USAGE",
      tier: "pro",
      status: "active",
      monthlyQuotaTokens: 1_000_000,
      currentPeriodStart: periodStart,
      currentPeriodEnd: new Date("2026-07-01T00:00:00Z"),
    });

    const today = new Date("2026-06-20T12:00:00Z");
    const yesterday = new Date("2026-06-19T12:00:00Z");
    await handle.db.insert(usageRecords).values([
      {
        userId: user.userId,
        model: "haiku-4.5",
        promptTokens: 100,
        completionTokens: 50,
        createdAt: today,
      },
      {
        userId: user.userId,
        model: "haiku-4.5",
        promptTokens: 200,
        completionTokens: 75,
        createdAt: today,
      },
      {
        userId: user.userId,
        model: "sonnet-4.6",
        promptTokens: 500,
        completionTokens: 250,
        createdAt: yesterday,
      },
    ]);

    const app = createApp({ usage: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/usage/summary", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      balance: number;
      lastRenewal: string | null;
      dailyTokens: Array<{ date: string; tokens: number }>;
    };

    expect(body.balance).toBe(42_000);
    expect(body.lastRenewal).toBe(periodStart.toISOString());
    expect(Array.isArray(body.dailyTokens)).toBe(true);
  });

  it("groups same-day rows and sums prompt + completion tokens", async () => {
    const user = await seedUser();
    const now = new Date();
    const earlier = new Date(now.getTime() - 60_000);

    await handle.db.insert(usageRecords).values([
      {
        userId: user.userId,
        model: "haiku-4.5",
        promptTokens: 100,
        completionTokens: 50,
        createdAt: now,
      },
      {
        userId: user.userId,
        model: "haiku-4.5",
        promptTokens: 200,
        completionTokens: 75,
        createdAt: earlier,
      },
    ]);

    const app = createApp({ usage: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/usage/summary", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const body = (await res.json()) as {
      dailyTokens: Array<{ date: string; tokens: number }>;
    };

    expect(body.dailyTokens.length).toBe(1);
    expect(body.dailyTokens[0]!.tokens).toBe(425);
  });
});
