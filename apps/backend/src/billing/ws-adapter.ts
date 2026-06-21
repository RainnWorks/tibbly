/**
 * Bridge from RAI-20's `TokenMeter` to the WS `BalanceMeter` port (RAI-17).
 *
 * Keeps the WS handler ignorant of the bus / Drizzle wiring: it just gets a
 * `BalanceMeter` shaped object that proxies into the production meter.
 *
 * Why a separate file: the `BalanceMeter` shape lives in `ws/plugin.ts` —
 * importing it from `meter.ts` would loop the modules (ws/plugin.ts already
 * imports from `events` and `db`). One thin file keeps the dependency
 * direction clean: ws → billing → db/events.
 */
import type { BalanceMeter } from "../ws/plugin";
import type { TokenMeter } from "./meter";

export function meterToBalancePort(meter: TokenMeter): BalanceMeter {
  return {
    async getBalanceTokens(userId) {
      return meter.getBalance(userId);
    },
    async ensureCanSpend({ userId, chatId, estimatedTokens }) {
      const r = await meter.ensureCanSpend(userId, estimatedTokens, { chatId });
      if (r === null) return null;
      return { balanceTokens: r.newBalance };
    },
    async applyTurnCost({ userId, promptTokens, completionTokens, preDebited, chatId }) {
      const { newBalance } = await meter.record({
        userId,
        // The WS port doesn't pass the model id today (the cost is computed
        // upstream and surfaced via `costMicroUsd`). Record under a stable
        // marker; per-call cost attribution rides on `billing.balance.decremented`
        // and the upstream `chat.message.sent` event, both of which carry the
        // real model id.
        model: "chat.turn",
        promptTokens,
        completionTokens,
        ...(preDebited !== undefined ? { preDebited } : {}),
        ...(chatId !== undefined ? { chatId } : {}),
      });
      return { balanceTokens: newBalance };
    },
  };
}
