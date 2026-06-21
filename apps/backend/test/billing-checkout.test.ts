/**
 * Tests for the anonymous Stripe Checkout entry point (marketing CTAs).
 *
 * Covers:
 *   - 400 on unknown tier
 *   - 503 when the tier's STRIPE_PRICE_* env var is unset
 *   - 200 + url on the happy path; Stripe called with the right price
 *     id, mode "subscription", success/cancel URLs
 *   - 502 if Stripe throws or returns no url
 *   - All three tier prices wire correctly
 */
import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import type Stripe from "stripe";

import { createApp } from "../src/app";
import type { StripeCheckoutLike } from "../src/api/billing-checkout";

const SUCCESS_URL = "https://tibbly.example/welcome";
const CANCEL_URL = "https://tibbly.example/#pricing";

interface CheckoutStub {
  checkout: {
    sessions: {
      create: ReturnType<typeof mock>;
    };
  };
}

function makeStripeStub(
  result: { url: string | null } | (() => Promise<never>) = { url: "https://checkout.stripe.test/c_xxx" },
): CheckoutStub {
  const create = typeof result === "function"
    ? mock(result)
    : mock(async () => result);
  return {
    checkout: {
      sessions: { create },
    },
  };
}

function envWithAllTiers(): Record<string, string | undefined> {
  return {
    STRIPE_PRICE_HOBBYIST: "price_hobbyist_test",
    STRIPE_PRICE_PRO: "price_pro_test",
    STRIPE_PRICE_IRON: "price_iron_test",
  };
}

describe("/v1/billing/checkout/:tier", () => {
  it("400s on unknown tier", async () => {
    const stub = makeStripeStub();
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/legend", { method: "POST" }),
    );
    expect(res.status).toBe(400);
    expect(stub.checkout.sessions.create).not.toHaveBeenCalled();
  });

  it("503s when the tier's STRIPE_PRICE_* env var is unset", async () => {
    const stub = makeStripeStub();
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: {}, // empty — no prices configured
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/pro", { method: "POST" }),
    );
    expect(res.status).toBe(503);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("tier_not_configured");
    expect(stub.checkout.sessions.create).not.toHaveBeenCalled();
  });

  it("creates a session and returns the url on the happy path", async () => {
    const stub = makeStripeStub({ url: "https://checkout.stripe.test/c_hobbyist" });
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });

    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/hobbyist", { method: "POST" }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { url: string; tier: string };
    expect(body.url).toBe("https://checkout.stripe.test/c_hobbyist");
    expect(body.tier).toBe("hobbyist");

    expect(stub.checkout.sessions.create).toHaveBeenCalledTimes(1);
    const args = stub.checkout.sessions.create.mock.calls[0]![0] as Stripe.Checkout.SessionCreateParams;
    expect(args.mode).toBe("subscription");
    expect(args.line_items).toEqual([{ price: "price_hobbyist_test", quantity: 1 }]);
    expect(args.success_url).toBe(SUCCESS_URL);
    expect(args.cancel_url).toBe(CANCEL_URL);
    expect(args.allow_promotion_codes).toBe(true);
  });

  it("uses the Pro price id for the pro tier", async () => {
    const stub = makeStripeStub({ url: "https://checkout.stripe.test/c_pro" });
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/pro", { method: "POST" }),
    );
    expect(res.status).toBe(200);
    const args = stub.checkout.sessions.create.mock.calls[0]![0] as Stripe.Checkout.SessionCreateParams;
    expect(args.line_items).toEqual([{ price: "price_pro_test", quantity: 1 }]);
  });

  it("uses the Iron price id for the iron tier", async () => {
    const stub = makeStripeStub({ url: "https://checkout.stripe.test/c_iron" });
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/iron", { method: "POST" }),
    );
    expect(res.status).toBe(200);
    const args = stub.checkout.sessions.create.mock.calls[0]![0] as Stripe.Checkout.SessionCreateParams;
    expect(args.line_items).toEqual([{ price: "price_iron_test", quantity: 1 }]);
  });

  it("502s when Stripe throws", async () => {
    const stub = makeStripeStub(async () => {
      throw new Error("stripe is down");
    });
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/hobbyist", { method: "POST" }),
    );
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("checkout_create_failed");
  });

  it("502s when Stripe returns a session without a url", async () => {
    const stub = makeStripeStub({ url: null });
    const app = createApp({
      billingCheckout: {
        stripe: stub as unknown as StripeCheckoutLike,
        successUrl: SUCCESS_URL,
        cancelUrl: CANCEL_URL,
        envSource: envWithAllTiers(),
      },
    });
    const res = await app.fetch(
      new Request("http://localhost/v1/billing/checkout/pro", { method: "POST" }),
    );
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("checkout_create_failed");
  });
});
