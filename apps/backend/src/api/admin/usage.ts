/**
 * Admin analytics endpoints (RAI-37).
 *
 * Routes (all gated by `requireAdmin`):
 *   GET /admin/tool-usage   — daily tool-usage rollup with optional date range.
 *   GET /admin/funnel       — funnel-step counts per day.
 *   GET /admin/errors       — errors by kind per day.
 *   GET /admin/realtime     — last-minute event rate + connected plugin count.
 *
 * Auth model for tonight:
 *   - Caller supplies `x-admin-email: <addr>` header (the dashboard sets it
 *     from the better-auth session). When that header is missing OR not in
 *     `ADMIN_EMAILS`, we return 401.
 *   - When better-auth lands the dashboard side (RAI-19), the header is
 *     replaced by a verified session lookup — same shape, no caller churn.
 *
 * Out of scope for tonight (per SCOPE_GUARD): per-row PII redaction, CSV
 * export, custom date pickers. Defaults below cover all four dashboard
 * widgets we ship in /admin.
 */
import { and, gte, lte } from "drizzle-orm";
import { Hono } from "hono";

import { getDb } from "../../db/client";
import type { DbClient } from "../../db/client";
import {
  events as eventsTable,
  metricsChatDaily,
  metricsErrorsDaily,
  metricsFunnelDaily,
  metricsToolUsageDaily,
} from "../../db/schema";
import { adminGate, type AdminGateOptions } from "./_gate";

export interface CreateAdminUsageOptions extends AdminGateOptions {
  /** Override DB for tests. */
  db?: DbClient;
}

export function createAdminUsageRouter(options: CreateAdminUsageOptions = {}): Hono {
  const db = options.db ?? getDb().db;

  const app = new Hono();
  app.use("/*", adminGate(options));

  app.get("/tool-usage", async (c) => {
    const range = parseDateRange(c.req.query("since"), c.req.query("until"));
    const rows = await db
      .select()
      .from(metricsToolUsageDaily)
      .where(
        and(
          gte(metricsToolUsageDaily.date, range.since),
          lte(metricsToolUsageDaily.date, range.until),
        ),
      );

    const byFamily: Record<string, number> = {};
    const byTool: Record<string, number> = {};
    for (const r of rows) {
      byFamily[r.family] = (byFamily[r.family] ?? 0) + r.count;
      byTool[r.toolName] = (byTool[r.toolName] ?? 0) + r.count;
    }
    return c.json({
      ok: true,
      range,
      rows,
      summary: { byFamily, byTool, totalCalls: rows.reduce((a, r) => a + r.count, 0) },
    });
  });

  app.get("/funnel", async (c) => {
    const range = parseDateRange(c.req.query("since"), c.req.query("until"));
    const rows = await db
      .select()
      .from(metricsFunnelDaily)
      .where(
        and(gte(metricsFunnelDaily.date, range.since), lte(metricsFunnelDaily.date, range.until)),
      );
    const byStep: Record<string, number> = {};
    for (const r of rows) byStep[r.step] = (byStep[r.step] ?? 0) + r.userCount;
    return c.json({ ok: true, range, rows, summary: { byStep } });
  });

  app.get("/errors", async (c) => {
    const range = parseDateRange(c.req.query("since"), c.req.query("until"));
    const rows = await db
      .select()
      .from(metricsErrorsDaily)
      .where(
        and(gte(metricsErrorsDaily.date, range.since), lte(metricsErrorsDaily.date, range.until)),
      );
    const byKind: Record<string, number> = {};
    for (const r of rows) byKind[r.kind] = (byKind[r.kind] ?? 0) + r.count;
    return c.json({ ok: true, range, rows, summary: { byKind } });
  });

  app.get("/realtime", async (c) => {
    const oneMinAgo = new Date(Date.now() - 60_000);
    const recent = await db
      .select()
      .from(eventsTable)
      .where(gte(eventsTable.createdAt, oneMinAgo));
    const byType: Record<string, number> = {};
    for (const r of recent) byType[r.type] = (byType[r.type] ?? 0) + 1;

    // RAI-19 fills this in from the presence registry; mock-friendly for now.
    const connectedPlugins = recent.filter(
      (r) =>
        r.type === "auth.pairing.claimed" || r.type.startsWith("chat."),
    ).length;

    return c.json({
      ok: true,
      windowSeconds: 60,
      eventCount: recent.length,
      byType,
      connectedPlugins,
    });
  });

  // /admin/usage/chat-daily — useful for the cost-vs-revenue chart.
  app.get("/chat-daily", async (c) => {
    const range = parseDateRange(c.req.query("since"), c.req.query("until"));
    const rows = await db
      .select()
      .from(metricsChatDaily)
      .where(and(gte(metricsChatDaily.date, range.since), lte(metricsChatDaily.date, range.until)));
    return c.json({
      ok: true,
      range,
      rows,
      summary: {
        totalMessages: rows.reduce((a, r) => a + r.messageCount, 0),
        totalTokensIn: rows.reduce((a, r) => a + Number(r.tokensIn), 0),
        totalTokensOut: rows.reduce((a, r) => a + Number(r.tokensOut), 0),
        totalCostMicroUsd: rows.reduce((a, r) => a + Number(r.costMicroUsd), 0),
      },
    });
  });

  return app;
}

/* -------------------------------------------------------------------------- */
/*  Helpers                                                                   */
/* -------------------------------------------------------------------------- */

export { parseAdminEmails } from "./_gate";

interface DateRange {
  since: string;
  until: string;
}

function parseDateRange(since: string | undefined, until: string | undefined): DateRange {
  const now = new Date();
  const u = until ?? toDateKey(now);
  // Default look-back: 30 days.
  const defaultSince = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  const s = since ?? toDateKey(defaultSince);
  return { since: s, until: u };
}

function toDateKey(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
