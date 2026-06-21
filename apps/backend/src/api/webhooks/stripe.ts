/**
 * Stripe webhook receiver (RAI-19).
 *
 * Mounted at `/api/webhooks/stripe`. Verifies the `stripe-signature` header,
 * then routes the event to one of a handful of handlers:
 *
 *   - checkout.session.completed             → link customer to user
 *   - customer.subscription.created/updated  → upsert `subscriptions`
 *   - customer.subscription.deleted          → mark cancelled, emit event
 *   - invoice.payment_succeeded              → credit monthly quota tokens
 *   - invoice.payment_failed                 → freeze access (status flip)
 *
 * Idempotency: every Stripe event id is inserted into
 * `processed_stripe_events` BEFORE we touch any other table. A double-replay
 * sees a primary-key conflict on insert and returns 200 without re-crediting.
 *
 * Why we don't transact every handler: the idempotency insert is itself a
 * cheap natural lock — if it succeeds the row is ours; if it fails the event
 * is a dupe. Either way we never need a SERIALIZABLE transaction across the
 * whole webhook flow.
 */
import type { Context } from "hono";
import { Hono } from "hono";
import { eq } from "drizzle-orm";
import type Stripe from "stripe";

import type { DbClient } from "../../db/client";
import {
  processedStripeEvents,
  subscriptions,
  users,
  type NewSubscription,
} from "../../db/schema";
import type { EventBus } from "../../events";
import { getDefaultBus } from "../../events";
import { log } from "../../lib/log";
import { verifyWebhook } from "../../billing/stripe";
import type { TokenMeter } from "../../billing/meter";
import { TIER_BY_NAME, tierForStripePriceId, type TierSpec } from "../../billing/tiers";

export interface StripeWebhookDeps {
  db: DbClient;
  meter: TokenMeter;
  /** Defaults to the process bus. Tests can pass a fresh bus. */
  bus?: EventBus;
  /** Override env source for resolving STRIPE_PRICE_* → tier. */
  envSource?: Record<string, string | undefined>;
  /** Override webhook secret. Defaults to `process.env.STRIPE_WEBHOOK_SECRET`. */
  webhookSecret?: string;
}

export function createStripeWebhookRouter(deps: StripeWebhookDeps): Hono {
  const router = new Hono();
  const bus = deps.bus ?? getDefaultBus();

  router.post("/stripe", async (c) => handle(c));

  async function handle(c: Context): Promise<Response> {
    const signature = c.req.header("stripe-signature");
    if (!signature) {
      return c.json({ ok: false, error: "missing_signature" }, 400);
    }
    const rawBody = await c.req.text();

    let event: Stripe.Event;
    try {
      const opts: Parameters<typeof verifyWebhook>[0] = { rawBody, signature };
      if (deps.webhookSecret !== undefined) opts.secret = deps.webhookSecret;
      event = verifyWebhook(opts);
    } catch (err) {
      log.warn({ err: (err as Error).message }, "stripe webhook: signature mismatch");
      return c.json({ ok: false, error: "invalid_signature" }, 400);
    }

    // Idempotency claim. If the insert conflicts we've seen this event before
    // and return early — Stripe will stop retrying after a 200.
    const claimed = await claimEvent(event);
    if (!claimed) {
      log.info({ eventId: event.id, type: event.type }, "stripe webhook: duplicate, skipping");
      return c.json({ received: true, duplicate: true });
    }

    try {
      // The `event.type` discriminant narrows `event.data.object` to the
      // exact `Stripe.X` subtype via the SDK's discriminated union, so we
      // never need an `as Stripe.X` cast inside the handlers. The previous
      // shape (`event.data.object as Stripe.Subscription` x4 plus a parallel
      // double-cast for the period fields) bypassed the SDK's type checks
      // and would silently compile when Stripe ships a new dated API.
      switch (event.type) {
        case "checkout.session.completed":
          await onCheckoutCompleted(event.data.object);
          break;
        case "customer.subscription.created":
        case "customer.subscription.updated":
          await onSubscriptionUpserted(event.data.object);
          break;
        case "customer.subscription.deleted":
          await onSubscriptionDeleted(event.data.object);
          break;
        case "invoice.payment_succeeded":
          await onInvoicePaid(event.data.object);
          break;
        case "invoice.payment_failed":
          await onInvoiceFailed(event.data.object);
          break;
        default:
          // Stripe sends a lot of types we don't care about; just log + 200.
          log.debug({ type: event.type }, "stripe webhook: ignoring unhandled type");
      }
    } catch (err) {
      log.error({ err, eventId: event.id, type: event.type }, "stripe webhook: handler threw");
      // Surface a 500 so Stripe retries — but only after we've already
      // claimed the id. We delete the claim so the retry can run cleanly.
      await unclaimEvent(event.id);
      return c.json({ ok: false, error: "handler_failed" }, 500);
    }

    return c.json({ received: true });
  }

  async function claimEvent(event: Stripe.Event): Promise<boolean> {
    const result = await deps.db
      .insert(processedStripeEvents)
      .values({ eventId: event.id, eventType: event.type })
      .onConflictDoNothing({ target: processedStripeEvents.eventId })
      .returning();
    return result.length > 0;
  }

  async function unclaimEvent(eventId: string): Promise<void> {
    try {
      await deps.db.delete(processedStripeEvents).where(eq(processedStripeEvents.eventId, eventId));
    } catch (err) {
      log.warn({ err, eventId }, "stripe webhook: failed to release idempotency claim");
    }
  }

  async function onCheckoutCompleted(session: Stripe.Checkout.Session): Promise<void> {
    const customerId =
      typeof session.customer === "string"
        ? session.customer
        : (session.customer?.id ?? null);
    const userId = session.client_reference_id ?? null;
    if (!customerId || !userId) {
      log.warn(
        { sessionId: session.id, hasCustomer: !!customerId, hasUserRef: !!userId },
        "stripe webhook: checkout.session.completed missing identifiers",
      );
      return;
    }
    // Bind the Stripe customer to our user so downstream subscription events
    // can find the right row by customer id.
    await deps.db
      .update(users)
      .set({ stripeCustomerId: customerId, updatedAt: new Date() })
      .where(eq(users.id, userId));
    log.info({ userId, customerId }, "stripe webhook: linked customer to user");
  }

  async function onSubscriptionUpserted(sub: Stripe.Subscription): Promise<void> {
    const customerId = typeof sub.customer === "string" ? sub.customer : sub.customer.id;
    const userRow = await findUserByCustomer(customerId);
    if (!userRow) {
      log.warn(
        { subId: sub.id, customerId },
        "stripe webhook: subscription event for unknown customer",
      );
      return;
    }

    // First subscription line item drives the tier classification. We don't
    // currently support add-ons.
    const item = sub.items.data[0];
    const priceId = item?.price.id ?? "";
    const tier = tierForStripePriceId(priceId, deps.envSource) ?? null;
    if (!tier) {
      log.warn(
        { subId: sub.id, priceId, customerId },
        "stripe webhook: unknown price id — refusing to upsert subscription",
      );
      return;
    }

    const period = readSubscriptionPeriod(sub);
    if (!period) {
      log.warn(
        { subId: sub.id, customerId },
        "stripe webhook: subscription missing current_period_start/end; refusing to upsert " +
          "(would have credited against new Date(0))",
      );
      return;
    }
    const status = sub.status;
    const cancelAtPeriodEnd = sub.cancel_at_period_end ? 1 : 0;
    const periodStart = period.start;
    const periodEnd = period.end;

    const values: NewSubscription = {
      userId: userRow.id,
      stripeSubscriptionId: sub.id,
      tier: tier.tier,
      status,
      monthlyQuotaTokens: tier.quotaTokens,
      currentPeriodStart: periodStart,
      currentPeriodEnd: periodEnd,
      cancelAtPeriodEnd,
    };

    const existing = await deps.db
      .select({ id: subscriptions.id, status: subscriptions.status })
      .from(subscriptions)
      .where(eq(subscriptions.stripeSubscriptionId, sub.id))
      .limit(1);

    if (existing.length === 0) {
      await deps.db.insert(subscriptions).values(values);
      await bus.publish({
        type: "billing.subscription.created",
        userId: userRow.id,
        payload: { tier: tier.tier, monthlyQuota: tier.quotaTokens },
      });
      // Funnel — only counts the first paid subscription per user (Stripe's
      // `subscription.created` only fires once per object, so this is safe).
      try {
        await bus.publish({
          type: "funnel.first_paid",
          userId: userRow.id,
          payload: { tier: tier.tier },
        });
      } catch {
        // Funnel events are best-effort; never block billing.
      }
      log.info(
        { userId: userRow.id, tier: tier.tier, subId: sub.id },
        "stripe webhook: subscription created",
      );
    } else {
      await deps.db
        .update(subscriptions)
        .set({ ...values, updatedAt: new Date() })
        .where(eq(subscriptions.stripeSubscriptionId, sub.id));
      // We don't emit `billing.subscription.renewed` here — that fires on
      // `invoice.payment_succeeded` after the new period actually pays.
      log.info(
        { userId: userRow.id, tier: tier.tier, subId: sub.id, status },
        "stripe webhook: subscription updated",
      );
    }
  }

  async function onSubscriptionDeleted(sub: Stripe.Subscription): Promise<void> {
    const customerId = typeof sub.customer === "string" ? sub.customer : sub.customer.id;
    const userRow = await findUserByCustomer(customerId);
    if (!userRow) {
      log.warn(
        { subId: sub.id, customerId },
        "stripe webhook: subscription.deleted for unknown customer",
      );
      return;
    }

    const rows = await deps.db
      .update(subscriptions)
      .set({ status: "canceled", updatedAt: new Date() })
      .where(eq(subscriptions.stripeSubscriptionId, sub.id))
      .returning();

    const tier = rows[0]?.tier;
    if (tier) {
      await bus.publish({
        type: "billing.subscription.cancelled",
        userId: userRow.id,
        payload: { tier, reason: "stripe.subscription.deleted" },
      });
    }
    log.info(
      { userId: userRow.id, subId: sub.id, tier },
      "stripe webhook: subscription cancelled",
    );
  }

  async function onInvoicePaid(invoice: Stripe.Invoice): Promise<void> {
    const customerId =
      typeof invoice.customer === "string"
        ? invoice.customer
        : (invoice.customer?.id ?? null);
    if (!customerId) {
      log.warn({ invoiceId: invoice.id }, "stripe webhook: invoice missing customer id");
      return;
    }
    const userRow = await findUserByCustomer(customerId);
    if (!userRow) {
      log.warn(
        { invoiceId: invoice.id, customerId },
        "stripe webhook: invoice.payment_succeeded for unknown customer",
      );
      return;
    }

    // Resolve tier via the subscription line item OR the price on the first
    // invoice line. Stripe puts the subscription id on `parent.subscription_details`
    // in 2026 API versions, but the simpler fallback is the price id on the
    // first line item.
    let tier: TierSpec | null = null;
    const line = invoice.lines.data[0];
    const rawPrice = line?.pricing?.price_details?.price ?? null;
    const linePriceId =
      typeof rawPrice === "string" ? rawPrice : (rawPrice?.id ?? null);
    if (linePriceId) tier = tierForStripePriceId(linePriceId, deps.envSource);
    if (!tier) {
      // Fallback: walk our local subscriptions table to find the tier.
      const subId = readInvoiceSubscriptionId(invoice);
      if (subId) {
        const local = await deps.db
          .select({ tier: subscriptions.tier })
          .from(subscriptions)
          .where(eq(subscriptions.stripeSubscriptionId, subId))
          .limit(1);
        const localTier = local[0]?.tier;
        if (localTier) tier = TIER_BY_NAME[localTier];
      }
    }
    if (!tier) {
      log.warn(
        { invoiceId: invoice.id, linePriceId },
        "stripe webhook: cannot resolve tier — skipping credit",
      );
      return;
    }

    const { newBalance } = await deps.meter.credit(userRow.id, tier.quotaTokens);
    log.info(
      { userId: userRow.id, tier: tier.tier, credited: tier.quotaTokens, newBalance },
      "stripe webhook: credited monthly quota",
    );

    // The brief asks us to emit `billing.balance.decremented` whenever the
    // balance moves — credits included. `meter.credit` already publishes this.
    // We also emit a `billing.subscription.renewed` so analytics can count
    // renewals distinctly from the initial create.
    try {
      await bus.publish({
        type: "billing.subscription.renewed",
        userId: userRow.id,
        payload: { tier: tier.tier },
      });
    } catch (err) {
      log.warn({ err }, "stripe webhook: renewed event publish failed");
    }
  }

  async function onInvoiceFailed(invoice: Stripe.Invoice): Promise<void> {
    const customerId =
      typeof invoice.customer === "string"
        ? invoice.customer
        : (invoice.customer?.id ?? null);
    if (!customerId) return;
    const userRow = await findUserByCustomer(customerId);
    if (!userRow) return;

    // Flag any matching subscription as past_due so the chat WS will block
    // future turns. We deliberately don't drop the balance — the user paid
    // for it; if the next renewal fails they simply don't get topped up.
    const subId = (
      invoice as unknown as { subscription?: string | null }
    ).subscription ?? null;
    if (subId) {
      await deps.db
        .update(subscriptions)
        .set({ status: "past_due", updatedAt: new Date() })
        .where(eq(subscriptions.stripeSubscriptionId, subId));
    }
    log.warn(
      { userId: userRow.id, invoiceId: invoice.id },
      "stripe webhook: invoice.payment_failed",
    );
  }

  async function findUserByCustomer(customerId: string): Promise<{ id: string } | null> {
    const rows = await deps.db
      .select({ id: users.id })
      .from(users)
      .where(eq(users.stripeCustomerId, customerId))
      .limit(1);
    return rows[0] ?? null;
  }

  return router;
}

/**
 * Resolve `current_period_start` / `current_period_end` from a Stripe
 * subscription with explicit null-tolerance.
 *
 * Stripe's TS types for the 2026 dated APIs declare these fields on
 * `Stripe.Subscription`, but the runtime payload may omit them (e.g. on
 * paused or incomplete subscriptions, or when Stripe ships a new API
 * variant that moves the fields to `parent.subscription_details`). The
 * original code did `new Date(maybe_null * 1000)` which silently produced
 * `1970-01-01`, then credited `tier.quotaTokens` against a meaningless
 * period.
 *
 * Returns `null` when either timestamp is missing/non-numeric. The caller
 * MUST refuse the upsert in that case so a corrupt envelope cannot land
 * `new Date(0)` in the meter.
 */
/**
 * Resolve the subscription id from a Stripe invoice across API versions.
 *
 * Stripe moved the field between `invoice.subscription` (pre-2024) and
 * `invoice.parent.subscription_details.subscription` (2026 dated APIs).
 * The SDK type for the current dated API drops the top-level field, but
 * older accounts may still surface it. We check both, in preference
 * order, and return null when neither carries it. Returning null is safe
 * here because the calling site only uses the id for a fallback lookup
 * and a tier fallback (the credit math comes from the price-id route).
 */
export function readInvoiceSubscriptionId(invoice: Stripe.Invoice): string | null {
  const view = invoice as unknown as {
    subscription?: unknown;
    parent?: { subscription_details?: { subscription?: unknown } };
  };
  if (typeof view.subscription === "string" && view.subscription.length > 0) {
    return view.subscription;
  }
  const nested = view.parent?.subscription_details?.subscription;
  if (typeof nested === "string" && nested.length > 0) {
    return nested;
  }
  return null;
}

export function readSubscriptionPeriod(
  sub: Stripe.Subscription,
): { start: Date; end: Date } | null {
  // Read via an `unknown` view that does NOT lie about presence. The SDK
  // typings for these fields are non-nullable numbers on paper, but the
  // wire payload can legitimately drop them. Treat them as `unknown` until
  // we have proven both are valid epoch-seconds.
  const view = sub as unknown as {
    current_period_start?: unknown;
    current_period_end?: unknown;
  };
  const startSec = view.current_period_start;
  const endSec = view.current_period_end;
  if (typeof startSec !== "number" || typeof endSec !== "number") return null;
  if (!Number.isFinite(startSec) || !Number.isFinite(endSec)) return null;
  if (startSec <= 0 || endSec <= 0) return null;
  return {
    start: new Date(startSec * 1000),
    end: new Date(endSec * 1000),
  };
}
