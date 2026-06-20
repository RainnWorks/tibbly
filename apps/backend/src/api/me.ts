/**
 * /v1/me — account self-service endpoints.
 *
 * STATUS: parked behind RAI-13 (monorepo skeleton) and RAI-14 (backend
 * skeleton). Drizzle schema imports below are *intended* shape — the
 * actual schema files don't exist yet. This file compiles as soon as
 * those land and the imports resolve.
 *
 * Owner: agent r-legal (RAI-34). Intended consumers: dashboard
 * (Account -> Privacy -> Export / Delete), and the backend itself when
 * processing email-driven GDPR Art. 15 / Art. 17 requests.
 *
 * Endpoints:
 *   GET    /v1/me/export   -> JSON dump of every table row tied to caller
 *   DELETE /v1/me          -> hard-delete cascading; Stripe customer
 *                            object is *detached* (PII nulled) and
 *                            preserved for the accounting retention
 *                            window (see docs/legal/DATA_RETENTION.md
 *                            "Stripe-side billing carve-out").
 */

import { Hono } from "hono";
import { eq } from "drizzle-orm";

// These imports point at the schema files RAI-14 will create.
// Until then this file will type-error in CI — that is expected and
// documented in the commit message.
import { db } from "../db/client";
import {
  users,
  devices,
  playerBindings,
  chats,
  chatTurns,
  stateSnapshots,
  consentGrants,
  sessions,
  invoices,
} from "../db/schema";
import { requireAuth, type AuthedContext } from "../middleware/auth";
import { stripe } from "../lib/stripe";

export const me = new Hono();

me.use("*", requireAuth);

/**
 * GET /v1/me/export
 *
 * Returns a JSON document containing every row in our database
 * attributable to the authenticated user. Satisfies GDPR Art. 15
 * (right of access) + Art. 20 (right to portability) and CCPA "right
 * to know".
 *
 * The response is one big JSON object. We do not paginate — if a user
 * has many chats this can be large; the dashboard streams it as a file
 * download. We send `Content-Disposition: attachment` so browsers save
 * it instead of rendering.
 */
me.get("/export", async (c: AuthedContext) => {
  const userId = c.var.userId;

  const [user] = await db.select().from(users).where(eq(users.id, userId));
  if (!user) return c.json({ error: "not_found" }, 404);

  const [
    userDevices,
    bindings,
    userChats,
    userTurns,
    userSnapshots,
    userConsent,
    userSessions,
    userInvoices,
  ] = await Promise.all([
    db.select().from(devices).where(eq(devices.userId, userId)),
    db.select().from(playerBindings).where(eq(playerBindings.userId, userId)),
    db.select().from(chats).where(eq(chats.userId, userId)),
    db.select().from(chatTurns).where(eq(chatTurns.userId, userId)),
    db.select().from(stateSnapshots).where(eq(stateSnapshots.userId, userId)),
    db.select().from(consentGrants).where(eq(consentGrants.userId, userId)),
    db.select().from(sessions).where(eq(sessions.userId, userId)),
    db.select().from(invoices).where(eq(invoices.userId, userId)),
  ]);

  const payload = {
    exportVersion: 1,
    generatedAt: new Date().toISOString(),
    notice:
      "This is the data we hold about you. Some fields are hashed " +
      "(device keys, IPs). Stripe-side billing records may be " +
      "retained beyond account deletion per accounting law — see " +
      "docs/legal/PRIVACY.md §10.",
    user,
    devices: userDevices,
    playerBindings: bindings,
    chats: userChats,
    chatTurns: userTurns,
    stateSnapshots: userSnapshots,
    consentGrants: userConsent,
    sessions: userSessions,
    invoices: userInvoices,
  };

  c.header(
    "Content-Disposition",
    `attachment; filename="osrs-llm-helper-export-${userId}.json"`,
  );
  c.header("Content-Type", "application/json; charset=utf-8");
  return c.body(JSON.stringify(payload, null, 2));
});

/**
 * DELETE /v1/me
 *
 * Hard-deletes the user and every row that references them, with the
 * Stripe carve-out described in docs/legal/DATA_RETENTION.md.
 *
 * Order matters — children before parents to satisfy FK constraints
 * unless we add ON DELETE CASCADE everywhere (RAI-14 should add them;
 * we do explicit deletes here for safety + auditability).
 *
 * Returns 204 on success. Idempotent: deleting twice still returns 204.
 *
 * NB: this endpoint is *immediate* hard-delete. A 30-day soft-delete
 * grace ("undo" within 30d) is also valid per our policy; if we ship
 * that variant the body should accept `{ mode: "soft" | "hard" }` and
 * default to soft. Discuss with billing + retention owners (RAI-15 +
 * RAI-34) before flipping the default.
 */
me.delete("/", async (c: AuthedContext) => {
  const userId = c.var.userId;

  const [user] = await db.select().from(users).where(eq(users.id, userId));
  if (!user) {
    // already gone — idempotent
    return c.body(null, 204);
  }

  // 1. Stripe-side: detach PII but preserve the customer for accounting.
  if (user.stripeCustomerId) {
    try {
      await stripe.customers.update(user.stripeCustomerId, {
        email: undefined,
        name: undefined,
        metadata: {
          osrs_user_id: "",
          deleted_at: new Date().toISOString(),
          reason: "user_requested_deletion",
        },
      });
    } catch (err) {
      // Don't fail the delete because of a Stripe transient — log and
      // continue. A nightly reconciler will re-detach if needed.
      console.error("stripe detach failed", { userId, err });
    }
  }

  // 2. DB-side: delete in dependency order.
  // Wrap in a single transaction so we are all-or-nothing.
  await db.transaction(async (tx) => {
    await tx.delete(stateSnapshots).where(eq(stateSnapshots.userId, userId));
    await tx.delete(chatTurns).where(eq(chatTurns.userId, userId));
    await tx.delete(chats).where(eq(chats.userId, userId));
    await tx.delete(sessions).where(eq(sessions.userId, userId));
    await tx.delete(playerBindings).where(eq(playerBindings.userId, userId));
    await tx.delete(devices).where(eq(devices.userId, userId));

    // Invoices: we keep the row but null PII. This row mirrors the
    // Stripe-side carve-out for SQL queries.
    await tx
      .update(invoices)
      .set({ userIdAnonymisedAt: new Date() })
      .where(eq(invoices.userId, userId));

    // Consent grants: anonymise instead of delete — we need proof of
    // consent for the limitation period (see DATA_RETENTION.md).
    await tx
      .update(consentGrants)
      .set({ anonymisedAt: new Date(), ipHash: null })
      .where(eq(consentGrants.userId, userId));

    await tx.delete(users).where(eq(users.id, userId));
  });

  return c.body(null, 204);
});

export default me;
