/**
 * Tier catalog tests (RAI-19).
 *
 * Pins:
 *   - Internal quotas match the margin analysis in
 *     docs/research/llm-providers/_SUMMARY.md. If any number drifts here we
 *     break the cost-model so the test loudly fails.
 *   - `tierForStripePriceId` resolves via the env source rather than the
 *     hard-coded process env (so a tier never silently flips in production).
 */
import { describe, expect, it } from "bun:test";

import { TIERS, TIER_BY_NAME, tierForStripePriceId } from "../src/billing/tiers";

describe("tiers", () => {
  it("hardcoded quotas match the locked margin numbers", () => {
    expect(TIER_BY_NAME.hobbyist.quotaTokens).toBe(100_000);
    expect(TIER_BY_NAME.pro.quotaTokens).toBe(500_000);
    expect(TIER_BY_NAME.iron.quotaTokens).toBe(2_000_000);
  });

  it("hardcoded prices match the public pricing page (GBP pence per D-11)", () => {
    expect(TIER_BY_NAME.hobbyist.monthlyPricePence).toBe(700);
    expect(TIER_BY_NAME.pro.monthlyPricePence).toBe(1900);
    expect(TIER_BY_NAME.iron.monthlyPricePence).toBe(4900);
  });

  it("orders cheapest-first so price resolution is stable", () => {
    expect(TIERS.map((t) => t.tier)).toEqual(["hobbyist", "pro", "iron"]);
  });

  it("resolves a Stripe price id via the supplied env", () => {
    const env = {
      STRIPE_PRICE_HOBBYIST: "price_hobby_test",
      STRIPE_PRICE_PRO: "price_pro_test",
      STRIPE_PRICE_IRON: "price_iron_test",
    };
    expect(tierForStripePriceId("price_pro_test", env)?.tier).toBe("pro");
    expect(tierForStripePriceId("price_hobby_test", env)?.tier).toBe("hobbyist");
    expect(tierForStripePriceId("price_iron_test", env)?.tier).toBe("iron");
  });

  it("returns null on an unknown price id (never silently picks a tier)", () => {
    const env = {
      STRIPE_PRICE_PRO: "price_pro_test",
    };
    expect(tierForStripePriceId("price_unknown", env)).toBeNull();
    expect(tierForStripePriceId("", env)).toBeNull();
  });
});
