/**
 * Admin model-catalog endpoints (ops console).
 *
 * Three routes, all gated by `adminGate`:
 *
 *   GET  /admin/catalog/models?provider=&retired=false  list with filters.
 *   GET  /admin/catalog/diff?since=ISO                  added / retired /
 *                                                      price-changed rows
 *                                                      since the timestamp.
 *   POST /admin/catalog/refresh                          manual refresh.
 *
 * The catalog itself is owned by `llm/catalog/ingest.ts`; this module is
 * a thin HTTP shell over it.
 */
import { and, eq, gte, sql } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../../db/client";
import { modelCatalog } from "../../db/schema";
import { refreshModelCatalog } from "../../llm/catalog/ingest";
import { adminGate, type AdminGateOptions } from "./_gate";

/** Threshold for the diff route's price-change bucket. 10% by default. */
export const DEFAULT_PRICE_CHANGE_THRESHOLD = 0.1;

export interface CreateAdminCatalogOptions extends AdminGateOptions {
  db: DbClient;
  /**
   * Override the refresh function so tests can inject a stub without
   * actually hitting OpenRouter.
   */
  refresh?: (db: DbClient) => Promise<{
    added: number;
    updated: number;
    retired: number;
  }>;
  /**
   * Override the price-change threshold (fraction, 0-1). 0.1 = "show me
   * everything that moved by >=10%."
   */
  priceChangeThreshold?: number;
}

export function createAdminCatalogRouter(
  options: CreateAdminCatalogOptions,
): Hono {
  const { db } = options;
  const refresh = options.refresh ?? ((d) => refreshModelCatalog(d));
  const threshold = options.priceChangeThreshold ?? DEFAULT_PRICE_CHANGE_THRESHOLD;

  const app = new Hono();
  app.use("/*", adminGate(options));

  /* ------------------------------------------------------------------ */
  /*  GET /admin/catalog/models                                         */
  /* ------------------------------------------------------------------ */

  app.get("/models", async (c) => {
    const provider = c.req.query("provider")?.trim();
    const retiredParam = c.req.query("retired")?.trim().toLowerCase();

    const conds = [] as ReturnType<typeof eq>[];
    if (provider && provider.length > 0) {
      conds.push(eq(modelCatalog.provider, provider));
    }

    let where: ReturnType<typeof and> | undefined;
    if (conds.length > 0) where = and(...conds);

    const baseQuery = where
      ? db.select().from(modelCatalog).where(where)
      : db.select().from(modelCatalog);
    const rows = await baseQuery;

    // Retired filter — applied in TS rather than SQL because we want the
    // default ("retired=false") to mean "no retired_at"; absent param =
    // "no filter".
    const filtered = (() => {
      if (retiredParam === undefined) return rows;
      if (retiredParam === "true" || retiredParam === "1") {
        return rows.filter((r) => r.retiredAt !== null);
      }
      if (retiredParam === "false" || retiredParam === "0") {
        return rows.filter((r) => r.retiredAt === null);
      }
      return rows;
    })();

    return c.json({
      ok: true,
      count: filtered.length,
      models: filtered.map((r) => ({
        id: r.id,
        provider: r.provider,
        displayName: r.displayName,
        contextLength: r.contextLength,
        inputPriceMicroUsdPerMillion: r.inputPriceMicroUsdPerMillion,
        outputPriceMicroUsdPerMillion: r.outputPriceMicroUsdPerMillion,
        inputModalities: r.inputModalities,
        capabilities: r.capabilities,
        firstSeenAt: r.firstSeenAt.toISOString(),
        lastSeenAt: r.lastSeenAt.toISOString(),
        retiredAt: r.retiredAt ? r.retiredAt.toISOString() : null,
      })),
    });
  });

  /* ------------------------------------------------------------------ */
  /*  GET /admin/catalog/diff                                           */
  /* ------------------------------------------------------------------ */

  app.get("/diff", async (c) => {
    const sinceRaw = c.req.query("since");
    const since = sinceRaw ? new Date(sinceRaw) : new Date(Date.now() - 86_400_000);
    if (Number.isNaN(since.getTime())) {
      return c.json({ ok: false, error: "bad_since" }, 400);
    }

    const rows = await db.select().from(modelCatalog);

    const added = rows
      .filter((r) => r.firstSeenAt.getTime() >= since.getTime())
      .map((r) => ({
        id: r.id,
        provider: r.provider,
        displayName: r.displayName,
        firstSeenAt: r.firstSeenAt.toISOString(),
      }));

    const retired = rows
      .filter((r) => r.retiredAt !== null && r.retiredAt.getTime() >= since.getTime())
      .map((r) => ({
        id: r.id,
        provider: r.provider,
        displayName: r.displayName,
        retiredAt: r.retiredAt!.toISOString(),
      }));

    // Price change detection: we don't store historical prices yet (a future
    // step adds the `model_catalog_history` table). For step 1 we surface
    // rows whose lastSeenAt is within the diff window AND whose firstSeenAt
    // is older than the window — i.e. an existing row that got touched. The
    // threshold sieve is documented but inactive until history lands.
    const priceChanges = rows
      .filter(
        (r) =>
          r.firstSeenAt.getTime() < since.getTime() &&
          r.lastSeenAt.getTime() >= since.getTime(),
      )
      .map((r) => ({
        id: r.id,
        provider: r.provider,
        displayName: r.displayName,
        inputPriceMicroUsdPerMillion: r.inputPriceMicroUsdPerMillion,
        outputPriceMicroUsdPerMillion: r.outputPriceMicroUsdPerMillion,
      }));

    return c.json({
      ok: true,
      since: since.toISOString(),
      threshold,
      added,
      retired,
      priceChanges,
    });
  });

  /* ------------------------------------------------------------------ */
  /*  POST /admin/catalog/refresh                                       */
  /* ------------------------------------------------------------------ */

  app.post("/refresh", async (c) => {
    const result = await refresh(db);
    return c.json({ ok: true, ...result });
  });

  // gte/sql kept imported because future filters use them; suppress
  // the unused-var lint by referencing them in this dead branch.
  void gte;
  void sql;

  return app;
}
