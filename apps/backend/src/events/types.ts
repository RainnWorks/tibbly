/**
 * Sealed Zod schemas for every event in the analytics taxonomy (RAI-37).
 *
 * Each event is published on the in-process bus as:
 *
 *   { id, timestamp, type, userId?, payload }
 *
 * Schemas double as runtime validators (so handlers and SQL can trust the
 * shape) AND as the single source of truth for the documented taxonomy.
 *
 * Naming rules:
 *   - Domain.subject.verb  e.g. `chat.message.sent`
 *   - Past-tense verbs (we log what already happened)
 *   - `userId` lives on the envelope, not on payload, when available.
 *
 * If you add a new event, also add it to the `EventTypeLiteral` union below
 * AND to `eventSchemaByType` — the compiler will complain at every other
 * call site until you do.
 */
import { z } from "zod";

/* -------------------------------------------------------------------------- */
/*  Envelope                                                                  */
/* -------------------------------------------------------------------------- */

const eventEnvelopeBase = z.object({
  id: z.string().min(1),
  timestamp: z.coerce.date(),
  userId: z.string().min(1).optional(),
});

/* -------------------------------------------------------------------------- */
/*  Payload schemas — one per event type                                      */
/* -------------------------------------------------------------------------- */

const authPairingRequestedPayload = z.object({
  deviceKey: z.string().min(1),
  playerName: z.string().min(1).optional(),
});

const authPairingClaimedPayload = z.object({
  userId: z.string().min(1),
  deviceId: z.string().min(1),
});

const chatMessageSentPayload = z.object({
  chatId: z.string().min(1),
  messageId: z.string().min(1),
  tokens: z.object({
    in: z.number().int().nonnegative(),
    out: z.number().int().nonnegative(),
  }),
  model: z.string().min(1),
  costMicroUsd: z.number().int().nonnegative().default(0),
});

const chatToolCallStartedPayload = z.object({
  chatId: z.string().min(1),
  messageId: z.string().min(1),
  toolName: z.string().min(1),
  family: z.string().min(1),
  inputBytes: z.number().int().nonnegative(),
});

const chatToolCallCompletedPayload = chatToolCallStartedPayload.extend({
  durationMs: z.number().int().nonnegative(),
  outputBytes: z.number().int().nonnegative(),
  status: z.enum(["ok", "error", "timeout"]),
});

const chatToolCallFailedPayload = chatToolCallStartedPayload.extend({
  durationMs: z.number().int().nonnegative(),
  error: z.string(),
});

const chatCapHitPayload = z.object({
  chatId: z.string().min(1),
  balanceBefore: z.number().int(),
});

const billingSubscriptionCreatedPayload = z.object({
  tier: z.enum(["hobbyist", "pro", "iron"]),
  monthlyQuota: z.number().int().nonnegative(),
});

const billingSubscriptionRenewedPayload = z.object({
  tier: z.enum(["hobbyist", "pro", "iron"]),
});

const billingSubscriptionCancelledPayload = z.object({
  tier: z.enum(["hobbyist", "pro", "iron"]),
  reason: z.string().optional(),
});

const billingBalanceDecrementedPayload = z.object({
  amount: z.number().int().positive(),
  model: z.string().min(1),
  newBalance: z.number().int(),
});

const featureToolFamilyExposedPayload = z.object({
  family: z.string().min(1),
  reason: z.enum(["keyword", "meta-tool", "default"]),
});

const featureToolFamilyEnabledViaMetaToolPayload = z.object({
  family: z.string().min(1),
});

const funnelPluginInstalledPayload = z.object({
  deviceKey: z.string().min(1),
  pluginVersion: z.string().min(1),
});

const funnelConsentAcceptedPayload = z.object({
  versions: z
    .object({
      privacy: z.string().optional(),
      terms: z.string().optional(),
    })
    .partial()
    .optional(),
});

const funnelFirstMessagePayload = z.object({
  chatId: z.string().min(1),
});

const funnelFirstPaidPayload = z.object({
  tier: z.enum(["hobbyist", "pro", "iron"]),
});

const errorOpenrouterPayload = z.object({
  model: z.string().min(1),
  status: z.number().int(),
  ms: z.number().int().nonnegative(),
  message: z.string().optional(),
});

const errorPluginDisconnectPayload = z.object({
  deviceId: z.string().min(1),
  reason: z.string(),
});

/* -------------------------------------------------------------------------- */
/*  Per-type envelopes                                                        */
/* -------------------------------------------------------------------------- */

function envelope<T extends z.ZodTypeAny>(type: string, payload: T) {
  return eventEnvelopeBase.extend({
    type: z.literal(type),
    payload,
  });
}

export const eventSchemas = {
  "auth.pairing.requested": envelope("auth.pairing.requested", authPairingRequestedPayload),
  "auth.pairing.claimed": envelope("auth.pairing.claimed", authPairingClaimedPayload),
  "chat.message.sent": envelope("chat.message.sent", chatMessageSentPayload),
  "chat.tool_call.started": envelope("chat.tool_call.started", chatToolCallStartedPayload),
  "chat.tool_call.completed": envelope("chat.tool_call.completed", chatToolCallCompletedPayload),
  "chat.tool_call.failed": envelope("chat.tool_call.failed", chatToolCallFailedPayload),
  "chat.cap_hit": envelope("chat.cap_hit", chatCapHitPayload),
  "billing.subscription.created": envelope(
    "billing.subscription.created",
    billingSubscriptionCreatedPayload,
  ),
  "billing.subscription.renewed": envelope(
    "billing.subscription.renewed",
    billingSubscriptionRenewedPayload,
  ),
  "billing.subscription.cancelled": envelope(
    "billing.subscription.cancelled",
    billingSubscriptionCancelledPayload,
  ),
  "billing.balance.decremented": envelope(
    "billing.balance.decremented",
    billingBalanceDecrementedPayload,
  ),
  "feature.tool_family.exposed": envelope(
    "feature.tool_family.exposed",
    featureToolFamilyExposedPayload,
  ),
  "feature.tool_family.enabled_via_meta_tool": envelope(
    "feature.tool_family.enabled_via_meta_tool",
    featureToolFamilyEnabledViaMetaToolPayload,
  ),
  "funnel.plugin.installed": envelope("funnel.plugin.installed", funnelPluginInstalledPayload),
  "funnel.consent.accepted": envelope("funnel.consent.accepted", funnelConsentAcceptedPayload),
  "funnel.first_message": envelope("funnel.first_message", funnelFirstMessagePayload),
  "funnel.first_paid": envelope("funnel.first_paid", funnelFirstPaidPayload),
  "error.openrouter": envelope("error.openrouter", errorOpenrouterPayload),
  "error.plugin_disconnect": envelope("error.plugin_disconnect", errorPluginDisconnectPayload),
} as const;

export type EventType = keyof typeof eventSchemas;

export const EVENT_TYPES: readonly EventType[] = Object.keys(eventSchemas) as EventType[];

/** Each entry's inferred shape — used to type the bus. */
export type DomainEvent = {
  [K in EventType]: z.infer<(typeof eventSchemas)[K]>;
}[EventType];

/** Shape of an event keyed by its specific type — used in handlers. */
export type DomainEventOf<T extends EventType> = z.infer<(typeof eventSchemas)[T]>;

/** Pre-envelope input — caller supplies type + payload + optional userId. */
export type EmitInput =
  | {
      [K in EventType]: {
        type: K;
        userId?: string;
        payload: z.input<(typeof eventSchemas)[K]>["payload"];
      };
    }[EventType];

/* -------------------------------------------------------------------------- */
/*  Discriminated-union parser — accepts any DomainEvent and returns the      */
/*  validated, typed shape. Useful when replaying from the events table.      */
/* -------------------------------------------------------------------------- */

export function parseEvent(raw: unknown): DomainEvent {
  // Cheap pre-flight so we can give a useful error when the type literal is wrong.
  const typed = z
    .object({ type: z.string() })
    .passthrough()
    .parse(raw);
  const schema = eventSchemas[typed.type as EventType];
  if (!schema) {
    throw new Error(`unknown event type: ${typed.type}`);
  }
  return schema.parse(raw) as DomainEvent;
}

/** Family classification for chat tool calls — extend as new families land. */
export const TOOL_FAMILIES = [
  "core",
  "bank",
  "inventory",
  "quest",
  "skill",
  "world",
  "combat",
  "grandexchange",
  "social",
  "meta",
  "unknown",
] as const;
export type ToolFamily = (typeof TOOL_FAMILIES)[number];
