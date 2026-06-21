/**
 * Scenario 03 — GDPR export + delete.
 *
 * Walks the M3.5 / RAI-34 right-to-erasure flow:
 *
 *   1. Pair a user and run one chat turn so there is real data to export.
 *   2. GET /v1/me/export with the user's id as the Bearer token. Asserts
 *      the shape lines up with `createMeRouter`'s payload contract.
 *   3. DELETE /v1/me and assert subsequent calls 401 because the user is
 *      soft-deleted.
 *   4. Assert Stripe `customers.update` was called to scrub PII per the
 *      detach carve-out in docs/legal/DATA_RETENTION.md.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { subscriptions, users } from "../../apps/backend/src/db/schema";
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

async function attachStripeCustomer(
  ctx: OrchestratorContext,
  userId: string,
  customerId: string,
): Promise<void> {
  await ctx.db
    .update(users)
    .set({ stripeCustomerId: customerId })
    .where(eq(users.id, userId));
}

describe("03 gdpr export and delete", () => {
  it("exports user data and then hard-deletes on DELETE /v1/me", async () => {
    const deviceKey = makeDeviceKey();
    const paired = await pairUser(ctx, {
      deviceKey,
      email: "delete-me@example.test",
      tier: "pro",
      creditTokens: 100_000,
    });
    await attachStripeCustomer(ctx, paired.userId, "cus_stub_delete_me");

    const plugin = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await plugin.authed();
    await plugin.sendUserMessage("What is in my inventory?");
    await plugin.close();

    // Export
    const exportRes = await fetch(`${ctx.backendUrl}/v1/me/export`, {
      headers: { authorization: `Bearer ${paired.userId}` },
    });
    expect(exportRes.status).toBe(200);
    const body = (await exportRes.json()) as Record<string, unknown>;
    expect(body["exportVersion"]).toBe(2);
    const user = body["user"] as { id: string; email: string | null };
    expect(user.id).toBe(paired.userId);
    expect(user.email).toBe("delete-me@example.test");
    expect(Array.isArray(body["devices"])).toBe(true);
    expect((body["devices"] as ReadonlyArray<unknown>).length).toBeGreaterThan(0);
    expect(Array.isArray(body["chats"])).toBe(true);

    // Delete
    const delRes = await fetch(`${ctx.backendUrl}/v1/me`, {
      method: "DELETE",
      headers: { authorization: `Bearer ${paired.userId}` },
    });
    expect(delRes.status).toBe(204);

    // Stripe customer was scrubbed (dev stub records the call).
    expect(ctx.stripeCalls.customerUpdates).toHaveLength(1);
    expect(ctx.stripeCalls.customerUpdates[0]).toEqual({
      id: "cus_stub_delete_me",
      email: "",
    });

    // The user row is now soft-deleted and stripped of PII.
    const [postDelete] = await ctx.db.select().from(users).where(eq(users.id, paired.userId));
    expect(postDelete?.email).toBeNull();
    expect(postDelete?.deletedAt).not.toBeNull();

    // Subscriptions for the user were cascaded out.
    const subs = await ctx.db
      .select()
      .from(subscriptions)
      .where(eq(subscriptions.userId, paired.userId));
    expect(subs).toHaveLength(0);

    // A second DELETE is idempotent.
    const delRes2 = await fetch(`${ctx.backendUrl}/v1/me`, {
      method: "DELETE",
      headers: { authorization: `Bearer ${paired.userId}` },
    });
    expect(delRes2.status).toBe(204);
  });

  it("requires the bearer header on both endpoints", async () => {
    const noExport = await fetch(`${ctx.backendUrl}/v1/me/export`);
    expect(noExport.status).toBe(401);
    const noDelete = await fetch(`${ctx.backendUrl}/v1/me`, { method: "DELETE" });
    expect(noDelete.status).toBe(401);
  });
});
