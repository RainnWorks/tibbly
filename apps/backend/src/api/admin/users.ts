/**
 * Admin user management endpoints (RAI-37 follow-up, ops console).
 *
 * Mounted under `/admin/users` and gated by the same `requireAdmin`
 * middleware as `admin/usage.ts` (header: `x-admin-email`).
 *
 * Routes:
 *   GET  /admin/users                 paginated list with filters
 *   GET  /admin/users/:id             one user with everything
 *   POST /admin/users/:id/ban         status -> banned
 *   POST /admin/users/:id/unban       status -> active
 *   POST /admin/users/:id/credit      grant token credit
 *   POST /admin/users/:id/refund      Stripe refund (full or partial)
 *
 * Stripe is injected; in dev without a key we expose a typed stub that
 * the UI labels as `dev_stub` so the operator never thinks a refund
 * actually went through.
 */
import { and, count, desc, eq, ilike, inArray, or, sql } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../../db/client";
import {
  chats,
  devices,
  events as eventsTable,
  messages,
  newId,
  osrsAccounts,
  sessions,
  subscriptions,
  tokenBalances,
  toolCalls,
  users,
} from "../../db/schema";
import { computeCostMicroUsd } from "../../llm/cost";
import { adminGate, type AdminGateOptions, type AdminGateVars } from "./_gate";

/* -------------------------------------------------------------------------- */
/*  Stripe surface                                                            */
/* -------------------------------------------------------------------------- */

export interface StripeAdminLike {
  refunds: {
    create(params: {
      charge: string;
      amount?: number;
      reason?: string;
    }): Promise<{
      id: string;
      amount: number;
      currency: string;
      status: string | null;
    }>;
  };
  invoices: {
    list(params: {
      customer: string;
      limit: number;
    }): Promise<{
      data: ReadonlyArray<{
        id: string;
        number: string | null;
        amount_paid: number;
        amount_due: number;
        currency: string;
        status: string | null;
        created: number;
        hosted_invoice_url: string | null;
      }>;
    }>;
  };
}

/**
 * Dev stub used when STRIPE_SECRET_KEY is absent. Every method returns a
 * loudly-labelled `dev_stub` value so the UI can render an honest
 * placeholder instead of faking a success.
 */
export function makeDevStubStripe(): StripeAdminLike {
  return {
    refunds: {
      async create({ charge, amount, reason }) {
        return {
          id: `dev_stub_re_${charge.slice(-6)}`,
          amount: amount ?? 0,
          currency: "usd",
          status: "dev_stub",
          // reason intentionally ignored in stub
          ...({ reason } as Record<string, unknown>),
        };
      },
    },
    invoices: {
      async list() {
        return { data: [] };
      },
    },
  };
}

/* -------------------------------------------------------------------------- */
/*  Router                                                                    */
/* -------------------------------------------------------------------------- */

export interface CreateAdminUsersOptions extends AdminGateOptions {
  db: DbClient;
  stripe?: StripeAdminLike;
}

export function createAdminUsersRouter(
  options: CreateAdminUsersOptions,
): Hono<{ Variables: AdminGateVars }> {
  const { db } = options;
  const stripe = options.stripe ?? makeDevStubStripe();

  const app = new Hono<{ Variables: AdminGateVars }>();

  app.use("/*", adminGate(options));

  /* ------------------------------------------------------------------ */
  /*  GET /admin/users  (list + filter)                                 */
  /* ------------------------------------------------------------------ */

  app.get("/", async (c) => {
    const q = (c.req.query("q") ?? "").trim();
    const tier = c.req.query("tier") as
      | "hobbyist"
      | "pro"
      | "iron"
      | "free"
      | undefined;
    const status = c.req.query("status") as
      | "active"
      | "banned"
      | "deleted"
      | undefined;
    const stripeCustomerId = c.req.query("stripeCustomerId")?.trim();
    const osrsName = c.req.query("osrsName")?.trim();
    const limit = clampInt(c.req.query("limit"), 25, 1, 100);
    const offset = clampInt(c.req.query("offset"), 0, 0, 100_000);

    const filters = [] as ReturnType<typeof eq>[];

    if (status === "active") {
      filters.push(eq(users.status, "active"));
      filters.push(sql`${users.deletedAt} IS NULL`);
    } else if (status === "banned") {
      filters.push(eq(users.status, "banned"));
      filters.push(sql`${users.deletedAt} IS NULL`);
    } else if (status === "deleted") {
      filters.push(sql`${users.deletedAt} IS NOT NULL`);
    } else {
      // Default: live rows (any status).
      filters.push(sql`${users.deletedAt} IS NULL`);
    }

    if (stripeCustomerId) {
      filters.push(eq(users.stripeCustomerId, stripeCustomerId));
    }
    if (q) {
      const term = `%${q}%`;
      filters.push(
        or(
          ilike(users.email, term),
          ilike(users.id, term),
          ilike(users.stripeCustomerId, term),
        )!,
      );
    }
    if (osrsName) {
      // Subquery: user has an osrs_account whose display_name matches.
      const matched = await db
        .select({ userId: osrsAccounts.userId })
        .from(osrsAccounts)
        .where(ilike(osrsAccounts.displayName, `%${osrsName}%`));
      const ids = matched.map((r) => r.userId);
      if (ids.length === 0) {
        return c.json({
          ok: true,
          rows: [],
          total: 0,
          limit,
          offset,
        });
      }
      filters.push(inArray(users.id, ids));
    }

    const whereExpr = filters.length > 0 ? and(...filters) : undefined;

    const totalRows = await db
      .select({ n: count() })
      .from(users)
      .where(whereExpr);
    const total = Number(totalRows[0]?.n ?? 0);

    const rows = await db
      .select({
        id: users.id,
        email: users.email,
        stripeCustomerId: users.stripeCustomerId,
        status: users.status,
        createdAt: users.createdAt,
        deletedAt: users.deletedAt,
      })
      .from(users)
      .where(whereExpr)
      .orderBy(desc(users.createdAt))
      .limit(limit)
      .offset(offset);

    // Cheap join: tier + balance per user. Two grouped lookups beat a join here.
    const userIds = rows.map((r) => r.id);
    const tierMap = new Map<string, { tier: string; status: string }>();
    const balanceMap = new Map<string, number>();
    if (userIds.length > 0) {
      const subs = await db
        .select({
          userId: subscriptions.userId,
          tier: subscriptions.tier,
          status: subscriptions.status,
        })
        .from(subscriptions)
        .where(inArray(subscriptions.userId, userIds));
      for (const s of subs) {
        const prev = tierMap.get(s.userId);
        // Prefer active over canceled when both exist.
        if (!prev || prev.status !== "active") {
          tierMap.set(s.userId, { tier: s.tier, status: s.status });
        }
      }

      const bal = await db
        .select({
          userId: tokenBalances.userId,
          balance: tokenBalances.balanceTokens,
        })
        .from(tokenBalances)
        .where(inArray(tokenBalances.userId, userIds));
      for (const b of bal) {
        balanceMap.set(b.userId, Number(b.balance));
      }
    }

    const enriched = rows
      .map((r) => {
        const tierEntry = tierMap.get(r.id);
        return {
          id: r.id,
          email: r.email,
          stripeCustomerId: r.stripeCustomerId,
          status: r.status,
          tier: tierEntry?.tier ?? "free",
          subscriptionStatus: tierEntry?.status ?? null,
          balanceTokens: balanceMap.get(r.id) ?? 0,
          createdAt: r.createdAt,
          deletedAt: r.deletedAt,
        };
      })
      .filter((r) => (tier ? r.tier === tier : true));

    return c.json({
      ok: true,
      rows: enriched,
      total,
      limit,
      offset,
    });
  });

  /* ------------------------------------------------------------------ */
  /*  GET /admin/users/:id  (one user, the whole story)                 */
  /* ------------------------------------------------------------------ */

  app.get("/:id", async (c) => {
    const id = c.req.param("id");

    const [user] = await db.select().from(users).where(eq(users.id, id)).limit(1);
    if (!user) {
      return c.json({ ok: false, error: "user_not_found" }, 404);
    }

    const [userDevices, userOsrs, userSubs, userBalance, recentChats, recentTools] =
      await Promise.all([
        db.select().from(devices).where(eq(devices.userId, id)),
        db.select().from(osrsAccounts).where(eq(osrsAccounts.userId, id)),
        db
          .select()
          .from(subscriptions)
          .where(eq(subscriptions.userId, id))
          .orderBy(desc(subscriptions.createdAt)),
        db
          .select()
          .from(tokenBalances)
          .where(eq(tokenBalances.userId, id))
          .limit(1),
        db
          .select({
            id: chats.id,
            title: chats.title,
            createdAt: chats.createdAt,
            deletedAt: chats.deletedAt,
          })
          .from(chats)
          .where(eq(chats.userId, id))
          .orderBy(desc(chats.createdAt))
          .limit(10),
        db
          .select({
            toolName: toolCalls.toolName,
            status: toolCalls.status,
            createdAt: toolCalls.createdAt,
            durationMs: toolCalls.durationMs,
            messageId: toolCalls.messageId,
          })
          .from(toolCalls)
          .innerJoin(messages, eq(messages.id, toolCalls.messageId))
          .innerJoin(chats, eq(chats.id, messages.chatId))
          .where(eq(chats.userId, id))
          .orderBy(desc(toolCalls.createdAt))
          .limit(20),
      ]);

    const activeSubscription =
      userSubs.find((s) => s.status === "active" || s.status === "trialing") ?? null;

    // Optional Stripe invoice list (last 10). Falls back to [] when no
    // customer id or stripe is the dev stub.
    let invoices: Awaited<ReturnType<typeof stripe.invoices.list>>["data"] = [];
    if (user.stripeCustomerId) {
      try {
        const list = await stripe.invoices.list({
          customer: user.stripeCustomerId,
          limit: 10,
        });
        invoices = list.data;
      } catch {
        // Stripe failure is non-fatal for the ops view.
      }
    }

    return c.json({
      ok: true,
      user: {
        id: user.id,
        email: user.email,
        stripeCustomerId: user.stripeCustomerId,
        status: user.status,
        createdAt: user.createdAt,
        updatedAt: user.updatedAt,
        deletedAt: user.deletedAt,
      },
      devices: userDevices.map((d) => ({
        id: d.id,
        displayName: d.displayName,
        playerName: d.playerName,
        lastSeenAt: d.lastSeenAt,
        createdAt: d.createdAt,
      })),
      osrsAccounts: userOsrs,
      subscriptions: userSubs,
      activeSubscription,
      balance: userBalance[0] ?? { userId: id, balanceTokens: 0, updatedAt: null },
      recentChats,
      recentToolCalls: recentTools,
      invoices,
    });
  });

  /* ------------------------------------------------------------------ */
  /*  POST /admin/users/:id/ban                                         */
  /* ------------------------------------------------------------------ */

  app.post("/:id/ban", async (c) => {
    const id = c.req.param("id");
    const body = (await c.req.json().catch(() => ({}))) as {
      reason?: string;
    };
    if (!body.reason || body.reason.trim().length === 0) {
      return c.json({ ok: false, error: "reason_required" }, 400);
    }

    const [user] = await db.select().from(users).where(eq(users.id, id)).limit(1);
    if (!user) return c.json({ ok: false, error: "user_not_found" }, 404);

    await db
      .update(users)
      .set({ status: "banned", updatedAt: new Date() })
      .where(eq(users.id, id));

    // End any active sessions so the next WS frame is rejected.
    await db
      .update(sessions)
      .set({ endedAt: new Date() })
      .where(and(eq(sessions.userId, id), sql`${sessions.endedAt} IS NULL`));

    const adminEmail = c.var.adminEmail;
    await db.insert(eventsTable).values({
      id: newId(),
      type: "user.banned",
      userId: id,
      payload: { reason: body.reason, by: adminEmail },
    });

    return c.json({ ok: true, status: "banned" });
  });

  /* ------------------------------------------------------------------ */
  /*  POST /admin/users/:id/unban                                       */
  /* ------------------------------------------------------------------ */

  app.post("/:id/unban", async (c) => {
    const id = c.req.param("id");

    const [user] = await db.select().from(users).where(eq(users.id, id)).limit(1);
    if (!user) return c.json({ ok: false, error: "user_not_found" }, 404);

    await db
      .update(users)
      .set({ status: "active", updatedAt: new Date() })
      .where(eq(users.id, id));

    const adminEmail = c.var.adminEmail;
    await db.insert(eventsTable).values({
      id: newId(),
      type: "user.unbanned",
      userId: id,
      payload: { by: adminEmail },
    });

    return c.json({ ok: true, status: "active" });
  });

  /* ------------------------------------------------------------------ */
  /*  POST /admin/users/:id/credit                                      */
  /* ------------------------------------------------------------------ */

  app.post("/:id/credit", async (c) => {
    const id = c.req.param("id");
    const body = (await c.req.json().catch(() => ({}))) as {
      tokens?: number;
      reason?: string;
    };
    const tokens = Number.isInteger(body.tokens) ? Number(body.tokens) : NaN;
    if (!Number.isFinite(tokens) || tokens <= 0) {
      return c.json({ ok: false, error: "tokens_must_be_positive_integer" }, 400);
    }
    if (!body.reason || body.reason.trim().length === 0) {
      return c.json({ ok: false, error: "reason_required" }, 400);
    }

    const [user] = await db.select().from(users).where(eq(users.id, id)).limit(1);
    if (!user) return c.json({ ok: false, error: "user_not_found" }, 404);

    // Atomic upsert: if no balance row, create one at the granted amount.
    const [existing] = await db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, id))
      .limit(1);

    if (existing) {
      await db
        .update(tokenBalances)
        .set({
          balanceTokens: sql`${tokenBalances.balanceTokens} + ${tokens}`,
          updatedAt: new Date(),
        })
        .where(eq(tokenBalances.userId, id));
    } else {
      await db.insert(tokenBalances).values({
        userId: id,
        balanceTokens: tokens,
      });
    }

    const [next] = await db
      .select()
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, id))
      .limit(1);

    const adminEmail = c.var.adminEmail;
    await db.insert(eventsTable).values({
      id: newId(),
      type: "billing.credit_granted",
      userId: id,
      payload: {
        tokens,
        reason: body.reason,
        by: adminEmail,
        balanceAfter: Number(next?.balanceTokens ?? 0),
      },
    });

    return c.json({
      ok: true,
      tokensGranted: tokens,
      balanceTokens: Number(next?.balanceTokens ?? 0),
    });
  });

  /* ------------------------------------------------------------------ */
  /*  POST /admin/users/:id/refund                                      */
  /* ------------------------------------------------------------------ */

  app.post("/:id/refund", async (c) => {
    const id = c.req.param("id");
    const body = (await c.req.json().catch(() => ({}))) as {
      chargeId?: string;
      amountUsdCents?: number;
      reason?: string;
    };
    if (!body.chargeId || !body.chargeId.trim()) {
      return c.json({ ok: false, error: "chargeId_required" }, 400);
    }
    if (!body.reason || !body.reason.trim()) {
      return c.json({ ok: false, error: "reason_required" }, 400);
    }
    if (
      body.amountUsdCents !== undefined &&
      (!Number.isInteger(body.amountUsdCents) || body.amountUsdCents <= 0)
    ) {
      return c.json({ ok: false, error: "amountUsdCents_must_be_positive_integer" }, 400);
    }

    const [user] = await db.select().from(users).where(eq(users.id, id)).limit(1);
    if (!user) return c.json({ ok: false, error: "user_not_found" }, 404);

    const refund = await stripe.refunds.create({
      charge: body.chargeId,
      ...(body.amountUsdCents !== undefined ? { amount: body.amountUsdCents } : {}),
      reason: body.reason.slice(0, 64),
    });

    const adminEmail = c.var.adminEmail;
    await db.insert(eventsTable).values({
      id: newId(),
      type: "billing.refund_issued",
      userId: id,
      payload: {
        chargeId: body.chargeId,
        amountUsdCents: body.amountUsdCents ?? null,
        refundId: refund.id,
        stripeStatus: refund.status,
        reason: body.reason,
        by: adminEmail,
      },
    });

    return c.json({
      ok: true,
      refund: {
        id: refund.id,
        amount: refund.amount,
        currency: refund.currency,
        status: refund.status,
        // The UI uses this to render the loud "dev_stub" badge.
        isDevStub: refund.status === "dev_stub",
      },
    });
  });

  return app;
}

/* -------------------------------------------------------------------------- */
/*  Helpers                                                                   */
/* -------------------------------------------------------------------------- */

function clampInt(
  raw: string | undefined,
  fallback: number,
  min: number,
  max: number,
): number {
  if (!raw) return fallback;
  const n = Number.parseInt(raw, 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}

// computeCostMicroUsd is re-exported from llm/cost; openrouter.ts uses it.
export { computeCostMicroUsd };
