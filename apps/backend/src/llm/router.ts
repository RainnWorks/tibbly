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
 *
 * D-9 (no hardcoded model ids) lands in two steps. Step 1 (RAI-66) shipped
 * the `model_catalog` table; step 2 will replace the constants below with
 * a `routing_policies` lookup keyed by `(segment, intent)`. Until step 2,
 * the constants here remain the only source of truth and every routing
 * decision in the codebase goes through one of the helpers here so the
 * step-2 swap is a single-file change.
 */
import { and, asc, isNull } from "drizzle-orm";

import type { Tier } from "@osrs-llm-helper/shared-types";

import type { DbClient } from "../db/client";
import { modelCatalog } from "../db/schema";

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

/**
 * Per-tier "cheap pass" model - the model the backend uses for tasks that
 * must not eat the user's interactive budget. Today that's:
 *   - End-of-session memory extraction (RAI-67)
 *   - Voice-style adaptation (RAI-67)
 *   - Any future preamble compression / routing classifier passes
 *
 * RULE: never Opus, regardless of tier. The cheap pass is for backend
 * housekeeping. The Iron-tier player still gets Opus on their interactive
 * turns via `chooseModel`.
 *
 * Step 2 (routing policies) will replace this with a catalog lookup like
 * `select id from routing_policies where intent='cheap_pass' and ...`.
 * Until then the helper returns the same Haiku id across tiers - the
 * cheapest Anthropic model OpenRouter exposes. Callers that want to be
 * explicit about the "I want the cheapest viable model from the live
 * catalog" semantics should use {@link chooseCheapModelFromCatalog}, which
 * consults `model_catalog` directly.
 */
export function chooseCheapModel(_tier: Tier): string {
  return MODEL_HAIKU;
}

/**
 * Pick the cheapest non-retired model in the live catalog (D-9 compliant).
 *
 * Selection rule:
 *   1. Filter `retired_at IS NULL` so we never pin a stale id.
 *   2. Optionally constrain to a provider (`anthropic` by default - the
 *      brand-voice doc commits to Anthropic-family voices and we don't
 *      want a hosted-Llama tone landing in the companion's mouth).
 *   3. Order by `output_price_micro_usd_per_million` ascending; pick the
 *      first non-zero row. Zero-priced rows are filtered out because a
 *      "$0" entry usually means the OpenRouter pricing column was empty
 *      and we don't want to silently route real traffic to it.
 *   4. If no row qualifies, fall back to `chooseCheapModel(tier)` so the
 *      caller never blocks on an empty catalog.
 *
 * Caller is expected to memoise the result for the life of a single job.
 */
export interface ChooseCheapModelFromCatalogOptions {
  /** Provider prefix to restrict the search to. Default: `anthropic`. */
  provider?: string;
  /** Fallback tier for the empty-catalog case. Default: `hobbyist`. */
  fallbackTier?: Tier;
}

export async function chooseCheapModelFromCatalog(
  db: DbClient,
  options: ChooseCheapModelFromCatalogOptions = {},
): Promise<string> {
  const provider = options.provider ?? "anthropic";
  const fallback = chooseCheapModel(options.fallbackTier ?? "hobbyist");

  const rows = await db
    .select({
      id: modelCatalog.id,
      provider: modelCatalog.provider,
      outputPrice: modelCatalog.outputPriceMicroUsdPerMillion,
    })
    .from(modelCatalog)
    .where(and(isNull(modelCatalog.retiredAt)))
    .orderBy(asc(modelCatalog.outputPriceMicroUsdPerMillion));

  for (const row of rows) {
    if (row.provider !== provider) continue;
    if (!row.outputPrice || row.outputPrice <= 0) continue;
    return row.id;
  }
  return fallback;
}
