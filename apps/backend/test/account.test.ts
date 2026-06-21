/**
 * D-8 plugin account-panel feed.
 *
 * Verifies:
 *   - /v1/account/summary 401s without auth, 404s for unknown users,
 *     surfaces tier/devices/osrs accounts, marks `isCurrent` from hints.
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
  users,
} from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

const USER_ID = "user_acctpanel_aaaaa";

async function seedUser(): Promise<void> {
  await handle.db.insert(users).values({
    id: USER_ID,
    email: "panel@example.com",
    stripeCustomerId: "cus_PANEL",
  });
}

describe("/v1/account/summary", () => {
  it("401s without x-user-id", async () => {
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary"),
    );
    expect(res.status).toBe(401);
  });

  it("404s for an unknown user", async () => {
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: { "x-user-id": "ghost-user-xxxxxx" },
      }),
    );
    expect(res.status).toBe(404);
  });

  it("returns null subscription + empty arrays for a freshly minted user", async () => {
    await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.tier).toBeNull();
    expect(body.subscriptionStatus).toBeNull();
    expect(body.renewsAt).toBeNull();
    expect(body.pairedOsrsAccounts).toEqual([]);
    expect(body.pairedDevices).toEqual([]);
  });

  it("surfaces tier + paired OSRS accounts + paired devices", async () => {
    await seedUser();

    const periodEnd = new Date("2026-07-14T00:00:00Z");
    await handle.db.insert(subscriptions).values({
      userId: USER_ID,
      stripeSubscriptionId: "sub_PANEL",
      tier: "pro",
      status: "active",
      monthlyQuotaTokens: 500_000,
      currentPeriodStart: new Date("2026-06-14T00:00:00Z"),
      currentPeriodEnd: periodEnd,
    });

    await handle.db.insert(osrsAccounts).values([
      { id: "acct_main_xxxxxxxxxx", userId: USER_ID, displayName: "Zezima", accountType: "main" },
      { id: "acct_iron_xxxxxxxxxx", userId: USER_ID, displayName: "B0aty", accountType: "ironman" },
    ]);

    await handle.db.insert(devices).values([
      {
        id: "dev_home_xxxxxxxxxx",
        userId: USER_ID,
        deviceKeyHash: "hash_home",
        displayName: "Tom's iMac",
      },
      {
        id: "dev_lap_xxxxxxxxxx",
        userId: USER_ID,
        deviceKeyHash: "hash_laptop",
        displayName: "Tom's MacBook",
      },
    ]);

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: {
          "x-user-id": USER_ID,
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
    const imac = body.pairedDevices.find((d) => d.displayName === "Tom's iMac");
    expect(imac?.isCurrent).toBe(false);
  });

  it("never leaks raw token / Stripe / device-key fields", async () => {
    await seedUser();
    await handle.db.insert(devices).values({
      id: "dev_secret_xxxxxxxx",
      userId: USER_ID,
      deviceKeyHash: "hash_super_secret",
      displayName: "secret-device",
    });
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/summary", {
        headers: { "x-user-id": USER_ID },
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
  it("401s without x-user-id", async () => {
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy"),
    );
    expect(res.status).toBe(401);
  });

  it("returns free-tier 'messages-left' shape for a freshly minted user", async () => {
    await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("messages-left");
    expect(body.messagesUsedToday).toBe(0);
    expect(body.messagesPerDay).toBe(30);
  });

  it("counts only today's usage records, not older ones", async () => {
    await seedUser();
    const now = new Date();
    const yesterday = new Date(now.getTime() - 36 * 60 * 60 * 1000);

    await handle.db.insert(usageRecords).values([
      { userId: USER_ID, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: now },
      { userId: USER_ID, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: now },
      { userId: USER_ID, model: "haiku-4.5", promptTokens: 1, completionTokens: 1, createdAt: yesterday },
    ]);

    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: { "x-user-id": USER_ID },
      }),
    );
    const body = (await res.json()) as { form: string; messagesUsedToday: number };
    expect(body.form).toBe("messages-left");
    expect(body.messagesUsedToday).toBe(2);
  });

  it("returns 'subscription-active' for Pro tier", async () => {
    await seedUser();
    const periodEnd = new Date("2026-07-14T00:00:00Z");
    await handle.db.insert(subscriptions).values({
      userId: USER_ID,
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
        headers: { "x-user-id": USER_ID },
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("subscription-active");
    expect(body.renewsAt).toBe(periodEnd.toISOString());
    expect(body.messagesUsedToday).toBeUndefined();
    expect(body.messagesPerDay).toBeUndefined();
  });

  it("returns 'unlimited' for Iron tier", async () => {
    await seedUser();
    await handle.db.insert(subscriptions).values({
      userId: USER_ID,
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
        headers: { "x-user-id": USER_ID },
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("unlimited");
    expect(body.renewsAt).toBeUndefined();
    expect(body.messagesUsedToday).toBeUndefined();
  });

  it("falls back to 'messages-left' for canceled subscriptions", async () => {
    await seedUser();
    await handle.db.insert(subscriptions).values({
      userId: USER_ID,
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
        headers: { "x-user-id": USER_ID },
      }),
    );
    const body = (await res.json()) as Record<string, unknown>;
    expect(body.form).toBe("messages-left");
  });

  it("never leaks raw token fields", async () => {
    await seedUser();
    const app = createApp({ account: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/account/usage-proxy", {
        headers: { "x-user-id": USER_ID },
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
