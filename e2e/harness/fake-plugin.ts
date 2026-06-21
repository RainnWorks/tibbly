/**
 * Fake RuneLite plugin — the harness's beating heart.
 *
 * Speaks the real Tibbly WSS protocol verbatim. Every outbound frame is
 * validated against `ClientToServer` and every inbound frame against
 * `ServerToClient` before it touches the rest of the harness, so a drift
 * in the wire format surfaces as a test failure, not a silent skip.
 *
 * What this replaces: the real plugin's [BackendWsClient] + auth supplier
 * + [ToolDispatcher] chain. The only thing we cannot do without a live
 * RuneLite client is the actual game-state read, and that's exactly the
 * outermost layer Tom asked us to stub.
 *
 * Usage from a scenario:
 *
 *   const plugin = await connectFakePlugin({
 *     wsUrl: ctx.wsUrl,
 *     deviceKey: "DEVKEY_e2e_abcd...",
 *     playerName: "Tibbly Test",
 *   });
 *   await plugin.authed();
 *   const result = await plugin.sendUserMessage("What's in my bank?");
 *   expect(result.lastBalance).toBeLessThan(100_000);
 *   await plugin.close();
 *
 * The fake-plugin is single-session by design. Tests that need multiple
 * concurrent plugins create multiple instances.
 */
import {
  encodeClientMessage,
  parseServerMessage,
  type ClientToServer,
  type ServerAssistantDoneMsg,
  type ServerAuthErrorMsg,
  type ServerAuthOkMsg,
  type ServerToClient,
  type ServerToolCallRequestMsg,
} from "@osrs-llm-helper/shared-types";

import { createToolResponder, type ToolResponder, type ResponderOptions } from "./tool-responder";

export interface FakePluginOptions {
  wsUrl: string;
  deviceKey: string;
  playerName?: string;
  pluginVersion?: string;
  /** Override / extend the tool responder. */
  responder?: ToolResponder;
  /** Forwarded to the default responder when `responder` is not supplied. */
  responderOptions?: ResponderOptions;
  /** Hard ceiling on a single chat turn (matches the plugin's 120s). */
  turnTimeoutMs?: number;
  /** Hard ceiling on the auth handshake. */
  authTimeoutMs?: number;
  /** Hook fired for every inbound frame (after Zod parse). */
  onServerFrame?: (frame: ServerToClient) => void;
  /** Hook fired for every outbound frame (after Zod-encode). */
  onClientFrame?: (frame: ClientToServer) => void;
}

export interface SendUserMessageOptions {
  chatId?: string;
  allowedTools?: string[];
  snapshot?: Record<string, unknown>;
}

export interface SendUserMessageResult {
  chatId: string;
  /** Concatenated assistant text deltas. */
  assistantText: string;
  /** Final `assistant_message_done` frame. */
  done: ServerAssistantDoneMsg;
  /** Tool calls fielded during the turn, with their inputs and outputs. */
  toolCalls: Array<{ name: string; input: unknown; output: unknown }>;
}

export interface FakePlugin {
  /** Resolve once the server has accepted the auth handshake. */
  authed(): Promise<ServerAuthOkMsg>;
  /** Send a `user_message`. Resolves on `assistant_message_done`. */
  sendUserMessage(content: string, opts?: SendUserMessageOptions): Promise<SendUserMessageResult>;
  /** Send a `cancel` frame for `chatId`. */
  cancel(chatId: string): void;
  /** Send a `ping` and wait for `pong`. Useful as a liveness probe. */
  ping(): Promise<void>;
  /** Last `assistant_message_done` we saw — handy for assertions. */
  lastBalance(): number | null;
  /** Currently-installed tool responder. */
  responder: ToolResponder;
  /** Close the WS cleanly. */
  close(): Promise<void>;
  /**
   * Resolve only when the connection has closed (server-initiated or local).
   * Tests use this to assert on auth_error paths that close the socket.
   */
  closed(): Promise<{ code: number; reason: string }>;
}

const DEFAULT_AUTH_TIMEOUT_MS = 5000;
const DEFAULT_TURN_TIMEOUT_MS = 30_000;

/**
 * Open a WS, send the auth frame, and return a controller. The returned
 * promise resolves once the socket is OPEN — call `authed()` to wait for
 * the server's `auth_ok` (or `auth_error`, which rejects with a typed
 * error).
 */
export async function connectFakePlugin(options: FakePluginOptions): Promise<FakePlugin> {
  const responder = options.responder ?? createToolResponder(options.responderOptions);
  const authTimeoutMs = options.authTimeoutMs ?? DEFAULT_AUTH_TIMEOUT_MS;
  const turnTimeoutMs = options.turnTimeoutMs ?? DEFAULT_TURN_TIMEOUT_MS;

  const ws = new WebSocket(options.wsUrl);

  // Wait for the socket to be OPEN before we send anything.
  await new Promise<void>((resolve, reject) => {
    const onOpen = (): void => {
      ws.removeEventListener("error", onError);
      resolve();
    };
    const onError = (ev: Event): void => {
      ws.removeEventListener("open", onOpen);
      reject(new Error(`fake-plugin: websocket failed to open: ${String(ev)}`));
    };
    ws.addEventListener("open", onOpen, { once: true });
    ws.addEventListener("error", onError, { once: true });
  });

  let lastBalance: number | null = null;
  let authResolved = false;
  let authResolve: ((v: ServerAuthOkMsg) => void) | null = null;
  let authReject: ((err: Error) => void) | null = null;
  const authPromise = new Promise<ServerAuthOkMsg>((resolve, reject) => {
    authResolve = resolve;
    authReject = reject;
  });
  const closedPromise = new Promise<{ code: number; reason: string }>((resolve) => {
    ws.addEventListener(
      "close",
      (ev) => resolve({ code: (ev as CloseEvent).code, reason: (ev as CloseEvent).reason }),
      { once: true },
    );
  });

  interface TurnState {
    chatId: string;
    deltas: string[];
    toolCalls: Array<{ name: string; input: unknown; output: unknown }>;
    resolve: (r: SendUserMessageResult) => void;
    reject: (err: Error) => void;
    timer: ReturnType<typeof setTimeout>;
  }

  let turn: TurnState | null = null;
  let pingResolve: (() => void) | null = null;

  function emitClient(frame: ClientToServer): void {
    options.onClientFrame?.(frame);
    ws.send(encodeClientMessage(frame));
  }

  async function handleServerFrame(frame: ServerToClient): Promise<void> {
    options.onServerFrame?.(frame);
    switch (frame.type) {
      case "auth_ok": {
        authResolved = true;
        lastBalance = frame.balanceTokens;
        authResolve?.(frame);
        return;
      }
      case "auth_error": {
        const reason = (frame as ServerAuthErrorMsg).reason;
        authResolved = true;
        authReject?.(new FakePluginAuthError(reason));
        return;
      }
      case "assistant_message_delta": {
        if (turn && turn.chatId === frame.chatId) {
          turn.deltas.push(frame.delta);
        }
        return;
      }
      case "assistant_message_done": {
        lastBalance = frame.balanceTokens;
        if (turn && turn.chatId === frame.chatId) {
          const result: SendUserMessageResult = {
            chatId: frame.chatId,
            assistantText: turn.deltas.join(""),
            done: frame,
            toolCalls: turn.toolCalls,
          };
          clearTimeout(turn.timer);
          turn.resolve(result);
          turn = null;
        }
        return;
      }
      case "tool_call_request": {
        const req = frame as ServerToolCallRequestMsg;
        try {
          const output = await responder.dispatch(req.name, req.input);
          if (turn) turn.toolCalls.push({ name: req.name, input: req.input, output });
          emitClient({
            type: "tool_call_result",
            toolCallId: req.toolCallId,
            output,
          });
        } catch (err) {
          emitClient({
            type: "tool_call_result",
            toolCallId: req.toolCallId,
            errorString: err instanceof Error ? err.message : String(err),
          });
        }
        return;
      }
      case "error": {
        if (turn) {
          clearTimeout(turn.timer);
          turn.reject(new Error(`server error ${frame.code}: ${frame.message}`));
          turn = null;
        }
        if (!authResolved) {
          authReject?.(new Error(`server error before auth: ${frame.code}: ${frame.message}`));
        }
        return;
      }
      case "pong": {
        pingResolve?.();
        pingResolve = null;
        return;
      }
    }
  }

  ws.addEventListener("message", (ev: MessageEvent) => {
    let raw: unknown;
    try {
      raw = JSON.parse(String(ev.data));
    } catch {
      return; // malformed; ignore — backend never sends malformed
    }
    const parsed = parseServerMessage(raw);
    if (!parsed.ok) return; // drift logged by the optional hook
    void handleServerFrame(parsed.value);
  });

  // Send auth immediately.
  const authFrame: ClientToServer = {
    type: "auth",
    deviceKey: options.deviceKey,
    ...(options.playerName ? { playerName: options.playerName } : {}),
    pluginVersion: options.pluginVersion ?? "0.0.0-e2e",
  };
  emitClient(authFrame);

  // Race the auth promise against a timeout so a wedged server fails loud.
  const authTimer = setTimeout(() => {
    if (!authResolved) {
      authReject?.(new Error(`fake-plugin: auth handshake timed out after ${authTimeoutMs}ms`));
    }
  }, authTimeoutMs);
  authTimer.unref?.();

  const fakePlugin: FakePlugin = {
    authed() {
      return authPromise;
    },
    sendUserMessage(content, opts = {}) {
      if (turn) {
        return Promise.reject(new Error("fake-plugin: another turn already in flight"));
      }
      const chatId = opts.chatId ?? `chat_${cryptoId()}`;
      const result = new Promise<SendUserMessageResult>((resolve, reject) => {
        const timer = setTimeout(() => {
          if (turn) {
            turn.reject(new Error(`fake-plugin: turn ${chatId} timed out`));
            turn = null;
          }
        }, turnTimeoutMs);
        timer.unref?.();
        turn = { chatId, deltas: [], toolCalls: [], resolve, reject, timer };
      });
      emitClient({
        type: "user_message",
        chatId,
        content,
        ...(opts.allowedTools && opts.allowedTools.length > 0
          ? { allowedTools: opts.allowedTools }
          : {}),
        ...(opts.snapshot ? { context: { snapshot: opts.snapshot } } : {}),
      });
      return result;
    },
    cancel(chatId) {
      emitClient({ type: "cancel", chatId });
    },
    ping() {
      return new Promise<void>((resolve, reject) => {
        if (pingResolve) {
          reject(new Error("fake-plugin: a previous ping is still outstanding"));
          return;
        }
        pingResolve = resolve;
        emitClient({ type: "ping", clientUptimeMs: 0 });
      });
    },
    lastBalance() {
      return lastBalance;
    },
    responder,
    async close() {
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close(1000, "fake-plugin close()");
      }
      await closedPromise;
    },
    closed() {
      return closedPromise;
    },
  };

  return fakePlugin;
}

/**
 * Auth-side error thrown by `authed()` when the server returns `auth_error`.
 * Tests assert on `.reason` to distinguish unknown_device vs balance_exhausted.
 */
export class FakePluginAuthError extends Error {
  constructor(public readonly reason: string) {
    super(`auth_error: ${reason}`);
    this.name = "FakePluginAuthError";
  }
}

function cryptoId(): string {
  // Bun ships globalThis.crypto.randomUUID() but we want something short
  // for log-readability. 12 base32-ish chars is plenty for test scope.
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  let s = "";
  for (const b of bytes) s += b.toString(16).padStart(2, "0");
  return s;
}

/**
 * Build a long-lived dev device key that the orchestrator's pairing flow
 * accepts. Mirrors the plugin's [DeviceKey.getOrCreate] format closely
 * enough that backend validation (min length, base64url-ish charset) is
 * satisfied. NEVER persisted; regenerated per test.
 */
export function makeDeviceKey(prefix = "DEVKEY_e2e_"): string {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  let s = "";
  for (const b of bytes) s += b.toString(16).padStart(2, "0");
  return `${prefix}${s}`;
}
