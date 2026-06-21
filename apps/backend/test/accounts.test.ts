/**
 * RAI-27 — /v1/accounts contract.
 *
 * Confirms:
 *   - 401 without a user header.
 *   - GET only returns the caller's rows (ownership filter).
 *   - DELETE 404s when the id belongs to another user (no info leak).
 *   - DELETE 200 + row gone when the id is owned by the caller.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import { createApp } from "../src/app";
import { osrsAccounts, users } from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

const USER_A = "user_accts_aaaaaaaaaa";
const USER_B = "user_accts_bbbbbbbbbb";

async function seed(): Promise<{ a1: string; a2: string; b1: string }> {
  await handle.db.insert(users).values([
    { id: USER_A, email: "a@example.com" },
    { id: USER_B, email: "b@example.com" },
  ]);
  await handle.db.insert(osrsAccounts).values([
    { id: "acct_A1_xxxxxxxxxxxxx", userId: USER_A, displayName: "Zezima", accountType: "main" },
    { id: "acct_A2_xxxxxxxxxxxxx", userId: USER_A, displayName: "B0aty", accountType: "ironman" },
    { id: "acct_B1_xxxxxxxxxxxxx", userId: USER_B, displayName: "Lynx Titan", accountType: "main" },
  ]);
  return { a1: "acct_A1_xxxxxxxxxxxxx", a2: "acct_A2_xxxxxxxxxxxxx", b1: "acct_B1_xxxxxxxxxxxxx" };
}

describe("/v1/accounts", () => {
  it("401s without x-user-id", async () => {
    const app = createApp({ accounts: { db: handle.db } });
    const res = await app.fetch(new Request("http://localhost/v1/accounts"));
    expect(res.status).toBe(401);
  });

  it("returns only the caller's accounts", async () => {
    await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request("http://localhost/v1/accounts", {
        headers: { "x-user-id": USER_A },
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      accounts: Array<{ id: string; displayName: string }>;
    };
    expect(body.accounts).toHaveLength(2);
    expect(body.accounts.map((a) => a.displayName).sort()).toEqual([
      "B0aty",
      "Zezima",
    ]);
  });

  it("DELETE 404s when the account belongs to a different user", async () => {
    const { b1 } = await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request(`http://localhost/v1/accounts/${b1}`, {
        method: "DELETE",
        headers: { "x-user-id": USER_A },
      }),
    );
    expect(res.status).toBe(404);

    // B's row still exists.
    const stillThere = await handle.db
      .select()
      .from(osrsAccounts)
      .where(eq(osrsAccounts.id, b1));
    expect(stillThere).toHaveLength(1);
  });

  it("DELETE removes the caller's own row", async () => {
    const { a1 } = await seed();
    const app = createApp({ accounts: { db: handle.db } });

    const res = await app.fetch(
      new Request(`http://localhost/v1/accounts/${a1}`, {
        method: "DELETE",
        headers: { "x-user-id": USER_A },
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
