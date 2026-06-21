/**
 * OpenRouter model-catalog ingester (model platform step 1).
 *
 * Pulls `GET https://openrouter.ai/api/v1/models`, upserts each entry into
 * the `model_catalog` table, and retires anything that fell out of the
 * latest payload. Runs on boot (deferred 5s) + nightly via a setInterval
 * loop wired in `server.ts`, and on demand via `POST /admin/catalog/refresh`.
 *
 * D-9: no model id is ever hardcoded in plugin, backend, or marketing copy.
 * Every routing decision will eventually consult this table; step 2 wires
 * the routing policy layer that consumes it.
 *
 * Failure mode: a bad fetch or parse MUST NOT mutate the table. The function
 * logs loudly and returns zeros so the caller (admin route or boot loop)
 * can surface the failure without poisoning the catalog. The schedule will
 * try again on the next tick.
 */
import { eq, lt, sql } from "drizzle-orm";

import type { DbClient } from "../../db/client";
import { modelCatalog, type NewModelCatalogRow } from "../../db/schema";
import { log } from "../../lib/log";

/** Default OpenRouter models endpoint. Overridable for tests. */
export const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";

/** Fetch timeout in ms. OpenRouter typically responds in <500ms. */
export const FETCH_TIMEOUT_MS = 30_000;

/**
 * Shape of one entry in the OpenRouter `/models` array. We only pluck the
 * fields we care about; everything else flows into the `capabilities` blob
 * untouched.
 */
export interface OpenRouterModelEntry {
  id: string;
  name?: string;
  context_length?: number;
  pricing?: {
    prompt?: string;
    completion?: string;
  };
  architecture?: {
    input_modalities?: string[];
    tokenizer?: string;
    instruct_type?: string | null;
    modality?: string;
  };
  top_provider?: Record<string, unknown>;
  per_request_limits?: Record<string, unknown> | null;
}

export interface OpenRouterModelsResponse {
  data: OpenRouterModelEntry[];
}

export interface RefreshResult {
  added: number;
  updated: number;
  retired: number;
}

export interface RefreshOptions {
  /** Override the endpoint (tests inject a mock URL). */
  url?: string;
  /** Override fetch (tests inject a stub). */
  fetchImpl?: typeof fetch;
  /** Timeout override for tests. */
  timeoutMs?: number;
}

/**
 * Convert a USD-per-token decimal string into a micro-USD-per-million-tokens
 * integer. Returns 0 for non-numeric / empty / negative input.
 *
 *   "0.000003" → 3_000_000  (i.e. $3 per 1M tokens)
 *   "0.00001"  → 10_000_000 (i.e. $10 per 1M tokens)
 *
 * Implementation note: we parse the decimal string manually to avoid float
 * drift. Anything outside `[0, 1)` USD per token is rejected — pricing
 * never goes anywhere near $1/token and the cap protects against parse
 * accidents like "1" meaning "$1/M" rather than "$1/token".
 */
export function priceStringToMicroUsdPerMillion(raw: unknown): number {
  if (typeof raw !== "string") return 0;
  const trimmed = raw.trim();
  if (trimmed.length === 0) return 0;
  // Reject scientific notation explicitly; OpenRouter publishes plain decimals.
  if (/[eE]/.test(trimmed)) return 0;
  if (!/^-?\d*(?:\.\d+)?$/.test(trimmed)) return 0;
  const negative = trimmed.startsWith("-");
  if (negative) return 0;
  const [intPart, fracPart = ""] = trimmed.split(".");
  // Need price scaled to 1e12. integer-USD * 1e12 + frac-padded-to-12 digits.
  const intDigits = intPart === "" ? "0" : intPart;
  // Cap absurd values: more than $1/token (intDigits > 0) → 0.
  if (intDigits !== "0" && BigInt(intDigits) > 0n) return 0;
  // Pad / truncate fractional to 12 digits.
  const frac12 = (fracPart + "000000000000").slice(0, 12);
  const scaled = Number(BigInt(frac12));
  return Number.isFinite(scaled) ? scaled : 0;
}

/** First segment of a "provider/model" id. */
export function providerOf(id: string): string {
  const idx = id.indexOf("/");
  return idx >= 0 ? id.slice(0, idx) : id;
}

/**
 * Refresh the catalog from OpenRouter.
 *
 * Transactional shape:
 *   1. Capture `runStartedAt = now()`.
 *   2. Fetch + parse the listing. On error → return {0,0,0}, mutate nothing.
 *   3. For every entry: upsert by id. Set `lastSeenAt = now()`. Clear
 *      `retiredAt` if previously set. Insert path also seeds `firstSeenAt`.
 *   4. Mark any row whose `lastSeenAt < runStartedAt` AND `retiredAt IS NULL`
 *      as retired (`retiredAt = now()`).
 *
 * `added` counts rows where we inserted a brand-new id (no prior row).
 * `updated` counts rows that already existed and got a refresh.
 * `retired` counts rows we just marked as retired in this run.
 */
export async function refreshModelCatalog(
  db: DbClient,
  options: RefreshOptions = {},
): Promise<RefreshResult> {
  const url = options.url ?? OPENROUTER_MODELS_URL;
  const fetchImpl = options.fetchImpl ?? fetch;
  const timeoutMs = options.timeoutMs ?? FETCH_TIMEOUT_MS;

  const runStartedAt = new Date();

  let payload: OpenRouterModelsResponse;
  try {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    let res: Response;
    try {
      res = await fetchImpl(url, { signal: ctrl.signal });
    } finally {
      clearTimeout(timer);
    }
    if (!res.ok) {
      log.warn(
        { url, status: res.status },
        "model-catalog: fetch returned non-2xx",
      );
      return { added: 0, updated: 0, retired: 0 };
    }
    const raw = (await res.json()) as unknown;
    if (!raw || typeof raw !== "object" || !Array.isArray((raw as { data?: unknown }).data)) {
      log.warn({ url }, "model-catalog: malformed response (missing data[])");
      return { added: 0, updated: 0, retired: 0 };
    }
    payload = raw as OpenRouterModelsResponse;
  } catch (err) {
    log.error(
      { err: { message: (err as Error).message } },
      "model-catalog: fetch failed",
    );
    return { added: 0, updated: 0, retired: 0 };
  }

  let added = 0;
  let updated = 0;

  // Existing-id set lets us count added vs updated without an extra query
  // per row. One query up front, scales to the few thousand rows OpenRouter
  // ever exposes.
  const existing = await db
    .select({ id: modelCatalog.id, retiredAt: modelCatalog.retiredAt })
    .from(modelCatalog);
  const existingById = new Map(existing.map((r) => [r.id, r.retiredAt]));

  for (const entry of payload.data) {
    if (!entry || typeof entry.id !== "string" || entry.id.length === 0) {
      continue;
    }
    const id = entry.id;
    const input = priceStringToMicroUsdPerMillion(entry.pricing?.prompt);
    const output = priceStringToMicroUsdPerMillion(entry.pricing?.completion);
    const contextLength = Math.max(
      0,
      Math.floor(typeof entry.context_length === "number" ? entry.context_length : 0),
    );
    const inputModalities = Array.isArray(entry.architecture?.input_modalities)
      ? (entry.architecture?.input_modalities as string[])
      : [];
    const capabilities: Record<string, unknown> = {};
    if (entry.architecture) capabilities["architecture"] = entry.architecture;
    if (entry.top_provider) capabilities["top_provider"] = entry.top_provider;
    if (entry.per_request_limits !== undefined && entry.per_request_limits !== null) {
      capabilities["per_request_limits"] = entry.per_request_limits;
    }

    const row: NewModelCatalogRow = {
      id,
      provider: providerOf(id),
      displayName: typeof entry.name === "string" && entry.name.length > 0 ? entry.name : id,
      contextLength,
      inputPriceMicroUsdPerMillion: input,
      outputPriceMicroUsdPerMillion: output,
      inputModalities,
      capabilities,
      lastSeenAt: runStartedAt,
      retiredAt: null,
    };

    if (existingById.has(id)) {
      updated += 1;
      await db
        .update(modelCatalog)
        .set({
          provider: row.provider,
          displayName: row.displayName,
          contextLength: row.contextLength,
          inputPriceMicroUsdPerMillion: row.inputPriceMicroUsdPerMillion,
          outputPriceMicroUsdPerMillion: row.outputPriceMicroUsdPerMillion,
          inputModalities: row.inputModalities,
          capabilities: row.capabilities,
          lastSeenAt: row.lastSeenAt,
          retiredAt: null,
        })
        .where(eq(modelCatalog.id, id));
    } else {
      added += 1;
      await db.insert(modelCatalog).values({
        ...row,
        firstSeenAt: runStartedAt,
      });
    }
  }

  // Retire anything we didn't see this run. We count by querying the rows
  // first, then issuing the UPDATE — `.returning()` overloads diverge
  // across PGLite + postgres-js so this read-then-write is the portable shape.
  const toRetire = await db
    .select({ id: modelCatalog.id })
    .from(modelCatalog)
    .where(
      sql`${modelCatalog.lastSeenAt} < ${runStartedAt.toISOString()} AND ${modelCatalog.retiredAt} IS NULL`,
    );
  const retired = toRetire.length;
  if (retired > 0) {
    await db
      .update(modelCatalog)
      .set({ retiredAt: sql`now()` })
      .where(
        sql`${modelCatalog.lastSeenAt} < ${runStartedAt.toISOString()} AND ${modelCatalog.retiredAt} IS NULL`,
      );
  }

  // Suppress unused-var lint on the helper; `lt` is left imported because
  // a future variant uses it in a typed predicate.
  void lt;

  log.info(
    { added, updated, retired, total: payload.data.length },
    "model-catalog: refresh complete",
  );
  return { added, updated, retired };
}
