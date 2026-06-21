/**
 * Session-token helpers.
 *
 * The dashboard's auth model is intentionally simple: a single opaque
 * session token issued by the backend (RAI-27) and stored in
 * localStorage. The token gets read once on hydration and attached as
 * an Authorization: Bearer header to every authenticated request.
 *
 * For SSR or pre-mount calls we guard `window` access so the helpers
 * are safe to import from anywhere.
 */

const STORAGE_KEY = "osrs-llm-helper:session-token";

function hasWindow(): boolean {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

export function getSessionToken(): string | null {
  if (!hasWindow()) return null;
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setSessionToken(token: string): void {
  if (!hasWindow()) return;
  try {
    window.localStorage.setItem(STORAGE_KEY, token);
  } catch {
    // localStorage may be unavailable (private mode, quota exceeded) —
    // we swallow rather than crash. Real auth lives in RAI-27.
  }
}

export function clearSessionToken(): void {
  if (!hasWindow()) return;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function isLoggedIn(): boolean {
  return getSessionToken() !== null;
}
