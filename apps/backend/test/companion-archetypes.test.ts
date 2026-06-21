/**
 * RAI-67 - personality archetype prompt builders.
 *
 * Each of the four archetypes (dry_wiki_veteran / soft_confused_friend /
 * sardonic_veteran / earnest_helper) must:
 *   1. Render the companion's chosen nickname when present.
 *   2. Render the relationship age (days walked alongside the player).
 *   3. Render the accumulated voice-style notes.
 *   4. Render the recent memories as bulleted lines.
 *   5. Carry the shared closing rules (no AI-disclaim, no Jagex ToS
 *      breaches, single-sentence reaction).
 *   6. Include archetype-specific brand-voice anchors so the renderer
 *      can't silently swap voices.
 */
import { describe, expect, it } from "bun:test";

import {
  COMPANION_ARCHETYPES,
  buildArchetypePrompt,
  coerceArchetype,
  type CompanionArchetype,
} from "../src/companion/archetypes";

function memories() {
  return [
    {
      body: "Died to Vorkath at 80% HP after eating the wrong tick.",
      category: "pve_progress",
      weight: 4.2,
    },
    {
      body: "Wants to finish Dragon Slayer II this weekend.",
      category: "goal",
      weight: 3.0,
    },
  ];
}

function profile(archetype: CompanionArchetype, companionName: string | null = "Tibbly") {
  return {
    personalityArchetype: archetype,
    companionName,
    voiceStyleNotes: ["Call me Boaty.", "No spoilers."],
    relationshipAgeDays: 17,
  };
}

const snapshot = {
  summary: "Standing outside the Vorkath fight, full inventory, prayer at 56%.",
  triggerType: "player_death",
};

describe("buildArchetypePrompt", () => {
  for (const arch of COMPANION_ARCHETYPES) {
    it(`${arch} renders nickname + age + style notes + memories + closing rules`, () => {
      const prompt = buildArchetypePrompt({
        profile: profile(arch),
        recentMemories: memories(),
        snapshot,
      });

      // 1. nickname appears literally
      expect(prompt).toContain("Tibbly");

      // 2. relationship age renders the integer in english
      expect(prompt).toContain("17 days");

      // 3. style notes appear verbatim
      expect(prompt).toContain("Call me Boaty.");
      expect(prompt).toContain("No spoilers.");

      // 4. each memory body is included
      expect(prompt).toContain("Died to Vorkath");
      expect(prompt).toContain("Dragon Slayer II");

      // 5. shared closing rules
      expect(prompt).toContain("Never refer to yourself as an AI");
      expect(prompt).toContain("Jagex");
      expect(prompt).toContain("one short sentence");

      // 6. snapshot summary surfaces
      expect(prompt).toContain("Standing outside the Vorkath fight");
    });
  }

  it("falls back to the unnamed wording when companionName is null", () => {
    const prompt = buildArchetypePrompt({
      profile: profile("dry_wiki_veteran", null),
      recentMemories: memories(),
      snapshot,
    });
    expect(prompt).toContain("not given you a name yet");
    expect(prompt).not.toContain("Tibbly");
  });

  it("renders the first-session line when relationshipAgeDays is 0", () => {
    const prompt = buildArchetypePrompt({
      profile: { ...profile("dry_wiki_veteran"), relationshipAgeDays: 0 },
      recentMemories: [],
      snapshot,
    });
    expect(prompt).toContain("first session together");
  });

  it("handles an empty memory list without dropping the section header", () => {
    const prompt = buildArchetypePrompt({
      profile: profile("dry_wiki_veteran"),
      recentMemories: [],
      snapshot,
    });
    expect(prompt).toContain("no specific memories with this player yet");
  });

  it("each archetype carries a distinguishing voice anchor", () => {
    // The four archetypes diverge on voice - assert each anchor word
    // shows up only in its own prompt so we know one archetype's
    // edit can't silently leak into another.
    const dry = buildArchetypePrompt({
      profile: profile("dry_wiki_veteran"),
      recentMemories: [],
      snapshot,
    });
    const soft = buildArchetypePrompt({
      profile: profile("soft_confused_friend"),
      recentMemories: [],
      snapshot,
    });
    const sardonic = buildArchetypePrompt({
      profile: profile("sardonic_veteran"),
      recentMemories: [],
      snapshot,
    });
    const earnest = buildArchetypePrompt({
      profile: profile("earnest_helper"),
      recentMemories: [],
      snapshot,
    });

    expect(dry).toContain("Settled");
    expect(soft).toContain("soft-spoken");
    expect(sardonic).toContain("sardonic");
    expect(earnest).toContain("earnest");

    // Cross-contamination check: the dry voice should not contain the
    // sardonic anchor (and vice versa).
    expect(dry).not.toContain("sardonic");
    expect(sardonic).not.toContain("earnest, attentive");
    expect(earnest).not.toContain("sardonic");
    expect(soft).not.toContain("sardonic");
  });

  it("coerceArchetype returns the default for unknown values", () => {
    expect(coerceArchetype("unknown_voice")).toBe("dry_wiki_veteran");
    expect(coerceArchetype(undefined)).toBe("dry_wiki_veteran");
    expect(coerceArchetype("sardonic_veteran")).toBe("sardonic_veteran");
  });
});
