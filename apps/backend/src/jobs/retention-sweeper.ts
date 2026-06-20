/**
 * Data-retention sweeper (RAI-37 + RAI-34 privacy carve-out).
 *
 * Policy:
 *   - Raw `events` table: 30 days. Hard-deleted by this sweeper.
 *   - Materialised metrics tables: 13 months. Hard-deleted by this sweeper.
 *
 * Why two windows? The raw events table can contain payload fields that
 * (despite our PII discipline) might become sensitive in aggregate — IPs,
 * deviceKey hashes, error stack traces. We want them gone fast. The
 * materialised metrics tables only hold counts and so are safe to keep
 * for trend analysis.
 *
 * The sweeper is idempotent and bounded — re-running it back-to-back does
 * nothing because everything older than the threshold is already gone.
 */
import { lt, sql } from "drizzle-orm";

import { log } from "../lib/log";
import type { DbClient } from "../db/client";
import {
  events as eventsTable,
  metricsChatDaily,
  metricsErrorsDaily,
  metricsFunnelDaily,
  metricsToolUsageDaily,
} from "../db/schema";

export interface RetentionOptions {
  db: DbClient;
  /** Raw event TTL — default 30 days. */
  rawEventsTtlMs?: number;
  /** Metrics TTL — default 13 months (≈396 days). */
  metricsTtlMs?: number;
  /** Override clock — used in tests to stage "old" rows. */
  now?: () => Date;
}

export interface RetentionResult {
  rawEventsDeleted: number;
  metricsToolUsageDeleted: number;
  metricsChatDeleted: number;
  metricsFunnelDeleted: number;
  metricsErrorsDeleted: number;
}

const DAY = 24 * 60 * 60 * 1000;

export async function sweepRetention(options: RetentionOptions): Promise<RetentionResult> {
  const now = (options.now ?? (() => new Date()))();
  const rawTtl = options.rawEventsTtlMs ?? 30 * DAY;
  const metricsTtl = options.metricsTtlMs ?? 396 * DAY;

  const rawCutoff = new Date(now.getTime() - rawTtl);
  const metricsCutoff = new Date(now.getTime() - metricsTtl);
  const metricsCutoffDate = toDateKey(metricsCutoff);

  // Count first → delete. Two-step keeps us portable across drivers (PGLite's
  // `.returning()` isn't typed by Drizzle for delete in this version) and the
  // overhead of one scan is negligible for the row counts retention deals with.
  const rawDoomed = await options.db
    .select({ c: sql<number>`count(*)::int` })
    .from(eventsTable)
    .where(lt(eventsTable.createdAt, rawCutoff));
  await options.db.delete(eventsTable).where(lt(eventsTable.createdAt, rawCutoff));

  const toolDoomed = await options.db
    .select({ c: sql<number>`count(*)::int` })
    .from(metricsToolUsageDaily)
    .where(lt(metricsToolUsageDaily.date, metricsCutoffDate));
  await options.db
    .delete(metricsToolUsageDaily)
    .where(lt(metricsToolUsageDaily.date, metricsCutoffDate));

  const chatDoomed = await options.db
    .select({ c: sql<number>`count(*)::int` })
    .from(metricsChatDaily)
    .where(lt(metricsChatDaily.date, metricsCutoffDate));
  await options.db.delete(metricsChatDaily).where(lt(metricsChatDaily.date, metricsCutoffDate));

  const funnelDoomed = await options.db
    .select({ c: sql<number>`count(*)::int` })
    .from(metricsFunnelDaily)
    .where(lt(metricsFunnelDaily.date, metricsCutoffDate));
  await options.db
    .delete(metricsFunnelDaily)
    .where(lt(metricsFunnelDaily.date, metricsCutoffDate));

  const errorDoomed = await options.db
    .select({ c: sql<number>`count(*)::int` })
    .from(metricsErrorsDaily)
    .where(lt(metricsErrorsDaily.date, metricsCutoffDate));
  await options.db
    .delete(metricsErrorsDaily)
    .where(lt(metricsErrorsDaily.date, metricsCutoffDate));

  const result: RetentionResult = {
    rawEventsDeleted: rawDoomed[0]?.c ?? 0,
    metricsToolUsageDeleted: toolDoomed[0]?.c ?? 0,
    metricsChatDeleted: chatDoomed[0]?.c ?? 0,
    metricsFunnelDeleted: funnelDoomed[0]?.c ?? 0,
    metricsErrorsDeleted: errorDoomed[0]?.c ?? 0,
  };

  log.info({ ...result, rawCutoff, metricsCutoff }, "retention-sweeper: pass complete");
  return result;
}

export interface RetentionCronHandle {
  stop(): void;
  runOnce(): Promise<RetentionResult>;
}

export function startRetentionCron(
  options: RetentionOptions & { intervalMs?: number },
): RetentionCronHandle {
  const intervalMs = options.intervalMs ?? 6 * 60 * 60 * 1000; // 6 hours
  const tick = (): Promise<RetentionResult> => sweepRetention(options);

  const interval = setInterval(() => {
    void tick();
  }, intervalMs);
  if (typeof interval === "object" && "unref" in interval) {
    (interval as { unref?: () => void }).unref?.();
  }

  return { stop: () => clearInterval(interval), runOnce: tick };
}

/** Format a Date as YYYY-MM-DD UTC — matches `date` column SQL coercion. */
function toDateKey(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
