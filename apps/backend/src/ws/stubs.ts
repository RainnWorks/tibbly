/**
 * TODO-stubs for the device lookup + balance meter (RAI-17).
 *
 * RAI-15 (Drizzle schema for users / devices) and RAI-20 (Stripe billing
 * meter) are in flight in parallel. Until they land, these stubs let the
 * server boot and accept a hard-coded developer device key so plugin agents
 * can iterate against a real WS without waiting on the DB schema.
 *
 * REPLACE WITH:
 *   - `apps/backend/src/db/lookup.ts` for device → user lookup.
 *   - `apps/backend/src/billing/meter.ts` for balance + decrement.
 *
 * Default behaviour:
 *   - Any device key prefixed `DEVKEY_dev_` resolves to a synthetic user on
 *     the `pro` tier with 100k tokens of balance. Anything else is unknown.
 *
 * Make sure the prod build either replaces these or refuses to boot — the
 * `assertNotDevStub` export lets the server entrypoint do exactly that.
 */
import { log } from "../lib/log";
import type { BalanceMeter, DeviceLookup } from "./plugin";

export const DEV_STUB_BALANCE = 100_000;

export const devStubDeviceLookup: DeviceLookup = {
  async resolveDeviceKey(deviceKey) {
    if (!deviceKey.startsWith("DEVKEY_dev_")) return null;
    return {
      userId: `user_dev_${deviceKey.slice(11, 19)}`,
      tier: "pro",
      deviceKey,
      playerName: null,
    };
  },
};

const inMemoryBalance = new Map<string, number>();

export const devStubBalanceMeter: BalanceMeter = {
  async getBalanceTokens(userId) {
    if (!inMemoryBalance.has(userId)) inMemoryBalance.set(userId, DEV_STUB_BALANCE);
    return inMemoryBalance.get(userId) ?? 0;
  },
  async applyTurnCost({ userId, promptTokens, completionTokens }) {
    const current = inMemoryBalance.get(userId) ?? DEV_STUB_BALANCE;
    const next = Math.max(0, current - promptTokens - completionTokens);
    inMemoryBalance.set(userId, next);
    return { balanceTokens: next };
  },
};

export function assertNotDevStub(nodeEnv: string): void {
  if (nodeEnv === "production") {
    log.error(
      "RAI-17 dev stubs (devStubDeviceLookup / devStubBalanceMeter) must not run in production",
    );
    throw new Error("dev stubs in production — refusing to boot");
  }
}
