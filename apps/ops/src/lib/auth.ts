/**
 * Ops session state.
 *
 * The backend mints an httpOnly cookie on /admin/login. The browser sends
 * it back automatically, so we never see the JWT here. What we do cache
 * client-side is the operator's email, which is needed to attach the
 * `x-admin-email` header that the legacy gate (in apps/backend/src/api/
 * admin/usage.ts) still requires. The email is sourced from
 * GET /admin/session and stored in memory + sessionStorage.
 */

import { apiFetch, ApiError } from "./api";

const EMAIL_KEY = "ops:operator-email";

let cachedEmail: string | null = null;

export function getOperatorEmail(): string | null {
  if (cachedEmail) return cachedEmail;
  if (typeof window === "undefined") return null;
  try {
    return window.sessionStorage.getItem(EMAIL_KEY);
  } catch {
    return null;
  }
}

export function setOperatorEmail(email: string): void {
  cachedEmail = email;
  if (typeof window !== "undefined") {
    try {
      window.sessionStorage.setItem(EMAIL_KEY, email);
    } catch {
      // sessionStorage may be unavailable in private modes; the in-memory
      // copy still works for the page lifetime.
    }
  }
}

export function clearOperatorEmail(): void {
  cachedEmail = null;
  if (typeof window !== "undefined") {
    try {
      window.sessionStorage.removeItem(EMAIL_KEY);
    } catch {
      // ignore
    }
  }
}

/**
 * Verify the cookie is still valid by calling GET /admin/session. Returns
 * the operator email on success, or null on 401. Throws on network error.
 */
export async function fetchSession(): Promise<string | null> {
  try {
    const res = await apiFetch<{ ok: true; email: string }>("/admin/session", {
      method: "GET",
    });
    setOperatorEmail(res.email);
    return res.email;
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      clearOperatorEmail();
      return null;
    }
    throw err;
  }
}

/** POST /admin/login. Server sets the cookie; we cache the email. */
export async function login(email: string): Promise<{ ok: true; email: string }> {
  const res = await apiFetch<{ ok: true; email: string }>("/admin/login", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
  setOperatorEmail(res.email);
  return res;
}

/** POST /admin/logout. Clears the cookie + local cache. */
export async function logout(): Promise<void> {
  try {
    await apiFetch("/admin/logout", { method: "POST" });
  } finally {
    clearOperatorEmail();
  }
}
