/**
 * Scenario 05 — public presence counter.
 *
 * The marketing site renders a live "X players online" counter against
 * /v1/presence. When a real plugin connects, the counter ticks up; when
 * it disconnects, the counter ticks down after the grace window.
 *
 * The orchestrator boots the presence tracker with a 50ms grace so the
 * test does not have to wait the production 30s window.
 *
 * Coverage:
 *   1. Baseline /v1/presence is 0.
 *   2. After a fake-plugin auths, /v1/presence is 1.
 *   3. After it closes + the grace window elapses, /v1/presence is 0.
 *   4. Two concurrent plugins are correctly counted.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { connectFakePlugin, makeDeviceKey } from "../harness/fake-plugin";
import { bootOrchestrator, type OrchestratorContext } from "../orchestrator/boot";
import { pairUser } from "../orchestrator/seed";

let ctx: OrchestratorContext;

beforeEach(async () => {
  ctx = await bootOrchestrator();
});
afterEach(async () => {
  await ctx.cleanup();
});

async function presenceCount(ctx: OrchestratorContext): Promise<number> {
  const res = await fetch(`${ctx.backendUrl}/v1/presence`);
  expect(res.status).toBe(200);
  const body = (await res.json()) as { count: number };
  return body.count;
}

async function waitForCount(
  ctx: OrchestratorContext,
  target: number,
  timeoutMs = 1000,
): Promise<number> {
  const deadline = Date.now() + timeoutMs;
  let last = -1;
  while (Date.now() < deadline) {
    last = await presenceCount(ctx);
    if (last === target) return last;
    await new Promise((r) => setTimeout(r, 25));
  }
  return last;
}

describe("05 presence counter", () => {
  it("starts at zero, increments when a plugin connects, and clears after grace", async () => {
    expect(await presenceCount(ctx)).toBe(0);

    const deviceKey = makeDeviceKey();
    await pairUser(ctx, { deviceKey });

    const plugin = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await plugin.authed();

    expect(await waitForCount(ctx, 1)).toBe(1);

    await plugin.close();
    expect(await waitForCount(ctx, 0)).toBe(0);
  });

  it("counts two concurrent plugins as two and recovers cleanly", async () => {
    const a = makeDeviceKey();
    const b = makeDeviceKey();
    await pairUser(ctx, { deviceKey: a });
    await pairUser(ctx, { deviceKey: b });

    const pa = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey: a });
    await pa.authed();
    const pb = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey: b });
    await pb.authed();

    expect(await waitForCount(ctx, 2)).toBe(2);

    await pa.close();
    await pb.close();
    expect(await waitForCount(ctx, 0)).toBe(0);
  });
});
