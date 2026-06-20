/**
 * Tests for GET /v1/me/export and DELETE /v1/me.
 *
 * STATUS: PARKED behind RAI-13 (monorepo skeleton) and RAI-14 (backend
 * skeleton). This file does not run today because:
 *   - `bun` workspace isn't set up at repo root yet;
 *   - the Drizzle schema this file imports is intended shape, not real;
 *   - the in-memory PGLite fixture helper is RAI-14 work too.
 *
 * As soon as RAI-13 + RAI-14 land, the imports resolve and this suite
 * should run with `bun test apps/backend/test/me.test.ts`.
 *
 * The test bodies are written against the shape contract from
 * `apps/backend/src/api/me.ts` so a future implementer cannot quietly
 * drift the response surface without breaking these.
 *
 * Owner: agent r-legal (RAI-34).
 */

import { describe, it, expect, beforeEach } from "bun:test";

// These imports point at modules RAI-14 will create.
import { app } from "../src/app";
import { withFixtures, seedUser } from "./fixtures";

describe("GET /v1/me/export", () => {
  let auth: { userId: string; token: string };

  beforeEach(async () => {
    await withFixtures();
    auth = await seedUser({
      email: "tom@example.com",
      playerNames: ["zezima"],
      withChats: 3,
      withConsent: true,
      withInvoices: 2,
    });
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await app.request("/v1/me/export");
    expect(res.status).toBe(401);
  });

  it("returns a JSON body containing every owned record", async () => {
    const res = await app.request("/v1/me/export", {
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    expect(res.status).toBe(200);

    const body = await res.json();
    expect(body.exportVersion).toBe(1);
    expect(body.user.email).toBe("tom@example.com");
    expect(body.playerBindings).toHaveLength(1);
    expect(body.playerBindings[0].playerName).toBe("zezima");
    expect(body.chats).toHaveLength(3);
    expect(body.invoices).toHaveLength(2);
    expect(body.consentGrants.length).toBeGreaterThan(0);
  });

  it("sets a Content-Disposition attachment header", async () => {
    const res = await app.request("/v1/me/export", {
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    const disposition = res.headers.get("Content-Disposition");
    expect(disposition).toContain("attachment");
    expect(disposition).toContain(auth.userId);
  });

  it("does NOT include other users' data", async () => {
    const other = await seedUser({
      email: "other@example.com",
      playerNames: ["lumby_lad"],
      withChats: 5,
    });

    const res = await app.request("/v1/me/export", {
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    const body = await res.json();

    expect(body.user.email).toBe("tom@example.com");
    for (const chat of body.chats) {
      expect(chat.userId).not.toBe(other.userId);
    }
  });
});

describe("DELETE /v1/me", () => {
  let auth: { userId: string; token: string };

  beforeEach(async () => {
    await withFixtures();
    auth = await seedUser({
      email: "tom@example.com",
      playerNames: ["zezima"],
      withChats: 3,
      withConsent: true,
      withInvoices: 2,
      withStripeCustomer: "cus_TEST123",
    });
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await app.request("/v1/me", { method: "DELETE" });
    expect(res.status).toBe(401);
  });

  it("returns 204 and hard-deletes the user row", async () => {
    const res = await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    expect(res.status).toBe(204);

    const { db } = await import("../src/db/client");
    const { users } = await import("../src/db/schema");
    const { eq } = await import("drizzle-orm");
    const rows = await db.select().from(users).where(eq(users.id, auth.userId));
    expect(rows).toHaveLength(0);
  });

  it("cascades to chats, snapshots, sessions, devices, bindings", async () => {
    await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });

    const { db } = await import("../src/db/client");
    const { chats, devices, playerBindings, sessions, stateSnapshots, chatTurns } =
      await import("../src/db/schema");
    const { eq } = await import("drizzle-orm");

    expect(await db.select().from(chats).where(eq(chats.userId, auth.userId))).toHaveLength(0);
    expect(await db.select().from(chatTurns).where(eq(chatTurns.userId, auth.userId))).toHaveLength(0);
    expect(await db.select().from(stateSnapshots).where(eq(stateSnapshots.userId, auth.userId))).toHaveLength(0);
    expect(await db.select().from(sessions).where(eq(sessions.userId, auth.userId))).toHaveLength(0);
    expect(await db.select().from(devices).where(eq(devices.userId, auth.userId))).toHaveLength(0);
    expect(await db.select().from(playerBindings).where(eq(playerBindings.userId, auth.userId))).toHaveLength(0);
  });

  it("anonymises consent grants instead of deleting them", async () => {
    await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });

    const { db } = await import("../src/db/client");
    const { consentGrants } = await import("../src/db/schema");
    const { eq } = await import("drizzle-orm");

    const rows = await db
      .select()
      .from(consentGrants)
      .where(eq(consentGrants.userId, auth.userId));

    // Still present (we keep them for limitation-period proof) but PII-stripped.
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) {
      expect(row.ipHash).toBeNull();
      expect(row.anonymisedAt).not.toBeNull();
    }
  });

  it("preserves invoice rows but marks them anonymised", async () => {
    await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });

    const { db } = await import("../src/db/client");
    const { invoices } = await import("../src/db/schema");
    const { eq } = await import("drizzle-orm");

    const rows = await db
      .select()
      .from(invoices)
      .where(eq(invoices.userId, auth.userId));

    expect(rows).toHaveLength(2);
    for (const row of rows) {
      expect(row.userIdAnonymisedAt).not.toBeNull();
    }
  });

  it("detaches PII on the Stripe customer object but does not delete it", async () => {
    const { stripeMock } = await import("./fixtures");

    await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });

    const call = stripeMock.customers.update.mock.calls.at(-1);
    expect(call?.[0]).toBe("cus_TEST123");
    expect(call?.[1].email).toBeUndefined();
    expect(call?.[1].name).toBeUndefined();
    expect(call?.[1].metadata.osrs_user_id).toBe("");
    expect(call?.[1].metadata.deleted_at).toBeTruthy();

    // We do NOT call stripe.customers.del().
    expect(stripeMock.customers.del).not.toHaveBeenCalled();
  });

  it("is idempotent — a second DELETE returns 204 or 401", async () => {
    await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    const res2 = await app.request("/v1/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    // Second call: token is stale / user gone — could legitimately be
    // 401 (auth fails because user record is gone) or 204 (idempotent).
    expect([204, 401]).toContain(res2.status);
  });
});
