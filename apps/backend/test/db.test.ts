/**
 * Schema smoke tests (RAI-15).
 *
 * Each test spins up a fresh in-memory PGLite, applies the generated migration
 * SQL, inserts a row into one of the 11 tables, and reads it back. The point
 * is to catch shape regressions (column rename, constraint drift, FK breakage)
 * without depending on a real Postgres.
 *
 * Why we run the SQL by hand instead of `drizzle-kit migrate`:
 *   PGLite is in-memory so there's no `DATABASE_URL` for drizzle-kit to point
 *   at, and Bun's test runner doesn't ship with a TS-aware drizzle-kit shim.
 *   The SQL we exec here is the literal output of `bun drizzle-kit generate`,
 *   so a divergence between schema.ts and the migration would still fail.
 */
import { PGlite } from "@electric-sql/pglite";
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";
import { drizzle } from "drizzle-orm/pglite";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * Drizzle's insert/update builders are PromiseLike, not Promise. Bun's
 * `expect(...).rejects.toThrow()` requires a Promise. Wrapping in
 * `Promise.resolve(builder)` converts the thenable into a real Promise so
 * the matcher can wait on it.
 */
function asPromise<T>(thenable: PromiseLike<T>): Promise<T> {
  return Promise.resolve(thenable);
}

import {
  chats,
  devices,
  messages,
  newId,
  osrsAccounts,
  pairingCodes,
  sessions,
  subscriptions,
  tokenBalances,
  toolCalls,
  usageRecords,
  users,
} from "../src/db/schema";
import * as schema from "../src/db/schema";

const MIGRATION_PATH = join(import.meta.dir, "..", "migrations", "0000_merged_schema.sql");

type Db = ReturnType<typeof drizzle<typeof schema>>;

async function freshDb(): Promise<{ pg: PGlite; db: Db }> {
  const pg = new PGlite();
  const sqlText = readFileSync(MIGRATION_PATH, "utf-8");
  // Drizzle's emitted SQL is split by `--> statement-breakpoint`. PGLite's
  // `exec` would also accept the whole blob, but splitting gives cleaner
  // error messages when a statement fails.
  const statements = sqlText
    .split("--> statement-breakpoint")
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  for (const statement of statements) {
    await pg.exec(statement);
  }
  const db = drizzle(pg, { schema });
  return { pg, db };
}

async function seedUser(db: Db, overrides: Partial<typeof users.$inferInsert> = {}) {
  const [row] = await db
    .insert(users)
    .values({
      email: `tom-${newId()}@example.com`,
      stripeCustomerId: `cus_${newId()}`,
      ...overrides,
    })
    .returning();
  if (!row) throw new Error("seedUser: insert returned no row");
  return row;
}

async function seedDevice(db: Db, userId: string) {
  const [row] = await db
    .insert(devices)
    .values({
      userId,
      deviceKeyHash: `hash_${newId()}`,
      displayName: "tom's laptop",
    })
    .returning();
  if (!row) throw new Error("seedDevice: insert returned no row");
  return row;
}

async function seedSession(db: Db, userId: string, deviceId: string) {
  const [row] = await db.insert(sessions).values({ deviceId, userId }).returning();
  if (!row) throw new Error("seedSession: insert returned no row");
  return row;
}

async function seedChat(db: Db, userId: string, sessionId: string | null = null) {
  const [row] = await db
    .insert(chats)
    .values({ userId, sessionId, title: "first chat" })
    .returning();
  if (!row) throw new Error("seedChat: insert returned no row");
  return row;
}

async function seedMessage(db: Db, chatId: string) {
  const [row] = await db
    .insert(messages)
    .values({
      chatId,
      role: "user",
      content: "where is the closest fairy ring to lumbridge?",
      model: "anthropic/claude-haiku-4.5",
      promptTokens: 42,
      completionTokens: 0,
    })
    .returning();
  if (!row) throw new Error("seedMessage: insert returned no row");
  return row;
}

describe("schema: round-trip per table", () => {
  let pg: PGlite;
  let db: Db;

  beforeEach(async () => {
    const fresh = await freshDb();
    pg = fresh.pg;
    db = fresh.db;
  });

  afterEach(async () => {
    await pg.close();
  });

  it("users — insert + read", async () => {
    const inserted = await seedUser(db, { email: "alice@example.com" });
    const [readback] = await db.select().from(users).where(eq(users.id, inserted.id));
    expect(readback?.email).toBe("alice@example.com");
    expect(readback?.status).toBe("active");
    expect(readback?.deletedAt).toBeNull();
    expect(readback?.createdAt).toBeInstanceOf(Date);
  });

  it("devices — insert + read + (user_id, device_key_hash) unique", async () => {
    const user = await seedUser(db);
    const device = await seedDevice(db, user.id);
    const [readback] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(readback?.userId).toBe(user.id);
    expect(readback?.displayName).toBe("tom's laptop");

    // Duplicate (user_id, device_key_hash) must fail.
    await expect(
      asPromise(
        db.insert(devices).values({ userId: user.id, deviceKeyHash: device.deviceKeyHash }),
      ),
    ).rejects.toThrow();
  });

  it("osrs_accounts — insert + read", async () => {
    const user = await seedUser(db);
    const [acct] = await db
      .insert(osrsAccounts)
      .values({
        userId: user.id,
        displayName: "Zezima",
        accountType: "ironman",
        status: "verified",
      })
      .returning();
    const [readback] = await db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.id, acct!.id));
    expect(readback?.displayName).toBe("Zezima");
    expect(readback?.accountType).toBe("ironman");
    expect(readback?.status).toBe("verified");
  });

  it("pairing_codes — insert + read + unique code", async () => {
    const user = await seedUser(db);
    const device = await seedDevice(db, user.id);
    const expires = new Date(Date.now() + 10 * 60_000);
    const code = "123456";
    const [pc] = await db
      .insert(pairingCodes)
      .values({ code, deviceId: device.id, expiresAt: expires })
      .returning();
    const [readback] = await db
      .select()
      .from(pairingCodes)
      .where(eq(pairingCodes.id, pc!.id));
    expect(readback?.code).toBe(code);
    expect(readback?.usedAt).toBeNull();

    // Same code on a fresh device must conflict.
    const device2 = await seedDevice(db, user.id);
    await expect(
      asPromise(
        db
          .insert(pairingCodes)
          .values({ code, deviceId: device2.id, expiresAt: expires }),
      ),
    ).rejects.toThrow();
  });

  it("sessions — insert + read + at most one active per device", async () => {
    const user = await seedUser(db);
    const device = await seedDevice(db, user.id);
    const session = await seedSession(db, user.id, device.id);

    const [readback] = await db.select().from(sessions).where(eq(sessions.id, session.id));
    expect(readback?.deviceId).toBe(device.id);
    expect(readback?.endedAt).toBeNull();

    // Cannot open a second active session for the same device.
    await expect(
      asPromise(db.insert(sessions).values({ deviceId: device.id, userId: user.id })),
    ).rejects.toThrow();

    // End the first, then a new one is allowed.
    await db.update(sessions).set({ endedAt: new Date() }).where(eq(sessions.id, session.id));
    const [second] = await db
      .insert(sessions)
      .values({ deviceId: device.id, userId: user.id })
      .returning();
    expect(second?.id).not.toBe(session.id);
  });

  it("chats — insert + read + soft-delete column present", async () => {
    const user = await seedUser(db);
    const chat = await seedChat(db, user.id);
    const [readback] = await db.select().from(chats).where(eq(chats.id, chat.id));
    expect(readback?.title).toBe("first chat");
    expect(readback?.deletedAt).toBeNull();

    // Soft delete round-trip.
    const now = new Date();
    await db.update(chats).set({ deletedAt: now }).where(eq(chats.id, chat.id));
    const [softDeleted] = await db.select().from(chats).where(eq(chats.id, chat.id));
    expect(softDeleted?.deletedAt).toBeInstanceOf(Date);
  });

  it("messages — insert + read + chat FK + soft-delete column present", async () => {
    const user = await seedUser(db);
    const chat = await seedChat(db, user.id);
    const msg = await seedMessage(db, chat.id);
    const [readback] = await db.select().from(messages).where(eq(messages.id, msg.id));
    expect(readback?.role).toBe("user");
    expect(readback?.promptTokens).toBe(42);
    expect(readback?.deletedAt).toBeNull();
  });

  it("tool_calls — insert + read + jsonb round-trips structured input", async () => {
    const user = await seedUser(db);
    const chat = await seedChat(db, user.id);
    const msg = await seedMessage(db, chat.id);
    const input = { region: "lumbridge", radius: 10 };
    const output = { fairyRing: { code: "DLR", distance: 24 } };
    const [tc] = await db
      .insert(toolCalls)
      .values({
        messageId: msg.id,
        toolName: "find_nearest_fairy_ring",
        input,
        outputJson: output,
        durationMs: 87,
        status: "ok",
      })
      .returning();
    const [readback] = await db.select().from(toolCalls).where(eq(toolCalls.id, tc!.id));
    expect(readback?.toolName).toBe("find_nearest_fairy_ring");
    expect(readback?.input).toEqual(input);
    expect(readback?.outputJson).toEqual(output);
    expect(readback?.status).toBe("ok");
    expect(readback?.durationMs).toBe(87);
  });

  it("usage_records — insert + read", async () => {
    const user = await seedUser(db);
    const chat = await seedChat(db, user.id);
    const [u] = await db
      .insert(usageRecords)
      .values({
        userId: user.id,
        chatId: chat.id,
        model: "anthropic/claude-sonnet-4.6",
        promptTokens: 1200,
        completionTokens: 400,
        costMicroUsd: 4_800,
      })
      .returning();
    const [readback] = await db
      .select()
      .from(usageRecords)
      .where(eq(usageRecords.id, u!.id));
    expect(readback?.model).toBe("anthropic/claude-sonnet-4.6");
    expect(readback?.promptTokens).toBe(1200);
    expect(readback?.completionTokens).toBe(400);
    expect(readback?.costMicroUsd).toBe(4_800);
  });

  it("subscriptions — insert + read + unique stripe_subscription_id", async () => {
    const user = await seedUser(db);
    const stripeSubId = `sub_${newId()}`;
    const now = new Date();
    const periodEnd = new Date(now.getTime() + 30 * 24 * 60 * 60_000);
    const [sub] = await db
      .insert(subscriptions)
      .values({
        userId: user.id,
        stripeSubscriptionId: stripeSubId,
        tier: "pro",
        status: "active",
        monthlyQuotaTokens: 5_000_000,
        currentPeriodStart: now,
        currentPeriodEnd: periodEnd,
      })
      .returning();
    const [readback] = await db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.id, sub!.id));
    expect(readback?.tier).toBe("pro");
    expect(readback?.status).toBe("active");
    expect(readback?.monthlyQuotaTokens).toBe(5_000_000);

    // Unique stripe_subscription_id.
    const other = await seedUser(db);
    await expect(
      asPromise(
        db.insert(subscriptions).values({
          userId: other.id,
          stripeSubscriptionId: stripeSubId,
          tier: "hobbyist",
          status: "active",
          currentPeriodStart: now,
          currentPeriodEnd: periodEnd,
        }),
      ),
    ).rejects.toThrow();
  });

  it("token_balances — insert + read + user_id PK", async () => {
    const user = await seedUser(db);
    const [tb] = await db
      .insert(tokenBalances)
      .values({ userId: user.id, balanceTokens: 1_000_000 })
      .returning();
    expect(tb?.balanceTokens).toBe(1_000_000);

    const [readback] = await db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, user.id));
    expect(readback?.balanceTokens).toBe(1_000_000);

    // Second insert for same user must fail (PK collision).
    await expect(
      asPromise(db.insert(tokenBalances).values({ userId: user.id, balanceTokens: 1 })),
    ).rejects.toThrow();
  });
});

describe("schema: migration + retention contract", () => {
  it("applies cleanly against a fresh PGLite instance", async () => {
    const { pg } = await freshDb();
    const res = await pg.query<{ count: number }>(
      "SELECT count(*)::int AS count FROM information_schema.tables WHERE table_schema = 'public'",
    );
    // 11 RAI-15 tables (users, devices, osrs_accounts, pairing_codes,
    // sessions, chats, messages, tool_calls, usage_records, subscriptions,
    // token_balances) + 5 RAI-37 analytics tables (events,
    // metrics_tool_usage_daily, metrics_chat_daily, metrics_funnel_daily,
    // metrics_errors_daily). No drizzle migrations table because we apply
    // the SQL by hand in this test fixture.
    expect(res.rows[0]?.count).toBe(16);
    await pg.close();
  });

  it("users / chats / messages all carry deleted_at for the retention sweeper", async () => {
    const { pg, db } = await freshDb();
    const user = await seedUser(db);
    const chat = await seedChat(db, user.id);
    const msg = await seedMessage(db, chat.id);

    const t = new Date();
    await db.update(users).set({ deletedAt: t }).where(eq(users.id, user.id));
    await db.update(chats).set({ deletedAt: t }).where(eq(chats.id, chat.id));
    await db.update(messages).set({ deletedAt: t }).where(eq(messages.id, msg.id));

    const [u] = await db.select().from(users).where(eq(users.id, user.id));
    const [c] = await db.select().from(chats).where(eq(chats.id, chat.id));
    const [m] = await db.select().from(messages).where(eq(messages.id, msg.id));
    expect(u?.deletedAt).toBeInstanceOf(Date);
    expect(c?.deletedAt).toBeInstanceOf(Date);
    expect(m?.deletedAt).toBeInstanceOf(Date);
    await pg.close();
  });
});
