/**
 * Per-connection state machine (RAI-17).
 *
 * A WebSocket lives in one of these states:
 *   `connecting` — TCP open, no auth message received yet.
 *   `authed`     — `auth` succeeded; user id + tier known.
 *   `inTurn`     — an LLM turn is streaming (mid-`user_message`).
 *   `closed`     — terminal; either party hung up.
 *
 * The state machine is deliberately tiny: we don't model retries or queues
 * here. The WS handler is the orchestrator; this file is just the
 * invariant-checker.
 *
 * Also tracks the per-chat pending tool-call promises — when the LLM emits a
 * tool call, the WS handler `registerPendingTool` to get a Promise, sends a
 * `tool_call_request` to the plugin, and the plugin's later `tool_call_result`
 * resolves it via `resolvePendingTool`.
 */
import type { Tier } from "@osrs-llm-helper/shared-types";

export type WsState = "connecting" | "authed" | "inTurn" | "closed";

export interface AuthedIdentity {
  userId: string;
  tier: Tier;
  /** Device key the connection presented — used for auditing. Never logged. */
  deviceKey: string;
  /** Player name (or null when the player is logged out / anonymous). */
  playerName: string | null;
}

interface PendingTool {
  resolve: (output: unknown) => void;
  reject: (error: Error) => void;
}

export class ProtocolStateMachine {
  private state: WsState = "connecting";
  private identity: AuthedIdentity | null = null;
  private currentChatId: string | null = null;
  private readonly pendingTools = new Map<string, PendingTool>();

  getState(): WsState {
    return this.state;
  }

  getIdentity(): AuthedIdentity | null {
    return this.identity;
  }

  getCurrentChatId(): string | null {
    return this.currentChatId;
  }

  /**
   * Transition `connecting → authed`. Throws if already authed or closed.
   * Idempotency: re-running `auth` after success returns false silently;
   * the WS handler emits a clean error frame to the client.
   */
  markAuthed(identity: AuthedIdentity): boolean {
    if (this.state !== "connecting") return false;
    this.identity = identity;
    this.state = "authed";
    return true;
  }

  /** Transition `authed → inTurn`. */
  beginTurn(chatId: string): boolean {
    if (this.state !== "authed") return false;
    this.currentChatId = chatId;
    this.state = "inTurn";
    return true;
  }

  /** Transition `inTurn → authed`. Always safe at end-of-turn or on error. */
  endTurn(): void {
    if (this.state === "inTurn") this.state = "authed";
    // Reject any tool calls left pending — the LLM finished without a
    // result. Don't leave dangling promises.
    for (const [id, pending] of this.pendingTools.entries()) {
      pending.reject(new Error(`turn ended with tool call ${id} unresolved`));
    }
    this.pendingTools.clear();
    this.currentChatId = null;
  }

  /** Terminal — connection closing. Rejects pending tools. */
  markClosed(reason = "connection closed"): void {
    this.state = "closed";
    for (const [, pending] of this.pendingTools.entries()) {
      pending.reject(new Error(reason));
    }
    this.pendingTools.clear();
  }

  /** Register a tool call that's waiting on the plugin's result. */
  registerPendingTool(toolCallId: string): Promise<unknown> {
    return new Promise<unknown>((resolve, reject) => {
      this.pendingTools.set(toolCallId, { resolve, reject });
    });
  }

  /** Resolve a pending tool with a successful output. */
  resolvePendingTool(toolCallId: string, output: unknown): boolean {
    const pending = this.pendingTools.get(toolCallId);
    if (!pending) return false;
    this.pendingTools.delete(toolCallId);
    pending.resolve(output);
    return true;
  }

  /** Reject a pending tool with an error string from the plugin. */
  rejectPendingTool(toolCallId: string, errorString: string): boolean {
    const pending = this.pendingTools.get(toolCallId);
    if (!pending) return false;
    this.pendingTools.delete(toolCallId);
    pending.reject(new Error(errorString));
    return true;
  }
}
