/**
 * Per-tier model router (RAI-17).
 *
 * NORTH_STAR § Default models:
 *   Hobbyist → Haiku 4.5  ($1 / $5 per 1M)
 *   Pro      → Sonnet 4.6 ($3 / $15 per 1M)
 *   Iron     → Opus   4.7 ($15 / $75 per 1M)
 *
 * `chooseModel` is a thin pure function over the tier; we keep it as a
 * standalone module so the LLM caller doesn't need to know about Stripe tier
 * semantics — that translation happens once, here.
 */
import type { Tier } from "@osrs-llm-helper/shared-types";

export const MODEL_HAIKU = "anthropic/claude-haiku-4.5";
export const MODEL_SONNET = "anthropic/claude-sonnet-4.6";
export const MODEL_OPUS = "anthropic/claude-opus-4.7";

export function chooseModel(tier: Tier): string {
  switch (tier) {
    case "hobbyist":
      return MODEL_HAIKU;
    case "pro":
      return MODEL_SONNET;
    case "iron":
      return MODEL_OPUS;
  }
}
