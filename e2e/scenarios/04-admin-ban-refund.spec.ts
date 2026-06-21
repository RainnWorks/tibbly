/**
 * Scenario 04 — admin ban + refund.
 *
 * Drives the ops-console endpoints with the production cookie-based admin
 * auth (audit C2 closed, PR #69: the legacy `x-admin-email` header path
 * no longer exists):
 *
 *   1. Pair a user, run one chat turn so a session exists.
 *   2. Admin logs in via POST /admin/login, captures the `ops_session`
 *      cookie, and uses it on every subsequent admin call.
 *   3. POST /admin/users/:id/ban with a reason. Status flips to banned,
 *      `events` row written, any open session ended.
 *   4. The banned user's device fails the next WS auth (lookup returns
 *      null because status != active).
 *   5. Admin POSTs a refund. The dev stub returns `status: "dev_stub"`
 *      so we know the fake Stripe ran but no real money moved.
 *   6. Admin unbans the user; auth succeeds again.
 *   7. A separate `it` pins the audit-C2 invariant at the e2e layer too:
 *      a request with the legacy `x-admin-email` header is rejected 401.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { events as eventsTable, users } from "../../apps/backend/src/db/schema";
import { connectFakePlugin, makeDeviceKey } from "../harness/fake-plugin";
import { bootOrchestrator, type OrchestratorContext } from "../orchestrator/boot";
import { assertHarnessHealthy, loginAsAdmin, pairUser } from "../orchestrator/seed";

let ctx: OrchestratorContext;

beforeEach(async () => {
  ctx = await bootOrchestrator();
});
afterEach(async () => {
  await ctx.cleanup();
});

describe("04 admin ban and refund", () => {
  it("bans a user, blocks their WS auth, processes a dev-stub refund, then unbans", async () => {
    await assertHarnessHealthy(ctx);

    const deviceKey = makeDeviceKey();
    const paired = await pairUser(ctx, {
      deviceKey,
      email: "naughty@example.test",
      tier: "hobbyist",
      creditTokens: 100_000,
    });
    // Attach a fake stripe customer + a charge id for the refund call.
    await ctx.db
      .update(users)
      .set({ stripeCustomerId: "cus_stub_to_refund" })
      .where(eq(users.id, paired.userId));

    // Warm-up chat turn so there is a session row to terminate on ban.
    const plugin = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await plugin.authed();
    await plugin.sendUserMessage("What is my slayer task?");
    await plugin.close();

    // Mint the admin session cookie via the real /admin/login flow.
    const { cookie } = await loginAsAdmin(ctx);
    const adminHeaders = {
      cookie,
      "content-type": "application/json",
    };

    // Ban
    const banRes = await fetch(`${ctx.backendUrl}/admin/users/${paired.userId}/ban`, {
      method: "POST",
      headers: adminHeaders,
      body: JSON.stringify({ reason: "abuse" }),
    });
    expect(banRes.status).toBe(200);
    const [bannedUser] = await ctx.db
      .select()
      .from(users)
      .where(eq(users.id, paired.userId));
    expect(bannedUser?.status).toBe("banned");

    const banEvents = await ctx.db
      .select()
      .from(eventsTable)
      .where(eq(eventsTable.type, "user.banned"));
    expect(banEvents).toHaveLength(1);
    expect((banEvents[0]!.payload as { reason: string }).reason).toBe("abuse");

    // A banned user can't auth — deviceLookup returns null because the
    // user row is not active.
    const banned = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    await expect(banned.authed()).rejects.toThrow(/unknown_device/);
    await banned.closed();

    // Refund — dev stub returns status "dev_stub".
    const refundRes = await fetch(`${ctx.backendUrl}/admin/users/${paired.userId}/refund`, {
      method: "POST",
      headers: adminHeaders,
      body: JSON.stringify({
        chargeId: "ch_stub_zero_one",
        amountCents: 700,
        reason: "duplicate",
      }),
    });
    expect(refundRes.status).toBe(200);
    const refundBody = (await refundRes.json()) as {
      ok: boolean;
      refund: { status: string; isDevStub: boolean };
    };
    expect(refundBody.ok).toBe(true);
    expect(refundBody.refund.status).toBe("dev_stub");
    expect(refundBody.refund.isDevStub).toBe(true);
    expect(ctx.stripeCalls.refunds).toHaveLength(1);
    expect(ctx.stripeCalls.refunds[0]?.charge).toBe("ch_stub_zero_one");

    // Unban
    const unbanRes = await fetch(`${ctx.backendUrl}/admin/users/${paired.userId}/unban`, {
      method: "POST",
      headers: adminHeaders,
      body: "{}",
    });
    expect(unbanRes.status).toBe(200);
    const recovered = await connectFakePlugin({ wsUrl: ctx.wsUrl, deviceKey });
    const ok = await recovered.authed();
    expect(ok.userId).toBe(paired.userId);
    await recovered.close();
  });

  it("refuses ops endpoints without an admin session cookie", async () => {
    await assertHarnessHealthy(ctx);

    const naked = await fetch(`${ctx.backendUrl}/admin/users/nope/ban`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ reason: "test" }),
    });
    expect(naked.status).toBe(401);
  });

  it("rejects the legacy x-admin-email header (audit C2 invariant)", async () => {
    await assertHarnessHealthy(ctx);

    // Even with a known-good admin email, the legacy header path must 401.
    // PR #69 made the device key + cookie session the trust root; any code
    // that re-enables `x-admin-email` would silently re-open audit C2.
    const res = await fetch(`${ctx.backendUrl}/admin/users/nope/ban`, {
      method: "POST",
      headers: {
        "x-admin-email": ctx.env.ADMIN_EMAIL,
        "content-type": "application/json",
      },
      body: JSON.stringify({ reason: "audit-c2-canary" }),
    });
    expect(res.status).toBe(401);
  });
});
