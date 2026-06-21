/**
 * D-8 plugin account-panel feed (RAI-39 auth migration).
 *
 * Verifies:
 *   - /v1/account/summary 401s without a real device-key Bearer.
 *   - 401 on the OLD `x-user-id` header (audit C1 closed).
 *   - With a real device key in the Bearer header, returns the masked DTO.
 *   - /v1/account/usage-proxy returns the tier-aware shape — and NEVER a
 *     `tokens`/`balance` field. The plugin's grep guard mirrors this rule.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import {
  devices,
  osrsAccounts,
  subscriptions,
  usageRecords,
} from "../src/db/schema";
import { bearerHeaders, seedDevice, type SeededDevice } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
let seeded: SeededDevice;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

async function seedUser(): Promise<SeededDevice> {
  seeded = await seedDevice(handle, {
    email: "panel@example.com",
    stripeCustomerId: "cus_PANEL",
  });
  return seeded;
}

describe("/v1/account/summary", () => {
  it("401s without an Authorization Bearer header", async () => {
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary"),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-user-id header is supplied (audit C1 attack closed)", async () => {
    const user = await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: { "x-user-id": user.userId },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the Bearer is an unknown raw key", async () => {
    await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: bearerHeaders("not-a-real-device-key-aaaaaaaaaaaaaaa"),
      }),
    );
    expect(res.status).toBe(401);
  });

  it("returns null subscription + empty arrays for a freshly minted user", async () => {
    const user = await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.tier).toBeNull();
    expect(body.subscriptionStatus).toBeNull();
    expect(body.renewsAt).toBeNull();
    expect(body.pairedOsrsAccounts).toEqual([]);
    // The seeded user has the bound device; surface it.
    expect((body.pairedDevices as unknown[]).length).toBe(1);
  });

  it("surfaces tier + paired OSRS accounts + paired devices", async () => {
    const user = await seedUser();

    const periodEnd = new Date("2026-07-14T00:00:00Z");
    await handle.db.insert(subscriptions).values({
      userId: user.userId,
      stripeSubscriptionId: "sub_PANEL",
      tier: "pro",
      status: "active",
      monthlyQuotaTokens: 500_000,
      currentPeriodStart: new Date("2026-06-14T00:00:00Z"),
      currentPeriodEnd: periodEnd,
    });

    await handle.db.insert(osrsAccounts).values([
      { id: "acct_main_xxxxxxxxxx", userId: user.userId, displayName: "Zezima", accountType: "main" },
      { id: "acct_iron_xxxxxxxxxx", userId: user.userId, displayName: "B0aty", accountType: "ironman" },
    ]);

    // Add an extra device alongside the seeded one.
    await handle.db.insert(devices).values({
      id: "dev_lap_xxxxxxxxxx",
      userId: user.userId,
      deviceKeyHash: "hash_laptop",
      displayName: "Tom's MacBook",
    });

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: {
          ...bearerHeaders(user.rawDeviceKey),
          "x-current-player": "zezima",
          "x-device-key": "hash_laptop",
        },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      tier: string | null;
      subscriptionStatus: string | null;
      renewsAt: string | null;
      pairedOsrsAccounts: Array<{ displayName: string; isCurrent: boolean }>;
      pairedDevices: Array<{ displayName: string | null; isCurrent: boolean }>;
    };

    expect(body.tier).toBe("pro");
    expect(body.subscriptionStatus).toBe("active");
    expect(body.renewsAt).toBe(periodEnd.toISOString());

    const zezima = body.pairedOsrsAccounts.find((a) => a.displayName === "Zezima");
    expect(zezima?.isCurrent).toBe(true);
    const b0aty = body.pairedOsrsAccounts.find((a) => a.displayName === "B0aty");
    expect(b0aty?.isCurrent).toBe(false);

    const laptop = body.pairedDevices.find((d) => d.displayName === "Tom's MacBook");
    expect(laptop?.isCurrent).toBe(true);
  });

  it("never leaks raw token / Stripe / device-key fields", async () => {
    const user = await seedUser();
    await handle.db.insert(devices).values({
      id: "dev_secret_xxxxxxxx",
      userId: user.userId,
      deviceKeyHash: "hash_super_secret",
      displayName: "secret-device",
    });
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const text = await res.text();
    expect(text).not.toContain("balance_tokens");
    expect(text).not.toContain("balanceTokens");
    expect(text).not.toContain("stripe_customer_id");
    expect(text).not.toContain("stripeCustomerId");
    expect(text).not.toContain("cus_PANEL");
    expect(text).not.toContain("hash_super_secret");
    expect(text).not.toContain("deviceKeyHash");
    expect(text).not.toContain("panel@example.com");
  });
});

describe("/v1/account/usage-proxy", () => {
  it("401s without an Authorization Bearer header", async () => {
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy"),
    );
    expect(res.status).toBe(401);
  });

  it("returns free-tier 'messages-left' shape for a freshly minted user", async () => {
    const user = await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("messages-left");
    expect(body.messagesUsedToday).toBe(0);
    expect(body.messagesPerDay).toBe(30);
  });

  it("counts only today's usage records, not older ones", async () => {
    const user = await seedUser();
    const now = new Date();
    const yesterday = new Date(now.getTime() - 36 * 60 * 60 * 1000);

    await handle.db.insert(usageRecords).values([
      { userId: user.userId, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: now },
      { userId: user.userId, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: now },
      { userId: user.userId, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: yesterday },
    ]);

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const body = (await res.json()) as { form: string; messagesUsedToday: number };
    expect(body.form).toBe("messages-left");
    expect(body.messagesUsedToday).toBe(2);
  });

  it("returns 'subscription-active' for Pro tier", async () => {
    const user = await seedUser();
    const periodEnd = new Date("2026-07-14T00:00:00Z");
    await handle.db.insert(subscriptions).values({
      userId: user.userId,
      stripeSubscriptionId: "sub_PRO",
      tier: "pro",
      status: "active",
      monthlyQuotaTokens: 500_000,
      currentPeriodStart: new Date("2026-06-14T00:00:00Z"),
      currentPeriodEnd: periodEnd,
    });

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("subscription-active");
    expect(body.renewsAt).toBe(periodEnd.toISOString());
    expect(body.messagesUsedToday).toBeUndefined();
    expect(body.messagesPerDay).toBeUndefined();
  });

  it("returns 'unlimited' for Iron tier", async () => {
    const user = await seedUser();
    await handle.db.insert(subscriptions).values({
      userId: user.userId,
      stripeSubscriptionId: "sub_IRON",
      tier: "iron",
      status: "active",
      monthlyQuotaTokens: 2_000_000,
      currentPeriodStart: new Date("2026-06-14T00:00:00Z"),
      currentPeriodEnd: new Date("2026-07-14T00:00:00Z"),
    });

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("unlimited");
    expect(body.renewsAt).toBeUndefined();
    expect(body.messagesUsedToday).toBeUndefined();
  });

  it("falls back to 'messages-left' for canceled subscriptions", async () => {
    const user = await seedUser();
    await handle.db.insert(subscriptions).values({
      userId: user.userId,
      stripeSubscriptionId: "sub_DEAD",
      tier: "pro",
      status: "canceled",
      monthlyQuotaTokens: 0,
      currentPeriodStart: new Date("2026-05-14T00:00:00Z"),
      currentPeriodEnd: new Date("2026-06-14T00:00:00Z"),
    });
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("messages-left");
  });

  it("never leaks raw token fields", async () => {
    const user = await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: bearerHeaders(user.rawDeviceKey),
      }),
    );
    const text = await res.text();
    expect(text).not.toContain("balance_tokens");
    expect(text).not.toContain("balanceTokens");
    expect(text).not.toContain("promptTokens");
    expect(text).not.toContain("completionTokens");
    expect(text).not.toContain("costMicroUsd");
  });
});
