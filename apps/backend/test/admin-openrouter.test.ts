/**
 * Contract for /admin/openrouter/spend + /admin/openrouter/revenue (RAI-39).
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import { chats, messages, subscriptions, users } from "../src/db/schema";
import { OPS_SESSION_SECRET, opsSessionCookieHeader } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
const ADMIN = "tom@rowm.co";

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

describe("/admin/openrouter auth gate", () => {
  it("401s without any auth", async () => {
    const app = createApp({
      adminOpenRouter: { db: handle.db, adminEmails: [ADMIN], jwtSecret: OPS_SESSION_SECRET },
    });
    const res = await app.fetch(
      new Request("http://localhost/admin/openrouter/spend"),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-admin-email header is sent (audit C2 closed)", async () => {
    const app = createApp({
      adminOpenRouter: { db: handle.db, adminEmails: [ADMIN], jwtSecret: OPS_SESSION_SECRET },
    });
    const res = await app.fetch(
      new Request("http://localhost/admin/openrouter/spend", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    expect(res.status).toBe(401);
  });
});

describe("GET /admin/openrouter/spend", () => {
  it("rolls up per-model totals from messages", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    await handle.db.insert(chats).values([{ id: "c1", userId: "u1" }]);
    await handle.db.insert(messages).values([
      {
        id: "m1",
        chatId: "c1",
        role: "assistant",
        model: "anthropic/claude-haiku-4.5",
        promptTokens: 1000,
        completionTokens: 500,
      },
      {
        id: "m2",
        chatId: "c1",
        role: "assistant",
        model: "anthropic/claude-sonnet-4.6",
        promptTokens: 2000,
        completionTokens: 1000,
      },
    ]);

    const app = createApp({
      adminOpenRouter: { db: handle.db, adminEmails: [ADMIN], jwtSecret: OPS_SESSION_SECRET },
    });
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app.fetch(
      new Request("http://localhost/admin/openrouter/spend", { headers: cookie }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      windows: { month: { spendMicroUsd: number } };
      byModelMonth: Record<string, { spendMicroUsd: number; messageCount: number }>;
    };
    expect(body.windows.month.spendMicroUsd).toBe(24500);
    expect(body.byModelMonth["anthropic/claude-haiku-4.5"]?.spendMicroUsd).toBe(3500);
    expect(body.byModelMonth["anthropic/claude-sonnet-4.6"]?.spendMicroUsd).toBe(21000);
    expect(body.byModelMonth["anthropic/claude-haiku-4.5"]?.messageCount).toBe(1);
  });
});

describe("GET /admin/openrouter/revenue", () => {
  it("counts active subscriptions and computes MRR", async () => {
    await handle.db.insert(users).values([{ id: "u1" }, { id: "u2" }, { id: "u3" }]);
    const futureEnd = new Date(Date.now() + 30 * 86_400_000);
    await handle.db.insert(subscriptions).values([
      {
        id: "s1",
        userId: "u1",
        stripeSubscriptionId: "sub_1",
        tier: "hobbyist",
        status: "active",
        monthlyQuotaTokens: 100,
        currentPeriodStart: new Date(),
        currentPeriodEnd: futureEnd,
      },
      {
        id: "s2",
        userId: "u2",
        stripeSubscriptionId: "sub_2",
        tier: "pro",
        status: "active",
        monthlyQuotaTokens: 100,
        currentPeriodStart: new Date(),
        currentPeriodEnd: futureEnd,
      },
      {
        id: "s3",
        userId: "u3",
        stripeSubscriptionId: "sub_3",
        tier: "iron",
        status: "canceled",
        monthlyQuotaTokens: 100,
        currentPeriodStart: new Date(),
        currentPeriodEnd: new Date(Date.now() - 1000),
      },
    ]);

    const app = createApp({
      adminOpenRouter: {
        db: handle.db,
        adminEmails: [ADMIN],
        jwtSecret: OPS_SESSION_SECRET,
        tierPriceUsdCents: { hobbyist: 700, pro: 1900, iron: 4900 },
      },
    });
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app.fetch(
      new Request("http://localhost/admin/openrouter/revenue", { headers: cookie }),
    );
    const body = (await res.json()) as {
      mrrUsdCents: number;
      activeSubscriptions: number;
      tierCounts: Record<string, number>;
    };
    expect(body.activeSubscriptions).toBe(2);
    expect(body.tierCounts.hobbyist).toBe(1);
    expect(body.tierCounts.pro).toBe(1);
    expect(body.mrrUsdCents).toBe(2600);
  });
});
