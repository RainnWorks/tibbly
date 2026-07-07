/**
 * Integration tests for the magic-link router mounted at /api/auth.
 *
 * Exercises:
 *  - /email/start sets a pending-auth cookie and calls the EmailSender once.
 *  - The link in the email round-trips through /email/verify, sets a
 *    `tibbly_session` cookie, and 302s to the safe redirect.
 *  - /session reads the JWT cookie and reports the signed-in user.
 *  - /logout clears the session cookie.
 *  - The rate-limit gate on /start enforces the configured capacity.
 *  - Cross-browser clicks fail when the link was bound (the cookie on
 *    /verify doesn't match the one that requested it).
 *  - The session JWT round-trips its `sub` and `email` claims.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../../src/app";
import { ConsoleEmailSender, type EmailSender, type MagicLinkEmailParams } from "../../src/auth/email";
import { applyMigrations, createDb, type DbHandle } from "../../src/db/client";

let handle: DbHandle;
beforeEach(async () => {
  handle = createDb("memory://");
  await applyMigrations(handle);
});
afterEach(async () => {
  await handle.close();
});

class RecordingSender implements EmailSender {
  public calls: MagicLinkEmailParams[] = [];
  async send(params: MagicLinkEmailParams): Promise<void> {
    this.calls.push(params);
  }
}

const JWT_SECRET = new TextEncoder().encode("a-test-jwt-secret-32-chars-or-more-please");

function appWith(opts: { sender?: EmailSender; clock?: () => number } = {}) {
  return createApp({
    authEmail: {
      db: handle.db,
      sender: opts.sender,
      jwtSecret: JWT_SECRET,
      webBaseUrl: "http://localhost:8787",
      cookieSecure: false,
      ...(opts.clock ? { clock: opts.clock } : {}),
    },
  });
}

function readSetCookies(res: Response): string[] {
  // Headers#getSetCookie() returns string[] in Hono / Bun.
  return res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get("set-cookie") ?? ""];
}

function tokenFromLink(link: string): string {
  return new URL(link).searchParams.get("token") ?? "";
}

function pendingCookieValue(setCookies: string[]): string | null {
  for (const c of setCookies) {
    const m = /(?:^|;\s*)?tibbly_pending_auth=([^;]+)/.exec(c);
    if (m) return m[1] ?? null;
  }
  return null;
}

function sessionCookieValue(setCookies: string[]): string | null {
  for (const c of setCookies) {
    const m = /(?:^|;\s*)?tibbly_session=([^;]+)/.exec(c);
    if (m) return m[1] ?? null;
  }
  return null;
}

describe("POST /api/auth/email/start", () => {
  it("sets a pending-auth cookie and triggers exactly one send", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const res = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "user@example.com" }),
      }),
    );
    expect(res.status).toBe(202);
    expect(sender.calls.length).toBe(1);
    expect(sender.calls[0].to).toBe("user@example.com");
    expect(sender.calls[0].link).toContain("/api/auth/email/verify?token=");
    const pending = pendingCookieValue(readSetCookies(res));
    expect(pending).not.toBeNull();
    expect((pending ?? "").length).toBeGreaterThan(8);
  });

  it("reuses an existing pending-auth cookie instead of minting a new one", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const first = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "u@x.co" }),
      }),
    );
    const pending1 = pendingCookieValue(readSetCookies(first));
    expect(pending1).not.toBeNull();
    const second = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          cookie: `tibbly_pending_auth=${pending1}`,
        },
        body: JSON.stringify({ email: "u@x.co" }),
      }),
    );
    const pending2 = pendingCookieValue(readSetCookies(second));
    expect(pending2).toBe(pending1);
  });

  it("422s on a bad email shape", async () => {
    const app = appWith();
    const res = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "not-an-email" }),
      }),
    );
    expect([400, 422]).toContain(res.status);
  });

  it("rate-limits aggressive callers from the same IP", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    // Default capacity is 5 — six in a row from the same IP trips.
    const mkReq = (i: number): Request =>
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json", "x-real-ip": "9.9.9.9" },
        body: JSON.stringify({ email: `burst-${i}@x.co` }),
      });
    for (let i = 0; i < 5; i++) {
      const r = await app.fetch(mkReq(i));
      expect(r.status).toBe(202);
    }
    const blocked = await app.fetch(mkReq(99));
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("Retry-After")).not.toBeNull();
  });
});

describe("GET /api/auth/email/verify", () => {
  it("happy path → 302 redirect, sets tibbly_session cookie, clears pending", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const start = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "happy@x.co" }),
      }),
    );
    const pending = pendingCookieValue(readSetCookies(start));
    const link = sender.calls[0].link;
    const token = tokenFromLink(link);

    const verify = await app.fetch(
      new Request(`http://localhost/api/auth/email/verify?token=${token}`, {
        headers: { cookie: `tibbly_pending_auth=${pending}` },
      }),
    );
    expect(verify.status).toBe(302);
    expect(verify.headers.get("location")).toBe("http://localhost:8787/");
    const session = sessionCookieValue(readSetCookies(verify));
    expect(session).not.toBeNull();
    expect((session ?? "").split(".").length).toBe(3); // JWT shape
    // Pending cookie cleared.
    const setCookies = readSetCookies(verify);
    expect(setCookies.some((c) => c.startsWith("tibbly_pending_auth=") && /Max-Age=0/.test(c))).toBe(true);
  });

  it("respects ?next= when it points at the same origin", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const start = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "next@x.co" }),
      }),
    );
    const pending = pendingCookieValue(readSetCookies(start));
    const token = tokenFromLink(sender.calls[0].link);

    const verify = await app.fetch(
      new Request(
        `http://localhost/api/auth/email/verify?token=${token}&next=${encodeURIComponent("/device?user_code=ABC123")}`,
        { headers: { cookie: `tibbly_pending_auth=${pending}` } },
      ),
    );
    expect(verify.status).toBe(302);
    expect(verify.headers.get("location")).toBe("http://localhost:8787/device?user_code=ABC123");
  });

  it("rejects an open-redirect attempt via ?next=", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "openredir@x.co" }),
      }),
    );
    const token = tokenFromLink(sender.calls[0].link);
    const verify = await app.fetch(
      new Request(
        `http://localhost/api/auth/email/verify?token=${token}&next=${encodeURIComponent("https://evil.example.com/")}`,
      ),
    );
    // Bad next falls back to the configured base; the bound cookie
    // check fails (no cookie carried) → 401 instead of a redirect to
    // attacker.example.com.
    expect([302, 401]).toContain(verify.status);
    if (verify.status === 302) {
      expect(verify.headers.get("location")).toBe("http://localhost:8787/");
    }
  });

  it("a second click 410s (already consumed)", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const start = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "twice@x.co" }),
      }),
    );
    const pending = pendingCookieValue(readSetCookies(start));
    const token = tokenFromLink(sender.calls[0].link);
    const first = await app.fetch(
      new Request(`http://localhost/api/auth/email/verify?token=${token}`, {
        headers: { cookie: `tibbly_pending_auth=${pending}` },
      }),
    );
    expect(first.status).toBe(302);
    const second = await app.fetch(
      new Request(`http://localhost/api/auth/email/verify?token=${token}`, {
        headers: { cookie: `tibbly_pending_auth=${pending}` },
      }),
    );
    expect(second.status).toBe(410);
  });

  it("rejects a click from a different browser when bound", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "wrongbrowser@x.co" }),
      }),
    );
    const token = tokenFromLink(sender.calls[0].link);
    // Click without the cookie (different browser).
    const verify = await app.fetch(
      new Request(`http://localhost/api/auth/email/verify?token=${token}`),
    );
    expect(verify.status).toBe(401);
  });
});

describe("GET /api/auth/session + POST /api/auth/logout", () => {
  it("session reports the signed-in user; logout clears the cookie", async () => {
    const sender = new RecordingSender();
    const app = appWith({ sender });
    const start = await app.fetch(
      new Request("http://localhost/api/auth/email/start", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "loop@x.co" }),
      }),
    );
    const pending = pendingCookieValue(readSetCookies(start));
    const token = tokenFromLink(sender.calls[0].link);
    const verify = await app.fetch(
      new Request(`http://localhost/api/auth/email/verify?token=${token}`, {
        headers: { cookie: `tibbly_pending_auth=${pending}` },
      }),
    );
    const session = sessionCookieValue(readSetCookies(verify));
    expect(session).not.toBeNull();

    const sessionCheck = await app.fetch(
      new Request("http://localhost/api/auth/session", {
        headers: { cookie: `tibbly_session=${session}` },
      }),
    );
    expect(sessionCheck.status).toBe(200);
    const body = (await sessionCheck.json()) as { ok: boolean; email: string; userId: string };
    expect(body.ok).toBe(true);
    expect(body.email).toBe("loop@x.co");
    expect(body.userId.length).toBeGreaterThan(0);

    const logout = await app.fetch(
      new Request("http://localhost/api/auth/logout", { method: "POST" }),
    );
    expect(logout.status).toBe(200);
    const clear = readSetCookies(logout);
    expect(clear.some((c) => c.startsWith("tibbly_session=") && /Max-Age=0/.test(c))).toBe(true);
  });

  it("session 401s when there is no cookie", async () => {
    const app = appWith();
    const res = await app.fetch(new Request("http://localhost/api/auth/session"));
    expect(res.status).toBe(401);
  });

  it("session 401s on a tampered JWT", async () => {
    const app = appWith();
    const res = await app.fetch(
      new Request("http://localhost/api/auth/session", {
        headers: { cookie: "tibbly_session=not.a.real.jwt" },
      }),
    );
    expect(res.status).toBe(401);
  });
});

describe("ConsoleEmailSender", () => {
  it("send() resolves without throwing — keeps dev flow green", async () => {
    const sender = new ConsoleEmailSender();
    await expect(
      sender.send({ to: "x@y.co", link: "http://example/?t=abc", ttlMinutes: 10 }),
    ).resolves.toBeUndefined();
  });
});
