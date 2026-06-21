/**
 * Account-panel feed for the in-RuneLite Tibbly panel (D-8 pivot).
 *
 * Two read-only endpoints feed the Swing `AccountPanel.kt` that ships in the
 * plugin. Both return shapes that have been hand-tuned for the panel's
 * "tier-aware proxy" requirement: NO raw token math ever crosses this
 * boundary into the player-visible surface.
 *
 *   GET /v1/account/summary       → tier badge, paired OSRS chars, paired devices.
 *   GET /v1/account/usage-proxy   → message-count-style proxy (NEVER tokens).
 *
 * The dashboard that used to consume `/v1/usage/summary` (raw tokens) lives
 * on internally at `apps/ops` now; the plugin must call THIS file. See
 * `docs/agents/DECISION_LOG.md` D-8 and the source-of-truth rule in
 * `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt`.
 *
 * Auth: `requireUser` middleware (header-based for now — matches every other
 * v1 endpoint). Production gateway is responsible for stripping `x-user-id`
 * from untrusted callers.
 */
import { and, desc, eq, gte, sql } from "drizzle-orm";
import { Hono } from "hono";

import type { DbClient } from "../db/client";
import {
  devices,
  osrsAccounts,
  subscriptions,
  usageRecords,
  users,
} from "../db/schema";
import { TIER_BY_NAME, type TierSpec } from "../billing/tiers";
import { requireUser, type AuthedVars } from "./_auth";

export interface CreateAccountRouterOptions {
  db: DbClient;
}

/* ---------------------------------------------------------------- summary ---- */

/**
 * What the account header section of the Swing panel renders. Field names are
 * deliberately camelCase to match the Kotlin `kotlinx.serialization` data-class
 * defaults — see `AccountSummaryClient.kt` in the plugin.
 *
 * `tier` is the *current* subscription tier or `null` for users who have never
 * paid. `subscriptionStatus` is the Stripe status string so the plugin can
 * distinguish "active" from "past_due" without re-mapping.
 *
 * `pairedOsrsAccounts` / `pairedDevices` are intentionally small DTOs — the
 * panel never displays the underlying Stripe customer id, device-key hash, or
 * raw email. Those are dropped at the SQL boundary.
 */
export interface AccountSummaryDTO {
  tier: "hobbyist" | "pro" | "iron" | null;
  subscriptionStatus: string | null;
  renewsAt: string | null;
  pairedOsrsAccounts: Array<{
    id: string;
    displayName: string;
    accountType: string;
    isCurrent: boolean;
  }>;
  pairedDevices: Array<{
    id: string;
    displayName: string | null;
    lastSeenAt: string | null;
    isCurrent: boolean;
  }>;
  consent: {
    /** When the panel's "delete my data" link should be primary vs hidden. */
    canDeleteAccount: boolean;
  };
}

/* -------------------------------------------------------------- usage proxy -- */

/**
 * The single field the player's panel actually shows. Three shapes, chosen
 * server-side so the plugin never has to translate raw token counts.
 *
 *  - `messages-left`        — Hobbyist: surface a daily cap as `N / M used`.
 *  - `subscription-active`  — Pro: surface a renewal date, no scary number.
 *  - `unlimited`            — Iron: hide the counter unless we actually hit it.
 *
 * If the underlying token meter ever leaks raw counts into here, that's the
 * bug — the backend must translate. The plugin asserts via the Gradle
 * `:checkAccountPanelNoRawTokens` grep guard.
 */
export type UsageProxyDTO =
  | {
      form: "messages-left";
      messagesUsedToday: number;
      messagesPerDay: number;
    }
  | {
      form: "subscription-active";
      renewsAt: string;
    }
  | {
      form: "unlimited";
    };

/* ----------------------------------------------------------------- router ---- */

export function createAccountRouter(
  options: CreateAccountRouterOptions,
): Hono<{ Variables: AuthedVars }> {
  const { db } = options;
  const app = new Hono<{ Variables: AuthedVars }>();

  app.use("*", requireUser);

  app.get("/summary", async (c) => {
    const userId = c.var.userId;

    // Optional hint headers — the plugin tells us which OSRS character /
    // device it's running as so we can mark "(current)" in the panel
    // without forcing a heuristic at render time.
    const currentPlayerName = c.req.header("x-current-player")?.trim() ?? null;
    const currentDeviceKey = c.req.header("x-device-key")?.trim() ?? null;

    const summary = await loadAccountSummary(db, userId, {
      currentPlayerName,
      currentDeviceKey,
    });
    if (!summary) return c.json({ ok: false, error: "user_not_found" }, 404);
    return c.json(summary);
  });

  app.get("/usage-proxy", async (c) => {
    const userId = c.var.userId;
    const proxy = await loadUsageProxy(db, userId);
    return c.json(proxy);
  });

  return app;
}

/* ------------------------------------------------------------------ loaders -- */

/**
 * Hint payload from the plugin so we can mark the active OSRS character +
 * device. Both are optional: the panel still renders without them, the
 * `isCurrent` flag just stays false everywhere.
 */
export interface SummaryHints {
  currentPlayerName: string | null;
  currentDeviceKey: string | null;
}

export async function loadAccountSummary(
  db: DbClient,
  userId: string,
  hints: SummaryHints = { currentPlayerName: null, currentDeviceKey: null },
): Promise<AccountSummaryDTO | null> {
  const [user] = await db
    .select({ id: users.id })
    .from(users)
    .where(eq(users.id, userId))
    .limit(1);
  if (!user) return null;

  const [sub] = await db
    .select({
      tier: subscriptions.tier,
      status: subscriptions.status,
      currentPeriodEnd: subscriptions.currentPeriodEnd,
    })
    .from(subscriptions)
    .where(eq(subscriptions.userId, userId))
    .orderBy(desc(subscriptions.currentPeriodEnd))
    .limit(1);

  const accountRows = await db
    .select()
    .from(osrsAccounts)
    .where(eq(osrsAccounts.userId, userId));

  const deviceRows = await db
    .select({
      id: devices.id,
      displayName: devices.displayName,
      playerName: devices.playerName,
      lastSeenAt: devices.lastSeenAt,
      deviceKeyHash: devices.deviceKeyHash,
    })
    .from(devices)
    .where(eq(devices.userId, userId));

  return {
    tier: sub ? sub.tier : null,
    subscriptionStatus: sub ? sub.status : null,
    renewsAt: sub ? sub.currentPeriodEnd.toISOString() : null,
    pairedOsrsAccounts: accountRows.map((row) => ({
      id: row.id,
      displayName: row.displayName,
      accountType: row.accountType,
      isCurrent:
        hints.currentPlayerName !== null &&
        row.displayName.toLowerCase() === hints.currentPlayerName.toLowerCase(),
    })),
    pairedDevices: deviceRows.map((row) => ({
      id: row.id,
      displayName: row.displayName,
      lastSeenAt: row.lastSeenAt ? row.lastSeenAt.toISOString() : null,
      // The panel sends the SAME hash the auth path uses — so we can match
      // exactly. The raw key never crosses this boundary in either direction.
      isCurrent:
        hints.currentDeviceKey !== null &&
        row.deviceKeyHash === hints.currentDeviceKey,
    })),
    consent: {
      canDeleteAccount: true,
    },
  };
}

/* --- tier → daily message budget ---------------------------------------- */

/**
 * How we translate a tier's monthly *token* quota into a daily *message* cap
 * shown to the player. Conservative: assume ~1500 tokens per message round-trip
 * (prompt + completion average, per docs/research/llm-providers/_SUMMARY.md).
 * Hobbyist's 100K monthly quota → ~66 msg/month → we floor at 30/day for the
 * "messages-left" proxy so the number stays sensible even mid-month.
 *
 * The exact mapping is intentionally an internal heuristic the player never
 * sees — they see "23 / 30 used today", we keep the math honest server-side.
 */
const TOKENS_PER_MESSAGE_ROUNDTRIP = 1500;

function dailyMessageBudgetFor(spec: TierSpec): number {
  // monthly quota / ~30 days / per-message tokens, rounded to the nearest 5.
  const raw = spec.quotaTokens / 30 / TOKENS_PER_MESSAGE_ROUNDTRIP;
  const rounded = Math.max(5, Math.round(raw / 5) * 5);
  return rounded;
}

/** Free-tier daily message cap when there's no subscription row. */
export const FREE_TIER_DAILY_MESSAGES = 30;

const DAY_MS = 24 * 60 * 60 * 1000;

export async function loadUsageProxy(
  db: DbClient,
  userId: string,
  now: Date = new Date(),
): Promise<UsageProxyDTO> {
  const [sub] = await db
    .select({
      tier: subscriptions.tier,
      status: subscriptions.status,
      currentPeriodEnd: subscriptions.currentPeriodEnd,
    })
    .from(subscriptions)
    .where(eq(subscriptions.userId, userId))
    .orderBy(desc(subscriptions.currentPeriodEnd))
    .limit(1);

  // Iron-tier users see no counter unless we hit a real cap (we don't yet
  // model that; surface "unlimited" until a future hard-cap event ships).
  if (sub && sub.status === "active" && sub.tier === "iron") {
    return { form: "unlimited" };
  }

  // Pro tier: surface the renewal date, never a number.
  if (sub && sub.status === "active" && sub.tier === "pro") {
    return {
      form: "subscription-active",
      renewsAt: sub.currentPeriodEnd.toISOString(),
    };
  }

  // Hobbyist or free: roll up today's messages-sent count into the proxy.
  const since = new Date(now.getTime() - DAY_MS);
  const [row] = await db
    .select({
      messages: sql<number>`coalesce(count(*)::int, 0)`,
    })
    .from(usageRecords)
    .where(
      and(eq(usageRecords.userId, userId), gte(usageRecords.createdAt, since)),
    );

  const messagesUsedToday = Number(row?.messages ?? 0);
  const messagesPerDay =
    sub && sub.tier && sub.status === "active"
      ? dailyMessageBudgetFor(TIER_BY_NAME[sub.tier])
      : FREE_TIER_DAILY_MESSAGES;

  return {
    form: "messages-left",
    messagesUsedToday,
    messagesPerDay,
  };
}
