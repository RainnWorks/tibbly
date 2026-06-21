/**
 * Plugin ↔ backend WebSocket chat protocol (RAI-17).
 *
 * Why this lives in `shared-types`
 * --------------------------------
 * The backend authors and validates these messages, and a future TypeScript
 * plugin port (or a browser-side test client) will too. The Kotlin plugin's
 * `OutboundPayload` sealed class is the source-of-truth mirror; the names here
 * keep parity with it so a reviewer can scan one file against the other.
 *
 * Direction
 * ---------
 * `ClientToServer` — the plugin (or test harness) sends these.
 * `ServerToClient` — the backend sends these.
 *
 * Naming parity with `apps/plugin/.../OutboundPayload.kt`:
 *   ClientToServer.auth                ↔ OutboundPayload.SessionHello
 *   ClientToServer.user_message        ↔ OutboundPayload.ChatUserMessage
 *                                        + .ChatStatePreamble (folded into
 *                                          `context.snapshot`)
 *   ClientToServer.tool_call_result    ↔ OutboundPayload.ToolResult
 *   ClientToServer.cancel              ↔ OutboundPayload.ChatCancel
 *   (heartbeat, ClientToServer.ping)   ↔ OutboundPayload.SessionHeartbeat
 *
 * Every message kind has a Zod schema so the boundary is validated and so
 * downstream typed inference (`z.infer<typeof X>`) is the source of the TS
 * types — no hand-rolled interfaces that can drift from the validator.
 */
import { z } from "zod";

// ─── primitives ────────────────────────────────────────────────────────────

/** Random per-chat identifier supplied by the plugin (uuid v7 / nanoid). */
export const ChatId = z.string().min(1).max(128);

/** Plugin-supplied turn / tool-call identifiers. Tracking only — opaque. */
export const ToolCallId = z.string().min(1).max(128);

/** Opaque device key the plugin generated at first run. Never logged. */
export const DeviceKey = z
  .string()
  .min(8)
  .max(256)
  .regex(/^[A-Za-z0-9_-]+$/, "device key must be base64url-ish");

/** In-game player display name. Optional — anonymous mode replaces it. */
export const PlayerName = z.string().min(1).max(32);

/** Plugin semver string. */
export const PluginVersion = z.string().min(1).max(32);

/**
 * Compact preamble snapshot — the plugin packs whatever rows of section B
 * of DATA_DISCLOSURE.md are allowed into a JSON object. Backend receives it
 * opaquely; the LLM reads it as system context.
 */
export const ChatSnapshot = z.record(z.string(), z.unknown());

// ─── client → server ──────────────────────────────────────────────────────

/**
 * §1 — Connection handshake. Backend validates the device key, looks up the
 * paying user, returns `auth_ok` or `auth_error`.
 *
 * Mirrors OutboundPayload.SessionHello.
 */
export const ClientAuthMsg = z.object({
  type: z.literal("auth"),
  deviceKey: DeviceKey,
  /** May be null when the player is logged out of OSRS. */
  playerName: PlayerName.optional(),
  pluginVersion: PluginVersion,
});

/**
 * §2+§3 — User chat input + optional state snapshot. The plugin sends one
 * `user_message` per turn. `allowedTools` lets the plugin narrow the tool
 * surface (token-economy gating); when omitted, backend uses defaults.
 *
 * Mirrors OutboundPayload.ChatUserMessage (+ ChatStatePreamble folded in).
 */
export const ClientUserMessageMsg = z.object({
  type: z.literal("user_message"),
  chatId: ChatId,
  content: z.string().min(1).max(8000),
  /** Optional allow-list of tool names; omit to use server default. */
  allowedTools: z.array(z.string().min(1).max(64)).max(128).optional(),
  context: z
    .object({
      snapshot: ChatSnapshot.optional(),
    })
    .optional(),
});

/**
 * §4 — The plugin returns a tool-call response. `errorString` is set when
 * tool execution failed; `output` is the JSON-serializable result on success.
 *
 * Mirrors OutboundPayload.ToolResult (`isError` collapsed into errorString
 * presence — backend treats either as terminal).
 */
export const ClientToolCallResultMsg = z.object({
  type: z.literal("tool_call_result"),
  toolCallId: ToolCallId,
  output: z.unknown().optional(),
  errorString: z.string().max(8000).optional(),
});

/** §5 — User aborted the turn in flight. */
export const ClientCancelMsg = z.object({
  type: z.literal("cancel"),
  chatId: ChatId,
});

/** §6 — Heartbeat (optional, mirrors SessionHeartbeat). */
export const ClientPingMsg = z.object({
  type: z.literal("ping"),
  clientUptimeMs: z.number().int().nonnegative().optional(),
});

/* -------------------------------------------------------------------------- */
/*  Embodied companion (RAI-67)                                               */
/* -------------------------------------------------------------------------- */

/**
 * Snapshot the plugin packs alongside any companion event. Free-form (the
 * shape of "what is the player doing right now" is itself a moving
 * target). The backend treats it as opaque context for the LLM.
 *
 * Two conventional fields are documented:
 *   - `summary`     — short human-readable "right now" string the plugin
 *                     pre-renders ("standing in the Lumbridge bank with
 *                     a full inventory and 27 sharks").
 *   - `triggerType` — discriminator the prompt builder reads (e.g.
 *                     "player_death", "login", "hover_examine").
 *
 * Everything else flows through untouched.
 */
export const CompanionSnapshot = z.record(z.string(), z.unknown());

/**
 * The plugin asks the backend for a proactive line. The backend pulls
 * the companion profile + recent memories, builds the archetype prompt,
 * runs a short LLM turn, and ships back a `CompanionLine` frame.
 *
 * Mirrors `OutboundPayload.CompanionTrigger` in the plugin.
 */
export const ClientCompanionTriggerMsg = z.object({
  type: z.literal("companion_trigger"),
  /**
   * Trigger discriminator. The plugin owns the set; the backend treats
   * unknown values as generic "say something appropriate". Conventional
   * values: `player_death`, `login`, `level_up`, `hover_examine`,
   * `quest_complete`, `pet_drop`, `pb`, `bank_idle`.
   */
  triggerType: z.string().min(1).max(64),
  /** Per-trigger snapshot of game state the backend reads opaquely. */
  contextSnapshot: CompanionSnapshot,
});

/**
 * Player did something to the companion through the UI (clicked the
 * sprite, gave it a name, posted a style note, etc.). The backend mutates
 * the companion profile and answers with a normal `CompanionLine` if a
 * response is appropriate.
 *
 * Mirrors `OutboundPayload.CompanionInteractionEvent`.
 */
export const ClientCompanionInteractionEventMsg = z.object({
  type: z.literal("companion_interaction_event"),
  /**
   * One of: `clicked_companion`, `name_companion`, `style_note`, `forget`.
   * Unknown values are no-ops on the backend side.
   */
  eventType: z.string().min(1).max(64),
  /**
   * Event-specific payload. Shape depends on `eventType`. Common shapes:
   *   - `name_companion` → `{ name: "<player-chosen nickname>" }`
   *   - `style_note`     → `{ note: "<short style instruction>" }`
   *   - `forget`         → `{ scope?: "last_session" | "all" }`
   *   - `clicked_companion` → `{ snapshot: <CompanionSnapshot> }`
   */
  payload: z.record(z.string(), z.unknown()).optional(),
});

/**
 * The plugin marks a moment as memorable. The backend queues it for the
 * end-of-session extraction job rather than storing it directly — the
 * extractor decides what's worth keeping.
 *
 * Mirrors `OutboundPayload.CompanionMemoryHint`.
 */
export const ClientCompanionMemoryHintMsg = z.object({
  type: z.literal("companion_memory_hint"),
  /** One-line description of the memorable moment from the plugin's POV. */
  memorableEvent: z.string().min(1).max(400),
  /**
   * Probe ids the plugin already fired, in case the extractor wants to
   * dereference them later. Opaque string ids.
   */
  evidenceProbeIds: z.array(z.string().min(1).max(128)).max(32).default([]),
});

export const ClientToServer = z.discriminatedUnion("type", [
  ClientAuthMsg,
  ClientUserMessageMsg,
  ClientToolCallResultMsg,
  ClientCancelMsg,
  ClientPingMsg,
  ClientCompanionTriggerMsg,
  ClientCompanionInteractionEventMsg,
  ClientCompanionMemoryHintMsg,
]);
export type ClientToServer = z.infer<typeof ClientToServer>;
export type ClientAuthMsg = z.infer<typeof ClientAuthMsg>;
export type ClientUserMessageMsg = z.infer<typeof ClientUserMessageMsg>;
export type ClientToolCallResultMsg = z.infer<typeof ClientToolCallResultMsg>;
export type ClientCancelMsg = z.infer<typeof ClientCancelMsg>;
export type ClientPingMsg = z.infer<typeof ClientPingMsg>;
export type ClientCompanionTriggerMsg = z.infer<typeof ClientCompanionTriggerMsg>;
export type ClientCompanionInteractionEventMsg = z.infer<
  typeof ClientCompanionInteractionEventMsg
>;
export type ClientCompanionMemoryHintMsg = z.infer<typeof ClientCompanionMemoryHintMsg>;
export type CompanionSnapshot = z.infer<typeof CompanionSnapshot>;

// ─── server → client ──────────────────────────────────────────────────────

/**
 * Billing tier label — gates model selection (Hobbyist → Haiku, Pro → Sonnet,
 * Iron → Opus). Aligns with docs/product/PRICING.md tiers.
 */
export const Tier = z.enum(["hobbyist", "pro", "iron"]);
export type Tier = z.infer<typeof Tier>;

export const ServerAuthOkMsg = z.object({
  type: z.literal("auth_ok"),
  userId: z.string().min(1),
  tier: Tier,
  /** Remaining token budget in raw token count. */
  balanceTokens: z.number().int().nonnegative(),
});

export const ServerAuthErrorReason = z.enum([
  "unknown_device",
  "no_active_subscription",
  "balance_exhausted",
  "version_unsupported",
  "rate_limited",
  "malformed",
]);
export type ServerAuthErrorReason = z.infer<typeof ServerAuthErrorReason>;

export const ServerAuthErrorMsg = z.object({
  type: z.literal("auth_error"),
  reason: ServerAuthErrorReason,
});

/**
 * Backend asks the plugin to execute a tool. The plugin replies with
 * `tool_call_result` keyed by `toolCallId`.
 */
export const ServerToolCallRequestMsg = z.object({
  type: z.literal("tool_call_request"),
  toolCallId: ToolCallId,
  name: z.string().min(1).max(64),
  input: z.unknown(),
});

/** Streamed text delta for the assistant's reply. */
export const ServerAssistantDeltaMsg = z.object({
  type: z.literal("assistant_message_delta"),
  chatId: ChatId,
  delta: z.string(),
});

/**
 * Turn finished. Token + cost metering for the dashboard / Stripe meter.
 * `costMicroUsd` = USD * 1_000_000 to stay integer-safe.
 */
export const ServerAssistantDoneMsg = z.object({
  type: z.literal("assistant_message_done"),
  chatId: ChatId,
  promptTokens: z.number().int().nonnegative(),
  completionTokens: z.number().int().nonnegative(),
  costMicroUsd: z.number().int().nonnegative(),
  balanceTokens: z.number().int().nonnegative(),
});

export const ServerErrorCode = z.enum([
  "unauthenticated",
  "malformed",
  "rate_limited",
  "balance_exhausted",
  "tool_timeout",
  "upstream_error",
  "internal",
]);
export type ServerErrorCode = z.infer<typeof ServerErrorCode>;

export const ServerErrorMsg = z.object({
  type: z.literal("error"),
  code: ServerErrorCode,
  message: z.string().max(2000),
});

/** Heartbeat echo. */
export const ServerPongMsg = z.object({
  type: z.literal("pong"),
});

/**
 * A single proactive companion utterance (RAI-67). One frame = one line
 * the plugin renders in the speech bubble above the sprite. We do not
 * stream this one chunk at a time — proactive lines are 60-150 tokens out
 * and shipping them as a single frame keeps the plugin renderer simple.
 *
 * `sourceTrigger` echoes the trigger the plugin sent so the renderer can
 * choose the right anchor animation (point at NPC, head-tilt, etc.).
 */
export const ServerCompanionLineMsg = z.object({
  type: z.literal("companion_line"),
  text: z.string().min(1).max(2000),
  sourceTrigger: z.string().min(1).max(64).optional(),
  /** Final token + cost telemetry so the dashboard meter stays accurate. */
  promptTokens: z.number().int().nonnegative().default(0),
  completionTokens: z.number().int().nonnegative().default(0),
  costMicroUsd: z.number().int().nonnegative().default(0),
  balanceTokens: z.number().int().nonnegative().default(0),
});

/**
 * Companion event acknowledgement (RAI-67). Sent in response to
 * `companion_interaction_event` or `companion_memory_hint` when the
 * backend mutated state. Lets the plugin update its local UI (e.g. show
 * "name saved" tick) without round-tripping a full line.
 */
export const ServerCompanionAckMsg = z.object({
  type: z.literal("companion_ack"),
  /** Echoes the event that produced the ack. */
  eventType: z.string().min(1).max(64),
  ok: z.boolean(),
  /** Optional short note ("nickname saved", "forgot last session", etc.). */
  note: z.string().max(400).optional(),
});

export const ServerToClient = z.discriminatedUnion("type", [
  ServerAuthOkMsg,
  ServerAuthErrorMsg,
  ServerToolCallRequestMsg,
  ServerAssistantDeltaMsg,
  ServerAssistantDoneMsg,
  ServerErrorMsg,
  ServerPongMsg,
  ServerCompanionLineMsg,
  ServerCompanionAckMsg,
]);
export type ServerToClient = z.infer<typeof ServerToClient>;
export type ServerAuthOkMsg = z.infer<typeof ServerAuthOkMsg>;
export type ServerAuthErrorMsg = z.infer<typeof ServerAuthErrorMsg>;
export type ServerToolCallRequestMsg = z.infer<typeof ServerToolCallRequestMsg>;
export type ServerAssistantDeltaMsg = z.infer<typeof ServerAssistantDeltaMsg>;
export type ServerAssistantDoneMsg = z.infer<typeof ServerAssistantDoneMsg>;
export type ServerErrorMsg = z.infer<typeof ServerErrorMsg>;
export type ServerPongMsg = z.infer<typeof ServerPongMsg>;
export type ServerCompanionLineMsg = z.infer<typeof ServerCompanionLineMsg>;
export type ServerCompanionAckMsg = z.infer<typeof ServerCompanionAckMsg>;

// ─── helpers ──────────────────────────────────────────────────────────────

/**
 * Lenient parse → result object. Wire-side use: backend wraps in this so a
 * malformed plugin message becomes an `error` frame instead of a thrown
 * exception that drops the socket.
 */
export type ParseResult<T> =
  | { ok: true; value: T }
  | { ok: false; error: string };

export function parseClientMessage(raw: unknown): ParseResult<ClientToServer> {
  const result = ClientToServer.safeParse(raw);
  if (result.success) return { ok: true, value: result.data };
  return { ok: false, error: result.error.message };
}

export function parseServerMessage(raw: unknown): ParseResult<ServerToClient> {
  const result = ServerToClient.safeParse(raw);
  if (result.success) return { ok: true, value: result.data };
  return { ok: false, error: result.error.message };
}

/**
 * Encode a server message to a stable JSON string. Centralized so we can add
 * sampling / tracing IDs in one place later.
 */
export function encodeServerMessage(msg: ServerToClient): string {
  return JSON.stringify(msg);
}

export function encodeClientMessage(msg: ClientToServer): string {
  return JSON.stringify(msg);
}
