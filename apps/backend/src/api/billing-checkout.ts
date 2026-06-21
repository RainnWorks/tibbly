/**
 * Stripe Checkout session for new signups.
 *
 * POST /v1/billing/checkout/{tier} → { url }
 *
 * Unauthenticated: this is the entry point for prospects on the
 * marketing site. Stripe Checkout collects payment, then the existing
 * `checkout.session.completed` webhook handler in `webhooks/stripe.ts`
 * creates the user + binds the Stripe customer. After payment the
 * player opens RuneLite, requests a pairing code, enters it into
 * Stripe Checkout's `client_reference_id` flow... actually no: with no
 * pre-auth here, the link from Checkout → user happens on the
 * webhook side via the Stripe customer email match. (See
 * `webhooks/stripe.ts:onCheckoutCompleted` — it uses
 * `client_reference_id` ?? Stripe customer id.)
 *
 * Pivot D-8 note: Tom moved most account flows into the RuneLite
 * plugin. This checkout route exists so the marketing PricingTiers
 * CTAs can do something real. The post-payment UX is "open RuneLite,
 * open Tibbly panel, pair this device".
 */
import { Hono } from "hono";
import { z } from "zod";
import type Stripe from "stripe";

import { TIERS, type TierSpec } from "../billing/tiers";
import { log } from "../lib/log";

export interface StripeCheckoutLike {
  checkout: {
    sessions: {
      create(params: Stripe.Checkout.SessionCreateParams): Promise<{ url: string | null }>;
    };
  };
}

export interface CreateBillingCheckoutRouterOptions {
  stripe: StripeCheckoutLike;
  /** Where Stripe redirects the player after successful payment. */
  successUrl: string;
  /** Where Stripe redirects on cancel. */
  cancelUrl: string;
  /** Optional override for tier→price env var lookup. */
  envSource?: Record<string, string | undefined>;
}

const TierSchema = z.enum(["hobbyist", "pro", "iron"]);

export function createBillingCheckoutRouter(
  options: CreateBillingCheckoutRouterOptions,
): Hono {
  const { stripe, successUrl, cancelUrl } = options;
  const envSource = options.envSource ?? (process.env as Record<string, string | undefined>);

  const app = new Hono();

  app.post("/checkout/:tier", async (c) => {
    const parsed = TierSchema.safeParse(c.req.param("tier"));
    if (!parsed.success) {
      return c.json({ ok: false, error: "unknown_tier" }, 400);
    }

    const tierName = parsed.data;
    const spec: TierSpec | undefined = TIERS.find((t) => t.tier === tierName);
    if (!spec) {
      return c.json({ ok: false, error: "unknown_tier" }, 400);
    }

    const priceId = envSource[spec.priceEnvVar];
    if (!priceId) {
      log.warn(
        { tier: tierName, envVar: spec.priceEnvVar },
        "stripe checkout: price env var missing",
      );
      return c.json({ ok: false, error: "tier_not_configured" }, 503);
    }

    let session: { url: string | null };
    try {
      session = await stripe.checkout.sessions.create({
        mode: "subscription",
        line_items: [{ price: priceId, quantity: 1 }],
        success_url: successUrl,
        cancel_url: cancelUrl,
        allow_promotion_codes: true,
        // Encourage Stripe to collect billing address — useful for VAT and
        // for the welcome-email path the post-payment flow eventually adds.
        billing_address_collection: "auto",
      });
    } catch (err) {
      log.error(
        { tier: tierName, err: (err as Error).message },
        "stripe checkout: session.create failed",
      );
      return c.json({ ok: false, error: "checkout_create_failed" }, 502);
    }

    if (!session.url) {
      log.error({ tier: tierName }, "stripe checkout: session.create returned no url");
      return c.json({ ok: false, error: "checkout_create_failed" }, 502);
    }

    return c.json({ url: session.url, tier: tierName });
  });

  return app;
}
