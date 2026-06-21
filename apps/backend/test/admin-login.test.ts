/**
 * Contract for /admin/login | /admin/session | /admin/logout.
 *
 * The login endpoint is the only public surface that takes an email and
 * mints a session cookie. Subsequent admin endpoints still use
 * `x-admin-email` so the existing gate keeps working; the dashboard
 * reads the session payload and attaches the header itself.
 */
import { describe, expect, it } from "bun:test";

import { createApp } from "../src/app";

const ADMIN = "tom@rowm.co";
const SECRET = new TextEncoder().encode("test-secret-must-be-at-least-32-bytes-yes");

function app() {
  return createApp({
    adminLogin: { adminEmails: [ADMIN], jwtSecret: SECRET, cookieSecure: false },
  });
}

describe("POST /admin/login", () => {
  it("401s when the email is not in the allow-list", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/login", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: "evil@example.com" }),
      }),
    );
    expect(res.status).toBe(401);
  });

  it("issues an httpOnly cookie on success", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/login", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: ADMIN }),
      }),
    );
    expect(res.status).toBe(200);
    const setCookie = res.headers.get("set-cookie");
    expect(setCookie).toContain("ops_session=");
    expect(setCookie).toContain("HttpOnly");
    expect(setCookie).toContain("SameSite=Strict");
  });
});

describe("GET /admin/session", () => {
  it("401s without the cookie", async () => {
    const res = await app().fetch(new Request("http://localhost/admin/session"));
    expect(res.status).toBe(401);
  });

  it("returns the email when the cookie is valid", async () => {
    const a = app();
    const login = await a.fetch(
      new Request("http://localhost/admin/login", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: ADMIN }),
      }),
    );
    const cookie = login.headers.get("set-cookie")!.split(";")[0]!; // "ops_session=…"
    const session = await a.fetch(
      new Request("http://localhost/admin/session", {
        headers: { cookie },
      }),
    );
    expect(session.status).toBe(200);
    const body = (await session.json()) as { email: string };
    expect(body.email).toBe(ADMIN);
  });
});

describe("POST /admin/logout", () => {
  it("clears the cookie", async () => {
    const res = await app().fetch(
      new Request("http://localhost/admin/logout", { method: "POST" }),
    );
    expect(res.status).toBe(200);
    const setCookie = res.headers.get("set-cookie");
    expect(setCookie).toContain("ops_session=;");
    expect(setCookie).toContain("Max-Age=0");
  });
});
