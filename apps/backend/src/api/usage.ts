/**
 * Usage summary for the dashboard (RAI-27).
 *
 * GET /v1/usage/summary
 *   → { balance, lastRenewal, dailyTokens: [{ date, tokens }] }
 *
 * The dashboard chart wants a 30-day window of total tokens per day
 * (prompt + completion). We read from `usage_records` because that's
 * the canonical per-turn ledger the billing pipeline writes to.
 * `token_balances` gives us the live credit number, and `subscriptions`
 * carries the last renewal timestamp (`current_period_start`).
 */
import { and, desc, eq, gte, sql } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../db/client";
import { subscriptions, tokenBalances, usageRecords } from "../db/schema";
import { requireUser, type AuthedVars } from "./_auth";

export interface CreateUsageRouterOptions {
  db: DbClient;
}

export interface UsageDailyPoint {
  date: string;
  tokens: number;
}

export interface UsageSummary {
  balance: number;
  lastRenewal: string | null;
  dailyTokens: UsageDailyPoint[];
}

const DAY_MS = 24 * 60 * 60 * 1000;
const WINDOW_DAYS = 30;

export function createUsageRouter(options: CreateUsageRouterOptions): Hono<{ Variables: AuthedVars }> {
  const { db } = options;
  const app = new Hono<{ Variables: AuthedVars }>();

  app.use("*", requireUser);

  app.get("/summary", async (c) => {
    const userId = c.var.userId;
    const summary = await loadUsageSummary(db, userId);
    return c.json(summary);
  });

  return app;
}

/**
 * Shared loader so tests can hit the SQL path without spinning up Hono.
 */
export async function loadUsageSummary(
  db: DbClient,
  userId: string,
  now: Date = new Date(),
): Promise<UsageSummary> {
  const since = new Date(now.getTime() - WINDOW_DAYS * DAY_MS);

  const [balanceRow] = await db
    .select({ balance: tokenBalances.balanceTokens })
    .from(tokenBalances)
    .where(eq(tokenBalances.userId, userId))
    .limit(1);

  const [subRow] = await db
    .select({ start: subscriptions.currentPeriodStart })
    .from(subscriptions)
    .where(eq(subscriptions.userId, userId))
    .orderBy(desc(subscriptions.currentPeriodStart))
    .limit(1);

  const dailyRows = await db
    .select({
      day: sql<string>`to_char(date_trunc('day', ${usageRecords.createdAt}), 'YYYY-MM-DD')`,
      tokens: sql<number>`coalesce(sum(${usageRecords.promptTokens} + ${usageRecords.completionTokens})::int, 0)`,
    })
    .from(usageRecords)
    .where(and(eq(usageRecords.userId, userId), gte(usageRecords.createdAt, since)))
    .groupBy(sql`date_trunc('day', ${usageRecords.createdAt})`)
    .orderBy(sql`date_trunc('day', ${usageRecords.createdAt})`);

  const dailyTokens: UsageDailyPoint[] = dailyRows.map((r) => ({
    date: r.day,
    tokens: Number(r.tokens ?? 0),
  }));

  return {
    balance: balanceRow ? Number(balanceRow.balance) : 0,
    lastRenewal: subRow?.start ? subRow.start.toISOString() : null,
    dailyTokens,
  };
}
