/**
 * Subscription tier catalog (RAI-19 / RAI-20).
 *
 * Declarative single-source-of-truth for:
 *   - which Stripe price IDs map to which internal tier;
 *   - how many tokens of monthly quota each tier grants;
 *   - the default model the chat loop should route through.
 *
 * The Stripe webhook (`src/api/webhooks/stripe.ts`) resolves a subscription's
 * line item to one of these rows. The token meter (`src/billing/meter.ts`)
 * reads `quotaTokens` when crediting `invoice.payment_succeeded`.
 *
 * Margin discipline (per docs/research/llm-providers/_SUMMARY.md):
 *   - Hobbyist $7 / 100K tokens → 97.4% margin headroom.
 *   - Pro $19 / 500K tokens → 88.0%.
 *   - Iron $49 / 2M tokens → 79.1%.
 * Changing any quota here MUST be checked against `cost-model.md` first.
 */
import type { Tier } from "@osrs-llm-helper/shared-types";

export interface TierSpec {
  /** Internal tier id — matches the `subscription_tier` enum. */
  tier: Tier;
  /**
   * Tokens granted at the start of each Stripe billing cycle. Decrements as
   * the user chats; resets on `invoice.payment_succeeded`. NOT a hard cap on
   * concurrent in-flight requests — see meter.md for the atomic decrement.
   */
  quotaTokens: number;
  /** Display price in cents USD — surfaced on the dashboard. */
  monthlyPriceCents: number;
  /** Marketing-facing human title. */
  title: string;
  /**
   * Env var holding the Stripe price id this tier maps to. Read lazily so
   * tests can mutate the env without recompiling the table.
   */
  priceEnvVar: "STRIPE_PRICE_HOBBYIST" | "STRIPE_PRICE_PRO" | "STRIPE_PRICE_IRON";
}

/**
 * Ordered intentionally — cheapest tier first. The webhook iterates this
 * list when classifying an arbitrary Stripe subscription so a misconfigured
 * price falls through to a sensible default.
 */
export const TIERS: readonly TierSpec[] = [
  {
    tier: "hobbyist",
    quotaTokens: 100_000,
    monthlyPriceCents: 700,
    title: "Hobbyist",
    priceEnvVar: "STRIPE_PRICE_HOBBYIST",
  },
  {
    tier: "pro",
    quotaTokens: 500_000,
    monthlyPriceCents: 1900,
    title: "Pro",
    priceEnvVar: "STRIPE_PRICE_PRO",
  },
  {
    tier: "iron",
    quotaTokens: 2_000_000,
    monthlyPriceCents: 4900,
    title: "Iron",
    priceEnvVar: "STRIPE_PRICE_IRON",
  },
];

export const TIER_BY_NAME: Readonly<Record<Tier, TierSpec>> = Object.freeze(
  Object.fromEntries(TIERS.map((t) => [t.tier, t])) as Record<Tier, TierSpec>,
);

/**
 * Resolve a Stripe `price.id` to one of our tiers by consulting
 * `STRIPE_PRICE_*` env vars. Returns `null` if no env matches (caller should
 * either ignore or log; we never silently fall back to a paid tier).
 *
 * `envSource` defaults to `process.env` but is overridable so tests can
 * inject a fixture without touching the real env.
 */
export function tierForStripePriceId(
  priceId: string,
  envSource: Record<string, string | undefined> = process.env,
): TierSpec | null {
  if (!priceId) return null;
  for (const tier of TIERS) {
    const v = envSource[tier.priceEnvVar];
    if (v && v === priceId) return tier;
  }
  return null;
}
