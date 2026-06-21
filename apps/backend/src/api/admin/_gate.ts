/**
 * Shared admin auth middleware.
 *
 * Every admin router mounts this. Identical contract to the legacy
 * inline gate in `admin/usage.ts`: header `x-admin-email` must match an
 * entry in `ADMIN_EMAILS` (case-insensitive). Empty allow-list -> 401
 * for every caller, so a missing env var locks the surface down rather
 * than opening it.
 */
import type { MiddlewareHandler } from "hono";

import { env } from "../../env";

export interface AdminGateOptions {
  /** Override admin email allow-list for tests. Defaults to env.ADMIN_EMAILS. */
  adminEmails?: readonly string[];
}

export function adminGate(options: AdminGateOptions = {}): MiddlewareHandler {
  const allow = new Set(
    (options.adminEmails ?? parseAdminEmails(env.ADMIN_EMAILS)).map((e) =>
      e.toLowerCase(),
    ),
  );
  return async (c, next) => {
    const headerEmail = c.req.header("x-admin-email")?.trim().toLowerCase();
    if (!headerEmail || allow.size === 0 || !allow.has(headerEmail)) {
      return c.json({ ok: false, error: "unauthorized" }, 401);
    }
    await next();
    return;
  };
}

export function parseAdminEmails(raw: string): string[] {
  return raw
    .split(",")
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}
