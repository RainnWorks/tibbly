/**
 * RAI-27 — /v1/accounts contract (RAI-39 auth migration).
 *
 * Confirms:
 *   - 401 without an Authorization Bearer device key.
 *   - 401 with the OLD `x-user-id` header (audit C1 attack is closed).
 *   - GET only returns the caller's rows (ownership filter).
 *   - DELETE 404s when the id belongs to another user (no info leak).
 *   - DELETE 200 + row gone when the id is owned by the caller.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import { osrsAccounts } from "../src/db/schema";
import { bearerHeaders, seedDevice, type SeededDevice } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

async function seed(): Promise<{
  a: SeededDevice;
  b: SeededDevice;
  a1: string;
  a2: string;
  b1: string;
}> {
  const a = await seedDevice(handle, { email: "a@example.com" });
  const b = await seedDevice(handle, { email: "b@example.com" });
  await handle.db.insert(osrsAccounts).values([
    { id: "acct_A1_xxxxxxxxxxxxx", userId: a.userId, displayName: "Zezima", accountType: "main" },
    { id: "acct_A2_xxxxxxxxxxxxx", userId: a.userId, displayName: "B0aty", accountType: "ironman" },
    { id: "acct_B1_xxxxxxxxxxxxx", userId: b.userId, displayName: "Lynx Titan", accountType: "main" },
  ]);
  return {
    a,
    b,
    a1: "acct_A1_xxxxxxxxxxxxx",
    a2: "acct_A2_xxxxxxxxxxxxx",
    b1: "acct_B1_xxxxxxxxxxxxx",
  };
}

describe("/v1/accounts", () => {
  it("401s without any auth header", async () => {
    const app = createApp({ accounts: { db: handle.db } });
    const res = await app.fetch(new Request("http://localhost/v1/accounts"));
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-user-id header is sent (audit C1 attack closed)", async () => {
    const { a } = await seed();
    const app = createApp({ accounts: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/accounts", {
        headers: { "x-user-id": a.userId },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the Bearer token is a raw user id (audit C1 second variant closed)", async () => {
    const { a } = await seed();
    const app = createApp({ accounts: { db: handle.db } });
    const res = await app.fetch(
      new Request("http://localhost/v1/accounts", {
        headers: { authorization: `Bearer ${a.userId}` },
      }),
    );
    expect(res.status).toBe(401);
  });

  it("returns only the caller's accounts with a valid device key", async () => {
    const { a } = await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request("http://localhost/v1/accounts", {
        headers: bearerHeaders(a.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      accounts: Array<{ id: string; displayName: string }>;
    };
    expect(body.accounts).toHaveLength(2);
    expect(body.accounts.map((acct) => acct.displayName).sort()).toEqual([
      "B0aty",
      "Zezima",
    ]);
  });

  it("DELETE 404s when the account belongs to a different user", async () => {
    const { a, b1 } = await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request(`http://localhost/v1/accounts/${b1}`, {
        method: "DELETE",
        headers: bearerHeaders(a.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(404);

    const stillThere = await handle.db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.id, b1));
    expect(stillThere).toHaveLength(1);
  });

  it("DELETE removes the caller's own row", async () => {
    const { a, a1 } = await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request(`http://localhost/v1/accounts/${a1}`, {
        method: "DELETE",
        headers: bearerHeaders(a.rawDeviceKey),
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean; id: string };
    expect(body.ok).toBe(true);
    expect(body.id).toBe(a1);

    const gone = await handle.db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.id, a1));
    expect(gone).toHaveLength(0);
  });
});
