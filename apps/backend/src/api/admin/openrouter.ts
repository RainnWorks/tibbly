/**
 * Admin OpenRouter spend + revenue endpoints (ops console).
 *
 * Two routes, both gated by `adminGate`:
 *
 *   GET /admin/openrouter/spend    today/week/month spend in micro-USD,
 *                                  per-model breakdown.
 *   GET /admin/openrouter/revenue  today/week/month revenue, derived
 *                                  from subscription tiers + price book.
 *
 * Spend math sources from `messages.promptTokens/completionTokens` joined
 * with `messages.model` and the price book in `llm/cost.ts`. We do NOT
 * trust an external "ledger" table tonight; the messages table is the
 * canonical record.
 *
 * Revenue math: count active subscriptions per tier, multiply by tier
 * price. Stripe webhook deliveries already populate the `subscriptions`
 * table, so this is a fast aggregate.
 */
import { and, gte, sql } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../../db/client";
import { messages, subscriptions } from "../../db/schema";
import { computeCostMicroUsd } from "../../llm/cost";
import { adminGate, type AdminGateOptions } from "./_gate";

/** Default Stripe tier prices in USD cents (Hobbyist / Pro / Iron). */
export const DEFAULT_TIER_PRICE_USD_CENTS: Record<string, number> = {
  hobbyist: 700,
  pro: 1900,
  iron: 4900,
};

export interface CreateAdminOpenRouterOptions extends AdminGateOptions {
  db: DbClient;
  /** Override tier prices in USD cents for tests. */
  tierPriceUsdCents?: Record<string, number>;
}

export function createAdminOpenRouterRouter(
  options: CreateAdminOpenRouterOptions,
): Hono {
  const { db } = options;
  const prices = options.tierPriceUsdCents ?? DEFAULT_TIER_PRICE_USD_CENTS;

  const app = new Hono();
  app.use("/*", adminGate(options));

  /* ------------------------------------------------------------------ */
  /*  GET /admin/openrouter/spend                                       */
  /* ------------------------------------------------------------------ */

  app.get("/spend", async (c) => {
    const now = new Date();
    const todayStart = startOfUtcDay(now);
    const weekStart = new Date(todayStart.getTime() - 6 * 24 * 60 * 60 * 1000);
    const monthStart = new Date(todayStart.getTime() - 29 * 24 * 60 * 60 * 1000);

    const rows = await db
      .select({
        model: messages.model,
        promptTokens: messages.promptTokens,
        completionTokens: messages.completionTokens,
        createdAt: messages.createdAt,
      })
      .from(messages)
      .where(
        and(
          gte(messages.createdAt, monthStart),
          sql`${messages.deletedAt} IS NULL`,
        ),
      );

    const totals = { today: 0, week: 0, month: 0 };
    const byModelMonth: Record<
      string,
      {
        spendMicroUsd: number;
        promptTokens: number;
        completionTokens: number;
        messageCount: number;
      }
    > = {};

    for (const r of rows) {
      const model = r.model ?? "unknown";
      const usage = {
        promptTokens: Number(r.promptTokens),
        completionTokens: Number(r.completionTokens),
      };
      const microUsd = computeCostMicroUsd(model, usage);
      const ts = r.createdAt.getTime();
      if (ts >= todayStart.getTime()) totals.today += microUsd;
      if (ts >= weekStart.getTime()) totals.week += microUsd;
      totals.month += microUsd;

      const bucket = (byModelMonth[model] ??= {
        spendMicroUsd: 0,
        promptTokens: 0,
        completionTokens: 0,
        messageCount: 0,
      });
      bucket.spendMicroUsd += microUsd;
      bucket.promptTokens += usage.promptTokens;
      bucket.completionTokens += usage.completionTokens;
      bucket.messageCount += 1;
    }

    return c.json({
      ok: true,
      windows: {
        today: { since: todayStart.toISOString(), spendMicroUsd: totals.today },
        week: { since: weekStart.toISOString(), spendMicroUsd: totals.week },
        month: { since: monthStart.toISOString(), spendMicroUsd: totals.month },
      },
      byModelMonth,
    });
  });

  /* ------------------------------------------------------------------ */
  /*  GET /admin/openrouter/revenue                                     */
  /* ------------------------------------------------------------------ */

  app.get("/revenue", async (c) => {
    // Active subscriptions: status in ('active','trialing') AND
    // currentPeriodEnd > now. We treat one active subscription as one
    // recurring monthly charge.
    const now = new Date();

    const subs = await db
      .select({
        tier: subscriptions.tier,
        status: subscriptions.status,
        currentPeriodEnd: subscriptions.currentPeriodEnd,
        cancelAtPeriodEnd: subscriptions.cancelAtPeriodEnd,
      })
      .from(subscriptions);

    const tierCounts: Record<string, number> = {};
    let mrrUsdCents = 0;
    for (const s of subs) {
      if (s.status !== "active" && s.status !== "trialing") continue;
      if (s.currentPeriodEnd.getTime() < now.getTime()) continue;
      tierCounts[s.tier] = (tierCounts[s.tier] ?? 0) + 1;
      const tierPrice = prices[s.tier];
      if (tierPrice !== undefined) mrrUsdCents += tierPrice;
    }

    // Today/week/month revenue approximated from MRR daily share. The
    // honest revenue figure needs Stripe Charge events; we surface that
    // as a follow-up. For tonight: MRR / 30 daily share.
    const dailyShareCents = Math.round(mrrUsdCents / 30);
    return c.json({
      ok: true,
      mrrUsdCents,
      activeSubscriptions: subs.filter(
        (s) =>
          (s.status === "active" || s.status === "trialing") &&
          s.currentPeriodEnd.getTime() >= now.getTime(),
      ).length,
      tierCounts,
      tierPriceUsdCents: prices,
      approxToday: { revenueUsdCents: dailyShareCents },
      approxWeek: { revenueUsdCents: dailyShareCents * 7 },
      approxMonth: { revenueUsdCents: mrrUsdCents },
      note:
        "Revenue is approximated from active-subscription MRR. Per-day actuals need Stripe charge events; see docs/architecture/OPS_DESIGN.md follow-ups.",
    });
  });

  return app;
}

function startOfUtcDay(d: Date): Date {
  const x = new Date(d);
  x.setUTCHours(0, 0, 0, 0);
  return x;
}
