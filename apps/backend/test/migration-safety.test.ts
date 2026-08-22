/**
 * RAI-59: the guards added by `0006_rai59_migration_safety.sql` must actually
 * be enforced by the DB the tests boot, not just present in the file.
 */
import { describe, expect, test } from "bun:test";

import { makeTestDb } from "./_db-fixture";

describe("0006_rai59_migration_safety", () => {
  test("metrics_*_pk indexes are unique", async () => {
    const h = await makeTestDb();
    try {
      const res = await h.raw.query<{ indexname: string; indexdef: string }>(
        `SELECT indexname, indexdef FROM pg_indexes
         WHERE indexname IN ('metrics_tool_usage_daily_pk','metrics_chat_daily_pk',
                             'metrics_funnel_daily_pk','metrics_errors_daily_pk')`,
      );
      expect(res.rows.length).toBe(4);
      for (const row of res.rows) {
        expect(row.indexdef).toContain("CREATE UNIQUE INDEX");
      }
    } finally {
      await h.close();
    }
  });

  test("a duplicate metrics_chat_daily row is rejected", async () => {
    const h = await makeTestDb();
    try {
      await h.raw.exec(
        `INSERT INTO metrics_chat_daily (date, user_id) VALUES ('2026-01-01', 'u_1')`,
      );
      await expect(
        h.raw.exec(
          `INSERT INTO metrics_chat_daily (date, user_id) VALUES ('2026-01-01', 'u_1')`,
        ),
      ).rejects.toThrow();
    } finally {
      await h.close();
    }
  });

  test("processed_stripe_events rejects an over-long event id", async () => {
    const h = await makeTestDb();
    try {
      const huge = `evt_${"a".repeat(200)}`;
      await expect(
        h.raw.exec(
          `INSERT INTO processed_stripe_events (event_id, event_type)
           VALUES ('${huge}', 'invoice.payment_succeeded')`,
        ),
      ).rejects.toThrow();
      // The shapes the webhook handler and its fixtures actually use still pass.
      await h.raw.exec(
        `INSERT INTO processed_stripe_events (event_id, event_type)
         VALUES ('evt_checkout_1', 'checkout.session.completed')`,
      );
    } finally {
      await h.close();
    }
  });

  test("re-running the migration file is a no-op", async () => {
    const h = await makeTestDb();
    try {
      const sql = await Bun.file(
        new URL("../migrations/0006_rai59_migration_safety.sql", import.meta.url).pathname,
      ).text();
      for (const stmt of sql
        .split(/-->\s*statement-breakpoint/g)
        .map((s) => s.trim())
        .filter(Boolean)) {
        await h.raw.exec(stmt);
      }
    } finally {
      await h.close();
    }
  });
});
