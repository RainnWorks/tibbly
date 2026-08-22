/**
 * Stripe webhook receiver tests (RAI-19).
 *
 * Strategy:
 *   - We mock the Stripe SDK via `setStripeOverride` so signature
 *     verification accepts any body we send. The fake's
 *     `webhooks.constructEvent` echoes the JSON body parsed back as the
 *     event — that's exactly the shape `verifyWebhook` would return on a
 *     real signed delivery.
 *   - The Hono app is created via `createApp({ stripeWebhook: { ... } })`
 *     so the router is the same one production mounts.
 *   - Each test uses a fresh in-memory PGLite + a fresh event bus so
 *     assertions on emitted events stay isolated.
 *
 * Locks in:
 *   - signature header missing → 400.
 *   - invalid signature → 400.
 *   - duplicate event id → 200, no second credit, no second emit.
 *   - checkout.session.completed → user.stripe_customer_id bound.
 *   - subscription.created → row inserted, billing.subscription.created
 *     emitted, monthly_quota_tokens populated from the tier table.
 *   - subscription.updated → existing row updated, no duplicate "created" event.
 *   - subscription.deleted → row marked canceled + cancelled event.
 *   - invoice.payment_succeeded → meter.credit called, renewed event.
 *   - invoice.payment_failed → subscription flipped to past_due.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";
import type Stripe from "stripe";

import { createApp } from "../src/app";
import { createTokenMeter } from "../src/billing/meter";
import {
  resetStripeForTests,
  setStripeOverride,
} from "../src/billing/stripe";
import {
  processedStripeEvents,
  subscriptions,
  tokenBalances,
  users,
} from "../src/db/schema";
import { createEventBus, type DomainEvent } from "../src/events";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

const ENV_FIXTURE = {
  STRIPE_PRICE_HOBBYIST: "price_hobby_test",
  STRIPE_PRICE_PRO: "price_pro_test",
  STRIPE_PRICE_IRON: "price_iron_test",
};

const WEBHOOK_SECRET = "whsec_test_only";

interface WebhookFixture {
  handle: TestDbHandle;
  events: DomainEvent[];
  bus: ReturnType<typeof createEventBus>;
  app: ReturnType<typeof createApp>;
  meter: ReturnType<typeof createTokenMeter>;
}

function makeFakeStripe(): Stripe {
  // The only method we call on the real client is webhooks.constructEvent.
  // The fake parses the raw body and surfaces it as a Stripe.Event — our
  // handlers walk that object directly.
  const fake = {
    webhooks: {
      constructEvent(rawBody: string, sig: string) {
        if (sig === "BAD") {
          throw new Error("bad sig");
        }
        return JSON.parse(rawBody) as Stripe.Event;
      },
    },
  };
  return fake as unknown as Stripe;
}

async function seedUserWithCustomer(
  handle: TestDbHandle,
  customerId: string,
): Promise<string> {
  const [row] = await handle.db
    .insert(users)
    .values({
      email: `${customerId}@example.com`,
      stripeCustomerId: customerId,
    })
    .returning();
  if (!row) throw new Error("seed user failed");
  return row.id;
}

async function setupFixture(): Promise<WebhookFixture> {
  const handle = await makeTestDb();
  const bus = createEventBus();
  const events: DomainEvent[] = [];
  bus.onAny((e) => events.push(e));
  const meter = createTokenMeter({ db: handle.db, bus });
  setStripeOverride(makeFakeStripe());
  const app = createApp({
    stripeWebhook: {
      db: handle.db,
      meter,
      bus,
      envSource: ENV_FIXTURE,
      webhookSecret: WEBHOOK_SECRET,
    },
  });
  return { handle, events, bus, app, meter };
}

async function post(
  app: ReturnType<typeof createApp>,
  body: unknown,
  options: { signature?: string | null } = {},
): Promise<Response> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  const sig = options.signature === undefined ? "valid" : options.signature;
  if (sig !== null) headers["stripe-signature"] = sig;
  return app.fetch(
    new Request("http://test.local/api/webhooks/stripe", {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    }),
  );
}

/**
 * Stripe event ids are `evt_` + an alphanumeric token — the shape the
 * `processed_stripe_events_event_id_shape` CHECK (RAI-59) enforces. Keep the
 * fixtures on that shape rather than dotted `Math.random()` output.
 */
function randomSuffix(): string {
  return Math.random().toString(36).slice(2, 12);
}

function subEvent(
  type: "customer.subscription.created" | "customer.subscription.updated" | "customer.subscription.deleted",
  overrides: Partial<{
    id: string;
    subscriptionId: string;
    customerId: string;
    priceId: string;
    status: string;
    cancelAtPeriodEnd: boolean;
  }> = {},
): Stripe.Event {
  const id = overrides.id ?? `evt_${type.replace(/\./g, "_")}_${randomSuffix()}`;
  const subscriptionId = overrides.subscriptionId ?? "sub_test_001";
  const customerId = overrides.customerId ?? "cus_test_001";
  const priceId = overrides.priceId ?? ENV_FIXTURE.STRIPE_PRICE_PRO;
  const periodStart = 1_700_000_000;
  const periodEnd = 1_700_000_000 + 30 * 24 * 60 * 60;
  return {
    id,
    type,
    api_version: "2026-05-27.dahlia",
    created: periodStart,
    livemode: false,
    pending_webhooks: 1,
    request: { id: null, idempotency_key: null },
    object: "event",
    data: {
      object: {
        id: subscriptionId,
        customer: customerId,
        status: overrides.status ?? "active",
        cancel_at_period_end: overrides.cancelAtPeriodEnd ?? false,
        current_period_start: periodStart,
        current_period_end: periodEnd,
        items: {
          data: [
            {
              id: `si_${subscriptionId}`,
              price: { id: priceId },
            },
          ],
        },
      },
    },
  } as unknown as Stripe.Event;
}

function invoiceEvent(
  type: "invoice.payment_succeeded" | "invoice.payment_failed",
  overrides: Partial<{
    id: string;
    invoiceId: string;
    customerId: string;
    subscriptionId: string;
    priceId: string;
  }> = {},
): Stripe.Event {
  const id = overrides.id ?? `evt_${type.replace(/\./g, "_")}_${randomSuffix()}`;
  return {
    id,
    type,
    api_version: "2026-05-27.dahlia",
    created: 1_700_000_000,
    livemode: false,
    pending_webhooks: 1,
    request: { id: null, idempotency_key: null },
    object: "event",
    data: {
      object: {
        id: overrides.invoiceId ?? "in_test_001",
        customer: overrides.customerId ?? "cus_test_001",
        subscription: overrides.subscriptionId ?? "sub_test_001",
        lines: {
          data: [
            {
              pricing: {
                price_details: {
                  price: overrides.priceId ?? ENV_FIXTURE.STRIPE_PRICE_PRO,
                },
              },
            },
          ],
        },
      },
    },
  } as unknown as Stripe.Event;
}

describe("Stripe webhook receiver", () => {
  let f: WebhookFixture;

  beforeEach(async () => {
    f = await setupFixture();
  });

  afterEach(async () => {
    resetStripeForTests();
    await f.handle.close();
  });

  it("returns 400 when the signature header is missing", async () => {
    const res = await post(f.app, { id: "evt_x" }, { signature: null });
    expect(res.status).toBe(400);
  });

  it("returns 400 when the signature is invalid", async () => {
    const res = await post(f.app, { id: "evt_x" }, { signature: "BAD" });
    expect(res.status).toBe(400);
  });

  it("binds the Stripe customer to a user on checkout.session.completed", async () => {
    // Seed user without a customer id; the webhook should attach it.
    const [u] = await f.handle.db
      .insert(users)
      .values({ email: "ck@example.com" })
      .returning();
    if (!u) throw new Error("seed failed");

    const ev = {
      id: "evt_checkout_1",
      type: "checkout.session.completed",
      data: {
        object: {
          id: "cs_test_001",
          customer: "cus_test_checkout",
          client_reference_id: u.id,
        },
      },
    };
    const res = await post(f.app, ev);
    expect(res.status).toBe(200);

    const [refreshed] = await f.handle.db.select().from(users).where(eq(users.id, u.id));
    expect(refreshed?.stripeCustomerId).toBe("cus_test_checkout");
  });

  it("inserts a subscription + emits created event", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    const res = await post(f.app, subEvent("customer.subscription.created"));
    expect(res.status).toBe(200);

    const rows = await f.handle.db.select().from(subscriptions);
    expect(rows).toHaveLength(1);
    expect(rows[0]?.tier).toBe("pro");
    expect(rows[0]?.monthlyQuotaTokens).toBe(500_000);

    const created = f.events.find((e) => e.type === "billing.subscription.created");
    expect(created).toBeDefined();
    if (created?.type === "billing.subscription.created") {
      expect(created.payload.tier).toBe("pro");
      expect(created.payload.monthlyQuota).toBe(500_000);
    }
    const firstPaid = f.events.find((e) => e.type === "funnel.first_paid");
    expect(firstPaid).toBeDefined();
  });

  it("updates an existing subscription without re-emitting created", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));
    f.events.length = 0;

    const res = await post(
      f.app,
      subEvent("customer.subscription.updated", { status: "active" }),
    );
    expect(res.status).toBe(200);
    const created = f.events.find((e) => e.type === "billing.subscription.created");
    expect(created).toBeUndefined();
  });

  it("marks a subscription cancelled on deleted", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));
    f.events.length = 0;

    const res = await post(f.app, subEvent("customer.subscription.deleted"));
    expect(res.status).toBe(200);

    const [row] = await f.handle.db.select().from(subscriptions);
    expect(row?.status).toBe("canceled");
    const cancelled = f.events.find((e) => e.type === "billing.subscription.cancelled");
    expect(cancelled).toBeDefined();
  });

  it("credits tokens on invoice.payment_succeeded", async () => {
    const userId = await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));
    f.events.length = 0;

    const res = await post(f.app, invoiceEvent("invoice.payment_succeeded"));
    expect(res.status).toBe(200);

    const balance = await f.meter.getBalance(userId);
    expect(balance).toBe(500_000); // pro tier quota

    const renewed = f.events.find((e) => e.type === "billing.subscription.renewed");
    expect(renewed).toBeDefined();

    // The brief explicitly requires the credit path to emit
    // `billing.balance.decremented`. The meter publishes one with model
    // = stripe.credit.
    const dec = f.events.find(
      (e) => e.type === "billing.balance.decremented" && e.payload.model === "stripe.credit",
    );
    expect(dec).toBeDefined();
  });

  it("is idempotent — replaying the same event id credits exactly once", async () => {
    const userId = await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));

    const invoice = invoiceEvent("invoice.payment_succeeded", { id: "evt_dupe_test" });

    await post(f.app, invoice);
    await post(f.app, invoice); // replay
    await post(f.app, invoice); // replay

    const balance = await f.meter.getBalance(userId);
    expect(balance).toBe(500_000); // not 1.5M

    const rows = await f.handle.db
      .select()
      .from(processedStripeEvents)
      .where(eq(processedStripeEvents.eventId, "evt_dupe_test"));
    expect(rows).toHaveLength(1);
  });

  it("flips subscription to past_due on invoice.payment_failed", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));

    const res = await post(f.app, invoiceEvent("invoice.payment_failed"));
    expect(res.status).toBe(200);

    const [row] = await f.handle.db.select().from(subscriptions);
    expect(row?.status).toBe("past_due");
  });

  it("ignores unknown event types with a 200", async () => {
    const ev = {
      id: "evt_random_thing",
      type: "customer.tax_id.created",
      data: { object: {} },
    };
    const res = await post(f.app, ev);
    expect(res.status).toBe(200);
    // The idempotency row still gets written so a replay short-circuits.
    const rows = await f.handle.db.select().from(processedStripeEvents);
    expect(rows.find((r) => r.eventId === "evt_random_thing")).toBeDefined();
  });

  it("refuses to upsert when current_period_start is null (does not credit against new Date(0))", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    const ev = subEvent("customer.subscription.created");
    // Simulate Stripe shipping a payload where the period timestamps are
    // missing. Pre-fix this would silently credit against 1970-01-01.
    const obj = (ev as unknown as { data: { object: Record<string, unknown> } }).data.object;
    obj["current_period_start"] = null;
    obj["current_period_end"] = null;

    const res = await post(f.app, ev);
    expect(res.status).toBe(200); // handler should not throw; idempotency row stays

    // No subscription row was written; the meter is untouched.
    const rows = await f.handle.db.select().from(subscriptions);
    expect(rows).toHaveLength(0);

    // No funnel / created events emitted either; meaningless period must
    // not propagate into analytics.
    expect(f.events.find((e) => e.type === "billing.subscription.created")).toBeUndefined();
    expect(f.events.find((e) => e.type === "funnel.first_paid")).toBeUndefined();
  });

  it("gracefully ignores an unknown event.type with no observable side-effects", async () => {
    // Distinct from "unknown event types with a 200": that test pins the
    // 200 + idempotency-row contract. This pins the BUS contract: nothing
    // is published when the type does not match a known case.
    const ev = {
      id: "evt_truly_unknown_001",
      type: "customer.cash_balance.funds_available",
      data: { object: { id: "cb_test_001" } },
    };
    const res = await post(f.app, ev);
    expect(res.status).toBe(200);

    expect(f.events).toEqual([]);
    const rows = await f.handle.db.select().from(subscriptions);
    expect(rows).toHaveLength(0);
  });

  it("balance row + idempotency row stay in sync after credit", async () => {
    await seedUserWithCustomer(f.handle, "cus_test_001");
    await post(f.app, subEvent("customer.subscription.created"));

    const invoice = invoiceEvent("invoice.payment_succeeded", { id: "evt_balance_sync" });
    await post(f.app, invoice);

    const [bal] = await f.handle.db.select().from(tokenBalances);
    expect(bal?.balanceTokens).toBe(500_000);

    const idem = await f.handle.db
      .select()
      .from(processedStripeEvents)
      .where(eq(processedStripeEvents.eventId, "evt_balance_sync"));
    expect(idem).toHaveLength(1);
    expect(idem[0]?.eventType).toBe("invoice.payment_succeeded");
  });
});
