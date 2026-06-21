/**
 * Scenario-side helpers that lean on the real backend HTTP + DB layers
 * to put the orchestrator into a known state.
 *
 * Why HTTP and not direct inserts: we want the same code paths the live
 * plugin will exercise. The pairing flow is the canonical way a device
 * shows up in the system; bypassing it would mean the scenarios are not
 * actually testing what production does.
 */
import { eq } from "drizzle-orm";
import { nanoid } from "nanoid";

import { subscriptions, tokenBalances, users } from "../../apps/backend/src/db/schema";
import type { OrchestratorContext } from "./boot";

export interface PairedUser {
  userId: string;
  deviceId: string;
  deviceKey: string;
  email: string | null;
  /** Tier the user was upgraded to. */
  tier: "hobbyist" | "pro" | "iron";
  /** Token balance that was credited. */
  initialBalanceTokens: number;
}

export interface PairUserOptions {
  deviceKey: string;
  /** Optional player name to bind on the pairing request. */
  playerName?: string;
  /** Email to associate with the new user. Optional. */
  email?: string;
  /** Tier to promote the user to after pairing. Default "hobbyist". */
  tier?: "hobbyist" | "pro" | "iron";
  /** Token credit to apply. Default 100_000. */
  creditTokens?: number;
}

/**
 * Run the full pair flow:
 *
 *   1. POST /v1/pairing/request (plugin side, with the device key).
 *   2. POST /v1/pairing/claim (dashboard side, with optional email).
 *   3. Direct DB insert of an `active` subscription + token balance so the
 *      WS auth gate has something to read. (We do not have a webhook in
 *      the harness because Stripe is stubbed; this is the moral equivalent
 *      of `checkout.session.completed` arriving for the user.)
 */
export async function pairUser(
  ctx: OrchestratorContext,
  opts: PairUserOptions,
): Promise<PairedUser> {
  const tier = opts.tier ?? "hobbyist";
  const creditTokens = opts.creditTokens ?? 100_000;

  const requestRes = await fetch(`${ctx.backendUrl}/v1/pairing/request`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      deviceKey: opts.deviceKey,
      ...(opts.playerName ? { playerName: opts.playerName } : {}),
    }),
  });
  if (!requestRes.ok) {
    throw new Error(`pair: /pairing/request ${requestRes.status}: ${await requestRes.text()}`);
  }
  const { code } = (await requestRes.json()) as { code: string };

  const claimRes = await fetch(`${ctx.backendUrl}/v1/pairing/claim`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      code,
      ...(opts.email ? { email: opts.email } : {}),
    }),
  });
  if (!claimRes.ok) {
    throw new Error(`pair: /pairing/claim ${claimRes.status}: ${await claimRes.text()}`);
  }
  const { userId, deviceId } = (await claimRes.json()) as {
    userId: string;
    deviceId: string;
  };

  // Activate a subscription so the WS auth path reads the right tier.
  const periodStart = new Date();
  const periodEnd = new Date(periodStart.getTime() + 30 * 24 * 60 * 60 * 1000);
  await ctx.db.insert(subscriptions).values({
    id: `sub_${nanoid(10)}`,
    userId,
    tier,
    status: "active",
    stripeSubscriptionId: `sub_e2e_${nanoid(8)}`,
    currentPeriodStart: periodStart,
    currentPeriodEnd: periodEnd,
    monthlyQuotaTokens: creditTokens,
  });

  // Credit tokens. The meter has a credit helper but we go direct so the
  // seed is the same whether the meter was already swapped out or not.
  await ctx.db
    .insert(tokenBalances)
    .values({ userId, balanceTokens: creditTokens })
    .onConflictDoUpdate({
      target: tokenBalances.userId,
      set: { balanceTokens: creditTokens },
    });

  return {
    userId,
    deviceId,
    deviceKey: opts.deviceKey,
    email: opts.email ?? null,
    tier,
    initialBalanceTokens: creditTokens,
  };
}

/** Read the current user row. Handy for asserting status/email mutations. */
export async function getUser(
  ctx: OrchestratorContext,
  userId: string,
): Promise<typeof users.$inferSelect | null> {
  const [row] = await ctx.db.select().from(users).where(eq(users.id, userId)).limit(1);
  return row ?? null;
}

/** Read the current token balance. Returns 0 when the row is missing. */
export async function getBalance(ctx: OrchestratorContext, userId: string): Promise<number> {
  const [row] = await ctx.db
    .select()
    .from(tokenBalances)
    .where(eq(tokenBalances.userId, userId))
    .limit(1);
  return row?.balanceTokens ?? 0;
}

/**
 * Drive the real admin-login flow (`POST /admin/login`) and extract the
 * `ops_session` cookie. This mirrors what the ops console does in the
 * browser, so the cookie carried back is byte-identical to production.
 *
 * Audit C2 closed the legacy `x-admin-email` trust root (PR #69). E2E
 * scenarios that touch /admin/* MUST authenticate via this helper now;
 * sending the legacy header gets 401, which is the right invariant.
 */
export async function loginAsAdmin(
  ctx: OrchestratorContext,
  email: string = ctx.env.ADMIN_EMAIL,
): Promise<{ cookie: string }> {
  const res = await fetch(`${ctx.backendUrl}/admin/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email }),
  });
  if (!res.ok) {
    throw new Error(`loginAsAdmin: /admin/login ${res.status}: ${await res.text()}`);
  }
  const setCookie = res.headers.get("set-cookie");
  if (!setCookie) {
    throw new Error("loginAsAdmin: /admin/login response missing Set-Cookie");
  }
  // Strip cookie attributes (Path=, HttpOnly, etc.); we only need name=value.
  const firstPair = setCookie.split(";")[0];
  if (!firstPair || !firstPair.startsWith("ops_session=")) {
    throw new Error(
      `loginAsAdmin: unexpected Set-Cookie shape: ${setCookie.slice(0, 80)}`,
    );
  }
  return { cookie: firstPair };
}

/**
 * Harness self-test: hit `/health` and assert 200. Called at the top of
 * every scenario so a future orchestrator regression that fails to boot
 * the backend fails LOUDLY instead of silently zero-ing the rest of the
 * assertions (test-quality-001 finding 1: the kind of bug that lets a
 * suite pass without exercising anything).
 */
export async function assertHarnessHealthy(ctx: OrchestratorContext): Promise<void> {
  const res = await fetch(`${ctx.backendUrl}/health`);
  if (res.status !== 200) {
    throw new Error(
      `harness self-test: /health returned ${res.status} (expected 200). ` +
        "The orchestrator did not boot a working backend; refusing to run.",
    );
  }
}
