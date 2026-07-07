/**
 * Core logic tests for `auth/magic-link.ts`.
 *
 * Hits a real PGLite DB so the upsert + transaction semantics are
 * exercised end-to-end. No HTTP layer, no email layer — those have
 * their own integration tests.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import {
  consumeMagicLink,
  hashToken,
  issueMagicLink,
  normaliseEmail,
  outstandingFor,
  sweepExpiredMagicLinks,
} from "../../src/auth/magic-link";
import { applyMigrations, createDb, type DbHandle } from "../../src/db/client";
import { authMagicLinks, users } from "../../src/db/schema";

let handle: DbHandle;
beforeEach(async () => {
  handle = createDb("memory://");
  await applyMigrations(handle);
});
afterEach(async () => {
  await handle.close();
});

describe("normaliseEmail", () => {
  it("lower-cases, trims, drops trailing dots", () => {
    expect(normaliseEmail("  TOM@Example.COM. ")).toBe("tom@example.com");
  });
  it("preserves +alias", () => {
    expect(normaliseEmail("tom+tibbly@example.com")).toBe("tom+tibbly@example.com");
  });
});

describe("issueMagicLink", () => {
  it("persists a row with the hashed token but never the raw", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "Tom@Example.com",
      pendingSessionId: "browser-1",
    });
    expect(issued.token.length).toBeGreaterThan(20);

    const rows = await handle.db.select().from(authMagicLinks);
    expect(rows.length).toBe(1);
    expect(rows[0].email).toBe("tom@example.com");
    expect(rows[0].tokenHash).toBe(await hashToken(issued.token));
    expect(rows[0].pendingSessionId).toBe("browser-1");
    expect(rows[0].consumedAt).toBeNull();
  });

  it("issues distinct tokens for repeat requests", async () => {
    const a = await issueMagicLink(handle.db, { email: "a@b.co", pendingSessionId: "br" });
    const b = await issueMagicLink(handle.db, { email: "a@b.co", pendingSessionId: "br" });
    expect(a.token).not.toBe(b.token);
    const outstanding = await outstandingFor(handle.db, "a@b.co");
    expect(outstanding.length).toBe(2);
  });

  it("respects a TTL override", async () => {
    const now = 1_000_000;
    const issued = await issueMagicLink(handle.db, {
      email: "a@b.co",
      pendingSessionId: "br",
      ttlMs: 60_000,
      now: () => now,
    });
    expect(issued.expiresAt.getTime()).toBe(now + 60_000);
  });
});

describe("consumeMagicLink", () => {
  it("succeeds on first click, creates the user, marks the row consumed", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "first@example.com",
      pendingSessionId: "br-1",
    });
    const res = await consumeMagicLink(handle.db, {
      token: issued.token,
      pendingSessionId: "br-1",
    });
    expect(res.ok).toBe(true);
    if (!res.ok) throw new Error();
    expect(res.value.email).toBe("first@example.com");
    expect(res.value.userCreated).toBe(true);

    const rows = await handle.db
      .select()
      .from(authMagicLinks)
      .where(eq(authMagicLinks.id, issued.id));
    expect(rows[0].consumedAt).not.toBeNull();

    const u = await handle.db.select().from(users).where(eq(users.email, "first@example.com"));
    expect(u.length).toBe(1);
    expect(u[0].id).toBe(res.value.userId);
  });

  it("rejects a second click on the same token", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "x@x.co",
      pendingSessionId: "br",
    });
    const first = await consumeMagicLink(handle.db, { token: issued.token, pendingSessionId: "br" });
    expect(first.ok).toBe(true);
    const replay = await consumeMagicLink(handle.db, { token: issued.token, pendingSessionId: "br" });
    expect(replay.ok).toBe(false);
    if (replay.ok) throw new Error();
    expect(replay.error.kind).toBe("already_consumed");
  });

  it("returns not_found for an unknown token", async () => {
    const res = await consumeMagicLink(handle.db, {
      token: "totally-not-a-real-token-token-token-token",
      pendingSessionId: "br",
    });
    expect(res.ok).toBe(false);
    if (res.ok) throw new Error();
    expect(res.error.kind).toBe("not_found");
  });

  it("rejects an expired link without consuming it", async () => {
    const now = 1_000_000;
    const issued = await issueMagicLink(handle.db, {
      email: "exp@x.co",
      pendingSessionId: "br",
      ttlMs: 1_000,
      now: () => now,
    });
    const res = await consumeMagicLink(handle.db, {
      token: issued.token,
      pendingSessionId: "br",
      now: () => now + 60_000,
    });
    expect(res.ok).toBe(false);
    if (res.ok) throw new Error();
    expect(res.error.kind).toBe("expired");

    // Not consumed, so a clock rewind would still see it unconsumed.
    const rows = await handle.db
      .select()
      .from(authMagicLinks)
      .where(eq(authMagicLinks.id, issued.id));
    expect(rows[0].consumedAt).toBeNull();
  });

  it("rejects a click from a different browser when bound", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "wrong@x.co",
      pendingSessionId: "browser-A",
    });
    const res = await consumeMagicLink(handle.db, {
      token: issued.token,
      pendingSessionId: "browser-B",
    });
    expect(res.ok).toBe(false);
    if (res.ok) throw new Error();
    expect(res.error.kind).toBe("wrong_browser");
  });

  it("allows cross-browser when pendingSessionId is null on the row", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "anywhere@x.co",
      pendingSessionId: null,
    });
    const res = await consumeMagicLink(handle.db, {
      token: issued.token,
      pendingSessionId: "any-browser",
    });
    expect(res.ok).toBe(true);
  });

  it("returns the existing user on second sign-in (userCreated=false)", async () => {
    const a = await issueMagicLink(handle.db, { email: "repeat@x.co", pendingSessionId: "br1" });
    const r1 = await consumeMagicLink(handle.db, { token: a.token, pendingSessionId: "br1" });
    expect(r1.ok).toBe(true);
    if (!r1.ok) throw new Error();
    const firstUserId = r1.value.userId;

    const b = await issueMagicLink(handle.db, { email: "repeat@x.co", pendingSessionId: "br2" });
    const r2 = await consumeMagicLink(handle.db, { token: b.token, pendingSessionId: "br2" });
    expect(r2.ok).toBe(true);
    if (!r2.ok) throw new Error();
    expect(r2.value.userCreated).toBe(false);
    expect(r2.value.userId).toBe(firstUserId);
  });

  it("preserves userCode through the verify hand-off", async () => {
    const issued = await issueMagicLink(handle.db, {
      email: "dev@x.co",
      pendingSessionId: "br",
      userCode: "ABC12345",
    });
    const res = await consumeMagicLink(handle.db, {
      token: issued.token,
      pendingSessionId: "br",
    });
    expect(res.ok).toBe(true);
    if (!res.ok) throw new Error();
    expect(res.value.userCode).toBe("ABC12345");
  });
});

describe("sweepExpiredMagicLinks", () => {
  it("drops rows older than the grace window and reports the count", async () => {
    const now = 2_000_000_000_000;
    const grace = 7 * 24 * 60 * 60 * 1000;
    // Two old, one fresh.
    await issueMagicLink(handle.db, {
      email: "old1@x.co",
      pendingSessionId: "br",
      ttlMs: 1_000,
      now: () => now - grace - 60_000,
    });
    await issueMagicLink(handle.db, {
      email: "old2@x.co",
      pendingSessionId: "br",
      ttlMs: 1_000,
      now: () => now - grace - 60_000,
    });
    await issueMagicLink(handle.db, {
      email: "fresh@x.co",
      pendingSessionId: "br",
      now: () => now,
    });
    const dropped = await sweepExpiredMagicLinks(handle.db, {
      now: () => now,
      idleGraceMs: grace,
    });
    expect(dropped).toBe(2);
    const left = await handle.db.select().from(authMagicLinks);
    expect(left.map((r) => r.email)).toEqual(["fresh@x.co"]);
  });
});
