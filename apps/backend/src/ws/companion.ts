/**
 * Embodied companion WS handler (RAI-67).
 *
 * Wires three new client-to-server message types into the plugin's existing
 * authenticated socket:
 *
 *   - `companion_trigger`            → run a proactive LLM line
 *   - `companion_interaction_event`  → mutate companion profile (name,
 *                                       style note, forget, click-to-chat)
 *   - `companion_memory_hint`        → queue a hint for end-of-session
 *                                       memory extraction
 *
 * The plugin still authenticates the socket through the existing flow in
 * `ws/plugin.ts`. This module is a service object that the plugin handler
 * delegates to once an authed identity is available - it doesn't open
 * its own socket.
 *
 * Token budget is enforced at the LLM level. Proactive lines run with a
 * cheap-tier model and a 60-150 output-token cap so a busy session
 * doesn't drain the user's balance with idle chatter.
 *
 * D-9 compliance: model selection goes through `chooseCheapModel(tier)`
 * with a comment pointing at the future `routing_policies` table swap.
 * The catalog-driven cheap-pick (`chooseCheapModelFromCatalog`) is used
 * by the offline jobs that have a db handle in hand; the WS hot path uses
 * the in-memory helper because we don't want to add a DB hit per line.
 */
import { and, desc, eq, isNull, sql } from "drizzle-orm";
import { nanoid } from "nanoid";

import type {
  ClientCompanionInteractionEventMsg,
  ClientCompanionMemoryHintMsg,
  ClientCompanionTriggerMsg,
  ServerCompanionAckMsg,
  ServerCompanionLineMsg,
} from "@osrs-llm-helper/shared-types";

import type { DbClient } from "../db/client";
import {
  companionMemories,
  companionProfile,
  type CompanionMemory,
  type CompanionProfile,
  type NewCompanionProfile,
} from "../db/schema";
import { computeCostMicroUsd } from "../llm/cost";
import {
  buildArchetypePrompt,
  coerceArchetype,
  type CompanionArchetype,
} from "../companion/archetypes";
import { chooseCheapModel } from "../llm/router";
import {
  getOpenRouter,
  runStream,
  type RunStreamArgs,
  type RunStreamResult,
} from "../llm/openrouter";
import type { AuthedIdentity } from "./protocol-state-machine";
import { log } from "../lib/log";

/* -------------------------------------------------------------------------- */
/*  Ports                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * Memory-hint queue port. Production wires this to an in-memory list
 * keyed by `(userId, osrsAccountId?)` that the end-of-session extractor
 * drains. Tests pass a synchronous fake.
 */
export interface MemoryHintQueue {
  enqueue(args: {
    userId: string;
    osrsAccountId: string | null;
    memorableEvent: string;
    evidenceProbeIds: string[];
    /** Best-effort timestamp for the extractor to weight recency. */
    occurredAt: number;
  }): void;
}

/**
 * Minimal balance hook for the companion path. We rely on the same
 * `BalanceMeter.applyTurnCost` already used by the chat path - the
 * companion service receives the closure directly so it doesn't need to
 * re-import the full plugin-WS deps.
 */
export interface CompanionBalanceHook {
  applyTurnCost(args: {
    userId: string;
    chatId: string;
    turnId: string;
    promptTokens: number;
    completionTokens: number;
    costMicroUsd: number;
    preDebited?: number;
  }): Promise<{ balanceTokens: number }>;
}

export interface CompanionServiceDeps {
  db: DbClient;
  /**
   * Run an LLM stream. Defaults to OpenRouter via `runStream`; tests
   * inject a fake.
   */
  llmRunner?: (args: RunStreamArgs) => RunStreamResult;
  /** Memory-hint queue. Required. */
  memoryHintQueue: MemoryHintQueue;
  /** Balance application hook (shared with the chat path). */
  balance: CompanionBalanceHook;
  /** Cheap-model resolver. Defaults to `chooseCheapModel`. */
  cheapModelChooser?: (tier: AuthedIdentity["tier"]) => string;
  /** Optional override for nanoid-flavoured id generation. */
  generateId?: () => string;
  /** Clock override for tests. */
  now?: () => Date;
  /** Max output tokens per proactive line. Default 150. */
  maxLineTokens?: number;
}

export interface CompanionService {
  handleTrigger(
    identity: AuthedIdentity,
    msg: ClientCompanionTriggerMsg,
  ): Promise<ServerCompanionLineMsg | null>;
  handleInteraction(
    identity: AuthedIdentity,
    msg: ClientCompanionInteractionEventMsg,
  ): Promise<ServerCompanionAckMsg | ServerCompanionLineMsg | null>;
  handleMemoryHint(
    identity: AuthedIdentity,
    msg: ClientCompanionMemoryHintMsg,
  ): Promise<ServerCompanionAckMsg>;
}

/* -------------------------------------------------------------------------- */
/*  Tunables                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Cap on `voice_style_notes` length. We drop oldest entries when a new
 * note pushes past this. 20 is what `EMBODIED_COMPANION.md` §5 commits to.
 */
export const MAX_VOICE_STYLE_NOTES = 20;

/**
 * How many memories to surface in the system-prompt prefix. Highest-weight
 * non-forgotten rows ordered by weight then recency.
 */
export const RECALL_LIMIT = 8;

/** Default cap on output tokens per proactive line. */
const DEFAULT_MAX_LINE_TOKENS = 150;

/* -------------------------------------------------------------------------- */
/*  Implementation                                                            */
/* -------------------------------------------------------------------------- */

export function createCompanionService(deps: CompanionServiceDeps): CompanionService {
  const llm = deps.llmRunner ?? runStream;
  const cheap = deps.cheapModelChooser ?? chooseCheapModel;
  const newId = deps.generateId ?? ((): string => nanoid());
  const clock = deps.now ?? ((): Date => new Date());
  const maxLineTokens = deps.maxLineTokens ?? DEFAULT_MAX_LINE_TOKENS;

  /* -- profile load/create ----------------------------------------------- */

  async function loadOrCreateProfile(
    identity: AuthedIdentity,
    starterHint?: string,
    archetypeHint?: string,
  ): Promise<CompanionProfile> {
    const osrsAccountId = null;
    const existing = await deps.db
      .select()
      .from(companionProfile)
      .where(
        and(
          eq(companionProfile.userId, identity.userId),
          osrsAccountId === null
            ? isNull(companionProfile.osrsAccountId)
            : eq(companionProfile.osrsAccountId, osrsAccountId as string),
        ),
      )
      .limit(1);
    if (existing.length > 0) return existing[0]!;

    const archetype = coerceArchetype(archetypeHint);
    const starter = typeof starterHint === "string" && starterHint.length > 0
      ? starterHint
      : "hooded_humanoid";
    const row: NewCompanionProfile = {
      id: newId(),
      userId: identity.userId,
      osrsAccountId: null,
      starterArchetype: starter,
      personalityArchetype: archetype,
      voiceStyleNotes: [],
      relationshipAgeDays: 0,
    };
    await deps.db.insert(companionProfile).values(row);
    const reread = await deps.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.id, row.id!))
      .limit(1);
    return reread[0]!;
  }

  /* -- memory fetch ------------------------------------------------------ */

  async function recallMemories(profileId: string): Promise<CompanionMemory[]> {
    return deps.db
      .select()
      .from(companionMemories)
      .where(
        and(
          eq(companionMemories.profileId, profileId),
          isNull(companionMemories.forgottenAt),
        ),
      )
      .orderBy(desc(companionMemories.weight), desc(companionMemories.lastReferencedAt))
      .limit(RECALL_LIMIT);
  }

  /* -- handle trigger ---------------------------------------------------- */

  async function handleTrigger(
    identity: AuthedIdentity,
    msg: ClientCompanionTriggerMsg,
  ): Promise<ServerCompanionLineMsg | null> {
    const profile = await loadOrCreateProfile(identity);
    const memories = await recallMemories(profile.id);

    const systemPrompt = buildArchetypePrompt({
      profile,
      recentMemories: memories,
      snapshot: {
        ...msg.contextSnapshot,
        triggerType: msg.triggerType,
      },
    });

    const modelId = cheap(identity.tier);

    // Resolve the language model. In tests `llmRunner` ignores `model` so we
    // pass an opaque sentinel; in production we go through OpenRouter.
    let model: RunStreamArgs["model"];
    try {
      model = getOpenRouter().chat(modelId);
    } catch {
      model = { kind: "fake", modelId } as unknown as RunStreamArgs["model"];
    }

    let result: RunStreamResult;
    try {
      result = llm({
        model,
        prompt: triggerPromptFor(msg.triggerType, msg.contextSnapshot),
        system: systemPrompt,
        // Proactive lines are short. Cap step count to 1: no tool loops.
        maxSteps: 1,
      });
    } catch (err) {
      log.warn({ err: (err as Error).message }, "companion: llm runner threw at start");
      return null;
    }

    // Drain the text stream into a single line. Cap by line tokens at a
    // best-effort character budget (4 chars/token is a fine approximation
    // for English-leaning prose).
    const charBudget = maxLineTokens * 4;
    let text = "";
    try {
      for await (const delta of result.textStream) {
        if (!delta) continue;
        text += delta;
        if (text.length >= charBudget) break;
      }
    } catch (err) {
      log.warn({ err: (err as Error).message }, "companion: stream errored mid-line");
      if (text.length === 0) return null;
    }

    let usage: { promptTokens: number; completionTokens: number };
    try {
      usage = await result.usagePromise;
    } catch {
      usage = { promptTokens: 0, completionTokens: 0 };
    }

    const costMicroUsd = computeCostMicroUsd(modelId, usage);
    const { balanceTokens } = await deps.balance.applyTurnCost({
      userId: identity.userId,
      // Companion lines have no chat id; we synthesize one keyed to the
      // trigger so the meter still aggregates cleanly.
      chatId: `companion:${msg.triggerType}`,
      turnId: newId(),
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      costMicroUsd,
    });

    return {
      type: "companion_line",
      text: text.trim() || "...",
      sourceTrigger: msg.triggerType,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      costMicroUsd,
      balanceTokens,
    };
  }

  /* -- handle interaction ------------------------------------------------ */

  async function handleInteraction(
    identity: AuthedIdentity,
    msg: ClientCompanionInteractionEventMsg,
  ): Promise<ServerCompanionAckMsg | ServerCompanionLineMsg | null> {
    const profile = await loadOrCreateProfile(identity);
    const payload = msg.payload ?? {};

    switch (msg.eventType) {
      case "name_companion": {
        const rawName =
          typeof payload["name"] === "string" ? (payload["name"] as string).trim() : "";
        if (rawName.length === 0) {
          return ackErr(msg.eventType, "empty name");
        }
        const name = rawName.slice(0, 32);
        await deps.db
          .update(companionProfile)
          .set({ companionName: name, updatedAt: clock() })
          .where(eq(companionProfile.id, profile.id));
        return ackOk(msg.eventType, `Saved nickname "${name}".`);
      }

      case "style_note": {
        const note =
          typeof payload["note"] === "string" ? (payload["note"] as string).trim() : "";
        if (note.length === 0) return ackErr(msg.eventType, "empty note");
        const trimmed = note.slice(0, 200);
        const current = profile.voiceStyleNotes ?? [];
        // Drop oldest if past cap. Newest at the END so the prompt builder
        // renders historical → fresh top to bottom.
        const next = [...current, trimmed].slice(-MAX_VOICE_STYLE_NOTES);
        await deps.db
          .update(companionProfile)
          .set({ voiceStyleNotes: next, updatedAt: clock() })
          .where(eq(companionProfile.id, profile.id));
        return ackOk(msg.eventType, "Voice note added.");
      }

      case "forget": {
        const scope =
          typeof payload["scope"] === "string" ? (payload["scope"] as string) : "last_session";
        const cutoff = clock();
        if (scope === "all") {
          await deps.db
            .update(companionMemories)
            .set({ forgottenAt: cutoff })
            .where(
              and(
                eq(companionMemories.profileId, profile.id),
                isNull(companionMemories.forgottenAt),
              ),
            );
          return ackOk(msg.eventType, "Forgot everything.");
        }
        // Default scope = "last_session" → mark anything seen since the
        // last session ended as forgotten. If we never recorded a session
        // end, fall back to "last 24h" so the command isn't a no-op on
        // first run.
        const since = profile.lastSessionEndedAt
          ? profile.lastSessionEndedAt
          : new Date(cutoff.getTime() - 24 * 60 * 60 * 1000);
        await deps.db
          .update(companionMemories)
          .set({ forgottenAt: cutoff })
          .where(
            and(
              eq(companionMemories.profileId, profile.id),
              isNull(companionMemories.forgottenAt),
              sql`${companionMemories.firstSeenAt} >= ${since.toISOString()}`,
            ),
          );
        return ackOk(msg.eventType, "Forgot the last session.");
      }

      case "clicked_companion": {
        // The player clicked the sprite to open a focused chat. We respond
        // with a short opener so the speech bubble has something while the
        // sidebar comes up.
        const snapshotIn =
          typeof payload["snapshot"] === "object" && payload["snapshot"] !== null
            ? (payload["snapshot"] as Record<string, unknown>)
            : {};
        const line = await handleTrigger(identity, {
          type: "companion_trigger",
          triggerType: "clicked_companion",
          contextSnapshot: snapshotIn,
        });
        if (line) return line;
        return ackOk(msg.eventType, "Listening.");
      }

      default:
        return ackErr(msg.eventType, "unknown event");
    }
  }

  /* -- handle memory hint ------------------------------------------------ */

  async function handleMemoryHint(
    identity: AuthedIdentity,
    msg: ClientCompanionMemoryHintMsg,
  ): Promise<ServerCompanionAckMsg> {
    // Make sure the profile exists so the extractor has somewhere to land
    // the rows when it runs end-of-session.
    await loadOrCreateProfile(identity);
    deps.memoryHintQueue.enqueue({
      userId: identity.userId,
      osrsAccountId: null,
      memorableEvent: msg.memorableEvent,
      evidenceProbeIds: msg.evidenceProbeIds ?? [],
      occurredAt: clock().getTime(),
    });
    return ackOk("companion_memory_hint", "Queued for end-of-session.");
  }

  return { handleTrigger, handleInteraction, handleMemoryHint };
}

/* -------------------------------------------------------------------------- */
/*  Helpers                                                                   */
/* -------------------------------------------------------------------------- */

function ackOk(eventType: string, note?: string): ServerCompanionAckMsg {
  return note !== undefined
    ? { type: "companion_ack", eventType, ok: true, note }
    : { type: "companion_ack", eventType, ok: true };
}

function ackErr(eventType: string, note: string): ServerCompanionAckMsg {
  return { type: "companion_ack", eventType, ok: false, note };
}

/**
 * Per-trigger prompt the model receives. The system prompt holds the
 * personality + memory + snapshot; this is the bare "now say one line"
 * instruction. Keeping it terse forces the model to react instead of
 * narrate.
 */
function triggerPromptFor(triggerType: string, snapshot: Record<string, unknown>): string {
  const summary =
    typeof snapshot["summary"] === "string" && (snapshot["summary"] as string).length > 0
      ? (snapshot["summary"] as string)
      : "(no summary supplied)";
  return [
    `Trigger: ${triggerType}.`,
    `Context: ${summary}.`,
    "React in one short sentence. Do not narrate. Do not greet. Do not echo the trigger word.",
  ].join("\n");
}

/* -------------------------------------------------------------------------- */
/*  In-memory queue (default)                                                 */
/* -------------------------------------------------------------------------- */

/**
 * Default in-process queue used when nothing else wires a queue port.
 * Production wires the end-of-session extractor as the consumer; tests
 * read `.drain()` directly.
 */
export class InMemoryMemoryHintQueue implements MemoryHintQueue {
  private readonly items: Parameters<MemoryHintQueue["enqueue"]>[0][] = [];

  enqueue(item: Parameters<MemoryHintQueue["enqueue"]>[0]): void {
    this.items.push(item);
  }

  /** Return + clear everything currently queued for the given user. */
  drain(userId: string): Parameters<MemoryHintQueue["enqueue"]>[0][] {
    const out: Parameters<MemoryHintQueue["enqueue"]>[0][] = [];
    for (let i = this.items.length - 1; i >= 0; i -= 1) {
      const it = this.items[i]!;
      if (it.userId === userId) {
        out.unshift(it);
        this.items.splice(i, 1);
      }
    }
    return out;
  }

  size(): number {
    return this.items.length;
  }
}

/* -------------------------------------------------------------------------- */
/*  Type re-exports for plugin-side consumers                                 */
/* -------------------------------------------------------------------------- */

export type { CompanionArchetype };
