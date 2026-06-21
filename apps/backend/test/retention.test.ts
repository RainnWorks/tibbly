/**
 * Retention-sweeper contract (RAI-37).
 *
 * Confirms:
 *   - Raw events older than 30 days are hard-deleted.
 *   - Materialised metric rows older than 13 months are hard-deleted.
 *   - Anything inside the window is preserved.
 *   - Sweeper is idempotent (second run with same clock is a no-op).
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import {
  events as eventsTable,
  metricsChatDaily,
  metricsToolUsageDaily,
} from "../src/db/schema";
import { sweepRetention } from "../src/jobs/retention-sweeper";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

const NOW = new Date("2026-06-21T12:00:00Z");
const DAY = 24 * 60 * 60 * 1000;

describe("sweepRetention", () => {
  it("hard-deletes raw events older than 30 days", async () => {
    const ancient = new Date(NOW.getTime() - 31 * DAY);
    const fresh = new Date(NOW.getTime() - 1 * DAY);

    await handle.db.insert(eventsTable).values([
      {
        id: "old",
        type: "error.openrouter",
        userId: null,
        payload: { model: "x", status: 500, ms: 1 },
        createdAt: ancient,
      },
      {
        id: "new",
        type: "error.openrouter",
        userId: null,
        payload: { model: "x", status: 500, ms: 1 },
        createdAt: fresh,
      },
    ]);

    const result = await sweepRetention({ db: handle.db, now: () => NOW });
    expect(result.rawEventsDeleted).toBe(1);

    const remaining = await handle.db.select().from(eventsTable);
    expect(remaining).toHaveLength(1);
    expect(remaining[0]!.id).toBe("new");
  });

  it("preserves materialised metrics within 13 months but evicts older", async () => {
    const ancient = "2025-04-01"; // > 13 months before NOW
    const fresh = "2026-05-01"; // within window

    await handle.db.insert(metricsChatDaily).values([
      { date: ancient, userId: "u1", messageCount: 5 },
      { date: fresh, userId: "u1", messageCount: 9 },
    ]);
    await handle.db.insert(metricsToolUsageDaily).values([
      {
        date: ancient,
        toolName: "t1",
        family: "f1",
        userId: "u1",
        count: 10,
      },
      {
        date: fresh,
        toolName: "t1",
        family: "f1",
        userId: "u1",
        count: 11,
      },
    ]);

    const result = await sweepRetention({ db: handle.db, now: () => NOW });
    expect(result.metricsChatDeleted).toBe(1);
    expect(result.metricsToolUsageDeleted).toBe(1);

    const chats = await handle.db.select().from(metricsChatDaily);
    expect(chats.map((r) => r.date)).toEqual([fresh]);
    const tools = await handle.db.select().from(metricsToolUsageDaily);
    expect(tools.map((r) => r.date)).toEqual([fresh]);
  });

  it("is idempotent — second pass deletes nothing", async () => {
    const ancient = new Date(NOW.getTime() - 50 * DAY);
    await handle.db.insert(eventsTable).values({
      id: "old",
      type: "error.openrouter",
      userId: null,
      payload: { model: "x", status: 500, ms: 1 },
      createdAt: ancient,
    });

    const first = await sweepRetention({ db: handle.db, now: () => NOW });
    expect(first.rawEventsDeleted).toBe(1);

    const second = await sweepRetention({ db: handle.db, now: () => NOW });
    expect(second.rawEventsDeleted).toBe(0);
    expect(second.metricsChatDeleted).toBe(0);
    expect(second.metricsErrorsDeleted).toBe(0);
    expect(second.metricsFunnelDeleted).toBe(0);
    expect(second.metricsToolUsageDeleted).toBe(0);
  });
});
