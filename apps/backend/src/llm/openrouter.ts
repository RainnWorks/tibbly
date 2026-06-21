/**
 * OpenRouter wrapper around the Vercel AI SDK (RAI-17).
 *
 * Why a wrapper:
 *   1. We only ever need `streamText` — keep the surface small.
 *   2. Tool execution lives on the *plugin* (the OSRS client). That means a
 *      tool's `execute` function on the backend can't actually do the work;
 *      it must (a) emit a `tool_call_request` over the per-chat WebSocket,
 *      then (b) await a matching `tool_call_result`. The WS handler in
 *      `src/ws/plugin.ts` is the one that fulfills these promises — this
 *      module just provides the plumbing.
 *   3. Tests need to swap in a fake LLM client. Anything that hits the
 *      network is hidden behind `LlmClient` so the round-trip test can stub.
 *
 * @see docs/research/libraries/llm.md
 */
import { stepCountIs, streamText, tool } from "ai";
import type { LanguageModel, Tool } from "ai";
import { createOpenRouter } from "@openrouter/ai-sdk-provider";
import type { z } from "zod";

import { env } from "../env";

/**
 * Singleton OpenRouter provider. Created lazily so test environments without
 * `OPENROUTER_API_KEY` set don't blow up on module load.
 */
let openrouterProvider: ReturnType<typeof createOpenRouter> | undefined;

export function getOpenRouter(): ReturnType<typeof createOpenRouter> {
  if (openrouterProvider) return openrouterProvider;
  const apiKey = env.OPENROUTER_API_KEY;
  if (!apiKey) {
    throw new Error("OPENROUTER_API_KEY missing — refusing to make a live LLM call");
  }
  openrouterProvider = createOpenRouter({ apiKey });
  return openrouterProvider;
}

/** Tool catalog item the backend exposes to the LLM. */
export interface RemoteToolSpec {
  name: string;
  description: string;
  /** Zod schema for the tool's input. Stays loose for v1 — plugin validates. */
  inputSchema: z.ZodTypeAny;
}

/**
 * Tool round-trip dispatcher. Implementations call the plugin (via WS), wait
 * for its `tool_call_result`, and return the parsed output.
 *
 * The default (production) impl is provided by `src/ws/plugin.ts` — it stuffs
 * `toolCallId → pending promise` into a per-chat map. The test harness uses a
 * fake that resolves synchronously.
 */
export type ToolDispatcher = (params: {
  toolCallId: string;
  name: string;
  input: unknown;
}) => Promise<unknown>;

/**
 * Build an AI-SDK `ToolSet` whose `execute` functions defer to `dispatch`.
 * The toolCallId we synthesise here is *backend-side* — when we forward to
 * the plugin we'll pass it as `tool_call_request.toolCallId`.
 */
export function buildRemoteTools(
  specs: RemoteToolSpec[],
  dispatch: ToolDispatcher,
  idGenerator: () => string,
): Record<string, Tool> {
  const out: Record<string, Tool> = {};
  for (const spec of specs) {
    out[spec.name] = tool({
      description: spec.description,
      inputSchema: spec.inputSchema,
      execute: async (input: unknown) => {
        const toolCallId = idGenerator();
        return dispatch({ toolCallId, name: spec.name, input });
      },
    });
  }
  return out;
}

export interface RunStreamArgs {
  /** Resolved language model — usually `openrouter.chat(modelId)`. */
  model: LanguageModel;
  /** Final user prompt for this turn. */
  prompt: string;
  /**
   * System prompt — typically a compact preamble including the player's
   * snapshot (gear, location, current task). Token budget the caller's
   * problem.
   */
  system?: string;
  tools?: Record<string, Tool>;
  /** Hard cap on the LLM-tool loop. Default = 5 steps. */
  maxSteps?: number;
  abortSignal?: AbortSignal;
}

/**
 * Stable shape we hand back to the WS handler regardless of which AI SDK
 * version we're on. The handler walks `fullStream` for text deltas; we surface
 * a `usagePromise` so we can compute cost at end-of-turn.
 */
export interface RunStreamResult {
  fullStream: AsyncIterable<unknown>;
  textStream: AsyncIterable<string>;
  usagePromise: Promise<{ promptTokens: number; completionTokens: number }>;
}

/**
 * Run an LLM turn — streaming text + (transparently) routing tool calls
 * back through the supplied dispatcher.
 */
export function runStream(args: RunStreamArgs): RunStreamResult {
  const streamArgs: Parameters<typeof streamText>[0] = {
    model: args.model,
    prompt: args.prompt,
    stopWhen: stepCountIs(args.maxSteps ?? 5),
  };
  if (args.system !== undefined) streamArgs.system = args.system;
  if (args.tools !== undefined) streamArgs.tools = args.tools;
  if (args.abortSignal !== undefined) streamArgs.abortSignal = args.abortSignal;

  const result = streamText(streamArgs);
  return {
    fullStream: result.fullStream,
    textStream: result.textStream,
    usagePromise: Promise.resolve(result.usage).then((u) => ({
      // The SDK uses `inputTokens` / `outputTokens` in v5+; fall back if absent.
      promptTokens:
        (u as { inputTokens?: number; promptTokens?: number }).inputTokens ??
        (u as { promptTokens?: number }).promptTokens ??
        0,
      completionTokens:
        (u as { outputTokens?: number; completionTokens?: number }).outputTokens ??
        (u as { completionTokens?: number }).completionTokens ??
        0,
    })),
  };
}
