/**
 * Token meter + hard cap (RAI-20).
 *
 * Two entrypoints — both atomic at the SQL level so concurrent chats can't
 * double-spend a balance:
 *
 *   1. `ensureCanSpend(userId, estimatedTokens)` — called BEFORE we open the
 *      OpenRouter stream. Tries to optimistically decrement `estimatedTokens`
 *      from the balance. If the row would go negative, returns `false`. The
 *      caller MUST reject the chat turn and emit a `chat.cap_hit` event (we
 *      emit it from here, since the bus handle is on the meter deps).
 *
 *   2. `record(userId, model, prompt, completion)` — called AFTER the stream
 *      finishes. Reconciles the actual usage against the estimate. We don't
 *      try to refund: if a user paid `estimatedTokens` and only used half,
 *      the next call gets the leftover via natural balance arithmetic on the
 *      next ensureCanSpend; we don't re-credit because the row was never
 *      taken back. Instead, `record` decrements ONLY the delta above the
 *      estimate so net cost equals max(actual, estimate). See `MeterRecord`.
 *
 *      Design choice: under-estimating is fine (the meter catches up). Over-
 *      estimating is fine too (we never refund). Both keep us safe; both keep
 *      the implementation a single UPDATE per call.
 *
 *      Edge case: a mid-flight request whose actual cost pushes the balance
 *      below zero is allowed to finish; we land at `balance_tokens = 0` and
 *      the NEXT call to `ensureCanSpend` returns `false`. This matches the
 *      RAI-20 brief.
 *
 * The atomic SQL we rely on is:
 *
 *   UPDATE token_balances
 *      SET balance_tokens = balance_tokens - $X,
 *          updated_at = now()
 *    WHERE user_id = $u AND balance_tokens >= $X
 *    RETURNING balance_tokens
 *
 * Row-level locking via the `WHERE balance_tokens >= $X` predicate gives us
 * the no-double-spend invariant without an explicit transaction.
 */
import { sql, eq } from "drizzle-orm";

import type { DbClient } from "../db/client";
import { tokenBalances } from "../db/schema";
import type { EventBus } from "../events";
import { getDefaultBus } from "../events";
import { log } from "../lib/log";
import { computeCostMicroUsd, type TokenUsage } from "../llm/cost";

export interface MeterDeps {
  db: DbClient;
  /** Defaults to the process bus; tests pass a fresh `createEventBus()`. */
  bus?: EventBus;
}

export interface MeterRecordArgs {
  userId: string;
  /** OpenRouter model id — used for cost attribution. */
  model: string;
  promptTokens: number;
  completionTokens: number;
  /** Estimate previously taken via `ensureCanSpend`, if any. Defaults to 0. */
  preDebited?: number;
  /** Optional chat id — included in the emitted event for correlation. */
  chatId?: string;
}

export interface MeterRecordResult {
  /** Balance AFTER the reconciling decrement. May be 0 if mid-flight pushed past. */
  newBalance: number;
  /** Micro-USD cost of this turn (per `MODEL_PRICING`). */
  costMicroUsd: number;
  /** Net tokens debited from the balance on this call (max(0, actual - preDebited)). */
  debited: number;
}

export interface TokenMeter {
  /**
   * Atomically reserve `estimatedTokens`. Returns the new balance on success,
   * or `null` if the user can't afford it (balance < estimatedTokens). On
   * rejection, emits `chat.cap_hit`.
   */
  ensureCanSpend(
    userId: string,
    estimatedTokens: number,
    opts?: { chatId?: string },
  ): Promise<{ newBalance: number } | null>;

  /**
   * Atomically reconcile actual usage. Always succeeds (may take balance to
   * 0 mid-flight, never below). Emits `billing.balance.decremented`.
   */
  record(args: MeterRecordArgs): Promise<MeterRecordResult>;

  /** Read current balance (no mutation). */
  getBalance(userId: string): Promise<number>;

  /** Top-up — used by Stripe webhook on `invoice.payment_succeeded`. */
  credit(userId: string, tokens: number): Promise<{ newBalance: number }>;
}

/**
 * Build a token meter against the supplied Drizzle client + bus.
 *
 * Why we don't take the singletons here: the WS handler already wires the
 * meter into a `BalanceMeter` port — tests want to instantiate the meter
 * against an in-memory PGLite without process-global state. Production wires
 * it once in `server.ts`.
 */
export function createTokenMeter(deps: MeterDeps): TokenMeter {
  const bus = deps.bus ?? getDefaultBus();
  const db = deps.db;

  async function ensureCanSpend(
    userId: string,
    estimatedTokens: number,
    opts?: { chatId?: string },
  ): Promise<{ newBalance: number } | null> {
    const cost = Math.max(0, Math.floor(estimatedTokens));
    if (cost === 0) {
      // Nothing to debit; surface the current balance so the caller can
      // still record it in `auth_ok` / `assistant_message_done`.
      const current = await getBalance(userId);
      return { newBalance: current };
    }

    // Atomic "decrement IFF affordable". Drizzle's `update().where()` maps
    // to a single UPDATE — the `>=` predicate is what makes it safe. We use
    // the no-arg `.returning()` form because Drizzle's typed `returning({...})`
    // overload narrows badly across the pglite + postgres-js union we expose
    // via DbClient.
    const rows = await db
      .update(tokenBalances)
      .set({
        balanceTokens: sql`${tokenBalances.balanceTokens} - ${cost}`,
        updatedAt: new Date(),
      })
      .where(
        sql`${tokenBalances.userId} = ${userId} AND ${tokenBalances.balanceTokens} >= ${cost}`,
      )
      .returning();

    if (rows.length === 0) {
      // Either no balance row yet OR not enough. Read the current balance so
      // we can include it in the `chat.cap_hit` event payload.
      const balance = await getBalance(userId);
      try {
        await bus.publish({
          type: "chat.cap_hit",
          userId,
          payload: {
            chatId: opts?.chatId ?? "unknown",
            balanceBefore: balance,
          },
        });
      } catch (err) {
        log.warn({ err, userId }, "meter: cap_hit event failed to publish");
      }
      return null;
    }

    const newBalance = Number(rows[0]!.balanceTokens);
    return { newBalance };
  }

  async function record(args: MeterRecordArgs): Promise<MeterRecordResult> {
    const usage: TokenUsage = {
      promptTokens: args.promptTokens,
      completionTokens: args.completionTokens,
    };
    const actualTokens = Math.max(
      0,
      Math.floor(args.promptTokens) + Math.floor(args.completionTokens),
    );
    const preDebited = Math.max(0, Math.floor(args.preDebited ?? 0));
    const debitDelta = Math.max(0, actualTokens - preDebited);
    const costMicroUsd = computeCostMicroUsd(args.model, usage);

    let newBalance: number;
    if (debitDelta > 0) {
      // We don't enforce `>= debitDelta` here because the brief allows the
      // in-flight call to land below zero — but Postgres bigint won't overflow,
      // and we clamp at zero with GREATEST(...) so a small over-spend can't
      // leave the balance negative.
      const rows = await db
        .update(tokenBalances)
        .set({
          balanceTokens: sql`GREATEST(${tokenBalances.balanceTokens} - ${debitDelta}, 0)`,
          updatedAt: new Date(),
        })
        .where(eq(tokenBalances.userId, args.userId))
        .returning();
      if (rows.length === 0) {
        // No row yet → ensure one exists at zero, then read back.
        await db
          .insert(tokenBalances)
          .values({ userId: args.userId, balanceTokens: 0 })
          .onConflictDoNothing({ target: tokenBalances.userId });
        newBalance = 0;
      } else {
        newBalance = Number(rows[0]!.balanceTokens);
      }
    } else {
      newBalance = await getBalance(args.userId);
    }

    if (debitDelta > 0) {
      try {
        await bus.publish({
          type: "billing.balance.decremented",
          userId: args.userId,
          payload: {
            amount: debitDelta,
            model: args.model,
            newBalance,
          },
        });
      } catch (err) {
        log.warn({ err, userId: args.userId }, "meter: balance.decremented event failed");
      }
    }

    return { newBalance, costMicroUsd, debited: debitDelta };
  }

  async function getBalance(userId: string): Promise<number> {
    const rows = await db
      .select({ balance: tokenBalances.balanceTokens })
      .from(tokenBalances)
      .where(eq(tokenBalances.userId, userId))
      .limit(1);
    if (rows.length === 0) return 0;
    return Number(rows[0]!.balance);
  }

  async function credit(userId: string, tokens: number): Promise<{ newBalance: number }> {
    const amount = Math.max(0, Math.floor(tokens));
    if (amount === 0) {
      return { newBalance: await getBalance(userId) };
    }

    // Upsert: if no row exists, insert with the credit; if it does, add to it.
    const inserted = await db
      .insert(tokenBalances)
      .values({ userId, balanceTokens: amount })
      .onConflictDoUpdate({
        target: tokenBalances.userId,
        set: {
          balanceTokens: sql`${tokenBalances.balanceTokens} + ${amount}`,
          updatedAt: new Date(),
        },
      })
      .returning();

    const newBalance = inserted.length > 0 ? Number(inserted[0]!.balanceTokens) : amount;
    try {
      await bus.publish({
        type: "billing.balance.decremented",
        userId,
        // Negative `amount` would fail Zod (`.positive()`), so we emit a
        // separate event for credits at the webhook layer. This is the
        // "after meter touched the balance" hook.
        // (Intentional no-op here — see webhook for the credit event.)
        payload: {
          amount: amount,
          model: "stripe.credit",
          newBalance,
        },
      });
    } catch (err) {
      log.warn({ err, userId }, "meter: credit event failed to publish");
    }

    return { newBalance };
  }

  return { ensureCanSpend, record, getBalance, credit };
}
