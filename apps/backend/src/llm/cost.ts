/**
 * Token → micro-USD cost math.
 *
 * Why micro-USD: we want integers so a Stripe meter increment or a usage row
 * can be summed exactly. 1 USD = 1_000_000 µUSD. A 1k-token chat at the
 * Sonnet output rate of $15/M tokens = 15_000 µUSD = $0.015 — fits in an int
 * with room to spare for the rest of this decade's pricing.
 *
 * Prices are sourced from OpenRouter's published rates (see
 * docs/research/libraries/llm.md). They are easy to override per environment
 * once we set up real pricing tables — for now we hard-code a conservative
 * cap; the meter will under-bill rather than over-bill if OpenRouter prices
 * drop. Update via `MODEL_PRICING` when we wire the live rate-card API.
 */

export interface ModelPrice {
  /** $ per 1M prompt tokens, expressed in µUSD per single token. */
  promptMicroUsdPerToken: number;
  /** $ per 1M completion tokens, expressed in µUSD per single token. */
  completionMicroUsdPerToken: number;
}

/**
 * Anthropic via OpenRouter — published USD/1M prices as of 2026-06-21.
 * We over-estimate slightly to keep a margin: the bill goes to our card,
 * not the user's, so under-counting hurts us.
 */
export const MODEL_PRICING: Record<string, ModelPrice> = {
  "anthropic/claude-haiku-4.5": {
    promptMicroUsdPerToken: 1, //   $1 / 1M
    completionMicroUsdPerToken: 5, //  $5 / 1M
  },
  "anthropic/claude-sonnet-4.6": {
    promptMicroUsdPerToken: 3, //   $3 / 1M
    completionMicroUsdPerToken: 15, // $15 / 1M
  },
  "anthropic/claude-opus-4.7": {
    promptMicroUsdPerToken: 15, //  $15 / 1M
    completionMicroUsdPerToken: 75, // $75 / 1M
  },
};

/** Fallback used when a model id isn't in the price table — Sonnet rate. */
export const FALLBACK_PRICE: ModelPrice = MODEL_PRICING["anthropic/claude-sonnet-4.6"]!;

export interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
}

/**
 * Compute the micro-USD cost of a single LLM turn. Both inputs must be
 * non-negative integers; we floor anything else to 0.
 */
export function computeCostMicroUsd(model: string, usage: TokenUsage): number {
  const price = MODEL_PRICING[model] ?? FALLBACK_PRICE;
  const prompt = Math.max(0, Math.floor(usage.promptTokens));
  const completion = Math.max(0, Math.floor(usage.completionTokens));
  return prompt * price.promptMicroUsdPerToken + completion * price.completionMicroUsdPerToken;
}
