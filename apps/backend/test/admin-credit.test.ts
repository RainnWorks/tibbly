/**
 * Contract for POST /admin/users/:id/credit (RAI-39 auth migration).
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import { events as eventsTable, tokenBalances, users } from "../src/db/schema";
import { OPS_SESSION_SECRET, opsSessionCookieHeader } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
const ADMIN = "tom@rowm.co";

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function app() {
  return createApp({
    adminUsers: { db: handle.db, adminEmails: [ADMIN], jwtSecret: OPS_SESSION_SECRET },
  });
}

async function adminHeaders(): Promise<Record<string, string>> {
  return { ...(await opsSessionCookieHeader(ADMIN)), "content-type": "application/json" };
}

describe("POST /admin/users/:id/credit", () => {
  it("rejects non-positive amounts", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/credit", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({ tokens: 0, reason: "test" }),
      }),
    );
    expect(res.status).toBe(400);
  });

  it("requires a reason", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/credit", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({ tokens: 1000 }),
      }),
    );
    expect(res.status).toBe(400);
  });

  it("creates a token_balances row on first credit", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/credit", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({ tokens: 1500, reason: "early-bird gift" }),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { tokensGranted: number; balanceTokens: number };
    expect(body.tokensGranted).toBe(1500);
    expect(body.balanceTokens).toBe(1500);

    const [b] = await handle.db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, "u1"));
    expect(Number(b?.balanceTokens)).toBe(1500);

    const ev = await handle.db
      .select()
      .from(eventsTable)
      .where(eq(eventsTable.type, "billing.credit_granted"));
    expect(ev).toHaveLength(1);
  });

  it("adds to an existing balance atomically", async () => {
    await handle.db.insert(users).values([{ id: "u1" }]);
    await handle.db.insert(tokenBalances).values([
      { userId: "u1", balanceTokens: 200 },
    ]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/credit", {
        method: "POST",
        headers: await adminHeaders(),
        body: JSON.stringify({ tokens: 800, reason: "apology" }),
      }),
    );
    const body = (await res.json()) as { balanceTokens: number };
    expect(body.balanceTokens).toBe(1000);
  });
});
