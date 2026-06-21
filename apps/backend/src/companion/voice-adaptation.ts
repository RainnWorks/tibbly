/**
 * Nightly voice-style refinement (RAI-67).
 *
 * Reads each profile's recent interactions, asks a cheap-tier LLM to
 * compact + de-contradict the `voice_style_notes` list, and writes the
 * refined list back. The job is opportunistic: a profile with no recent
 * style notes is skipped.
 *
 * Token discipline:
 *   - One cheap-tier call per profile per night.
 *   - Returns at most `MAX_VOICE_STYLE_NOTES` (20) entries; older or
 *     contradicted ones get dropped.
 *   - Structured-output via Zod so a bad response is dropped rather than
 *     blanking a player's voice notes.
 *
 * D-9: same catalog-driven cheap-pick the extractor uses.
 */
import { eq, isNotNull } from "drizzle-orm";
import { z } from "zod";

import type { DbClient } from "../db/client";
import { companionProfile } from "../db/schema";
import { chooseCheapModelFromCatalog } from "../llm/router";
import { log } from "../lib/log";
import { MAX_VOICE_STYLE_NOTES } from "../ws/companion";
import type { StructuredExtractor } from "./extract-memories";

export const RefinedVoiceSchema = z.object({
  notes: z.array(z.string().min(1).max(200)).max(MAX_VOICE_STYLE_NOTES),
});
export type RefinedVoice = z.infer<typeof RefinedVoiceSchema>;

export interface RefineVoiceDeps {
  db: DbClient;
  extractor: StructuredExtractor;
  /** Clock override for tests. */
  now?: () => Date;
}

export interface RefineVoiceResult {
  profilesScanned: number;
  profilesRefined: number;
  totalNotesBefore: number;
  totalNotesAfter: number;
}

/**
 * Run one refinement pass across every active companion profile.
 *
 * "Active" = `lastSessionEndedAt IS NOT NULL`. New profiles with no
 * recorded session yet are skipped - the player hasn't given the
 * companion enough signal to refine against.
 */
export async function refineVoiceStyleNotes(
  deps: RefineVoiceDeps,
): Promise<RefineVoiceResult> {
  const now = (deps.now ?? ((): Date => new Date()))();
  const rows = await deps.db
    .select()
    .from(companionProfile)
    .where(isNotNull(companionProfile.lastSessionEndedAt));

  let profilesRefined = 0;
  let totalNotesBefore = 0;
  let totalNotesAfter = 0;
  let modelId: string | null = null;

  for (const profile of rows) {
    const notes = profile.voiceStyleNotes ?? [];
    totalNotesBefore += notes.length;
    if (notes.length === 0) continue;

    if (modelId === null) {
      modelId = await chooseCheapModelFromCatalog(deps.db, { provider: "anthropic" });
    }

    let raw: string;
    try {
      raw = await deps.extractor({
        modelId,
        systemPrompt: buildSystemPrompt(),
        userPrompt: buildUserPrompt(notes),
      });
    } catch (err) {
      log.warn(
        { err: (err as Error).message, profileId: profile.id },
        "companion-voice: extractor threw",
      );
      continue;
    }

    const parsed = safeParseJson(raw);
    if (!parsed) continue;
    const validated = RefinedVoiceSchema.safeParse(parsed);
    if (!validated.success) continue;

    const refined = validated.data.notes.slice(-MAX_VOICE_STYLE_NOTES);
    totalNotesAfter += refined.length;

    await deps.db
      .update(companionProfile)
      .set({ voiceStyleNotes: refined, updatedAt: now })
      .where(eq(companionProfile.id, profile.id));
    profilesRefined += 1;
  }

  return {
    profilesScanned: rows.length,
    profilesRefined,
    totalNotesBefore,
    totalNotesAfter,
  };
}

function buildSystemPrompt(): string {
  return [
    "You compact the embodied OSRS companion's voice-style notes.",
    "Input: a list of short style instructions the player has set over time.",
    "Output: a single JSON object with a `notes` array, at most 20 entries.",
    "",
    "Rules:",
    "- Merge near-duplicate instructions into one.",
    "- If a newer instruction contradicts an older one, keep the newer one and drop the older.",
    "- Preserve nicknames and preferred address forms exactly as written.",
    "- Drop instructions that are vague or self-contradictory.",
    "- Return JSON only, no fences, no commentary.",
  ].join("\n");
}

function buildUserPrompt(notes: string[]): string {
  return [
    "Voice style notes (oldest first):",
    ...notes.map((n, i) => `${i + 1}. ${n}`),
    "",
    'Return: { "notes": [string, ...] }',
  ].join("\n");
}

function safeParseJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    const stripped = raw
      .replace(/^```(?:json)?\s*/i, "")
      .replace(/```\s*$/i, "")
      .trim();
    try {
      return JSON.parse(stripped);
    } catch {
      return null;
    }
  }
}

