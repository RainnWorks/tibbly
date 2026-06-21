/**
 * Plugin ↔ backend WebSocket handler (RAI-17).
 *
 * Bidirectional. The plugin opens the socket, authenticates, sends user
 * messages, and answers our tool requests. The backend drives the LLM loop
 * via OpenRouter and streams assistant deltas back.
 *
 * Wire shape: every frame is JSON, one message per frame. See
 * `packages/shared-types/src/protocol.ts` for the Zod-validated schemas.
 *
 * Library choice: Bun's native WebSocket (per
 * docs/research/libraries/realtime.md — the plugin link is the hot path).
 * We expose the handler via `pluginWsHandler` which returns a
 * `WebSocketHandler` compatible with `Bun.serve({ websocket: ... })`.
 *
 * This module is responsible for:
 *   - Parsing inbound frames with Zod (malformed → `error` + close).
 *   - Auth gate (device key + balance check; TODO-stubs until RAI-15/RAI-20
 *     land their schemas).
 *   - Running an LLM stream per `user_message` and forwarding deltas.
 *   - Bridging tool calls: when the LLM picks a tool, send
 *     `tool_call_request` and `await` the plugin's `tool_call_result`.
 *   - Emitting `assistant_message_done` with token + cost telemetry.
 */
import type { ServerWebSocket, WebSocketHandler } from "bun";
import { nanoid } from "nanoid";

import {
  encodeServerMessage,
  parseClientMessage,
  type ClientToServer,
  type ServerErrorCode,
  type ServerToClient,
  type Tier,
} from "@osrs-llm-helper/shared-types";

import { getDefaultBus, type EventBus } from "../events";
import { log } from "../lib/log";
import { computeCostMicroUsd } from "../llm/cost";
import { chooseModel } from "../llm/router";
import {
  buildRemoteTools,
  getOpenRouter,
  runStream,
  type RemoteToolSpec,
  type RunStreamArgs,
  type RunStreamResult,
} from "../llm/openrouter";
import { ProtocolStateMachine, type AuthedIdentity } from "./protocol-state-machine";

/**
 * The minimum surface area the WS handler needs from the database / billing
 * subsystems. RAI-15 (schema) and RAI-20 (billing) own the real impls; we
 * keep them as ports so the test can pass in fakes.
 */
export interface DeviceLookup {
  /**
   * Resolve a device key to a user. Return `null` if the device is unknown.
   * Implementations may also reject if subscription is cancelled etc — we
   * treat that as `null` so the WS handler responds with `unknown_device`.
   */
  resolveDeviceKey(deviceKey: string): Promise<AuthedIdentity | null>;
}

export interface BalanceMeter {
  /** Current remaining token balance for the user (raw token count). */
  getBalanceTokens(userId: string): Promise<number>;
  /**
   * Pre-flight gate (RAI-20). Called BEFORE we open the OpenRouter stream
   * with a rough estimate of how many tokens the turn will burn. Implementations
   * must atomically reject when the balance can't cover the estimate; on
   * rejection they emit `chat.cap_hit` so analytics can count caps without
   * the chat path having to wire its own bus.
   *
   * Returning `null` means "blocked"; the WS handler will surface
   * `balance_exhausted` to the client and abort the turn.
   *
   * Returning `{ balanceTokens }` means "go ahead" with the new (reserved)
   * balance — `applyTurnCost` reconciles the actual usage after.
   */
  ensureCanSpend?(args: {
    userId: string;
    chatId: string;
    estimatedTokens: number;
  }): Promise<{ balanceTokens: number } | null>;
  /** Decrement after a turn finishes. Idempotency: per turn-id. */
  applyTurnCost(args: {
    userId: string;
    chatId: string;
    turnId: string;
    promptTokens: number;
    completionTokens: number;
    costMicroUsd: number;
    /** Tokens already taken via `ensureCanSpend`; pass 0 if no pre-debit. */
    preDebited?: number;
  }): Promise<{ balanceTokens: number }>;
}

export interface EventLogger {
  /**
   * Append-only event row. RAI-37 (analytics) owns the real implementation
   * — we treat the call as fire-and-forget so a slow analytics tier can't
   * stall the chat loop. Use `bridgeEventLoggerToBus` to wire in the
   * production bus (RAI-37); tests pass a fake collector.
   */
  logEvent(args: { userId: string | null; kind: string; payload: unknown }): Promise<void>;
}

/**
 * Bridge the freeform `EventLogger` port to the typed RAI-37 event bus.
 *
 * Most WS-level events don't have a strict typed equivalent yet (the bus
 * is curated; see `events/types.ts`). The bridge maps the few that do
 * (`ws.assistant_message_done` → `chat.message.sent`) and best-effort
 * logs the rest via pino so we don't lose them. Add more cases here as the
 * RAI-37 taxonomy grows.
 */
export function bridgeEventLoggerToBus(bus: EventBus = getDefaultBus()): EventLogger {
  return {
    async logEvent(args) {
      try {
        if (args.kind === "ws.assistant_message_done") {
          const p = args.payload as {
            chatId: string;
            promptTokens: number;
            completionTokens: number;
            costMicroUsd: number;
            modelId: string;
          };
          const userId = args.userId ?? undefined;
          await bus.publish({
            type: "chat.message.sent",
            ...(userId ? { userId } : {}),
            payload: {
              chatId: p.chatId,
              messageId: nanoid(),
              tokens: { in: p.promptTokens, out: p.completionTokens },
              model: p.modelId,
              costMicroUsd: p.costMicroUsd,
            },
          });
          return;
        }
        // Other WS events aren't part of the curated taxonomy yet; just log.
        log.info({ kind: args.kind, userId: args.userId }, "ws.event");
      } catch (err) {
        log.warn({ err, kind: args.kind }, "ws: event bridge failed");
      }
    },
  };
}

/**
 * Hook the WS handler can call when it wants to actually do an LLM turn.
 * Tests replace this with a fake that emits a canned tool call + reply.
 */
export type LlmRunner = (args: RunStreamArgs) => RunStreamResult;

export interface PluginWsDeps {
  deviceLookup: DeviceLookup;
  balanceMeter: BalanceMeter;
  eventLogger: EventLogger;
  /** Optional tool catalog override — defaults to a tiny core set. */
  tools?: RemoteToolSpec[];
  /** Optional LLM stream runner — defaults to OpenRouter via `runStream`. */
  llmRunner?: LlmRunner;
  /** Optional model overrider (per turn). Falls back to tier router. */
  chooseModel?: (tier: Tier) => string;
  /** Optional clock for deterministic tests. */
  now?: () => number;
  /** Optional id generator for deterministic tests. */
  generateId?: () => string;
}

/**
 * Per-socket data — Bun WS passes this to every handler callback.
 */
interface SocketData {
  sm: ProtocolStateMachine;
  /** Connection id for log correlation; opaque to the client. */
  connId: string;
}

const DEFAULT_TOOLS: RemoteToolSpec[] = [];

/**
 * Build a Bun WebSocket handler for the plugin link.
 *
 * Usage:
 *   const handler = pluginWsHandler({ deviceLookup, balanceMeter, eventLogger });
 *   Bun.serve({
 *     port: 3000,
 *     fetch(req, server) {
 *       if (new URL(req.url).pathname === "/ws/plugin") {
 *         const ok = server.upgrade<SocketData>(req, { data: handler.makeSocketData() });
 *         return ok ? undefined : new Response("upgrade failed", { status: 400 });
 *       }
 *       return new Response("not found", { status: 404 });
 *     },
 *     websocket: handler.websocket,
 *   });
 */
export function pluginWsHandler(deps: PluginWsDeps): {
  websocket: WebSocketHandler<SocketData>;
  makeSocketData(): SocketData;
} {
  const toolSpecs = deps.tools ?? DEFAULT_TOOLS;
  const llmRunner = deps.llmRunner ?? runStream;
  const modelChooser = deps.chooseModel ?? chooseModel;
  const generateId = deps.generateId ?? (() => nanoid());

  function send(ws: ServerWebSocket<SocketData>, msg: ServerToClient): void {
    if (ws.readyState !== 1) return; // 1 === OPEN
    ws.send(encodeServerMessage(msg));
  }

  function sendError(
    ws: ServerWebSocket<SocketData>,
    code: ServerErrorCode,
    message: string,
  ): void {
    send(ws, { type: "error", code, message });
  }

  async function handleAuth(
    ws: ServerWebSocket<SocketData>,
    msg: Extract<ClientToServer, { type: "auth" }>,
  ): Promise<void> {
    const identity = await deps.deviceLookup.resolveDeviceKey(msg.deviceKey);
    if (!identity) {
      send(ws, { type: "auth_error", reason: "unknown_device" });
      ws.close(4401, "unknown_device");
      return;
    }

    // Allow the plugin to bring playerName in on auth; identity overrides it
    // only if absent (the DB binding wins).
    const finalIdentity: AuthedIdentity = {
      ...identity,
      playerName: identity.playerName ?? msg.playerName ?? null,
    };

    const balanceTokens = await deps.balanceMeter.getBalanceTokens(finalIdentity.userId);
    if (balanceTokens <= 0) {
      send(ws, { type: "auth_error", reason: "balance_exhausted" });
      ws.close(4402, "balance_exhausted");
      return;
    }

    ws.data.sm.markAuthed(finalIdentity);
    send(ws, {
      type: "auth_ok",
      userId: finalIdentity.userId,
      tier: finalIdentity.tier,
      balanceTokens,
    });

    void deps.eventLogger.logEvent({
      userId: finalIdentity.userId,
      kind: "ws.auth_ok",
      payload: { pluginVersion: msg.pluginVersion },
    });
  }

  async function handleUserMessage(
    ws: ServerWebSocket<SocketData>,
    msg: Extract<ClientToServer, { type: "user_message" }>,
  ): Promise<void> {
    const sm = ws.data.sm;
    const identity = sm.getIdentity();
    if (!identity) {
      sendError(ws, "unauthenticated", "auth required before user_message");
      return;
    }
    if (!sm.beginTurn(msg.chatId)) {
      sendError(ws, "malformed", "another turn is already in flight");
      return;
    }

    void deps.eventLogger.logEvent({
      userId: identity.userId,
      kind: "ws.user_message",
      payload: { chatId: msg.chatId, contentLen: msg.content.length },
    });

    // Pre-flight balance gate (RAI-20). We reserve a conservative estimate
    // before the OpenRouter request opens — better to reject early than to
    // burn LLM tokens we can't bill for. The estimate is intentionally low:
    // the post-call `applyTurnCost` reconciles the actual usage (clamped at
    // zero so a mid-flight call can still finish).
    const PREFLIGHT_ESTIMATE_TOKENS = 1;
    let preDebited = 0;
    if (deps.balanceMeter.ensureCanSpend) {
      const reserved = await deps.balanceMeter.ensureCanSpend({
        userId: identity.userId,
        chatId: msg.chatId,
        estimatedTokens: PREFLIGHT_ESTIMATE_TOKENS,
      });
      if (reserved === null) {
        sendError(ws, "rate_limited", "balance_exhausted");
        send(ws, {
          type: "assistant_message_done",
          chatId: msg.chatId,
          promptTokens: 0,
          completionTokens: 0,
          costMicroUsd: 0,
          balanceTokens: 0,
        });
        sm.endTurn();
        return;
      }
      preDebited = PREFLIGHT_ESTIMATE_TOKENS;
    }

    // Build the tool dispatcher — every LLM tool call we register a pending
    // promise here, then send a `tool_call_request` for the plugin to fulfil.
    const dispatch = async (params: {
      toolCallId: string;
      name: string;
      input: unknown;
    }): Promise<unknown> => {
      const promise = sm.registerPendingTool(params.toolCallId);
      send(ws, {
        type: "tool_call_request",
        toolCallId: params.toolCallId,
        name: params.name,
        input: params.input,
      });
      return promise;
    };

    // Allow-list filter — if the plugin restricted the tool surface, only
    // expose those. (Token-economy gating; see docs/architecture/TOOL_ECONOMY.md.)
    let activeTools = toolSpecs;
    if (msg.allowedTools && msg.allowedTools.length > 0) {
      const allow = new Set(msg.allowedTools);
      activeTools = toolSpecs.filter((t) => allow.has(t.name));
    }

    const llmTools =
      activeTools.length > 0
        ? buildRemoteTools(activeTools, dispatch, generateId)
        : undefined;

    const modelId = (deps.chooseModel ?? modelChooser)(identity.tier);

    // Resolve the language model. In tests `llmRunner` ignores `model` so we
    // pass a sentinel; in production we go through OpenRouter.
    let model: RunStreamArgs["model"];
    try {
      model = getOpenRouter().chat(modelId);
    } catch {
      // No API key (test env). Pass an opaque marker — the runner ignores it.
      model = { kind: "fake", modelId } as unknown as RunStreamArgs["model"];
    }

    const snapshotJson = msg.context?.snapshot
      ? JSON.stringify(msg.context.snapshot)
      : null;
    const systemPrompt = buildSystemPrompt(identity, snapshotJson);

    let result: RunStreamResult;
    try {
      const args: RunStreamArgs = {
        model,
        prompt: msg.content,
        system: systemPrompt,
        ...(llmTools ? { tools: llmTools } : {}),
      };
      result = llmRunner(args);
    } catch (err) {
      log.error({ err }, "ws: llm runner threw at start");
      sendError(ws, "upstream_error", err instanceof Error ? err.message : "llm failed");
      sm.endTurn();
      return;
    }

    // Stream text deltas. We don't enumerate `fullStream` for tool events
    // — the AI SDK fires `execute` automatically and our dispatcher handles
    // the round-trip transparently. We only relay text here.
    try {
      for await (const delta of result.textStream) {
        if (delta) {
          send(ws, { type: "assistant_message_delta", chatId: msg.chatId, delta });
        }
      }
    } catch (err) {
      log.warn({ err }, "ws: stream errored mid-turn");
      sendError(ws, "upstream_error", err instanceof Error ? err.message : "stream failed");
      sm.endTurn();
      return;
    }

    let usage: { promptTokens: number; completionTokens: number };
    try {
      usage = await result.usagePromise;
    } catch (err) {
      log.warn({ err }, "ws: usage promise rejected — billing 0 tokens");
      usage = { promptTokens: 0, completionTokens: 0 };
    }

    const costMicroUsd = computeCostMicroUsd(modelId, usage);
    const { balanceTokens } = await deps.balanceMeter.applyTurnCost({
      userId: identity.userId,
      chatId: msg.chatId,
      turnId: generateId(),
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      costMicroUsd,
      preDebited,
    });

    send(ws, {
      type: "assistant_message_done",
      chatId: msg.chatId,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      costMicroUsd,
      balanceTokens,
    });

    void deps.eventLogger.logEvent({
      userId: identity.userId,
      kind: "ws.assistant_message_done",
      payload: {
        chatId: msg.chatId,
        promptTokens: usage.promptTokens,
        completionTokens: usage.completionTokens,
        costMicroUsd,
        balanceTokens,
        modelId,
      },
    });

    sm.endTurn();
  }

  function handleToolCallResult(
    ws: ServerWebSocket<SocketData>,
    msg: Extract<ClientToServer, { type: "tool_call_result" }>,
  ): void {
    const sm = ws.data.sm;
    if (msg.errorString) {
      const rejected = sm.rejectPendingTool(msg.toolCallId, msg.errorString);
      if (!rejected) {
        sendError(ws, "malformed", `unknown toolCallId ${msg.toolCallId}`);
      }
      return;
    }
    const resolved = sm.resolvePendingTool(msg.toolCallId, msg.output);
    if (!resolved) {
      sendError(ws, "malformed", `unknown toolCallId ${msg.toolCallId}`);
    }
  }

  function handleCancel(
    ws: ServerWebSocket<SocketData>,
    _msg: Extract<ClientToServer, { type: "cancel" }>,
  ): void {
    // Resolve pending tool calls with an aborted error and end the turn.
    ws.data.sm.endTurn();
  }

  function handlePing(ws: ServerWebSocket<SocketData>): void {
    send(ws, { type: "pong" });
  }

  const websocket: WebSocketHandler<SocketData> = {
    open(_ws): void {
      // No-op until auth. We don't even read identity yet.
    },

    async message(ws, raw): Promise<void> {
      let parsed: unknown;
      try {
        parsed = typeof raw === "string" ? JSON.parse(raw) : JSON.parse(raw.toString());
      } catch {
        sendError(ws, "malformed", "frame is not valid JSON");
        return;
      }

      const result = parseClientMessage(parsed);
      if (!result.ok) {
        sendError(ws, "malformed", result.error);
        return;
      }

      try {
        switch (result.value.type) {
          case "auth":
            await handleAuth(ws, result.value);
            return;
          case "user_message":
            await handleUserMessage(ws, result.value);
            return;
          case "tool_call_result":
            handleToolCallResult(ws, result.value);
            return;
          case "cancel":
            handleCancel(ws, result.value);
            return;
          case "ping":
            handlePing(ws);
            return;
        }
      } catch (err) {
        log.error({ err }, "ws: unhandled error processing message");
        sendError(ws, "internal", err instanceof Error ? err.message : "internal error");
      }
    },

    close(ws): void {
      ws.data.sm.markClosed();
    },

    drain(_ws): void {
      // Bun backpressure hook — we don't queue large writes today.
    },
  };

  return {
    websocket,
    makeSocketData(): SocketData {
      return { sm: new ProtocolStateMachine(), connId: generateId() };
    },
  };
}

/**
 * Build the system prompt sent to the LLM. Keeping it tiny is a deliberate
 * token-budget choice — the snapshot JSON is the only player-state we eat
 * tokens for upfront. See docs/architecture/TOOL_ECONOMY.md.
 */
function buildSystemPrompt(identity: AuthedIdentity, snapshotJson: string | null): string {
  const lines = [
    "You are the OSRS LLM Helper — an expert Old School RuneScape co-pilot.",
    "Always be concise. Prefer one-paragraph answers unless asked for detail.",
    "Never recommend bot software or anything against Jagex's ToS.",
  ];
  if (identity.playerName) lines.push(`The player's display name is "${identity.playerName}".`);
  if (snapshotJson) lines.push(`Current player state JSON:\n${snapshotJson}`);
  return lines.join("\n");
}
