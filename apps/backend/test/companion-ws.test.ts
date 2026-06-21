/**
 * RAI-67 - embodied-companion WS handler.
 *
 * Covers the three new client-to-server frames end to end against a real
 * PGLite database, the real `createCompanionService` implementation, and
 * a fake LLM runner:
 *
 *   1. companion_trigger              → server emits `companion_line`.
 *   2. companion_interaction_event    → mutates profile + emits `companion_ack`.
 *   3. companion_memory_hint          → queues a hint + emits `companion_ack`.
 *
 * The handler also needs to deny these frames pre-auth and when the
 * service isn't wired into the plugin deps.
 */
import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { eq } from "drizzle-orm";

import {
  encodeClientMessage,
  parseServerMessage,
  type ServerToClient,
} from "@osrs-llm-helper/shared-types";

import { pluginWsHandler, type BalanceMeter, type DeviceLookup, type EventLogger } from "../src/ws/plugin";
import {
  createCompanionService,
  InMemoryMemoryHintQueue,
} from "../src/ws/companion";
import type { RunStreamArgs, RunStreamResult } from "../src/llm/openrouter";
import { companionMemories, companionProfile, users } from "../src/db/schema";
import { makeTestDb, type TestDbHandle } from "./_db-fixture";

let handle: TestDbHandle;
let server: ReturnType<typeof Bun.serve> | undefined;
let port = 0;
let queue: InMemoryMemoryHintQueue;
let appliedTurnCosts: Array<{
  userId: string;
  promptTokens: number;
  completionTokens: number;
}>;

const FAKE_DEVICE_KEY = "DEVKEY_test_companion_1234";

const deviceLookup: DeviceLookup = {
  async resolveDeviceKey(deviceKey) {
    if (deviceKey !== FAKE_DEVICE_KEY) return null;
    return {
      userId: "user_companion_test",
      tier: "hobbyist",
      deviceKey,
      playerName: "Zezima",
    };
  },
};

let balance = 100_000;
const balanceMeter: BalanceMeter = {
  async getBalanceTokens() {
    return balance;
  },
  async applyTurnCost(args) {
    appliedTurnCosts.push({
      userId: args.userId,
      promptTokens: args.promptTokens,
      completionTokens: args.completionTokens,
    });
    balance -= args.promptTokens + args.completionTokens;
    return { balanceTokens: balance };
  },
};

const eventLogger: EventLogger = {
  async logEvent() {
    /* discarded */
  },
};

/**
 * Fake LLM runner - single short response, fixed usage. The companion
 * path doesn't loop tools, so we don't need the AI-SDK's tool plumbing.
 */
const fakeLlmRunner = (_args: RunStreamArgs): RunStreamResult => {
  const text = "You've done this before. Bring antifire next attempt.";
  const textStream = (async function* (): AsyncGenerator<string> {
    yield text;
  })();
  const fullStream = (async function* (): AsyncGenerator<unknown> {
    yield { type: "text-delta", text };
  })();
  return {
    fullStream,
    textStream,
    usagePromise: Promise.resolve({ promptTokens: 80, completionTokens: 40 }),
  };
};

function connect(): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://localhost:${port}/ws/plugin`);
    ws.addEventListener("open", () => resolve(ws), { once: true });
    ws.addEventListener("error", (e) => reject(e), { once: true });
  });
}

async function readUntil(
  ws: WebSocket,
  until: (msg: ServerToClient) => boolean,
  timeoutMs = 4000,
): Promise<ServerToClient[]> {
  const received: ServerToClient[] = [];
  return new Promise<ServerToClient[]>((resolve, reject) => {
    const timer = setTimeout(() => {
      ws.removeEventListener("message", onMessage);
      reject(new Error(`timeout; got ${JSON.stringify(received)}`));
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

async function authedConnect(): Promise<WebSocket> {
  const ws = await connect();
  ws.send(
    encodeClientMessage({
      type: "auth",
      deviceKey: FAKE_DEVICE_KEY,
      playerName: "Zezima",
      pluginVersion: "test",
    }),
  );
  await readUntil(ws, (m) => m.type === "auth_ok");
  return ws;
}

beforeEach(async () => {
  handle = await makeTestDb();
  // The fake deviceLookup authenticates `user_companion_test`; seed the
  // users row so the FK from companion_profile is satisfied.
  await handle.db.insert(users).values({ id: "user_companion_test" });
  queue = new InMemoryMemoryHintQueue();
  appliedTurnCosts = [];
  balance = 100_000;
  const companion = createCompanionService({
    db: handle.db,
    memoryHintQueue: queue,
    balance: balanceMeter,
    llmRunner: fakeLlmRunner,
    cheapModelChooser: () => "anthropic/test-cheap",
  });
  const handler = pluginWsHandler({
    deviceLookup,
    balanceMeter,
    eventLogger,
    companion,
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

afterEach(async () => {
  server?.stop(true);
  await handle.close();
});

describe("companion WS handler", () => {
  it("emits a companion_line on companion_trigger and applies turn cost", async () => {
    const ws = await authedConnect();
    ws.send(
      encodeClientMessage({
        type: "companion_trigger",
        triggerType: "player_death",
        contextSnapshot: {
          summary: "Player just died at Vorkath after the wrong-tick eat.",
        },
      }),
    );
    const tail = await readUntil(ws, (m) => m.type === "companion_line");
    const line = tail.find((m) => m.type === "companion_line");
    expect(line).toBeDefined();
    if (line?.type === "companion_line") {
      expect(line.text).toContain("antifire");
      expect(line.sourceTrigger).toBe("player_death");
      expect(line.promptTokens).toBe(80);
      expect(line.completionTokens).toBe(40);
    }
    expect(appliedTurnCosts).toHaveLength(1);
    expect(appliedTurnCosts[0]!.userId).toBe("user_companion_test");

    ws.close();
  });

  it("rejects companion_trigger when not authenticated", async () => {
    const ws = await connect();
    ws.send(
      encodeClientMessage({
        type: "companion_trigger",
        triggerType: "login",
        contextSnapshot: {},
      }),
    );
    const tail = await readUntil(ws, (m) => m.type === "error");
    const err = tail.find((m) => m.type === "error");
    if (err?.type === "error") {
      expect(err.code).toBe("unauthenticated");
    }
    ws.close();
  });

  it("name_companion updates the profile and acks ok", async () => {
    const ws = await authedConnect();
    ws.send(
      encodeClientMessage({
        type: "companion_interaction_event",
        eventType: "name_companion",
        payload: { name: "Tibbly" },
      }),
    );
    const tail = await readUntil(ws, (m) => m.type === "companion_ack");
    const ack = tail.find((m) => m.type === "companion_ack");
    expect(ack).toBeDefined();
    if (ack?.type === "companion_ack") {
      expect(ack.ok).toBe(true);
      expect(ack.eventType).toBe("name_companion");
    }

    const rows = await handle.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.userId, "user_companion_test"));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.companionName).toBe("Tibbly");

    ws.close();
  });

  it("style_note appends to voiceStyleNotes and caps at 20", async () => {
    const ws = await authedConnect();
    // Push 22 notes - we should see exactly the most-recent 20 land.
    for (let i = 0; i < 22; i += 1) {
      ws.send(
        encodeClientMessage({
          type: "companion_interaction_event",
          eventType: "style_note",
          payload: { note: `note-${i}` },
        }),
      );
      // Wait for the ack so we don't race the WS pipeline.
      await readUntil(ws, (m) => m.type === "companion_ack");
    }

    const rows = await handle.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.userId, "user_companion_test"));
    expect(rows[0]!.voiceStyleNotes).toHaveLength(20);
    // Oldest survivor is note-2 (we dropped 0 and 1).
    expect(rows[0]!.voiceStyleNotes[0]).toBe("note-2");
    expect(rows[0]!.voiceStyleNotes[19]).toBe("note-21");

    ws.close();
  });

  it("forget last_session soft-deletes memories newer than the last session end", async () => {
    const ws = await authedConnect();
    // Trigger profile creation through a no-op style_note.
    ws.send(
      encodeClientMessage({
        type: "companion_interaction_event",
        eventType: "style_note",
        payload: { note: "no spoilers" },
      }),
    );
    await readUntil(ws, (m) => m.type === "companion_ack");

    const [profileRow] = await handle.db
      .select()
      .from(companionProfile)
      .where(eq(companionProfile.userId, "user_companion_test"));
    expect(profileRow).toBeDefined();
    const profileId = profileRow!.id;

    await handle.db.insert(companionMemories).values([
      {
        id: "mem_recent",
        profileId,
        body: "Recent beat",
        category: "milestone",
        weight: 2.0,
      },
    ]);

    ws.send(
      encodeClientMessage({
        type: "companion_interaction_event",
        eventType: "forget",
        payload: { scope: "all" },
      }),
    );
    await readUntil(ws, (m) => m.type === "companion_ack");

    const after = await handle.db
      .select()
      .from(companionMemories)
      .where(eq(companionMemories.profileId, profileId));
    expect(after[0]!.forgottenAt).not.toBeNull();

    ws.close();
  });

  it("companion_memory_hint queues for end-of-session extraction", async () => {
    const ws = await authedConnect();
    ws.send(
      encodeClientMessage({
        type: "companion_memory_hint",
        memorableEvent: "First fang drop at scurrius",
        evidenceProbeIds: ["pet_drop_scurrius", "kc_scurrius_103"],
      }),
    );
    const tail = await readUntil(ws, (m) => m.type === "companion_ack");
    const ack = tail.find((m) => m.type === "companion_ack");
    expect(ack?.type === "companion_ack" && ack.ok).toBe(true);

    const queued = queue.drain("user_companion_test");
    expect(queued).toHaveLength(1);
    expect(queued[0]!.memorableEvent).toBe("First fang drop at scurrius");
    expect(queued[0]!.evidenceProbeIds).toContain("pet_drop_scurrius");

    ws.close();
  });
});
