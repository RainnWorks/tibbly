/**
 * Event bus + persistence contract (RAI-37).
 *
 * Locks in:
 *   - `bus.publish` validates payloads via the per-type Zod schemas.
 *   - Typed subscribers receive only matching events; wildcard sees all.
 *   - Persistence writes the envelope to the `events` table with the
 *     original timestamp and full payload.
 *   - Subscriber exceptions are swallowed and routed to `onError`.
 *   - `parseEvent` replays a raw row back to the typed shape.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { events as eventsTable } from "../src/db/schema";
import { attachEventPersister, createEventBus, parseEvent } from "../src/events";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

describe("event bus", () => {
  it("validates payloads and rejects malformed events", async () => {
    const bus = createEventBus();
    await expect(
      bus.publish({
        type: "chat.message.sent",
        // missing required fields
        payload: { chatId: "c1" } as never,
      }),
    ).rejects.toThrow();
  });

  it("routes typed subscribers to their type only", async () => {
    const bus = createEventBus();
    const seenChat: string[] = [];
    const seenError: string[] = [];

    bus.on("chat.message.sent", (e) => {
      seenChat.push(e.payload.chatId);
    });
    bus.on("error.openrouter", (e) => {
      seenError.push(String(e.payload.status));
    });

    await bus.publish({
      type: "chat.message.sent",
      userId: "u1",
      payload: {
        chatId: "c1",
        messageId: "m1",
        tokens: { in: 5, out: 7 },
        model: "openrouter/anthropic/claude-haiku-4.5",
      },
    });
    await bus.publish({
      type: "error.openrouter",
      payload: { model: "openrouter/anthropic/claude-haiku-4.5", status: 502, ms: 1200 },
    });

    expect(seenChat).toEqual(["c1"]);
    expect(seenError).toEqual(["502"]);
  });

  it("fires wildcard subscribers for every event", async () => {
    const bus = createEventBus();
    const types: string[] = [];
    bus.onAny((e) => {
      types.push(e.type);
    });

    await bus.publish({
      type: "funnel.first_message",
      userId: "u1",
      payload: { chatId: "c1" },
    });
    await bus.publish({
      type: "auth.pairing.requested",
      payload: { deviceKey: "dk_test" },
    });
    expect(types).toEqual(["funnel.first_message", "auth.pairing.requested"]);
  });

  it("isolates a broken subscriber via onError", async () => {
    const errors: unknown[] = [];
    const bus = createEventBus({ onError: (err) => errors.push(err) });

    bus.on("chat.cap_hit", () => {
      throw new Error("boom");
    });

    let goodFired = false;
    bus.onAny(() => {
      goodFired = true;
    });

    await bus.publish({
      type: "chat.cap_hit",
      userId: "u1",
      payload: { chatId: "c1", balanceBefore: 0 },
    });

    expect(errors).toHaveLength(1);
    expect((errors[0] as Error).message).toBe("boom");
    expect(goodFired).toBe(true);
  });

  it("size + clear + unsubscribe handles work", async () => {
    const bus = createEventBus();
    const off1 = bus.on("chat.cap_hit", () => {});
    const off2 = bus.onAny(() => {});
    expect(bus.size()).toEqual({ typed: 1, wildcard: 1 });
    off1();
    off2();
    expect(bus.size()).toEqual({ typed: 0, wildcard: 0 });
  });

  it("parseEvent round-trips a stored envelope", () => {
    const sample = {
      id: "ev_123",
      timestamp: new Date("2026-06-21T00:00:00Z"),
      type: "billing.balance.decremented",
      userId: "u1",
      payload: { amount: 100, model: "x", newBalance: 900 },
    };
    const parsed = parseEvent(sample);
    expect(parsed.type).toBe("billing.balance.decremented");
    if (parsed.type === "billing.balance.decremented") {
      expect(parsed.payload.amount).toBe(100);
    }
  });

  it("parseEvent rejects unknown event types", () => {
    expect(() => parseEvent({ type: "unknown.thing" })).toThrow();
  });
});

describe("event persister", () => {
  let handle: TestDbHandle;
  beforeEach(async () => {
    handle = await makeTestDb();
  });
  afterEach(async () => {
    await handle.close();
  });

  it("writes published events to the events table", async () => {
    const bus = createEventBus();
    attachEventPersister(bus, { db: handle.db });

    await bus.publish({
      type: "chat.tool_call.completed",
      userId: "u1",
      payload: {
        chatId: "c1",
        messageId: "m1",
        toolName: "get_inventory",
        family: "inventory",
        inputBytes: 100,
        durationMs: 12,
        outputBytes: 800,
        status: "ok",
      },
    });

    const rows = await handle.db.select().from(eventsTable);
    expect(rows).toHaveLength(1);
    const row = rows[0]!;
    expect(row.type).toBe("chat.tool_call.completed");
    expect(row.userId).toBe("u1");
    expect((row.payload as { toolName: string }).toolName).toBe("get_inventory");
  });

  it("survives a persistence error", async () => {
    const captured: unknown[] = [];
    const bus = createEventBus();
    // Inject a DB that throws on insert.
    attachEventPersister(bus, {
      db: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        insert: () => ({ values: () => Promise.reject(new Error("db down")) }) as any,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
      } as any,
      onError: (err) => captured.push(err),
    });

    await bus.publish({
      type: "funnel.plugin.installed",
      payload: { deviceKey: "dk_test", pluginVersion: "0.1.0" },
    });

    expect(captured).toHaveLength(1);
    expect((captured[0] as Error).message).toBe("db down");
  });
});
