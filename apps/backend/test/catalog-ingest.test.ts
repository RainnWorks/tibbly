/**
 * Contract for `llm/catalog/ingest.ts`.
 *
 * Exercises:
 *   - Happy path: rows land in the table with correct price conversion.
 *   - Add + retire round-trip across two runs.
 *   - A retirement clears when an id re-emerges in a later run.
 *   - Malformed response shape returns zeros and mutates nothing.
 *   - Fetch timeout is treated as failure; mutates nothing.
 *   - Refresh against an empty DB seeds rows from scratch.
 *   - Price-conversion math is exact for the documented decimal inputs.
 *   - Provider derivation from the id.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";

import { modelCatalog } from "../src/db/schema";
import {
  priceStringToMicroUsdPerMillion,
  providerOf,
  refreshModelCatalog,
} from "../src/llm/catalog/ingest";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
});
afterEach(async () => {
  await handle.close();
});

function mockOkFetch(body: unknown): typeof fetch {
  return (async () =>
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    })) as unknown as typeof fetch;
}

const SAMPLE = {
  data: [
    {
      id: "anthropic/claude-haiku-4.5",
      name: "Claude Haiku 4.5",
      context_length: 200_000,
      pricing: { prompt: "0.000001", completion: "0.000005" },
      architecture: { input_modalities: ["text"], tokenizer: "claude" },
    },
    {
      id: "anthropic/claude-sonnet-4.6",
      name: "Claude Sonnet 4.6",
      context_length: 200_000,
      pricing: { prompt: "0.000003", completion: "0.000015" },
      architecture: { input_modalities: ["text", "image"], tokenizer: "claude" },
    },
    {
      id: "openai/gpt-5",
      name: "GPT-5",
      context_length: 128_000,
      pricing: { prompt: "0.000010", completion: "0.000030" },
      architecture: { input_modalities: ["text"], tokenizer: "tiktoken" },
    },
  ],
};

describe("priceStringToMicroUsdPerMillion", () => {
  it("converts the documented OpenRouter decimals exactly", () => {
    expect(priceStringToMicroUsdPerMillion("0.000001")).toBe(1_000_000);
    expect(priceStringToMicroUsdPerMillion("0.000003")).toBe(3_000_000);
    expect(priceStringToMicroUsdPerMillion("0.000015")).toBe(15_000_000);
    expect(priceStringToMicroUsdPerMillion("0.00001")).toBe(10_000_000);
    // 5e-8 USD/token = 5e-8 * 1e12 µUSD/M = 50_000.
    expect(priceStringToMicroUsdPerMillion("0.00000005")).toBe(50_000);
  });
  it("rejects malformed / out-of-range / negative input", () => {
    expect(priceStringToMicroUsdPerMillion("not-a-number")).toBe(0);
    expect(priceStringToMicroUsdPerMillion("")).toBe(0);
    expect(priceStringToMicroUsdPerMillion(undefined)).toBe(0);
    expect(priceStringToMicroUsdPerMillion("-0.000003")).toBe(0);
    expect(priceStringToMicroUsdPerMillion("1")).toBe(0);
    expect(priceStringToMicroUsdPerMillion("1e-6")).toBe(0);
  });
});

describe("providerOf", () => {
  it("returns the segment before the slash", () => {
    expect(providerOf("anthropic/claude-haiku-4.5")).toBe("anthropic");
    expect(providerOf("openai/gpt-5")).toBe("openai");
    expect(providerOf("no-slash")).toBe("no-slash");
  });
});

describe("refreshModelCatalog — happy path", () => {
  it("seeds rows with correctly converted prices", async () => {
    const result = await refreshModelCatalog(handle.db, {
      fetchImpl: mockOkFetch(SAMPLE),
    });
    expect(result).toEqual({ added: 3, updated: 0, retired: 0 });

    const rows = await handle.db.select().from(modelCatalog);
    expect(rows).toHaveLength(3);
    const sonnet = rows.find((r) => r.id === "anthropic/claude-sonnet-4.6");
    expect(sonnet).toBeDefined();
    expect(sonnet!.inputPriceMicroUsdPerMillion).toBe(3_000_000);
    expect(sonnet!.outputPriceMicroUsdPerMillion).toBe(15_000_000);
    expect(sonnet!.provider).toBe("anthropic");
    expect(sonnet!.contextLength).toBe(200_000);
    expect(sonnet!.inputModalities).toEqual(["text", "image"]);
    expect(sonnet!.retiredAt).toBeNull();
  });
});

describe("refreshModelCatalog — retire then re-emerge", () => {
  it("retires absent rows, then clears retirement when they return", async () => {
    await refreshModelCatalog(handle.db, { fetchImpl: mockOkFetch(SAMPLE) });

    // Second run: drop the openai row.
    const withoutOpenai = { data: SAMPLE.data.filter((r) => r.id !== "openai/gpt-5") };
    const second = await refreshModelCatalog(handle.db, {
      fetchImpl: mockOkFetch(withoutOpenai),
    });
    expect(second.added).toBe(0);
    expect(second.updated).toBe(2);
    expect(second.retired).toBe(1);

    const retired = (await handle.db.select().from(modelCatalog)).find(
      (r) => r.id === "openai/gpt-5",
    );
    expect(retired?.retiredAt).not.toBeNull();

    // Third run: openai returns.
    const third = await refreshModelCatalog(handle.db, {
      fetchImpl: mockOkFetch(SAMPLE),
    });
    expect(third.added).toBe(0);
    expect(third.updated).toBe(3);
    expect(third.retired).toBe(0);
    const back = (await handle.db.select().from(modelCatalog)).find(
      (r) => r.id === "openai/gpt-5",
    );
    expect(back?.retiredAt).toBeNull();
  });
});

describe("refreshModelCatalog — price change updates", () => {
  it("updates prices on an existing row when OpenRouter publishes a change", async () => {
    await refreshModelCatalog(handle.db, { fetchImpl: mockOkFetch(SAMPLE) });
    const cheaper = {
      data: SAMPLE.data.map((r) =>
        r.id === "anthropic/claude-sonnet-4.6"
          ? {
              ...r,
              pricing: { prompt: "0.000002", completion: "0.000010" },
            }
          : r,
      ),
    };
    await refreshModelCatalog(handle.db, { fetchImpl: mockOkFetch(cheaper) });
    const row = (await handle.db.select().from(modelCatalog)).find(
      (r) => r.id === "anthropic/claude-sonnet-4.6",
    );
    expect(row?.inputPriceMicroUsdPerMillion).toBe(2_000_000);
    expect(row?.outputPriceMicroUsdPerMillion).toBe(10_000_000);
  });
});

describe("refreshModelCatalog — malformed response", () => {
  it("returns zeros and leaves the table untouched on malformed JSON shape", async () => {
    await refreshModelCatalog(handle.db, { fetchImpl: mockOkFetch(SAMPLE) });
    const before = await handle.db.select().from(modelCatalog);

    const bad = mockOkFetch({ unexpected: "shape" });
    const res = await refreshModelCatalog(handle.db, { fetchImpl: bad });
    expect(res).toEqual({ added: 0, updated: 0, retired: 0 });

    const after = await handle.db.select().from(modelCatalog);
    expect(after.length).toBe(before.length);
    // No retirements either.
    expect(after.every((r) => r.retiredAt === null)).toBe(true);
  });
});

describe("refreshModelCatalog — fetch timeout / error", () => {
  it("returns zeros when fetch throws", async () => {
    await refreshModelCatalog(handle.db, { fetchImpl: mockOkFetch(SAMPLE) });
    const before = await handle.db.select().from(modelCatalog);
    const throwingFetch: typeof fetch = (async () => {
      throw new Error("ETIMEDOUT");
    }) as unknown as typeof fetch;
    const res = await refreshModelCatalog(handle.db, { fetchImpl: throwingFetch });
    expect(res).toEqual({ added: 0, updated: 0, retired: 0 });
    const after = await handle.db.select().from(modelCatalog);
    expect(after.length).toBe(before.length);
    expect(after.every((r) => r.retiredAt === null)).toBe(true);
  });
});

describe("refreshModelCatalog — empty DB seed", () => {
  it("seeds correctly from an empty starting state", async () => {
    const empty = await handle.db.select().from(modelCatalog);
    expect(empty).toHaveLength(0);

    const res = await refreshModelCatalog(handle.db, {
      fetchImpl: mockOkFetch({ data: [SAMPLE.data[0]] }),
    });
    expect(res).toEqual({ added: 1, updated: 0, retired: 0 });
    const after = await handle.db.select().from(modelCatalog);
    expect(after).toHaveLength(1);
    expect(after[0]?.id).toBe("anthropic/claude-haiku-4.5");
  });
});
