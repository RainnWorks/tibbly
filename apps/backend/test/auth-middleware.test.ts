/**
 * RAI-39 — direct contract tests for the new `requireUser` middleware
 * and the `ops_session` JWT cookie helpers.
 *
 * These tests are the closest mirror of the audit's recommended live
 * checks. They use the real argon2 device-key path and the real jose
 * JWT path; only the DB is in-memory (PGLite).
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { Hono } from "hono";

import { requireUserWith, type AuthedVars, DeviceKeyCache } from "../src/api/_auth";
import { adminGate, type AdminGateVars } from "../src/api/admin/_gate";
import {
  bearerHeaders,
  opsSessionCookieHeader,
  OPS_SESSION_SECRET,
  seedDevice,
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

/* ------------------------------------------------------------------ */
/*  requireUser — device-key Bearer path                              */
/* ------------------------------------------------------------------ */

function makeUserApp(): Hono<{ Variables: AuthedVars }> {
  const app = new Hono<{ Variables: AuthedVars }>();
  app.use("*", requireUserWith({ db: handle.db, cache: new DeviceKeyCache(50) }));
  app.get("/whoami", (c) =>
    c.json({ userId: c.var.userId, deviceId: c.var.deviceId }),
  );
  return app;
}

describe("requireUser — device-key Bearer", () => {
  it("401s on no header (audit C1 default path)", async () => {
    const res = await makeUserApp().fetch(new Request("http://localhost/whoami"));
    expect(res.status).toBe(401);
  });

  it("401s on legacy x-user-id (audit C1 attack closed)", async () => {
    const seeded = await seedDevice(handle);
    const res = await makeUserApp().fetch(
      new Request("http://localhost/whoami", {
        headers: { "x-user-id": seeded.userId },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s on Bearer that is just a raw user id (audit C1 second variant)", async () => {
    const seeded = await seedDevice(handle);
    const res = await makeUserApp().fetch(
      new Request("http://localhost/whoami", {
        headers: { authorization: `Bearer ${seeded.userId}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("200 with correct userId + deviceId for the real raw device key", async () => {
    const seeded = await seedDevice(handle);
    const res = await makeUserApp().fetch(
      new Request("http://localhost/whoami", {
        headers: bearerHeaders(seeded.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { userId: string; deviceId: string };
    expect(body.userId).toBe(seeded.userId);
    expect(body.deviceId).toBe(seeded.deviceId);
  });

  it("warm cache: a second request returns 200 with no extra DB scan needed", async () => {
    const seeded = await seedDevice(handle);
    const app = makeUserApp();
    const first = await app.fetch(
      new Request("http://localhost/whoami", { headers: bearerHeaders(seeded.rawDeviceKey) }),
    );
    expect(first.status).toBe(200);
    const second = await app.fetch(
      new Request("http://localhost/whoami", { headers: bearerHeaders(seeded.rawDeviceKey) }),
    );
    expect(second.status).toBe(200);
  });

  it("does not authenticate a different user's bearer (cross-account isolation)", async () => {
    const a = await seedDevice(handle, { email: "a@example.com" });
    const b = await seedDevice(handle, { email: "b@example.com" });

    const app = makeUserApp();
    const res = await app.fetch(
      new Request("http://localhost/whoami", { headers: bearerHeaders(b.rawDeviceKey) }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { userId: string };
    expect(body.userId).toBe(b.userId);
    expect(body.userId).not.toBe(a.userId);
  });
});

/* ------------------------------------------------------------------ */
/*  adminGate — ops_session JWT cookie path                           */
/* ------------------------------------------------------------------ */

function makeAdminApp(extra?: { adminEmails?: string[]; jwtSecret?: Uint8Array }) {
  const app = new Hono<{ Variables: AdminGateVars }>();
  app.use(
    "*",
    adminGate({
      adminEmails: extra?.adminEmails ?? ["tom@rowm.co"],
      jwtSecret: extra?.jwtSecret ?? OPS_SESSION_SECRET,
    }),
  );
  app.get("/ops", (c) => c.json({ ok: true, adminEmail: c.var.adminEmail }));
  return app;
}

describe("adminGate — ops_session JWT cookie", () => {
  it("401s on no cookie", async () => {
    const res = await makeAdminApp().fetch(new Request("http://localhost/ops"));
    expect(res.status).toBe(401);
  });

  it("401s on legacy x-admin-email header (audit C2 attack closed)", async () => {
    const res = await makeAdminApp().fetch(
      new Request("http://localhost/ops", {
        headers: { "x-admin-email": "tom@rowm.co" },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when ADMIN_EMAILS allow-list is empty", async () => {
    const cookie = await opsSessionCookieHeader("tom@rowm.co");
    const res = await makeAdminApp({ adminEmails: [] }).fetch(
      new Request("http://localhost/ops", { headers: cookie }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when cookie email is not in the allow-list", async () => {
    const cookie = await opsSessionCookieHeader("evil@example.com");
    const res = await makeAdminApp().fetch(
      new Request("http://localhost/ops", { headers: cookie }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when cookie is signed with a different secret (wrong issuer-equivalent)", async () => {
    const wrong = new TextEncoder().encode("a-totally-different-32+byte-secret-XXX");
    const jwt = await signOpsSession("tom@rowm.co", wrong);
    const res = await makeAdminApp().fetch(
      new Request("http://localhost/ops", {
        headers: { cookie: `ops_session=${jwt}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when cookie has expired", async () => {
    const jwt = await signOpsSession("tom@rowm.co", OPS_SESSION_SECRET, {
      lifetimeSeconds: -60,
    });
    const res = await makeAdminApp().fetch(
      new Request("http://localhost/ops", {
        headers: { cookie: `ops_session=${jwt}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("200 with adminEmail set when cookie is valid", async () => {
    const cookie = await opsSessionCookieHeader("tom@rowm.co");
    const res = await makeAdminApp().fetch(
      new Request("http://localhost/ops", { headers: cookie }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean; adminEmail: string };
    expect(body.ok).toBe(true);
    expect(body.adminEmail).toBe("tom@rowm.co");
  });
});

/* ------------------------------------------------------------------ */
/*  Dev-only x-dev-user-id gate                                       */
/* ------------------------------------------------------------------ */

describe("requireUser — dev-only x-dev-user-id gate", () => {
  it("ignores the header when env.ALLOW_DEV_HEADERS != 'true'", async () => {
    // Test env defaults to NODE_ENV=test and ALLOW_DEV_HEADERS unset.
    // The header MUST be rejected.
    const app = makeUserApp();
    const res = await app.fetch(
      new Request("http://localhost/whoami", {
        headers: { "x-dev-user-id": "user_who_cares" },
      }),
    );
    expect(res.status).toBe(401);
  });
});
