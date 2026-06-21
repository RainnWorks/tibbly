/**
 * Tests for the GDPR Art. 15 / Art. 17 router (`apps/backend/src/api/me.ts`).
 *
 * Covers:
 *   - 401 without `x-user-id`
 *   - GET /v1/me/export returns one JSON blob with the caller's rows
 *   - GET /v1/me/export returns 404 for an unknown user
 *   - DELETE /v1/me cascades through user-owned tables, soft-deletes
 *     the `users` row and nulls its email
 *   - DELETE /v1/me detaches PII from the Stripe customer (stub Stripe)
 *   - DELETE /v1/me is idempotent (second call returns 204 with no
 *     side effects on Stripe or DB)
 *   - DELETE /v1/me anonymises `events.user_id` rather than dropping
 *     the row (so funnel metrics survive)
 *   - GET /v1/me/export does not leak another user's rows
 */
import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { eq } from "drizzle-orm";
import type Stripe from "stripe";

import { createApp } from "../src/app";
import {
  chats,
  devices,
  events,
  messages,
  osrsAccounts,
  sessions,
  subscriptions,
  tokenBalances,
  toolCalls,
  usageRecords,
  users,
} from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

const USER_ID = "user_me_aaaaaaaaaaaaaaaa";
const OTHER_USER_ID = "user_me_bbbbbbbbbbbbbbbb";

interface StubStripe {
  customers: { update: ReturnType<typeof mock> };
}

function makeStripeStub(): StubStripe {
  return {
    customers: {
      update: mock(async () => ({}) as unknown),
    },
  };
}

async function seedUser(
  options: { id?: string; email?: string; stripeCustomerId?: string } = {},
): Promise<string> {
  const id = options.id ?? USER_ID;
  await handle.db.insert(users).values({
    id,
    email: options.email ?? "alice@example.com",
    stripeCustomerId: options.stripeCustomerId ?? null,
  });
  return id;
}

async function seedSurroundingRows(userId: string): Promise<{ chatId: string; messageId: string }> {
  await handle.db.insert(devices).values({
    id: "dev_xxxxxxxxxxxxxxxxxxxx",
    userId,
    deviceKeyHash: "hash_xxxxxxxxxxxxxxxxxxxx",
  });
  await handle.db.insert(osrsAccounts).values({
    id: "acct_xxxxxxxxxxxxxxxxxxxx",
    userId,
    displayName: "Zezima",
    accountType: "main",
  });
  await handle.db.insert(sessions).values({
    id: "sess_xxxxxxxxxxxxxxxxxxxx",
    userId,
    deviceId: "dev_xxxxxxxxxxxxxxxxxxxx",
  });
  const chatId = "chat_xxxxxxxxxxxxxxxxxxxx";
  await handle.db.insert(chats).values({ id: chatId, userId });
  const messageId = "msg_xxxxxxxxxxxxxxxxxxxxx";
  await handle.db.insert(messages).values({
    id: messageId,
    chatId,
    role: "user",
    content: "hi",
  });
  await handle.db.insert(toolCalls).values({
    id: "tc_xxxxxxxxxxxxxxxxxxxxxx",
    messageId,
    toolName: "get_inventory",
  });
  await handle.db.insert(usageRecords).values({
    id: "use_xxxxxxxxxxxxxxxxxxxx",
    userId,
    model: "anthropic/claude-haiku-4.5",
    promptTokens: 1000,
    completionTokens: 500,
    costMicroUsd: 4500,
  });
  await handle.db.insert(tokenBalances).values({
    userId,
    balanceTokens: 99_000,
    tier: "hobbyist",
  });
  await handle.db.insert(events).values({
    id: "evt_xxxxxxxxxxxxxxxxxxxx",
    type: "chat.message",
    userId,
    payload: {},
  });
  return { chatId, messageId };
}

describe("/v1/me", () => {
  it("401s when x-user-id is missing", async () => {
    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(new Request("http://localhost/v1/me/export"));
    expect(res.status).toBe(401);
  });

  it("GET /v1/me/export returns the caller's rows as a JSON attachment", async () => {
    await seedUser();
    await seedSurroundingRows(USER_ID);

    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me/export", {
        headers: { "x-user-id": USER_ID },
      }),
    );

    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toContain("application/json");
    expect(res.headers.get("content-disposition")).toContain("attachment");

    const body = (await res.json()) as {
      exportVersion: number;
      user: { id: string; email: string | null };
      devices: unknown[];
      osrsAccounts: unknown[];
      chats: unknown[];
      messages: unknown[];
      toolCalls: unknown[];
      usageRecords: unknown[];
    };
    expect(body.exportVersion).toBe(2);
    expect(body.user.id).toBe(USER_ID);
    expect(body.devices).toHaveLength(1);
    expect(body.osrsAccounts).toHaveLength(1);
    expect(body.chats).toHaveLength(1);
    expect(body.messages).toHaveLength(1);
    expect(body.toolCalls).toHaveLength(1);
    expect(body.usageRecords).toHaveLength(1);
  });

  it("GET /v1/me/export returns 404 when the user row is missing", async () => {
    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me/export", {
        headers: { "x-user-id": "user_me_ghosted_zzzzzzzzz" },
      }),
    );
    expect(res.status).toBe(404);
  });

  it("DELETE /v1/me soft-deletes the user, nulls email, and cascades user-owned rows", async () => {
    await seedUser({ email: "to-delete@example.com" });
    await seedSurroundingRows(USER_ID);

    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me", {
        method: "DELETE",
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(204);

    const userRows = await handle.db.select().from(users).where(eq(users.id, USER_ID));
    expect(userRows).toHaveLength(1);
    expect(userRows[0]!.deletedAt).not.toBeNull();
    expect(userRows[0]!.email).toBeNull();

    const devicesAfter = await handle.db
      .select()
      .from(devices)
      .where(eq(devices.userId, USER_ID));
    const osrsAfter = await handle.db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.userId, USER_ID));
    const sessionsAfter = await handle.db
      .select()
      .from(sessions)
      .where(eq(sessions.userId, USER_ID));
    const chatsAfter = await handle.db.select().from(chats).where(eq(chats.userId, USER_ID));
    const usageAfter = await handle.db
      .select()
      .from(usageRecords)
      .where(eq(usageRecords.userId, USER_ID));
    const balanceAfter = await handle.db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, USER_ID));
    const subscriptionsAfter = await handle.db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.userId, USER_ID));
    expect(devicesAfter).toHaveLength(0);
    expect(osrsAfter).toHaveLength(0);
    expect(sessionsAfter).toHaveLength(0);
    expect(chatsAfter).toHaveLength(0);
    expect(usageAfter).toHaveLength(0);
    expect(balanceAfter).toHaveLength(0);
    expect(subscriptionsAfter).toHaveLength(0);
  });

  it("DELETE /v1/me anonymises events.user_id rather than dropping the row", async () => {
    await seedUser();
    await seedSurroundingRows(USER_ID);

    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me", {
        method: "DELETE",
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(204);

    const eventRows = await handle.db
      .select()
      .from(events)
      .where(eq(events.id, "evt_xxxxxxxxxxxxxxxxxxxx"));
    expect(eventRows).toHaveLength(1);
    expect(eventRows[0]!.userId).toBeNull();
  });

  it("DELETE /v1/me detaches PII from the Stripe customer", async () => {
    const stripeStub = makeStripeStub();
    await seedUser({ stripeCustomerId: "cus_TIBBLYxxxxxxxxxx" });

    const app = createApp({ me: { db: handle.db, stripe: stripeStub as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me", {
        method: "DELETE",
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(204);

    expect(stripeStub.customers.update).toHaveBeenCalledTimes(1);
    const callArgs = stripeStub.customers.update.mock.calls[0]!;
    expect(callArgs[0]).toBe("cus_TIBBLYxxxxxxxxxx");
    const update = callArgs[1] as {
      email: string;
      name: string;
      metadata: Record<string, string>;
    };
    expect(update.email).toBe("");
    expect(update.name).toBe("");
    expect(update.metadata.reason).toBe("user_requested_deletion");
    expect(update.metadata.deleted_at).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it("DELETE /v1/me is idempotent — second call returns 204 without touching Stripe", async () => {
    const stripeStub = makeStripeStub();
    await seedUser({ stripeCustomerId: "cus_TIBBLYxxxxxxxxxx" });

    const app = createApp({ me: { db: handle.db, stripe: stripeStub as unknown as Stripe } });
    const first = await app.fetch(
      new Request("http://localhost/v1/me", {
        method: "DELETE",
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(first.status).toBe(204);
    expect(stripeStub.customers.update).toHaveBeenCalledTimes(1);

    const second = await app.fetch(
      new Request("http://localhost/v1/me", {
        method: "DELETE",
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(second.status).toBe(204);
    expect(stripeStub.customers.update).toHaveBeenCalledTimes(1);
  });

  it("GET /v1/me/export does not leak another user's rows", async () => {
    await seedUser({ id: USER_ID });
    await seedUser({ id: OTHER_USER_ID, email: "bob@example.com" });
    await handle.db.insert(devices).values({
      id: "dev_OTHERxxxxxxxxxxxxxx",
      userId: OTHER_USER_ID,
      deviceKeyHash: "hash_OTHERxxxxxxxxxxxxxx",
    });

    const app = createApp({ me: { db: handle.db, stripe: makeStripeStub() as unknown as Stripe } });
    const res = await app.fetch(
      new Request("http://localhost/v1/me/export", {
        headers: { "x-user-id": USER_ID },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { devices: Array<{ id: string }> };
    expect(body.devices).toHaveLength(0);
  });
});
