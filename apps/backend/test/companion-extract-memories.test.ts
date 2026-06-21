/**
 * RAI-67 - end-of-session memory extraction.
 *
 * Covered:
 *   - Happy path: fake extractor returns valid JSON, rows land in DB.
 *   - Malformed-output: dropped (no rows, dropped: true).
 *   - Schema-rejection: extractor returns valid JSON but with bad weight.
 *   - Empty input: skipped without an LLM hit.
 *   - relationshipAgeDays bumps on new calendar day.
 *   - Missing profile: silently skipped.
 *   - D-9: extractor is given a non-empty modelId pulled from
 *     `model_catalog`. We seed a row and assert the id used.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import {
  companionMemories,
  companionProfile,
  modelCatalog,
  users,
} from "../src/db/schema";
import {
  ExtractedMemoriesSchema,
  extractAndStoreMemories,
  type StructuredExtractor,
} from "../src/companion/extract-memories";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;

beforeEach(async () => {
  handle = await makeTestDb();
  // Seed a model-catalog row so the chooser doesn't fall back to the
  // hardcoded default. This is the D-9 evidence: the extractor reads the
  // live catalog, never a constant.
  await handle.db.insert(modelCatalog).values({
    id: "anthropic/claude-haiku-test",
    provider: "anthropic",
    displayName: "Haiku Test",
    contextLength: 200_000,
    inputPriceMicroUsdPerMillion: 1_000_000,
    outputPriceMicroUsdPerMillion: 5_000_000,
  });
  await handle.db.insert(users).values({ id: "user_extract_test" });
});

afterEach(async () => {
  await handle.close();
});

async function seedProfile(): Promise<string> {
  const [row] = await handle.db
    .insert(companionProfile)
    .values({
      userId: "user_extract_test",
      starterArchetype: "hooded_humanoid",
      personalityArchetype: "dry_wiki_veteran",
    })
    .returning({ id: companionProfile.id });
  return row!.id;
}

describe("extractAndStoreMemories", () => {
  it("inserts validated memories from a happy-path response", async () => {
    let observedModel: string | null = null;
    const extractor: StructuredExtractor = async ({ modelId }) => {
      observedModel = modelId;
      return JSON.stringify({
        memories: [
          {
            body: "PB at Vorkath in 1:17, two ranges off the BiS.",
            category: "pve_progress",
            weight: 4.0,
          },
          {
            body: "Wants to finish DT2 this weekend.",
            category: "goal",
            weight: 2.5,
          },
        ],
      });
    };
    const profileId = await seedProfile();
    const result = await extractAndStoreMemories(
      { db: handle.db, extractor },
      {
        profileId,
        transcript: [
          { role: "user", content: "i just pb'd vorkath" },
          { role: "assistant", content: "Decent split." },
        ],
        hints: [
          { memorableEvent: "Vorkath PB", evidenceProbeIds: ["pb_vorkath"], occurredAt: 0 },
        ],
        probes: [{ id: "vorkath_pb_ticks", value: 1_870 }],
      },
    );
    expect(result.dropped).toBe(false);
    expect(result.inserted).toBe(2);
    expect(observedModel).toBe("anthropic/claude-haiku-test");
    const rows = await handle.db
      .select()
      .from(companionMemories)
      .where(eq(companionMemories.profileId, profileId));
    expect(rows).toHaveLength(2);
    expect(rows.find((r) => r.body.includes("Vorkath"))).toBeDefined();
    // Evidence should reflect both the probe and the hint.
    expect(rows[0]!.evidence.length).toBeGreaterThan(0);
  });

  it("drops malformed (non-JSON) responses without inserting", async () => {
    const extractor: StructuredExtractor = async () => "this is not json";
    const profileId = await seedProfile();
    const result = await extractAndStoreMemories(
      { db: handle.db, extractor },
      {
        profileId,
        transcript: [{ role: "user", content: "x" }],
        hints: [],
        probes: [],
      },
    );
    expect(result.dropped).toBe(true);
    expect(result.inserted).toBe(0);
  });

  it("rejects extractor responses that fail the Zod schema", async () => {
    const extractor: StructuredExtractor = async () =>
      JSON.stringify({
        memories: [
          // weight is out of the 0.1-5.0 range.
          { body: "Bad row", category: "goal", weight: 99 },
        ],
      });
    const profileId = await seedProfile();
    const result = await extractAndStoreMemories(
      { db: handle.db, extractor },
      {
        profileId,
        transcript: [{ role: "user", content: "x" }],
        hints: [],
        probes: [],
      },
    );
    expect(result.dropped).toBe(true);
    expect(result.inserted).toBe(0);
  });

  it("skips the LLM entirely when there is nothing to extract from", async () => {
    let called = false;
    const extractor: StructuredExtractor = async () => {
      called = true;
      return "{}";
    };
    const profileId = await seedProfile();
    const result = await extractAndStoreMemories(
      { db: handle.db, extractor },
      { profileId, transcript: [], hints: [], probes: [] },
    );
    expect(called).toBe(false);
    expect(result.inserted).toBe(0);
    expect(result.dropped).toBe(false);
    // Even on the skip path we still bump the session-end marker.
    const [row] = await handle.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.id, profileId));
    expect(row!.lastSessionEndedAt).not.toBeNull();
  });

  it("bumps relationshipAgeDays on a new calendar day", async () => {
    const profileId = await seedProfile();
    // Pin lastSessionEndedAt to yesterday at noon UTC.
    const yesterday = new Date(Date.UTC(2026, 5, 20, 12));
    const today = new Date(Date.UTC(2026, 5, 21, 12));
    await handle.db
      .update(companionProfile)
      .set({ lastSessionEndedAt: yesterday, relationshipAgeDays: 4 })
      .where(eq(companionProfile.id, profileId));

    const extractor: StructuredExtractor = async () =>
      JSON.stringify({
        memories: [{ body: "Played for a bit.", category: "chat_history", weight: 1 }],
      });
    await extractAndStoreMemories(
      { db: handle.db, extractor, now: () => today },
      {
        profileId,
        transcript: [{ role: "user", content: "hi" }],
        hints: [],
        probes: [],
      },
    );
    const [row] = await handle.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.id, profileId));
    expect(row!.relationshipAgeDays).toBe(5);
  });

  it("silently skips when the profile no longer exists", async () => {
    const extractor: StructuredExtractor = async () => "{}";
    const result = await extractAndStoreMemories(
      { db: handle.db, extractor },
      {
        profileId: "missing_profile",
        transcript: [{ role: "user", content: "x" }],
        hints: [],
        probes: [],
      },
    );
    expect(result.inserted).toBe(0);
    expect(result.dropped).toBe(false);
  });

  it("ExtractedMemoriesSchema accepts a valid shape and rejects an over-long body", () => {
    const good = ExtractedMemoriesSchema.safeParse({
      memories: [{ body: "Wants to finish quest.", category: "goal", weight: 1 }],
    });
    expect(good.success).toBe(true);
    const bad = ExtractedMemoriesSchema.safeParse({
      memories: [{ body: "x".repeat(200), category: "goal", weight: 1 }],
    });
    expect(bad.success).toBe(false);
  });
});
