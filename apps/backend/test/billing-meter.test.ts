/**
 * Token meter contract (RAI-20).
 *
 * Pins:
 *   - `ensureCanSpend` succeeds while the balance covers the request,
 *     atomically decrements, and BLOCKS once the balance can't.
 *   - Concurrent requests sum exactly — no double-spend.
 *   - `chat.cap_hit` event fires only on rejection, with the pre-decrement
 *     balance attached.
 *   - `record` reconciles actual usage above the pre-debited reservation,
 *     and emits `billing.balance.decremented` with the model id.
 *   - `credit` is atomic + idempotent in the natural sense (sums on
 *     existing rows).
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createTokenMeter } from "../src/billing/meter";
import { tokenBalances, users } from "../src/db/schema";
import { createEventBus, type DomainEvent } from "../src/events";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

interface Fixture {
  handle: TestDbHandle;
  bus: ReturnType<typeof createEventBus>;
  events: DomainEvent[];
  meter: ReturnType<typeof createTokenMeter>;
}

async function setup(initialBalance: number): Promise<Fixture> {
  const handle = await makeTestDb();
  const bus = createEventBus();
  const events: DomainEvent[] = [];
  bus.onAny((e) => {
    events.push(e);
  });

  // Seed a single user with a known balance — every meter test runs against
  // it. We don't need other schema rows for these tests.
  const [user] = await handle.db
    .insert(users)
    .values({ id: "user_meter_test", email: "meter@example.com" })
    .returning();
  if (!user) throw new Error("seed: user insert returned no row");
  await handle.db
    .insert(tokenBalances)
    .values({ userId: user.id, balanceTokens: initialBalance });

  const meter = createTokenMeter({ db: handle.db, bus });
  return { handle, bus, events, meter };
}

describe("token meter", () => {
  let f: Fixture;
  afterEach(async () => {
    await f.handle.close();
  });

  it("blocks once balance hits zero", async () => {
    f = await setup(1000);
    const userId = "user_meter_test";

    // 600 of 1000 → balance 400.
    const r1 = await f.meter.ensureCanSpend(userId, 600);
    expect(r1).not.toBeNull();
    expect(r1!.newBalance).toBe(400);
    expect(await f.meter.getBalance(userId)).toBe(400);

    // 500 of 400 → blocked. New balance unchanged.
    const r2 = await f.meter.ensureCanSpend(userId, 500, { chatId: "chat_test" });
    expect(r2).toBeNull();
    expect(await f.meter.getBalance(userId)).toBe(400);

    // Cap_hit event with pre-decrement balance.
    const capHit = f.events.find((e) => e.type === "chat.cap_hit");
    expect(capHit).toBeDefined();
    if (capHit?.type === "chat.cap_hit") {
      expect(capHit.payload.balanceBefore).toBe(400);
      expect(capHit.payload.chatId).toBe("chat_test");
      expect(capHit.userId).toBe(userId);
    }
  });

  it("does not emit cap_hit on a successful reservation", async () => {
    f = await setup(1000);
    await f.meter.ensureCanSpend("user_meter_test", 100);
    const capHit = f.events.find((e) => e.type === "chat.cap_hit");
    expect(capHit).toBeUndefined();
  });

  it("concurrent reservations sum exactly — no double spend", async () => {
    // 10 parallel reservations of 100 against a balance of 500 should leave
    // exactly 5 successes and 5 rejections. With the atomic UPDATE … WHERE
    // balance_tokens >= X predicate this is guaranteed.
    f = await setup(500);
    const userId = "user_meter_test";

    const results = await Promise.all(
      Array.from({ length: 10 }, () => f.meter.ensureCanSpend(userId, 100)),
    );

    const successes = results.filter((r) => r !== null).length;
    const rejects = results.filter((r) => r === null).length;
    expect(successes).toBe(5);
    expect(rejects).toBe(5);

    // Final balance reflects the 5 atomic decrements.
    expect(await f.meter.getBalance(userId)).toBe(0);
  });

  it("record reconciles above the pre-debit and emits balance.decremented", async () => {
    f = await setup(10_000);
    const userId = "user_meter_test";

    // Reserve 100 up-front.
    const reserved = await f.meter.ensureCanSpend(userId, 100);
    expect(reserved!.newBalance).toBe(9900);

    // Actual usage 400 → debit delta 300.
    const rec = await f.meter.record({
      userId,
      model: "anthropic/claude-haiku-4.5",
      promptTokens: 250,
      completionTokens: 150,
      preDebited: 100,
    });
    expect(rec.debited).toBe(300);
    expect(rec.newBalance).toBe(9600);
    // Cost: 250*1 + 150*5 = 1000 µUSD.
    expect(rec.costMicroUsd).toBe(1000);

    // Event with the model id surfaced.
    const decremented = f.events.find((e) => e.type === "billing.balance.decremented");
    expect(decremented).toBeDefined();
    if (decremented?.type === "billing.balance.decremented") {
      expect(decremented.payload.amount).toBe(300);
      expect(decremented.payload.model).toBe("anthropic/claude-haiku-4.5");
      expect(decremented.payload.newBalance).toBe(9600);
    }
  });

  it("record clamps at zero when actual exceeds remaining (mid-flight)", async () => {
    f = await setup(50);
    const userId = "user_meter_test";
    // Pre-debit takes the balance to zero already.
    await f.meter.ensureCanSpend(userId, 50);
    expect(await f.meter.getBalance(userId)).toBe(0);

    // Actual usage of 500 tokens overshoots — we let the in-flight call
    // finish but the balance can't go negative.
    const rec = await f.meter.record({
      userId,
      model: "anthropic/claude-haiku-4.5",
      promptTokens: 300,
      completionTokens: 200,
      preDebited: 50,
    });
    expect(rec.debited).toBe(450);
    expect(rec.newBalance).toBe(0);
  });

  it("does not emit balance.decremented when usage is fully covered by pre-debit", async () => {
    f = await setup(1000);
    const userId = "user_meter_test";
    await f.meter.ensureCanSpend(userId, 100);
    f.events.length = 0;

    await f.meter.record({
      userId,
      model: "anthropic/claude-haiku-4.5",
      promptTokens: 50,
      completionTokens: 30,
      preDebited: 100,
    });
    const decremented = f.events.find((e) => e.type === "billing.balance.decremented");
    expect(decremented).toBeUndefined();
  });

  it("credit tops up an existing balance idempotently", async () => {
    f = await setup(0);
    const userId = "user_meter_test";

    const r1 = await f.meter.credit(userId, 100_000);
    expect(r1.newBalance).toBe(100_000);
    const r2 = await f.meter.credit(userId, 50_000);
    expect(r2.newBalance).toBe(150_000);
    expect(await f.meter.getBalance(userId)).toBe(150_000);
  });

  it("ensureCanSpend(0) is a no-op that returns the current balance", async () => {
    f = await setup(123);
    const r = await f.meter.ensureCanSpend("user_meter_test", 0);
    expect(r).not.toBeNull();
    expect(r!.newBalance).toBe(123);
  });

  it("returns null when the user has no balance row", async () => {
    // Fresh fixture with an extra user but no balance row for them.
    f = await setup(0);
    const [user] = await f.handle.db
      .insert(users)
      .values({ id: "user_no_balance", email: "no-balance@example.com" })
      .returning();
    if (!user) throw new Error("seed: user insert returned no row");
    const r = await f.meter.ensureCanSpend(user.id, 100);
    expect(r).toBeNull();
  });

  // tests that depend on a fresh fixture per `it` — wire up a default so
  // afterEach doesn't crash on the first test if setup wasn't called.
  beforeEach(async () => {
    // no-op; each test calls `setup` explicitly so we know the initial balance.
  });

  // safety: drop the (unreferenced) helper if some tests skipped setup.
  it("eq + tokenBalances import is wired", async () => {
    f = await setup(7);
    const rows = await f.handle.db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, "user_meter_test"));
    expect(rows[0]?.balanceTokens).toBe(7);
  });
});
