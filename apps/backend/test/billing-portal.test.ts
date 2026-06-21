/**
 * RAI-27 — /v1/billing/portal contract.
 *
 * Confirms:
 *   - 401 without a user header.
 *   - 404 when the user id has no row.
 *   - 409 when the user has no Stripe customer (paywall hasn't fired yet).
 *   - 200 with `{ url }` when the user has a customer; verifies the
 *     Stripe stub got called with the right `customer` arg + return_url.
 */
import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";

import { createApp } from "../src/app";
import type { StripeBillingPortalLike } from "../src/api/billing-portal";
import { users } from "../src/db/schema";
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
  it("401s without x-user-id", async () => {
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", { method: "POST" }),
    );
    expect(res.status).toBe(401);
  });

  it("404 when the user row is missing", async () => {
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: { "x-user-id": "missing-user-xxxxxx" },
      }),
    );
    expect(res.status).toBe(404);
  });

  it("409 when the user has no stripe_customer_id yet", async () => {
    await handle.db.insert(users).values({
      id: "user_no_stripe_xxxxxxx",
      email: "free@example.com",
    });
    const { stripe } = fakeStripe("https://stripe.test/portal/X");
    const app = createApp({ billing: { db: handle.db, stripe } });

    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: { "x-user-id": "user_no_stripe_xxxxxxx" },
      }),
    );
    expect(res.status).toBe(409);
  });

  it("200 with portal URL when user has a stripe_customer_id", async () => {
    await handle.db.insert(users).values({
      id: "user_paid_xxxxxxxxxxx",
      email: "paid@example.com",
      stripeCustomerId: "cus_PAID",
    });
    const { stripe, calls } = fakeStripe("https://stripe.test/portal/ABC");
    const app = createApp({
      billing: { db: handle.db, stripe, returnUrl: "https://app.test/billing" },
    });

    const res = await app.fetch(
      new Request("http://localhost/v1/billing/portal", {
        method: "POST",
        headers: { "x-user-id": "user_paid_xxxxxxxxxxx" },
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
