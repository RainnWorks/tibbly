/**
 * RAI-27 — /v1/billing/portal contract (RAI-39 auth migration, plugs H2).
 *
 * Confirms:
 *   - 401 without an Authorization Bearer device key.
 *   - 401 when the OLD `x-user-id` header is sent (audit C1 closed).
 *   - 409 when the authed user has no Stripe customer.
 *   - 200 with `{ url }` when the authed user has a customer; verifies the
 *     Stripe stub got called with the right `customer` arg + return_url.
 */
import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import type { StripeBillingPortalLike } from "../src/api/billing-portal";
import { users } from "../src/db/schema";
import { bearerHeaders, seedDevice } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function fakeStripe(url: string): {
  stripe: StripeBillingPortalLike;
  calls: Array<{ customer: string; return_url?: string }>;
} {
  const calls: Array<{ customer: string; return_url?: string }> = [];
  const create = mock(async (params: { customer: string; return_url?: string }) => {
    calls.push(params);
    return { url };
  });
  return {
    stripe: { billingPortal: { sessions: { create } } },
    calls,
  };
}

describe("/v1/billing/portal", () => {
  it("401s without an Authorization Bearer", async () => {
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", { method: "POST" }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-user-id header is sent (audit C1+H2 attack closed)", async () => {
    const seeded = await seedDevice(handle, {
      email: "paid@example.com",
      stripeCustomerId: "cus_PAID",
    });
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: { "x-user-id": seeded.userId },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("409 when the authed user has no stripe_customer_id yet", async () => {
    const seeded = await seedDevice(handle, { email: "free@example.com" });
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });

    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: bearerHeaders(seeded.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(409);
  });

  it("200 with portal URL when user has a stripe_customer_id", async () => {
    const seeded = await seedDevice(handle, { email: "paid@example.com" });
    // Attach a Stripe customer to the seeded user.
    await handle.db
      .update(users)
      .set({ stripeCustomerId: "cus_PAID" })
      .where(eq(users.id, seeded.userId));

    const { stripe, calls } = fakeStripe("https://stripe.test/portal/ABC");
    const app = createApp({
      billing: { db: handle.db, stripe, returnUrl: "https://app.test/billing" },
    });

    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: bearerHeaders(seeded.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { url: string };
    expect(body.url).toBe("https://stripe.test/portal/ABC");

    expect(calls).toHaveLength(1);
    expect(calls[0]!.customer).toBe("cus_PAID");
    expect(calls[0]!.return_url).toBe("https://app.test/billing");
  });
});
