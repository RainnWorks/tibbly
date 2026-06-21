/**
 * A deterministic fake LLM runner the orchestrator injects into the WS
 * handler in place of the real OpenRouter stream.
 *
 * Why this exists: hitting OpenRouter for every test would be slow,
 * flaky, and require a real API key. The fake runner is keyword-driven:
 * it scans the user prompt for tool-name hints, dispatches each matching
 * tool, then streams a canned reply. This is enough to exercise the
 * tool round-trip + billing path end-to-end without any external
 * dependency.
 *
 * It also captures the prompt + tools list each call so scenarios can
 * assert on `chooseTools.callLog()`.
 *
 * For real BYOK tests, the orchestrator can be re-booted with
 * `llmRunner: undefined` so the production runStream kicks in. That path
 * is documented in scenarios/02-byok-direct-chat.spec.ts.
 */
import type {
  RemoteToolSpec,
  RunStreamArgs,
  RunStreamResult,
} from "../../apps/backend/src/llm/openrouter";

export interface FakeLlmCall {
  prompt: string;
  toolNames: string[];
  toolsRequested: string[];
}

export interface FakeLlmRunner {
  /** Drop-in for `pluginWsHandler({ llmRunner })`. */
  runner: (args: RunStreamArgs) => RunStreamResult;
  /** What we saw on every invocation. */
  callLog(): ReadonlyArray<FakeLlmCall>;
  /** Optional: override the next reply text. */
  setNextReply(text: string): void;
  /** Optional: override the next tool list (defaults to keyword-scanned). */
  setNextTools(names: string[]): void;
  /** Override the synthetic usage on the next call. */
  setNextUsage(promptTokens: number, completionTokens: number): void;
}

const DEFAULT_USAGE = { promptTokens: 1200, completionTokens: 84 };

const KEYWORD_TOOLS: Array<{ keywords: RegExp; tool: string }> = [
  { keywords: /\bbank\b|\btab\b/i, tool: "bank_tab" },
  { keywords: /\binventory\b|\bcarrying\b/i, tool: "inventory" },
  { keywords: /\bprayer\b/i, tool: "active_prayers" },
  { keywords: /\bquest\b/i, tool: "current_quest" },
  { keywords: /\bslayer\b|\btask\b/i, tool: "slayer_task" },
  { keywords: /\bfarming\b|\bpatch(es)?\b|\bherb\b/i, tool: "farming_patches" },
  { keywords: /\braid\b|\btob\b|\bcox\b|\btoa\b/i, tool: "raid_layout" },
  { keywords: /\bprojectile\b|\bincoming\b|\btick\b/i, tool: "target_projectiles" },
  { keywords: /\bcombat\b|\bhp\b|\bhitpoints\b/i, tool: "combat_stats" },
  { keywords: /\bworld\b|\blocation\b|\bwhere\b/i, tool: "world_state" },
  { keywords: /\bwho am i\b|\bidentity\b|\baccount\b/i, tool: "account_identity" },
];

function pickTools(prompt: string, available: ReadonlyArray<RemoteToolSpec>): string[] {
  if (available.length === 0) return [];
  const names = new Set(available.map((t) => t.name));
  const picks = new Set<string>();
  for (const { keywords, tool } of KEYWORD_TOOLS) {
    if (keywords.test(prompt) && names.has(tool)) picks.add(tool);
  }
  return [...picks];
}

export function createFakeLlm(): FakeLlmRunner {
  const log: FakeLlmCall[] = [];
  const overrides: {
    reply?: string;
    tools?: string[];
    usage?: { promptTokens: number; completionTokens: number };
  } = {};

  const runner = (args: RunStreamArgs): RunStreamResult => {
    const availableNames = Object.keys(args.tools ?? {});
    const available = availableNames.map(
      (name) => ({ name }) as RemoteToolSpec,
    );
    const toolNames = overrides.tools ?? pickTools(args.prompt, available);
    delete overrides.tools;

    if (process.env["E2E_LLM_DEBUG"]) {
      // eslint-disable-next-line no-console
      console.log(
        "[fake-llm] prompt=",
        JSON.stringify(args.prompt).slice(0, 60),
        "available=",
        availableNames,
        "picked=",
        toolNames,
      );
    }
    log.push({ prompt: args.prompt, toolsRequested: availableNames, toolNames });

    const reply = overrides.reply ?? buildDefaultReply(toolNames);
    delete overrides.reply;
    const usage = overrides.usage ?? DEFAULT_USAGE;
    delete overrides.usage;

    // Pre-fire all selected tools so the WS handler ships
    // tool_call_request frames before we stream text. The dispatcher in
    // `pluginWsHandler` invokes the tool's `execute` itself; we await
    // each one so the test sees them in order before deltas arrive.
    const toolPromise = (async () => {
      for (const name of toolNames) {
        const spec = (args.tools ?? {})[name];
        if (!spec || typeof spec.execute !== "function") {
          if (process.env["E2E_LLM_DEBUG"]) {
            // eslint-disable-next-line no-console
            console.log("[fake-llm] skip tool", name, "execute=", typeof spec?.execute);
          }
          continue;
        }
        if (process.env["E2E_LLM_DEBUG"]) {
          // eslint-disable-next-line no-console
          console.log("[fake-llm] await execute", name);
        }
        try {
          await spec.execute({}, { messages: [], toolCallId: `fake_${name}` } as never);
        } catch (err) {
          if (process.env["E2E_LLM_DEBUG"]) {
            // eslint-disable-next-line no-console
            console.log("[fake-llm] execute threw", name, err);
          }
        }
      }
    })();

    const textStream = (async function* (): AsyncGenerator<string> {
      await toolPromise;
      for (const chunk of chunkReply(reply)) {
        yield chunk;
      }
    })();

    const fullStream = (async function* (): AsyncGenerator<unknown> {
      await toolPromise;
      for (const chunk of chunkReply(reply)) {
        yield { type: "text-delta", text: chunk };
      }
    })();

    return {
      fullStream,
      textStream,
      usagePromise: Promise.resolve(usage),
    };
  };

  return {
    runner,
    callLog() {
      return log;
    },
    setNextReply(text) {
      overrides.reply = text;
    },
    setNextTools(names) {
      overrides.tools = names;
    },
    setNextUsage(promptTokens, completionTokens) {
      overrides.usage = { promptTokens, completionTokens };
    },
  };
}

function buildDefaultReply(toolNames: string[]): string {
  if (toolNames.length === 0) {
    return "I do not need a tool for that. Ask me something specific about your account.";
  }
  return `Looked at ${toolNames.join(", ")}. Here is the short version.`;
}

function chunkReply(reply: string): string[] {
  // Break by spaces so consumers see streaming behaviour rather than
  // one giant delta. Keeps the test honest about delta concatenation.
  return reply.split(/(\s+)/).filter((s) => s.length > 0);
}
