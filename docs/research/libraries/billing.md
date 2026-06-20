# Billing — recommendation

## Decision

**Use Stripe.** Specifically `stripe` (Node SDK) on the backend and
`@stripe/stripe-js` only if we end up using Stripe Elements (we likely don't —
Stripe Checkout / Customer Portal is enough).

| Option | Verdict | Why |
|---|---|---|
| **Stripe (`stripe`)** | ✅ PICK | Mature, official, NORTH_STAR mandate, supports subscriptions + metered + one-time credits + Customer Portal. v22.x line on 2026-06 |
| Lemon Squeezy | ❌ Skip | Merchant-of-record convenience but worse API ergonomics and slower webhooks; not needed at our stage |
| Paddle | ❌ Skip | Same reason as Lemon Squeezy |

## Packages

- `stripe` — official Node SDK. v22.2.2 (2026-06-18), 4.5k★.
  - https://github.com/stripe/stripe-node
  - https://stripe.com/docs/api?lang=node

## Install

```bash
bun add stripe
```

> Note: Stripe ships fully typed in the package itself — `stripe-event-types` is
> no longer required as of stripe-node v15+ (event types are inline). Do **not**
> add it. Tom's brief mentioned it; current best practice is to drop it.

## Hello-world: webhook handler

Source: https://github.com/stripe/stripe-node#webhook-signing

```ts
// apps/backend/src/billing/webhook.ts
import Stripe from 'stripe';
import type { Context } from 'hono';

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY!);

export async function handleStripeWebhook(c: Context) {
  const sig = c.req.header('stripe-signature')!;
  const rawBody = await c.req.text();

  let event: Stripe.Event;
  try {
    event = stripe.webhooks.constructEvent(
      rawBody,
      sig,
      process.env.STRIPE_WEBHOOK_SECRET!,
    );
  } catch (err) {
    return c.text(`webhook signature mismatch: ${(err as Error).message}`, 400);
  }

  switch (event.type) {
    case 'checkout.session.completed': {
      const session = event.data.object;
      // grant credits / activate subscription
      break;
    }
    case 'customer.subscription.deleted': {
      // disable user
      break;
    }
    case 'invoice.payment_failed': {
      // freeze chat
      break;
    }
  }

  return c.json({ received: true });
}
```

## Hello-world: create a Checkout session

```ts
const session = await stripe.checkout.sessions.create({
  mode: 'subscription',
  line_items: [{ price: process.env.STRIPE_PRICE_PRO!, quantity: 1 }],
  success_url: `${process.env.PUBLIC_URL}/dashboard/success?session={CHECKOUT_SESSION_ID}`,
  cancel_url: `${process.env.PUBLIC_URL}/pricing`,
  customer_email: user.email,
  client_reference_id: user.id,
});
return Response.redirect(session.url!, 303);
```

## Hello-world: Customer Portal

```ts
const portal = await stripe.billingPortal.sessions.create({
  customer: user.stripeCustomerId!,
  return_url: `${process.env.PUBLIC_URL}/dashboard`,
});
return Response.redirect(portal.url, 303);
```

## Usage metering (token credits)

Two viable models. NORTH_STAR's PRICING.md should pick one.

1. **Prepaid credits (recommended for v1).** Top-up packs via Checkout
   `mode: 'payment'`. Each chat turn decrements `users.credits` by tokens used
   × rate. Hard stop when `credits <= 0`. Simple, no Stripe-side metering.
2. **Stripe meters / subscription items.** Use
   `stripe.billing.meterEvents.create({ event_name, payload })` after each
   chat to report token usage. Stripe handles billing aggregation. Better
   long-term, harder to operate tonight.

Start with (1). Add (2) later if we offer a "pay as you go" tier.

## When NOT to use

- **Test mode in production.** Always derive the key from env, never hardcode.
- **Bypassing webhook signing in dev.** Use `stripe listen --forward-to
  localhost:3000/api/stripe/webhook` so the signing path runs in dev too.

## Maintenance signals

- `stripe` — v22.2.2 (2026-06-18). Pinned API version: `2026-05-27.dahlia`.
  https://github.com/stripe/stripe-node
- Releases on a 1–2 week cadence.

## Required env

```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_HOBBYIST=price_...
STRIPE_PRICE_PRO=price_...
STRIPE_PRICE_IRON=price_...
PUBLIC_URL=http://localhost:5173
```
