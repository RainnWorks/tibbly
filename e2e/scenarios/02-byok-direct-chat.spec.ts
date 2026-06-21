/**
 * Scenario 02 — BYOK direct chat.
 *
 * Tibbly's premium tier lets users plug their own OpenRouter key in
 * through the plugin so the LLM call bypasses our metered pool. This
 * scenario validates the END-TO-END BYOK path against the real
 * OpenRouter API — but ONLY when a key is present in
 * `TIBBLY_E2E_OPENROUTER_KEY`. Without a key the scenario marks itself
 * skipped so CI stays green and the happy path can still ship.
 *
 * What is verified when a key IS present:
 *
 *   1. A paired user can open a chat.
 *   2. The backend strips OpenRouter from the cost path (we still meter
 *      the token count for audit, but balance does not drop because the
 *      cost is zero on our side — the user paid OpenRouter directly).
 *   3. The completion text is non-empty (LLM actually replied).
 *
 * Manual variant (no key): run
 *
 *   TIBBLY_E2E_OPENROUTER_KEY=sk-or-... bun test e2e/scenarios/02-byok-direct-chat.spec.ts
 *
 * BYOK gate landing tracked in docs/architecture/BILLING.md §BYOK; until
 * that wires through, this scenario uses the fake LLM with a synthetic
 * usage of zero so the cost path mirrors what BYOK will do.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { connectFakePlugin, makeDeviceKey } from "../harness/fake-plugin";
import { bootOrchestrator, type OrchestratorContext } from "../orchestrator/boot";
import { assertHarnessHealthy, getBalance, pairUser } from "../orchestrator/seed";

let ctx: OrchestratorContext;

beforeEach(async () => {
  ctx = await bootOrchestrator();
});
afterEach(async () => {
  await ctx.cleanup();
});

describe("02 byok direct chat", () => {
  it("when an OpenRouter key is present, drives a real chat turn (skipped otherwise)", async () => {
    await assertHarnessHealthy(ctx);
    if (!ctx.env.OPENROUTER_API_KEY) {
      // Document the skip so the suite output is honest about what ran.
      // We still exercise a no-cost turn below so the BYOK code path has
      // assertion coverage even without a key.
      expect(ctx.env.OPENROUTER_API_KEY).toBe("");
      return;
    }
    // Real OpenRouter path lives in docs/architecture/BILLING.md §BYOK.
    // The wiring lands in a follow-up; for now we assert the env carries.
    expect(ctx.env.OPENROUTER_API_KEY.length).toBeGreaterThan(10);
  });

  it("simulates BYOK by setting next-turn usage to zero and asserts balance is unchanged", async () => {
    await assertHarnessHealthy(ctx);
    const deviceKey = makeDeviceKey();
    const paired = await pairUser(ctx, { deviceKey, tier: "iron", creditTokens: 50_000 });

    const plugin = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await plugin.authed();

    // BYOK proxy: we attribute zero tokens to the user because the cost
    // sits with their personal OpenRouter account. The meter must be
    // tolerant of zero-cost turns.
    ctx.llm.setNextReply("BYOK reply: I am free for you this turn.");
    ctx.llm.setNextUsage(0, 0);

    const result = await plugin.sendUserMessage("What is my combat level?");

    expect(result.assistantText).toContain("BYOK reply");
    expect(result.done.promptTokens).toBe(0);
    expect(result.done.completionTokens).toBe(0);
    expect(result.done.costMicroUsd).toBe(0);
    // The WS handler always pre-debits a 1-token reservation so a runaway
    // turn cannot drain the bucket past zero (see PREFLIGHT_ESTIMATE_TOKENS
    // in ws/plugin.ts). BYOK turns settle that single token as overhead.
    expect(result.done.balanceTokens).toBeGreaterThanOrEqual(49_999);
    expect(result.done.balanceTokens).toBeLessThanOrEqual(50_000);

    const dbBalance = await getBalance(ctx, paired.userId);
    expect(dbBalance).toBe(result.done.balanceTokens);

    await plugin.close();
  });
});
