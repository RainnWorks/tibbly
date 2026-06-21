/**
 * Scenario 01 — pair, chat, bill.
 *
 * The headline scenario. Covers:
 *   1. The pairing flow (request a code, claim it as a fresh user).
 *   2. Auth handshake on the WS using the freshly-paired device key.
 *   3. A real chat turn including a tool round-trip that ships the bank-tab
 *      fixture back to the backend.
 *   4. Token balance decrement + audit event row.
 *
 * Nothing is mocked except the OpenRouter call and the Stripe stub. The
 * pairing routes, WS handler, billing meter, event bus, and presence
 * tracker all run their real code paths against an in-memory PGLite.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { events as eventsTable } from "../../apps/backend/src/db/schema";
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

describe("01 pair chat bill", () => {
  it("pairs a user, drives a chat turn through the WS, decrements balance, writes events", async () => {
    await assertHarnessHealthy(ctx);
    const deviceKey = makeDeviceKey();
    const paired = await pairUser(ctx, {
      deviceKey,
      playerName: "Tibbly Test",
      email: "player@example.test",
      tier: "pro",
      creditTokens: 100_000,
    });

    expect(paired.userId).toMatch(/^.{8,}$/);
    expect(paired.initialBalanceTokens).toBe(100_000);

    const plugin = await connectFakePlugin({
      wsUrl: ctx.wsUrl,
      deviceKey,
      playerName: "Tibbly Test",
    });

    const authOk = await plugin.authed();
    expect(authOk.userId).toBe(paired.userId);
    expect(authOk.tier).toBe("pro");
    expect(authOk.balanceTokens).toBe(100_000);

    ctx.llm.setNextReply("Tab three holds Coal, Iron, and Gold ore.");
    const result = await plugin.sendUserMessage(
      "What is in my bank tab 3?",
      { snapshot: { hp: 91 } },
    );

    expect(result.assistantText).toContain("Coal");
    expect(result.toolCalls.length).toBeGreaterThan(0);
    const bankCall = result.toolCalls.find((c) => c.name === "bank_tab");
    expect(bankCall).toBeDefined();
    expect((bankCall?.output as { items: ReadonlyArray<{ name: string }> }).items[0]?.name).toBe(
      "Coal",
    );

    // Balance dropped by exactly promptTokens + completionTokens.
    expect(result.done.balanceTokens).toBeLessThan(100_000);
    expect(result.done.balanceTokens).toBe(
      100_000 - result.done.promptTokens - result.done.completionTokens,
    );

    const dbBalance = await getBalance(ctx, paired.userId);
    expect(dbBalance).toBe(result.done.balanceTokens);

    // Audit events landed via the event bus.
    const eventRows = await ctx.db
      .select()
      .from(eventsTable)
      .where(eq(eventsTable.userId, paired.userId));
    const types = new Set(eventRows.map((r) => r.type));
    expect(types.has("chat.message.sent")).toBe(true);

    // Presence ticked up while the plugin was connected.
    expect(ctx.presence.snapshot().count).toBeGreaterThanOrEqual(1);

    await plugin.close();
  });

  it("rejects an unknown device with auth_error", async () => {
    await assertHarnessHealthy(ctx);
    const plugin = await connectFakePlugin({
      wsUrl: ctx.wsUrl,
      deviceKey: makeDeviceKey("DEVKEY_unknown_"),
    });
    await expect(plugin.authed()).rejects.toThrow(/unknown_device/);
    await plugin.closed();
  });

  it("rejects a paired device with zero balance as balance_exhausted", async () => {
    await assertHarnessHealthy(ctx);
    const deviceKey = makeDeviceKey();
    await pairUser(ctx, { deviceKey, tier: "hobbyist", creditTokens: 0 });
    const plugin = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await expect(plugin.authed()).rejects.toThrow(/balance_exhausted/);
    await plugin.closed();
  });
});
