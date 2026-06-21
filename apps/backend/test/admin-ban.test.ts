/**
 * Contract for POST /admin/users/:id/ban|unban (RAI-39 auth migration).
 *
 * - Auth: ops_session JWT cookie (audit C2 closed).
 * - Ban flips status to "banned" and ends any active sessions.
 * - Unban flips it back to "active".
 * - Both emit an `events` row so the audit trail is durable.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { and, eq } from "drizzle-orm";

import { createApp } from "../src/app";
import { devices, events as eventsTable, sessions, users } from "../src/db/schema";
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

async function adminHeaders(body?: boolean): Promise<Record<string, string>> {
  const cookie = await opsSessionCookieHeader(ADMIN);
  return body ? { ...cookie, "content-type": "application/json" } : cookie;
}

describe("POST /admin/users/:id/ban", () => {
  it("400s without a reason", async () => {
    await handle.db.insert(users).values([{ id: "u1", email: "a@example.com" }]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/ban", {
        method: "POST",
        headers: await adminHeaders(true),
        body: JSON.stringify({}),
      }),
    );
    expect(res.status).toBe(400);
  });

  it("flips status, ends active sessions, emits an event", async () => {
    await handle.db.insert(users).values([{ id: "u1", email: "a@example.com" }]);
    await handle.db.insert(devices).values([
      { id: "d1", userId: "u1", deviceKeyHash: "h1" },
    ]);
    await handle.db.insert(sessions).values([
      { id: "s1", userId: "u1", deviceId: "d1" },
    ]);

    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/ban", {
        method: "POST",
        headers: await adminHeaders(true),
        body: JSON.stringify({ reason: "abuse" }),
      }),
    );
    expect(res.status).toBe(200);

    const [u] = await handle.db.select().from(users).where(eq(users.id, "u1"));
    expect(u?.status).toBe("banned");

    const liveSessions = await handle.db
      .select()
      .from(sessions)
      .where(
        and(eq(sessions.userId, "u1"), eq(sessions.id, "s1")),
      );
    expect(liveSessions[0]?.endedAt).not.toBeNull();

    const evs = await handle.db
      .select()
      .from(eventsTable)
      .where(eq(eventsTable.type, "user.banned"));
    expect(evs).toHaveLength(1);
    expect((evs[0]!.payload as { reason: string }).reason).toBe("abuse");
    // The audit `by` field now comes from the verified cookie, not the
    // forgeable header.
    expect((evs[0]!.payload as { by: string }).by).toBe(ADMIN);
  });

  it("404s on unknown user", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/users/nope/ban", {
        method: "POST",
        headers: await adminHeaders(true),
        body: JSON.stringify({ reason: "abuse" }),
      }),
    );
    expect(res.status).toBe(404);
  });
});

describe("POST /admin/users/:id/unban", () => {
  it("flips status back to active", async () => {
    await handle.db.insert(users).values([
      { id: "u1", email: "a@example.com", status: "banned" },
    ]);
    const res = await app().fetch(
      new Request("http://localhost/admin/users/u1/unban", {
        method: "POST",
        headers: await adminHeaders(true),
        body: "{}",
      }),
    );
    expect(res.status).toBe(200);
    const [u] = await handle.db.select().from(users).where(eq(users.id, "u1"));
    expect(u?.status).toBe("active");
  });
});
