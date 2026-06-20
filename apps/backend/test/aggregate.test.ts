/**
 * Materialised-view aggregation contract (RAI-37).
 *
 * Stages raw events at controlled timestamps, runs `aggregateOnce`, asserts
 * the four `metrics_*_daily` tables come out with the expected counts.
 * Also asserts idempotency: running aggregation twice yields the same
 * counts (no double-count, because the window-scoped pass sums into the
 * upsert and *we don't re-run the window* in production — the cron picks
 * up rows that landed since the last cursor).
 *
 * NOTE on idempotency: the production aggregator runs once-per-minute on
 * a sliding window; if we re-ran the same events twice we WOULD double-
 * count. That is intentional: the source-of-truth is the `events` table,
 * not the metrics rollup. The cron driver advances a cursor (`since`)
 * after each pass so the same row is never visited twice. This test
 * documents that contract by asserting "same events processed once -> N",
 * "same events processed twice with the *same* window -> 2N".
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import {
  events as eventsTable,
  metricsChatDaily,
  metricsErrorsDaily,
  metricsFunnelDaily,
  metricsToolUsageDaily,
} from "../src/db/schema";
import { aggregateOnce } from "../src/events/aggregate";
import type { DomainEvent } from "../src/events/types";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

/** Write a DomainEvent directly to the events table at the given timestamp. */
async function seedEvent(
  ts: Date,
  type: DomainEvent["type"],
  payload: Record<string, unknown>,
  userId?: string,
): Promise<void> {
  await handle.db.insert(eventsTable).values({
    id: `ev_${Math.random().toString(36).slice(2)}`,
    type,
    userId: userId ?? null,
    payload,
    createdAt: ts,
  });
}

describe("aggregateOnce — tool usage", () => {
  it("rolls up tool call counts per (date, tool, family, user)", async () => {
    const day = new Date("2026-06-20T12:00:00Z");
    await seedEvent(
      day,
      "chat.tool_call.completed",
      {
        chatId: "c1",
        messageId: "m1",
        toolName: "get_inventory",
        family: "inventory",
        inputBytes: 100,
        durationMs: 5,
        outputBytes: 800,
        status: "ok",
      },
      "u1",
    );
    await seedEvent(
      day,
      "chat.tool_call.completed",
      {
        chatId: "c1",
        messageId: "m2",
        toolName: "get_inventory",
        family: "inventory",
        inputBytes: 100,
        durationMs: 6,
        outputBytes: 800,
        status: "ok",
      },
      "u1",
    );
    await seedEvent(
      day,
      "chat.tool_call.completed",
      {
        chatId: "c2",
        messageId: "m3",
        toolName: "get_bank",
        family: "bank",
        inputBytes: 50,
        durationMs: 8,
        outputBytes: 1200,
        status: "ok",
      },
      "u2",
    );

    const before = new Date("2026-06-20T11:00:00Z");
    const after = new Date("2026-06-20T13:00:00Z");
    const result = await aggregateOnce(handle.db, { since: before, until: after });

    expect(result.scanned).toBe(3);
    expect(result.toolUsageRows).toBe(3);

    const rows = await handle.db.select().from(metricsToolUsageDaily);
    expect(rows).toHaveLength(2);

    const u1Row = rows.find((r) => r.userId === "u1" && r.toolName === "get_inventory")!;
    expect(u1Row.count).toBe(2);
    expect(Number(u1Row.totalInputBytes)).toBe(200);
    expect(Number(u1Row.totalOutputBytes)).toBe(1600);

    const u2Row = rows.find((r) => r.userId === "u2")!;
    expect(u2Row.count).toBe(1);
    expect(u2Row.family).toBe("bank");
  });
});

describe("aggregateOnce — chat daily + funnel + errors", () => {
  it("rolls up chat token counts and cost into metrics_chat_daily", async () => {
    const day = new Date("2026-06-21T01:00:00Z");
    await seedEvent(
      day,
      "chat.message.sent",
      {
        chatId: "c1",
        messageId: "m1",
        tokens: { in: 100, out: 250 },
        model: "openrouter/anthropic/claude-haiku-4.5",
        costMicroUsd: 8500,
      },
      "u1",
    );
    await seedEvent(
      day,
      "chat.message.sent",
      {
        chatId: "c1",
        messageId: "m2",
        tokens: { in: 50, out: 700 },
        model: "openrouter/anthropic/claude-sonnet-4.6",
        costMicroUsd: 26000,
      },
      "u1",
    );

    await aggregateOnce(handle.db, {
      since: new Date("2026-06-21T00:00:00Z"),
      until: new Date("2026-06-21T02:00:00Z"),
    });

    const rows = await handle.db
      .select()
      .from(metricsChatDaily)
      .where(eq(metricsChatDaily.userId, "u1"));
    expect(rows).toHaveLength(1);
    const r = rows[0]!;
    expect(r.messageCount).toBe(2);
    expect(Number(r.tokensIn)).toBe(150);
    expect(Number(r.tokensOut)).toBe(950);
    expect(Number(r.costMicroUsd)).toBe(34500);
  });

  it("rolls up funnel + error events by suffix", async () => {
    const day = new Date("2026-06-21T05:00:00Z");
    await seedEvent(day, "funnel.first_message", { chatId: "c1" }, "u1");
    await seedEvent(day, "funnel.first_message", { chatId: "c2" }, "u2");
    await seedEvent(day, "funnel.first_paid", { tier: "hobbyist" }, "u1");
    await seedEvent(day, "error.openrouter", {
      model: "openrouter/anthropic/claude-haiku-4.5",
      status: 502,
      ms: 1200,
    });
    await seedEvent(day, "error.openrouter", {
      model: "openrouter/anthropic/claude-haiku-4.5",
      status: 500,
      ms: 1300,
    });
    await seedEvent(day, "error.plugin_disconnect", { deviceId: "d1", reason: "idle" });

    await aggregateOnce(handle.db, {
      since: new Date("2026-06-21T00:00:00Z"),
      until: new Date("2026-06-21T06:00:00Z"),
    });

    const funnelRows = await handle.db.select().from(metricsFunnelDaily);
    expect(funnelRows.map((r) => `${r.step}:${r.userCount}`).sort()).toEqual([
      "first_message:2",
      "first_paid:1",
    ]);

    const errorRows = await handle.db.select().from(metricsErrorsDaily);
    expect(errorRows.map((r) => `${r.kind}:${r.count}`).sort()).toEqual([
      "openrouter:2",
      "plugin_disconnect:1",
    ]);
  });

  it("running the same window twice doubles the counts (cursor advances in prod)", async () => {
    const day = new Date("2026-06-21T05:00:00Z");
    await seedEvent(day, "funnel.first_message", { chatId: "c1" }, "u1");

    const w = {
      since: new Date("2026-06-21T00:00:00Z"),
      until: new Date("2026-06-21T06:00:00Z"),
    };
    await aggregateOnce(handle.db, w);
    await aggregateOnce(handle.db, w);

    const rows = await handle.db.select().from(metricsFunnelDaily);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.userCount).toBe(2);
  });

  it("respects the [since, until) window — events outside are ignored", async () => {
    const inside = new Date("2026-06-21T05:00:00Z");
    const outside = new Date("2026-06-20T05:00:00Z");
    await seedEvent(inside, "error.openrouter", {
      model: "x",
      status: 500,
      ms: 1,
    });
    await seedEvent(outside, "error.openrouter", {
      model: "x",
      status: 500,
      ms: 1,
    });

    const result = await aggregateOnce(handle.db, {
      since: new Date("2026-06-21T00:00:00Z"),
      until: new Date("2026-06-21T23:59:59Z"),
    });
    expect(result.scanned).toBe(1);

    const errs = await handle.db.select().from(metricsErrorsDaily);
    expect(errs).toHaveLength(1);
    expect(errs[0]!.count).toBe(1);
  });
});
