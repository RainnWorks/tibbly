/**
 * RAI-17 round-trip test for the plugin↔backend WebSocket protocol.
 *
 * What we cover end-to-end:
 *   1. The client sends `auth` and gets `auth_ok` back.
 *   2. The client sends `user_message`.
 *   3. The backend (via a fake LLM runner) emits a tool call — handler turns
 *      that into a `tool_call_request` on the wire.
 *   4. The client replies with `tool_call_result`.
 *   5. The backend streams `assistant_message_delta` then
 *      `assistant_message_done` with token + cost metering.
 *
 * The fake LLM avoids any network hit. It simulates exactly one tool call
 * followed by streaming "Welcome back, K!" so we can assert on the deltas.
 */
import { afterAll, beforeAll, describe, expect, it } from "bun:test";
import { z } from "zod";

import {
  encodeClientMessage,
  parseServerMessage,
  type ServerToClient,
} from "@osrs-llm-helper/shared-types";

import {
  pluginWsHandler,
  type BalanceMeter,
  type DeviceLookup,
  type EventLogger,
} from "../src/ws/plugin";
import type { RemoteToolSpec, RunStreamArgs, RunStreamResult } from "../src/llm/openrouter";

// ─── fakes ────────────────────────────────────────────────────────────────

const FAKE_DEVICE_KEY = "DEVKEY_test_1234567890";

const deviceLookup: DeviceLookup = {
  async resolveDeviceKey(deviceKey) {
    if (deviceKey !== FAKE_DEVICE_KEY) return null;
    return {
      userId: "user_test",
      tier: "pro",
      deviceKey,
      playerName: null,
    };
  },
};

let balance = 100_000;
const balanceCalls: Array<{ promptTokens: number; completionTokens: number; costMicroUsd: number }> = [];
const balanceMeter: BalanceMeter = {
  async getBalanceTokens() {
    return balance;
  },
  async applyTurnCost({ promptTokens, completionTokens, costMicroUsd }) {
    balanceCalls.push({ promptTokens, completionTokens, costMicroUsd });
    balance -= promptTokens + completionTokens;
    return { balanceTokens: balance };
  },
};

const events: Array<{ kind: string }> = [];
const eventLogger: EventLogger = {
  async logEvent({ kind }) {
    events.push({ kind });
  },
};

const tools: RemoteToolSpec[] = [
  {
    name: "stats",
    description: "Return the player's combat and skill stats.",
    inputSchema: z.object({}).passthrough(),
  },
];

/**
 * Fake LLM runner — simulates the AI SDK's `streamText` result. It:
 *   1. Invokes the `stats` tool once (via the dispatcher in the supplied
 *      `tools`) so the WS handler ships a `tool_call_request` to the client.
 *   2. Streams three text deltas.
 *   3. Resolves `usagePromise` with a synthetic token count.
 */
const fakeLlmRunner = (args: RunStreamArgs): RunStreamResult => {
  // Resolve the tool call by invoking its `execute` ourselves. The AI SDK
  // normally does this internally; we shim it.
  const statsTool = args.tools?.["stats"];
  const toolPromise = statsTool
    ? Promise.resolve(statsTool.execute?.({}, { messages: [], toolCallId: "synthetic" } as never))
    : Promise.resolve(undefined);

  const textStream = (async function* (): AsyncGenerator<string> {
    // Wait for the tool round-trip so the delta order matches reality.
    await toolPromise;
    yield "Welcome ";
    yield "back, ";
    yield "K!";
  })();

  // Build a `fullStream` shim — the WS handler doesn't actually consume it
  // for tool events (the AI SDK fires `execute` directly), but the type
  // surface requires it. We surface the same deltas wrapped in
  // `{ type: "text-delta", text }` for completeness.
  const fullStream = (async function* (): AsyncGenerator<unknown> {
    await toolPromise;
    for (const t of ["Welcome ", "back, ", "K!"]) {
      yield { type: "text-delta", text: t };
    }
  })();

  return {
    fullStream,
    textStream,
    usagePromise: Promise.resolve({ promptTokens: 123, completionTokens: 17 }),
  };
};

// ─── server boot ─────────────────────────────────────────────────────────

let port = 0;
let server: ReturnType<typeof Bun.serve> | undefined;

beforeAll(() => {
  const handler = pluginWsHandler({
    deviceLookup,
    balanceMeter,
    eventLogger,
    tools,
    llmRunner: fakeLlmRunner,
  });

  server = Bun.serve({
    port: 0,
    fetch(req, srv) {
      if (new URL(req.url).pathname === "/ws/plugin") {
        const ok = srv.upgrade(req, { data: handler.makeSocketData() });
        return ok ? undefined : new Response("upgrade failed", { status: 400 });
      }
      return new Response("not found", { status: 404 });
    },
    websocket: handler.websocket,
  });
  port = server.port;
});

afterAll(() => {
  server?.stop(true);
});

// ─── helpers ─────────────────────────────────────────────────────────────

function connect(): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://localhost:${port}/ws/plugin`);
    ws.addEventListener("open", () => resolve(ws), { once: true });
    ws.addEventListener("error", (e) => reject(e), { once: true });
  });
}

/** Read messages off the socket until `until(msg)` returns true. */
async function readUntil(
  ws: WebSocket,
  until: (msg: ServerToClient) => boolean,
  timeoutMs = 4000,
): Promise<ServerToClient[]> {
  const received: ServerToClient[] = [];
  return new Promise<ServerToClient[]>((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.removeEventListener("message", onMessage);
      reject(new Error(`timeout waiting for predicate; got ${JSON.stringify(received)}`));
    }, timeoutMs);

    function onMessage(ev: MessageEvent): void {
      let parsed: unknown;
      try {
        parsed = JSON.parse(ev.data as string);
      } catch {
        return;
      }
      const r = parseServerMessage(parsed);
      if (!r.ok) return;
      received.push(r.value);
      if (until(r.value)) {
        clearTimeout(timer);
        ws.removeEventListener("message", onMessage);
        resolve(received);
      }
    }
    ws.addEventListener("message", onMessage);
  });
}

// ─── the test ────────────────────────────────────────────────────────────

describe("plugin WS protocol", () => {
  it("auth → user_message → tool round-trip → assistant_message_done", async () => {
    const ws = await connect();

    ws.send(
      encodeClientMessage({
        type: "auth",
        deviceKey: FAKE_DEVICE_KEY,
        playerName: "Zezima",
        pluginVersion: "0.0.0-test",
      }),
    );

    const afterAuth = await readUntil(ws, (m) => m.type === "auth_ok");
    const authOk = afterAuth.find((m) => m.type === "auth_ok");
    expect(authOk).toBeDefined();
    expect(authOk!.type).toBe("auth_ok");
    if (authOk?.type === "auth_ok") {
      expect(authOk.userId).toBe("user_test");
      expect(authOk.tier).toBe("pro");
      expect(authOk.balanceTokens).toBeGreaterThan(0);
    }

    // Send the user message and immediately wait for a tool_call_request.
    ws.send(
      encodeClientMessage({
        type: "user_message",
        chatId: "chat-1",
        content: "What are my stats?",
        context: { snapshot: { hp: 99 } },
      }),
    );

    const beforeTool = await readUntil(ws, (m) => m.type === "tool_call_request");
    const toolReq = beforeTool.find((m) => m.type === "tool_call_request");
    expect(toolReq).toBeDefined();
    if (toolReq?.type === "tool_call_request") {
      expect(toolReq.name).toBe("stats");
      // Reply to the tool call.
      ws.send(
        encodeClientMessage({
          type: "tool_call_result",
          toolCallId: toolReq.toolCallId,
          output: { hp: 99, prayer: 99 },
        }),
      );
    }

    // Now consume deltas until the turn is done.
    const tail = await readUntil(ws, (m) => m.type === "assistant_message_done");
    const deltas = tail
      .filter((m) => m.type === "assistant_message_delta")
      .map((m) => (m.type === "assistant_message_delta" ? m.delta : ""));
    expect(deltas.join("")).toBe("Welcome back, K!");

    const done = tail.find((m) => m.type === "assistant_message_done");
    expect(done).toBeDefined();
    if (done?.type === "assistant_message_done") {
      expect(done.chatId).toBe("chat-1");
      expect(done.promptTokens).toBe(123);
      expect(done.completionTokens).toBe(17);
      // pro tier → Sonnet. 123 * 3 + 17 * 15 = 624 µUSD
      expect(done.costMicroUsd).toBe(624);
      expect(done.balanceTokens).toBe(100_000 - 123 - 17);
    }

    expect(balanceCalls).toHaveLength(1);
    expect(events.some((e) => e.kind === "ws.auth_ok")).toBe(true);
    expect(events.some((e) => e.kind === "ws.user_message")).toBe(true);
    expect(events.some((e) => e.kind === "ws.assistant_message_done")).toBe(true);

    ws.close();
  });

  it("rejects unknown device keys with auth_error", async () => {
    const ws = await connect();
    ws.send(
      encodeClientMessage({
        type: "auth",
        deviceKey: "DEVKEY_unknown_aaaa",
        pluginVersion: "0.0.0-test",
      }),
    );
    const msgs = await readUntil(ws, (m) => m.type === "auth_error" || m.type === "auth_ok");
    const authErr = msgs.find((m) => m.type === "auth_error");
    expect(authErr).toBeDefined();
    if (authErr?.type === "auth_error") {
      expect(authErr.reason).toBe("unknown_device");
    }
    ws.close();
  });

  it("returns malformed error for unparsable frames", async () => {
    const ws = await connect();
    // Send malformed JSON.
    ws.send("not-json");
    const msgs = await readUntil(ws, (m) => m.type === "error");
    const err = msgs.find((m) => m.type === "error");
    expect(err).toBeDefined();
    if (err?.type === "error") {
      expect(err.code).toBe("malformed");
    }
    ws.close();
  });

  it("rejects user_message before auth", async () => {
    const ws = await connect();
    ws.send(
      encodeClientMessage({
        type: "user_message",
        chatId: "chat-no-auth",
        content: "hi",
      }),
    );
    const msgs = await readUntil(ws, (m) => m.type === "error");
    const err = msgs.find((m) => m.type === "error");
    expect(err).toBeDefined();
    if (err?.type === "error") {
      expect(err.code).toBe("unauthenticated");
    }
    ws.close();
  });

  it("responds to ping with pong", async () => {
    const ws = await connect();
    ws.send(encodeClientMessage({ type: "ping", clientUptimeMs: 0 }));
    const msgs = await readUntil(ws, (m) => m.type === "pong");
    expect(msgs.some((m) => m.type === "pong")).toBe(true);
    ws.close();
  });
});
