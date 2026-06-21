/**
 * `/v1/me` — account self-service GDPR endpoints (RAI-34 + M3.5).
 *
 * Two endpoints:
 *   GET    /v1/me/export   — Art. 15 / Art. 20 data export (JSON dump).
 *   DELETE /v1/me          — Art. 17 right-to-erasure (hard cascade,
 *                            Stripe customer detached per retention
 *                            carve-out in docs/legal/DATA_RETENTION.md).
 *
 * Auth: `requireUser` middleware (header-based for now; session upgrade
 * tracked separately). Tests should never reach Stripe; pass a stub via
 * `createMeRouter({ stripe })`.
 *
 * History: pre-loop M+2 this file referenced a hypothetical schema
 * (`playerBindings`, `stateSnapshots`, `consentGrants`, `invoices`) that
 * never landed and was not mounted on the app. Rewritten in loop M+2
 * against the real schema; mounted in `app.ts`.
 */
import { Hono } from "hono";
import { eq, inArray } from "drizzle-orm";
import type Stripe from "stripe";

import type { DbClient } from "../db/client";
import { getDb } from "../db/client";
import {
  users,
  devices,
  osrsAccounts,
  pairingCodes,
  sessions,
  chats,
  messages,
  toolCalls,
  usageRecords,
  subscriptions,
  tokenBalances,
  events,
} from "../db/schema";
import { requireUserWith, type AuthedVars, type DeviceKeyCache } from "./_auth";
import { getStripe } from "../billing/stripe";
import { log } from "../lib/log";

export interface CreateMeRouterOptions {
  /** Override DB client; defaults to the process-wide `getDb()`. */
  db?: DbClient;
  /** Override Stripe client; defaults to `getStripe()`. Tests pass a stub. */
  stripe?: Stripe;
  /** Optional shared device-key cache; defaults to a per-router cache. */
  deviceKeyCache?: DeviceKeyCache;
}

export function createMeRouter(options: CreateMeRouterOptions = {}): Hono<{
  Variables: AuthedVars;
}> {
  const db = options.db ?? getDb().db;
  const stripeClient = options.stripe ?? null;

  const app = new Hono<{ Variables: AuthedVars }>();
  app.use("*", requireUserWith({ db, ...(options.deviceKeyCache ? { cache: options.deviceKeyCache } : {}) }));

  /**
   * GET /v1/me/export — every row attributable to the caller, in one JSON.
   *
   * No pagination: the dashboard streams the response as an attachment so
   * browsers save it as a file rather than rendering. Stripe-side billing
   * records are NOT included (they live with Stripe per the carve-out).
   */
  app.get("/export", async (c) => {
    const userId = c.var.userId;

    const [user] = await db.select().from(users).where(eq(users.id, userId));
    if (!user) return c.json({ ok: false, error: "not_found" }, 404);

    const userChats = await db.select().from(chats).where(eq(chats.userId, userId));
    const chatIds = userChats.map((row) => row.id);

    const [
      userDevices,
      userOsrsAccounts,
      userPairingCodes,
      userSessions,
      userMessages,
      userToolCalls,
      userUsage,
      userSubscriptions,
      userBalance,
    ] = await Promise.all([
      db.select().from(devices).where(eq(devices.userId, userId)),
      db.select().from(osrsAccounts).where(eq(osrsAccounts.userId, userId)),
      db.select().from(pairingCodes).where(eq(pairingCodes.userId, userId)),
      db.select().from(sessions).where(eq(sessions.userId, userId)),
      chatIds.length === 0
        ? Promise.resolve([])
        : db.select().from(messages).where(inArray(messages.chatId, chatIds)),
      chatIds.length === 0
        ? Promise.resolve([])
        : db
            .select()
            .from(toolCalls)
            .where(
              inArray(
                toolCalls.messageId,
                db
                  .select({ id: messages.id })
                  .from(messages)
                  .where(inArray(messages.chatId, chatIds)),
              ),
            ),
      db.select().from(usageRecords).where(eq(usageRecords.userId, userId)),
      db.select().from(subscriptions).where(eq(subscriptions.userId, userId)),
      db
        .select()
        .from(tokenBalances)
        .where(eq(tokenBalances.userId, userId)),
    ]);

    const payload = {
      exportVersion: 2,
      generatedAt: new Date().toISOString(),
      notice:
        "This is the data we hold about you. Device keys are stored as " +
        "Argon2id hashes; raw keys are never persisted. Stripe-side " +
        "billing records may be retained beyond account deletion per " +
        "UK/US accounting law — see docs/legal/PRIVACY.md §10 and " +
        "docs/legal/DATA_RETENTION.md.",
      user,
      devices: userDevices,
      osrsAccounts: userOsrsAccounts,
      pairingCodes: userPairingCodes,
      sessions: userSessions,
      chats: userChats,
      messages: userMessages,
      toolCalls: userToolCalls,
      usageRecords: userUsage,
      subscriptions: userSubscriptions,
      tokenBalance: userBalance[0] ?? null,
    };

    c.header(
      "Content-Disposition",
      `attachment; filename="tibbly-export-${userId}.json"`,
    );
    c.header("Content-Type", "application/json; charset=utf-8");
    return c.body(JSON.stringify(payload, null, 2));
  });

  /**
   * DELETE /v1/me — Art. 17 hard-delete with Stripe carve-out.
   *
   * Idempotent: a second call on a soft-deleted user returns 204 without
   * re-running the cascade. Stripe customer PII is detached on first call.
   *
   * The cascade order is explicit (children → parents) even though most
   * FKs use ON DELETE CASCADE — explicit deletes make the audit log
   * readable and let the transaction roll back cleanly on any failure.
   *
   * Rows kept (anonymised) for compliance audit:
   *   - `usage_records` rows are dropped (the aggregate billing total
   *     lives with Stripe).
   *   - `events.user_id` rows are nulled where present (analytics
   *     retention is bounded; nulling preserves the row count for
   *     funnel math without retaining PII).
   *   - `subscriptions` rows are dropped; Stripe is source of truth.
   */
  app.delete("/", async (c) => {
    const userId = c.var.userId;

    const [user] = await db.select().from(users).where(eq(users.id, userId));
    if (!user) {
      // Idempotent — already gone.
      return c.body(null, 204);
    }
    if (user.deletedAt) {
      // Soft-deleted already; nothing to do.
      return c.body(null, 204);
    }

    // 1. Stripe carve-out: detach PII but keep the customer object so
    //    accounting/audit can resolve historical invoices.
    if (user.stripeCustomerId) {
      const sc = stripeClient ?? getStripe();
      try {
        await sc.customers.update(user.stripeCustomerId, {
          email: "",
          name: "",
          metadata: {
            tibbly_user_id: "",
            deleted_at: new Date().toISOString(),
            reason: "user_requested_deletion",
          },
        });
      } catch (err) {
        // Don't fail the whole flow on a Stripe transient — the nightly
        // reconciler will re-detach. Log loud so it's visible in ops.
        log.warn(
          { userId, err: (err as Error).message },
          "stripe customer detach failed during /v1/me delete",
        );
      }
    }

    // 2. DB-side cascade — explicit, deepest-first.
    await db.transaction(async (tx) => {
      const userChats = await tx
        .select({ id: chats.id })
        .from(chats)
        .where(eq(chats.userId, userId));
      const chatIds = userChats.map((row) => row.id);

      if (chatIds.length > 0) {
        // tool_calls → messages → chats (FK cascades cover messages →
        // tool_calls, but we delete explicitly for the audit trail).
        await tx.delete(toolCalls).where(
          inArray(
            toolCalls.messageId,
            tx
              .select({ id: messages.id })
              .from(messages)
              .where(inArray(messages.chatId, chatIds)),
          ),
        );
        await tx.delete(messages).where(inArray(messages.chatId, chatIds));
        await tx.delete(chats).where(eq(chats.userId, userId));
      }

      await tx.delete(usageRecords).where(eq(usageRecords.userId, userId));
      await tx.delete(tokenBalances).where(eq(tokenBalances.userId, userId));
      await tx.delete(subscriptions).where(eq(subscriptions.userId, userId));
      await tx.delete(sessions).where(eq(sessions.userId, userId));
      await tx.delete(pairingCodes).where(eq(pairingCodes.userId, userId));
      await tx.delete(osrsAccounts).where(eq(osrsAccounts.userId, userId));
      await tx.delete(devices).where(eq(devices.userId, userId));

      // Analytics events: null the user id so the funnel-step count
      // survives without the PII linkage.
      await tx
        .update(events)
        .set({ userId: null })
        .where(eq(events.userId, userId));

      // Finally: mark the user soft-deleted and null PII. The retention
      // sweeper hard-deletes the row after the accounting window expires.
      await tx
        .update(users)
        .set({
          email: null,
          deletedAt: new Date(),
          updatedAt: new Date(),
        })
        .where(eq(users.id, userId));
    });

    log.info({ userId }, "/v1/me delete completed");
    return c.body(null, 204);
  });

  return app;
}
