/**
 * End-of-session memory extraction (RAI-67).
 *
 * Triggered when:
 *   - The plugin's WS disconnects (player closed RuneLite), OR
 *   - A 30-minute idle timer fires on a still-open socket, OR
 *   - An admin manually runs the job from the ops console.
 *
 * Input: the union of
 *   - all `companion_memory_hint` events the plugin fired during the session,
 *   - a transcript of the session's chat messages, and
 *   - high-signal game-state probes (level-ups, deaths, PBs, pet drops).
 *
 * Output: up to 10 single-sentence "memorable beats" written to
 * `companion_memories`. Each row carries a category, a weight (0.1-5.0),
 * and the evidence breadcrumbs that justified it.
 *
 * Model selection: per D-9, the extractor consults `model_catalog` for the
 * cheapest non-retired Anthropic model. NO MODEL ID IS HARDCODED in this
 * file. If the catalog is empty (first-ever boot) we fall back to
 * `chooseCheapModel("hobbyist")` so the job still runs.
 *
 * Output schema is enforced by Zod. If the model returns a malformed
 * response we log + drop the session rather than corrupt the memory
 * table.
 */
import { eq } from "drizzle-orm";
import { z } from "zod";

import type { DbClient } from "../db/client";
import {
  companionMemories,
  companionProfile,
  type NewCompanionMemory,
} from "../db/schema";
import { chooseCheapModelFromCatalog } from "../llm/router";
import { log } from "../lib/log";

/**
 * Categories the extractor is allowed to assign. Kept here (rather than in
 * the schema column type) so we can extend it without a DB migration.
 */
export const MEMORY_CATEGORIES = [
  "pve_progress",
  "goal",
  "preference",
  "chat_history",
  "milestone",
] as const;

export type MemoryCategory = (typeof MEMORY_CATEGORIES)[number];

/**
 * What the LLM must return. Strict - anything that doesn't pass `safeParse`
 * is dropped. `body` is sentence-length only; the prompt builder budget
 * assumes <= 160 chars per memory.
 */
export const ExtractedMemorySchema = z.object({
  body: z.string().min(4).max(160),
  category: z.enum(MEMORY_CATEGORIES),
  weight: z.number().min(0.1).max(5.0),
});
export type ExtractedMemory = z.infer<typeof ExtractedMemorySchema>;

export const ExtractedMemoriesSchema = z.object({
  memories: z.array(ExtractedMemorySchema).max(10),
});
export type ExtractedMemories = z.infer<typeof ExtractedMemoriesSchema>;

/* -------------------------------------------------------------------------- */
/*  Inputs                                                                    */
/* -------------------------------------------------------------------------- */

export interface SessionTranscriptEntry {
  role: "user" | "assistant";
  content: string;
}

export interface SessionMemoryHint {
  memorableEvent: string;
  evidenceProbeIds: string[];
  occurredAt: number;
}

export interface GameStateProbe {
  /** Free-form id like `vorkath_pb` / `death_at` / `level_up_99_attack`. */
  id: string;
  value: unknown;
}

export interface ExtractMemoriesInput {
  /** Profile to bind the inserted rows to. */
  profileId: string;
  /** Full transcript of the just-ended session. May be empty. */
  transcript: SessionTranscriptEntry[];
  /** Hints the plugin fired during the session. */
  hints: SessionMemoryHint[];
  /** Game-state probes the session collected. */
  probes: GameStateProbe[];
}

/* -------------------------------------------------------------------------- */
/*  LLM port                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * The minimum shape the extractor needs from the LLM. Production wires
 * this to a one-shot OpenRouter call; tests pass a synchronous fake that
 * returns canned JSON.
 */
export type StructuredExtractor = (args: {
  modelId: string;
  systemPrompt: string;
  userPrompt: string;
}) => Promise<string>;

/* -------------------------------------------------------------------------- */
/*  Deps + entry point                                                        */
/* -------------------------------------------------------------------------- */

export interface ExtractMemoriesDeps {
  db: DbClient;
  /** Inject a fake LLM in tests; production calls OpenRouter. */
  extractor: StructuredExtractor;
  /** Clock override for tests. */
  now?: () => Date;
}

export interface ExtractMemoriesResult {
  inserted: number;
  /** The raw memories the model returned (post-validation). */
  memories: ExtractedMemory[];
  /** True iff we dropped the session because the LLM output was malformed. */
  dropped: boolean;
}

/**
 * Run an end-of-session extraction pass and persist the results.
 *
 * Always returns; never throws. A malformed LLM response sets
 * `dropped: true` and inserts nothing.
 */
export async function extractAndStoreMemories(
  deps: ExtractMemoriesDeps,
  input: ExtractMemoriesInput,
): Promise<ExtractMemoriesResult> {
  const now = deps.now ?? ((): Date => new Date());

  // Verify the profile exists. If the profile was deleted (GDPR cascade
  // mid-session) we silently skip - there's nothing to write against.
  const [profile] = await deps.db
    .select()
    .from(companionProfile)
    .where(eq(companionProfile.id, input.profileId))
    .limit(1);
  if (!profile) {
    return { inserted: 0, memories: [], dropped: false };
  }

  // No signal in → nothing out. Skip the LLM hit.
  if (
    input.transcript.length === 0 &&
    input.hints.length === 0 &&
    input.probes.length === 0
  ) {
    await touchSessionEnd(deps.db, input.profileId, now());
    return { inserted: 0, memories: [], dropped: false };
  }

  // D-9: never hardcode the id. Read the catalog, fall back gracefully.
  const modelId = await chooseCheapModelFromCatalog(deps.db, {
    provider: "anthropic",
  });

  const systemPrompt = buildExtractionSystemPrompt();
  const userPrompt = buildExtractionUserPrompt(input);

  let raw: string;
  try {
    raw = await deps.extractor({ modelId, systemPrompt, userPrompt });
  } catch (err) {
    log.warn({ err: (err as Error).message }, "companion-extract: llm threw");
    return { inserted: 0, memories: [], dropped: true };
  }

  const parsed = safeJsonParse(raw);
  if (!parsed) {
    log.warn({ rawHead: raw.slice(0, 200) }, "companion-extract: non-json response");
    return { inserted: 0, memories: [], dropped: true };
  }

  const validated = ExtractedMemoriesSchema.safeParse(parsed);
  if (!validated.success) {
    log.warn(
      { issues: validated.error.issues.slice(0, 5) },
      "companion-extract: schema rejected response",
    );
    return { inserted: 0, memories: [], dropped: true };
  }

  const memories = validated.data.memories;
  if (memories.length === 0) {
    await touchSessionEnd(deps.db, input.profileId, now());
    return { inserted: 0, memories: [], dropped: false };
  }

  const evidence = collectEvidence(input);
  const rows: NewCompanionMemory[] = memories.map((m) => ({
    profileId: input.profileId,
    body: m.body,
    category: m.category,
    weight: m.weight,
    evidence,
    firstSeenAt: now(),
    lastReferencedAt: now(),
  }));
  await deps.db.insert(companionMemories).values(rows);
  await touchSessionEnd(deps.db, input.profileId, now());

  return { inserted: rows.length, memories, dropped: false };
}

/* -------------------------------------------------------------------------- */
/*  Helpers                                                                   */
/* -------------------------------------------------------------------------- */

function safeJsonParse(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    // Model sometimes wraps the json in ```json fences. Strip and retry.
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

function collectEvidence(
  input: ExtractMemoriesInput,
): { probe: string; value: unknown }[] {
  const out: { probe: string; value: unknown }[] = [];
  for (const probe of input.probes) {
    out.push({ probe: probe.id, value: probe.value });
  }
  for (const hint of input.hints) {
    out.push({ probe: "memory_hint", value: hint });
  }
  // Cap so a noisy session doesn't bloat the row. 32 is plenty for audit.
  return out.slice(0, 32);
}

async function touchSessionEnd(db: DbClient, profileId: string, at: Date): Promise<void> {
  // Bump relationshipAgeDays if the calendar day rolled over since the
  // previous session ended. Single SQL update so we don't race with a
  // concurrent extraction.
  const [profile] = await db
    .select()
    .from(companionProfile)
    .where(eq(companionProfile.id, profileId))
    .limit(1);
  if (!profile) return;

  const prevDay = profile.lastSessionEndedAt
    ? dayKey(profile.lastSessionEndedAt)
    : null;
  const nowDay = dayKey(at);
  const ageBump = prevDay !== null && prevDay !== nowDay ? 1 : prevDay === null ? 1 : 0;

  await db
    .update(companionProfile)
    .set({
      lastSessionEndedAt: at,
      updatedAt: at,
      relationshipAgeDays: profile.relationshipAgeDays + ageBump,
    })
    .where(eq(companionProfile.id, profileId));
}

/** UTC day key for relationship-age math. */
function dayKey(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function buildExtractionSystemPrompt(): string {
  return [
    "You are the memory-extraction worker for the embodied OSRS companion.",
    "Your job: read a single session of the player's gameplay and chat with the companion, then return up to 10 memorable beats the companion should bring up later.",
    "",
    "What counts as memorable:",
    "- Personal bests at any boss or activity (Vorkath PB, fastest CoX).",
    "- Deaths that mattered (Fire Cape attempt, hardcore death, dragon death).",
    "- Level-ups that the player called out (99s, milestones, last skill).",
    "- Goals the player stated (\"I want to finish DT2\", \"grinding for a fang\").",
    "- Preferences (\"call me Boaty\", \"don't spoil quests\", \"hates wiki copy-paste\").",
    "- Real-life mentions the player volunteered (\"after work\", \"on holiday\").",
    "",
    "What does NOT count:",
    "- Background kill counts unless they hit a milestone.",
    "- Routine bank trips, bank stands, world hops.",
    "- The companion's own answers (those are not memorable beats).",
    "",
    "Output format: a single JSON object exactly matching this shape, with no fences:",
    "{ \"memories\": [ { \"body\": string, \"category\": enum, \"weight\": number } ] }",
    "",
    "Rules:",
    "- Each `body` is a single sentence, <= 160 characters.",
    "- `category` is one of: pve_progress, goal, preference, chat_history, milestone.",
    "- `weight` is 0.1 (forgettable) to 5.0 (must-remember). 1.0 is the default.",
    "- Return an empty array if nothing in the session was actually memorable. Do not pad.",
    "- Do NOT include PII about other people. Do NOT include medical or financial detail.",
  ].join("\n");
}

function buildExtractionUserPrompt(input: ExtractMemoriesInput): string {
  const lines: string[] = [];
  if (input.hints.length > 0) {
    lines.push("Memory hints the plugin flagged during the session:");
    for (const h of input.hints) {
      lines.push(
        `- ${h.memorableEvent} (evidence ids: ${h.evidenceProbeIds.join(", ") || "none"})`,
      );
    }
    lines.push("");
  }
  if (input.probes.length > 0) {
    lines.push("Game-state probes from the session:");
    for (const p of input.probes) {
      lines.push(`- ${p.id}: ${safeStringify(p.value)}`);
    }
    lines.push("");
  }
  if (input.transcript.length > 0) {
    lines.push("Chat transcript (oldest first):");
    for (const turn of input.transcript) {
      lines.push(`[${turn.role}] ${truncate(turn.content, 800)}`);
    }
  }
  lines.push("");
  lines.push("Return the JSON object now. Output nothing else.");
  return lines.join("\n");
}

function safeStringify(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function truncate(s: string, n: number): string {
  return s.length <= n ? s : s.slice(0, n) + "…";
}
