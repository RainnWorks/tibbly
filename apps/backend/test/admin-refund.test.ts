/**
 * Contract for POST /admin/users/:id/refund (RAI-39 auth migration).
 *
 * Uses a fake Stripe object that records the call. In dev without
 * STRIPE_SECRET_KEY the production wiring substitutes a `dev_stub`
 * Stripe; the response field `isDevStub` is the UI's loud signal.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import { events as eventsTable, users } from "../src/db/schema";
import { OPS_SESSION_SECRET, opsSessionCookieHeader } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";
import type { StripeAdminLike } from "../src/api/admin/users";

let handle: TestDbHandle;
const ADMIN = "tom@rowm.co";

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function makeFakeStripe() {
  const calls: Array<{ charge: string; amount?: number; reason?: string }> = [];
  const fake: StripeAdminLike = {
    refunds: {
      async create(params) {
        calls.push(params);
        return {
          id: "re_test_123",
          amount: params.amount ?? 4900,
          currency: "usd",
          status: "succeeded",
        };
      },
    },
    invoices: {
      async list() {
        return { data: [] };
      },
    },
  };
  return { fake, calls };
}

async function adminHeaders(): Promise<Record<string, string>> {
  return { ...(await opsSessionCookieHeader(ADMIN)), "content-type": "application/json" };
}

describe("POST /admin/users/:id/refund", () => {
  it("requires chargeId and reason", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const { fake } = makeFakeStripe();
    const app = createApp({
      adminUsers: {
        db: handle.db,
        adminEmails: [ADMIN],
        jwtSecret: OPS_SESSION_SECRET,
        stripe: fake,
      },
    });

    const res1 = await app.fetch(
      new Request("http://localhost/admin/users/u1/refund", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({}),
      }),
    );
    expect(res1.status).toBe(400);

    const res2 = await app.fetch(
      new Request("http://localhost/admin/users/u1/refund", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({ chargeId: "ch_1" }),
      }),
    );
    expect(res2.status).toBe(400);
  });

  it("calls Stripe and emits an audit event", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const { fake, calls } = makeFakeStripe();
    const app = createApp({
      adminUsers: {
        db: handle.db,
        adminEmails: [ADMIN],
        jwtSecret: OPS_SESSION_SECRET,
        stripe: fake,
      },
    });

    const res = await app.fetch(
      new Request("http://localhost/admin/users/u1/refund", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({
          chargeId: "ch_test_1",
          amountUsdCents: 1900,
          reason: "duplicate",
        }),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      refund: { id: string; status: string; isDevStub: boolean };
    };
    expect(body.refund.id).toBe("re_test_123");
    expect(body.refund.isDevStub).toBe(false);
    expect(calls).toHaveLength(1);
    expect(calls[0]!.charge).toBe("ch_test_1");
    expect(calls[0]!.amount).toBe(1900);

    const ev = await handle.db
      .select()
      .from(eventsTable)
      .where(eq(eventsTable.type, "billing.refund_issued"));
    expect(ev).toHaveLength(1);
    expect((ev[0]!.payload as { by: string }).by).toBe(ADMIN);
  });

  it("marks isDevStub=true when the stub Stripe is used", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const app = createApp({
      adminUsers: {
        db: handle.db,
        adminEmails: [ADMIN],
        jwtSecret: OPS_SESSION_SECRET,
      },
    });

    const res = await app.fetch(
      new Request("http://localhost/admin/users/u1/refund", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({
          chargeId: "ch_dev_1",
          amountUsdCents: 100,
          reason: "test",
        }),
      }),
    );
    const body = (await res.json()) as { refund: { isDevStub: boolean } };
    expect(body.refund.isDevStub).toBe(true);
  });
});
