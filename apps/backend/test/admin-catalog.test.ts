/**
 * Contract for /admin/catalog/* routes (RAI-39 auth migration).
 *
 * Auth gate (ops_session JWT cookie), list with filters, diff window,
 * manual refresh.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { createApp } from "../src/app";
import { modelCatalog } from "../src/db/schema";
import { OPS_SESSION_SECRET, opsSessionCookieHeader } from "./_auth-fixture";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
const ADMIN = "tom@rowm.co";

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function buildApp(extra?: {
  refresh?: () => Promise<{ added: number; updated: number; retired: number }>;
}) {
  return createApp({
    adminCatalog: {
      db: handle.db,
      adminEmails: [ADMIN],
      jwtSecret: OPS_SESSION_SECRET,
      ...(extra?.refresh ? { refresh: async () => extra.refresh!() } : {}),
    },
  });
}

async function seedSample(): Promise<void> {
  const now = new Date();
  const oldDate = new Date(now.getTime() - 10 * 86_400_000);
  await handle.db.insert(modelCatalog).values([
    {
      id: "anthropic/claude-sonnet-4.6",
      provider: "anthropic",
      displayName: "Claude Sonnet 4.6",
      contextLength: 200_000,
      inputPriceMicroUsdPerMillion: 3_000_000,
      outputPriceMicroUsdPerMillion: 15_000_000,
      inputModalities: ["text"],
      capabilities: {},
      firstSeenAt: oldDate,
      lastSeenAt: now,
      retiredAt: null,
    },
    {
      id: "openai/gpt-5",
      provider: "openai",
      displayName: "GPT-5",
      contextLength: 128_000,
      inputPriceMicroUsdPerMillion: 10_000_000,
      outputPriceMicroUsdPerMillion: 30_000_000,
      inputModalities: ["text"],
      capabilities: {},
      firstSeenAt: oldDate,
      lastSeenAt: now,
      retiredAt: null,
    },
    {
      id: "anthropic/claude-haiku-old",
      provider: "anthropic",
      displayName: "Claude Haiku (retired)",
      contextLength: 100_000,
      inputPriceMicroUsdPerMillion: 250_000,
      outputPriceMicroUsdPerMillion: 1_250_000,
      inputModalities: ["text"],
      capabilities: {},
      firstSeenAt: oldDate,
      lastSeenAt: oldDate,
      retiredAt: now,
    },
  ]);
}

describe("/admin/catalog auth gate", () => {
  it("401s without any auth", async () => {
    const res = await buildApp().fetch(
      new Request("http://localhost/admin/catalog/models"),
    );
    expect(res.status).toBe(401);
  });

  it("401s when the OLD x-admin-email header is sent", async () => {
    const res = await buildApp().fetch(
      new Request("http://localhost/admin/catalog/models", {
        headers: { "x-admin-email": ADMIN },
      }),
    );
    expect(res.status).toBe(401);
  });
});

describe("GET /admin/catalog/models", () => {
  it("lists all models when no filter applied", async () => {
    await seedSample();
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await buildApp().fetch(
      new Request("http://localhost/admin/catalog/models", { headers: cookie }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      ok: true;
      count: number;
      models: Array<{ id: string; provider: string; retiredAt: string | null }>;
    };
    expect(body.count).toBe(3);
    expect(body.models.find((m) => m.id === "openai/gpt-5")).toBeDefined();
  });

  it("filters by provider", async () => {
    await seedSample();
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await buildApp().fetch(
      new Request("http://localhost/admin/catalog/models?provider=openai", { headers: cookie }),
    );
    const body = (await res.json()) as {
      ok: true;
      count: number;
      models: Array<{ id: string; provider: string }>;
    };
    expect(body.count).toBe(1);
    expect(body.models[0]?.id).toBe("openai/gpt-5");
  });

  it("hides retired when retired=false", async () => {
    await seedSample();
    const cookie = await opsSessionCookieHeader(ADMIN);
    const res = await buildApp().fetch(
      new Request("http://localhost/admin/catalog/models?retired=false", { headers: cookie }),
    );
    const body = (await res.json()) as {
      ok: true;
      count: number;
      models: Array<{ id: string; retiredAt: string | null }>;
    };
    expect(body.count).toBe(2);
    expect(body.models.every((m) => m.retiredAt === null)).toBe(true);
  });
});

describe("GET /admin/catalog/diff", () => {
  it("groups added / retired / price-changed rows by window", async () => {
    await seedSample();
    const cookie = await opsSessionCookieHeader(ADMIN);
    const since = new Date(Date.now() - 86_400_000).toISOString();
    const res = await buildApp().fetch(
      new Request(
        `http://localhost/admin/catalog/diff?since=${encodeURIComponent(since)}`,
        { headers: cookie },
      ),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      ok: true;
      added: Array<{ id: string }>;
      retired: Array<{ id: string }>;
      priceChanges: Array<{ id: string }>;
    };
    expect(body.added).toHaveLength(0);
    expect(body.retired.map((r) => r.id)).toContain("anthropic/claude-haiku-old");
    expect(body.priceChanges.length).toBeGreaterThanOrEqual(2);
  });
});

describe("POST /admin/catalog/refresh", () => {
  it("invokes the injected refresh function and returns counts", async () => {
    const cookie = await opsSessionCookieHeader(ADMIN);
    const app = buildApp({
      refresh: async () => ({ added: 7, updated: 12, retired: 3 }),
    });
    const res = await app.fetch(
      new Request("http://localhost/admin/catalog/refresh", {
        method: "POST",
        headers: cookie,
      }),
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      added: number;
      updated: number;
      retired: number;
    };
    expect(body.added).toBe(7);
    expect(body.updated).toBe(12);
    expect(body.retired).toBe(3);
  });
});
