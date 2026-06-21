/**
 * Linked OSRS accounts management (RAI-27).
 *
 * Routes:
 *   GET    /v1/accounts          → list every osrs_accounts row for the user.
 *   DELETE /v1/accounts/:id      → delete one, but only if it belongs to the
 *                                  authenticated user (404 otherwise — we
 *                                  don't leak ownership info via 403).
 *
 * The dashboard renders these so a paying user can keep their main +
 * iron + GIM alts in one place.
 */
import { and, eq } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../db/client";
import { osrsAccounts } from "../db/schema";
import { requireUserWith, type AuthedVars, type DeviceKeyCache } from "./_auth";

export interface CreateAccountsRouterOptions {
  db: DbClient;
  /** Optional shared device-key cache; defaults to a per-router cache. */
  deviceKeyCache?: DeviceKeyCache;
}

export interface AccountDTO {
  id: string;
  displayName: string;
  accountType: string;
  status: string;
  lastVerifiedAt: string | null;
  createdAt: string;
}

export function createAccountsRouter(
  options: CreateAccountsRouterOptions,
): Hono<{ Variables: AuthedVars }> {
  const { db } = options;
  const app = new Hono<{ Variables: AuthedVars }>();

  app.use("*", requireUserWith({ db, ...(options.deviceKeyCache ? { cache: options.deviceKeyCache } : {}) }));

  // List handler accepts both "/v1/accounts" and "/v1/accounts/" — Hono
  // treats these as separate routes by default.
  app.get("/", async (c) => {
    const userId = c.var.userId;
    const rows = await db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.userId, userId));

    const dtos: AccountDTO[] = rows.map((r) => ({
      id: r.id,
      displayName: r.displayName,
      accountType: r.accountType,
      status: r.status,
      lastVerifiedAt: r.lastVerifiedAt ? r.lastVerifiedAt.toISOString() : null,
      createdAt: r.createdAt.toISOString(),
    }));

    return c.json({ accounts: dtos });
  });

  app.delete("/:id", async (c) => {
    const userId = c.var.userId;
    const id = c.req.param("id");

    const [existing] = await db
      .select()
      .from(osrsAccounts)
      .where(and(eq(osrsAccounts.id, id), eq(osrsAccounts.userId, userId)))
      .limit(1);

    if (!existing) {
      return c.json({ ok: false, error: "not_found" }, 404);
    }

    await db
      .delete(osrsAccounts)
      .where(and(eq(osrsAccounts.id, id), eq(osrsAccounts.userId, userId)));

    return c.json({ ok: true, id });
  });

  return app;
}
