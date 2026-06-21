/**
 * Tiny session middleware for the v1 dashboard endpoints (RAI-27).
 *
 * Until the proper better-auth session lookup lands, we accept a
 * verified user id from one of two trusted headers:
 *
 *   - `x-user-id` — the dashboard sets this from its (forthcoming) session
 *     cookie; production gateway is responsible for stripping it from
 *     untrusted callers.
 *   - `authorization: Bearer <userId>` — used by the device-key path and
 *     by the dashboard's `apiFetch(..., { authenticated: true })` shim.
 *
 * This mirrors the auth pattern in `api/admin/usage.ts` which gates on
 * `x-admin-email` until session lookup arrives. Replacing this with a
 * cookie-backed session is a single-file change.
 *
 * Returns 401 when neither header resolves a user id.
 */
import type { Context, MiddlewareHandler } from "hono";

/**
 * Hono variables added by `requireUser`. Route handlers read
 * `c.var.userId` once the middleware has accepted them.
 */
export interface AuthedVars {
  userId: string;
}

export function readUserId(c: Context): string | null {
  const direct = c.req.header("x-user-id")?.trim();
  if (direct && direct.length > 0) return direct;
  const auth = c.req.header("authorization")?.trim();
  if (auth && auth.toLowerCase().startsWith("bearer ")) {
    const token = auth.slice("bearer ".length).trim();
    if (token.length > 0) return token;
  }
  return null;
}

export const requireUser: MiddlewareHandler<{ Variables: AuthedVars }> = async (c, next) => {
  const userId = readUserId(c);
  if (!userId) {
    return c.json({ ok: false, error: "unauthorized" }, 401);
  }
  c.set("userId", userId);
  await next();
  return;
};
