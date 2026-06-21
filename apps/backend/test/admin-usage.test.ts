/**
 * Admin analytics router contract (RAI-37, RAI-39 auth migration).
 *
 * Confirms:
 *   - All endpoints reject requests without the ops_session JWT cookie.
 *   - The OLD x-admin-email header is NOT accepted (audit C2 closed).
 *   - With a valid cookie, they return the materialised data.
 *   - Empty `ADMIN_EMAILS` env locks everything down (defensive default).
 *   - Expired / wrong-secret cookies → 401.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import {
  events as eventsTable,
  metricsErrorsDaily,
  metricsFunnelDaily,
  metricsToolUsageDaily,
} from "../src/db/schema";
import {
  OPS_SESSION_SECRET,
  opsSessionCookieHeader,
  signOpsSession,
} from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

const ADMIN = "tom@rowm.co";

function app() {
  return createApp({
    admin: { db: handle.db, adminEmails: [ADMIN], jwtSecret: OPS_SESSION_SECRET },
  });
}

describe("/admin/* auth gate", () => {
  it("401s when no admin emails are configured (defensive default)", async () => {
    const noAdmin = createApp({
      admin: { db: handle.db, adminEmails: [], jwtSecret: OPS_SESSION_SECRET },
    });
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await noAdmin.fetch(
      new Request("http://localhost/admin/tool-usage", { headers: cookie }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when no cookie is sent", async () => {
    const res = await app().fetch(new Request("http://localhost/admin/tool-usage"));
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-admin-email header is sent (audit C2 closed)", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/tool-usage", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the cookie email is not in the allow-list (revoked)", async () => {
    const cookie = await opsSessionCookieHeader("other@example.com");
    const res = await app().fetch(
      new Request("http://localhost/admin/tool-usage", { headers: cookie }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the cookie is signed with a different secret", async () => {
    const wrongSecret = new TextEncoder().encode("a-different-32+byte-secret-XXXXXXXXXX");
    const jwt = await signOpsSession(ADMIN, wrongSecret);
    const res = await app().fetch(
      new Request("http://localhost/admin/tool-usage", {
        headers: { cookie: `ops_session=${jwt}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the cookie has expired", async () => {
    // Negative lifetime -> "exp" is in the past, jose rejects on verify.
    const jwt = await signOpsSession(ADMIN, OPS_SESSION_SECRET, { lifetimeSeconds: -60 });
    const res = await app().fetch(
      new Request("http://localhost/admin/tool-usage", {
        headers: { cookie: `ops_session=${jwt}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("is case-insensitive on the email claim", async () => {
    const a = createApp({
      admin: { db: handle.db, adminEmails: ["Tom@Rowm.co"], jwtSecret: OPS_SESSION_SECRET },
    });
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await a.fetch(
      new Request("http://localhost/admin/tool-usage", { headers: cookie }),
    );
    expect(res.status).toBe(200);
  });
});

describe("/admin endpoints (200 path)", () => {
  it("/admin/tool-usage returns rows + summary", async () => {
    await handle.db.insert(metricsToolUsageDaily).values([
      {
        date: "2026-06-21",
        toolName: "get_inventory",
        family: "inventory",
        userId: "u1",
        count: 5,
        totalInputBytes: 100,
        totalOutputBytes: 4000,
      },
      {
        date: "2026-06-21",
        toolName: "get_bank",
        family: "bank",
        userId: "u1",
        count: 2,
        totalInputBytes: 100,
        totalOutputBytes: 2000,
      },
    ]);

    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app().fetch(
      new Request(
        "http://localhost/admin/tool-usage?since=2026-06-01&until=2026-06-30",
        { headers: cookie },
      ),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      rows: unknown[];
      summary: { totalCalls: number; byFamily: Record<string, number> };
    };
    expect(body.rows).toHaveLength(2);
    expect(body.summary.totalCalls).toBe(7);
    expect(body.summary.byFamily.inventory).toBe(5);
    expect(body.summary.byFamily.bank).toBe(2);
  });

  it("/admin/funnel returns funnel rows by step", async () => {
    await handle.db.insert(metricsFunnelDaily).values([
      { date: "2026-06-21", step: "first_message", userCount: 14 },
      { date: "2026-06-21", step: "first_paid", userCount: 3 },
    ]);
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app().fetch(
      new Request(
        "http://localhost/admin/funnel?since=2026-06-01&until=2026-06-30",
        { headers: cookie },
      ),
    );
    const body = (await res.json()) as {
      summary: { byStep: Record<string, number> };
    };
    expect(body.summary.byStep.first_message).toBe(14);
    expect(body.summary.byStep.first_paid).toBe(3);
  });

  it("/admin/errors returns error rows by kind", async () => {
    await handle.db.insert(metricsErrorsDaily).values([
      { date: "2026-06-21", kind: "openrouter", count: 9 },
      { date: "2026-06-21", kind: "plugin_disconnect", count: 4 },
    ]);
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app().fetch(
      new Request(
        "http://localhost/admin/errors?since=2026-06-01&until=2026-06-30",
        { headers: cookie },
      ),
    );
    const body = (await res.json()) as { summary: { byKind: Record<string, number> } };
    expect(body.summary.byKind.openrouter).toBe(9);
    expect(body.summary.byKind.plugin_disconnect).toBe(4);
  });

  it("/admin/realtime returns last-minute event counts", async () => {
    const fresh = new Date(Date.now() - 5_000);
    const stale = new Date(Date.now() - 120_000);
    await handle.db.insert(eventsTable).values([
      {
        id: "ev1",
        type: "chat.message.sent",
        userId: "u1",
        payload: { foo: 1 },
        createdAt: fresh,
      },
      {
        id: "ev2",
        type: "auth.pairing.claimed",
        userId: "u1",
        payload: { userId: "u1", deviceId: "d1" },
        createdAt: fresh,
      },
      {
        id: "ev3",
        type: "chat.message.sent",
        userId: "u2",
        payload: { foo: 2 },
        createdAt: stale,
      },
    ]);

    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await app().fetch(
      new Request("http://localhost/admin/realtime", { headers: cookie }),
    );
    const body = (await res.json()) as {
      eventCount: number;
      byType: Record<string, number>;
      connectedPlugins: number;
    };
    expect(body.eventCount).toBe(2);
    expect(body.byType["chat.message.sent"]).toBe(1);
    expect(body.byType["auth.pairing.claimed"]).toBe(1);
    expect(body.connectedPlugins).toBe(2);
  });
});
