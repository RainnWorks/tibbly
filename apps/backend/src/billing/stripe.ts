/**
 * Stripe SDK + webhook signature verification helpers (RAI-19).
 *
 * The webhook route imports `verifyWebhook` and `getStripe`; everything else
 * about Stripe goes through this module so:
 *   - we have a single place to override the API version when Stripe ships
 *     a new dated release;
 *   - tests can inject a fake `Stripe`-shaped object via `setStripeOverride`
 *     and replay fixture events without touching live keys.
 *
 * Per docs/research/libraries/billing.md: `stripe-event-types` is no longer
 * needed — event types are inline in the official SDK since v15.
 */
import Stripe from "stripe";

import { env } from "../env";

let stripeOverride: Stripe | null = null;
let stripeSingleton: Stripe | null = null;

/**
 * Lazily build the Stripe client. We avoid touching env at module-load time
 * so tests that never call `getStripe()` won't blow up when the key is
 * absent.
 *
 * The pinned API version (`2026-05-27.dahlia`) matches the SDK's typed
 * snapshot — bumping the SDK and the API version should always happen in
 * lockstep (see DECISION_LOG.md "Stripe API pinning").
 */
export function getStripe(): Stripe {
  if (stripeOverride) return stripeOverride;
  if (stripeSingleton) return stripeSingleton;
  const key = env.STRIPE_SECRET_KEY;
  if (!key) {
    throw new Error("STRIPE_SECRET_KEY missing — cannot build Stripe client");
  }
  // Pin so a future Stripe API revision can't silently change the shape of
  // `customer.subscription.updated` payloads. The current SDK's ApiVersion
  // literal matches "2026-05-27.dahlia"; if a future SDK upgrade ships a new
  // dated version we want a typecheck failure here so the bump is intentional.
  stripeSingleton = new Stripe(key, {
    apiVersion: "2026-05-27.dahlia",
    typescript: true,
  });
  return stripeSingleton;
}

/** Test-only: swap the Stripe client out for a stub or a fake event source. */
export function setStripeOverride(stub: Stripe | null): void {
  stripeOverride = stub;
  if (stub) stripeSingleton = null;
}

export interface VerifyWebhookOptions {
  /** Raw HTTP body — bytes-faithful. MUST be the unparsed body. */
  rawBody: string;
  /** `stripe-signature` header value. */
  signature: string;
  /** Webhook signing secret (`whsec_...`). Defaults to env. */
  secret?: string;
  /** Override the Stripe client (defaults to the singleton). */
  client?: Stripe;
  /** Tolerance window for replay protection — defaults to Stripe's 5 minutes. */
  toleranceSeconds?: number;
}

/**
 * Verify a Stripe webhook payload and return the typed `Stripe.Event` on
 * success. Throws `Error("invalid_signature")` on mismatch — the route
 * handler catches that and 400s without leaking the underlying SDK error.
 *
 * Why not `stripe.webhooks.constructEventAsync`: Hono on Bun gives us the
 * body string directly; we don't need the WHATWG-stream variant.
 */
export function verifyWebhook(options: VerifyWebhookOptions): Stripe.Event {
  const secret = options.secret ?? env.STRIPE_WEBHOOK_SECRET;
  if (!secret) {
    throw new Error("STRIPE_WEBHOOK_SECRET missing — refusing to verify webhook");
  }
  const client = options.client ?? getStripe();
  try {
    return client.webhooks.constructEvent(
      options.rawBody,
      options.signature,
      secret,
      options.toleranceSeconds,
    );
  } catch (err) {
    throw new Error("invalid_signature", { cause: err });
  }
}

/** Reset singleton — for tests that want to assert "no override leaked". */
export function resetStripeForTests(): void {
  stripeOverride = null;
  stripeSingleton = null;
}
