/**
 * Stripe Customer Portal session (RAI-27).
 *
 * POST /v1/billing/portal → { url }
 *
 * Looks up the authenticated user's `stripe_customer_id` and creates a
 * Stripe Billing Portal session. The dashboard then redirects the
 * browser to that URL so the user can manage their subscription,
 * payment method, invoices, etc.
 *
 * The Stripe client is injected via `options.stripe` so tests can pass
 * a fake; production wiring constructs the real client from
 * `env.STRIPE_SECRET_KEY` when the route is mounted in `app.ts`.
 */
import { eq } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../db/client";
import { users } from "../db/schema";
import { requireUser, type AuthedVars } from "./_auth";

export interface StripeBillingPortalLike {
  billingPortal: {
    sessions: {
      create(params: {
        customer: string;
        return_url?: string;
      }): Promise<{ url: string }>;
    };
  };
}

export interface CreateBillingPortalRouterOptions {
  db: DbClient;
  stripe: StripeBillingPortalLike;
  /** Where Stripe sends the user back after the portal session. */
  returnUrl?: string;
}

export function createBillingPortalRouter(
  options: CreateBillingPortalRouterOptions,
): Hono<{ Variables: AuthedVars }> {
  const { db, stripe, returnUrl } = options;
  const app = new Hono<{ Variables: AuthedVars }>();

  app.use("*", requireUser);

  app.post("/portal", async (c) => {
    const userId = c.var.userId;

    const [user] = await db
      .select({ stripeCustomerId: users.stripeCustomerId })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1);

    if (!user) {
      return c.json({ ok: false, error: "user_not_found" }, 404);
    }
    if (!user.stripeCustomerId) {
      return c.json({ ok: false, error: "no_stripe_customer" }, 409);
    }

    const session = await stripe.billingPortal.sessions.create({
      customer: user.stripeCustomerId,
      ...(returnUrl ? { return_url: returnUrl } : {}),
    });

    return c.json({ url: session.url });
  });

  return app;
}
