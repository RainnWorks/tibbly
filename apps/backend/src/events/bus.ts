/**
 * In-process domain event bus (RAI-37).
 *
 * Tiny hand-rolled emitter — no `mitt`, no `eventemitter3`, ~50 LOC of code.
 * The interface is intentionally close to mitt's so we can swap in a NATS or
 * Kafka client later by re-implementing `publish` / `on` and keeping every
 * handler call-site untouched.
 *
 * Guarantees:
 *   - `publish` is fire-and-forget. Sync handlers run inline; async handlers
 *     are awaited so that test harnesses can `await bus.publish(...)` and
 *     observe side effects without a sleep.
 *   - Handler exceptions are caught and surfaced to `onError` (default: pino)
 *     so one broken subscriber can't take the request thread down.
 *   - `'*'` wildcard subscribers fire for every event — used by persistence
 *     and aggregation.
 */
import { nanoid } from "nanoid";

import { log } from "../lib/log";
import type { DomainEvent, EmitInput, EventType } from "./types";
import { eventSchemas } from "./types";

export type EventHandler<E extends DomainEvent = DomainEvent> = (
  event: E,
) => void | Promise<void>;

export type WildcardHandler = EventHandler<DomainEvent>;

export interface EventBus {
  publish(input: EmitInput): Promise<DomainEvent>;
  on<T extends EventType>(
    type: T,
    handler: EventHandler<Extract<DomainEvent, { type: T }>>,
  ): () => void;
  onAny(handler: WildcardHandler): () => void;
  /** Test helper: drop every subscriber. */
  clear(): void;
  /** Test helper: subscriber counts (overall + wildcard). */
  size(): { typed: number; wildcard: number };
}

export interface CreateEventBusOptions {
  /** Override clock for deterministic test timestamps. */
  now?: () => Date;
  /** Override ID generator (defaults to nanoid 21-char). */
  idFactory?: () => string;
  /** Called when a subscriber throws. Defaults to pino error log. */
  onError?: (err: unknown, event: DomainEvent) => void;
}

export function createEventBus(options: CreateEventBusOptions = {}): EventBus {
  const now = options.now ?? (() => new Date());
  const idFactory = options.idFactory ?? (() => nanoid());
  const onError =
    options.onError ??
    ((err, event) => log.error({ err, eventType: event.type }, "event-bus: subscriber threw"));

  const typed = new Map<EventType, Set<EventHandler>>();
  const wildcard = new Set<WildcardHandler>();

  async function publish(input: EmitInput): Promise<DomainEvent> {
    const schema = eventSchemas[input.type];
    // Build envelope, then validate as one shot so payload + envelope errors
    // surface together.
    const envelope = {
      id: idFactory(),
      timestamp: now(),
      type: input.type,
      userId: input.userId,
      payload: input.payload,
    };
    const event = schema.parse(envelope) as DomainEvent;

    const subs = typed.get(event.type as EventType);
    if (subs) {
      for (const handler of subs) {
        try {
          await handler(event);
        } catch (err) {
          onError(err, event);
        }
      }
    }
    for (const handler of wildcard) {
      try {
        await handler(event);
      } catch (err) {
        onError(err, event);
      }
    }
    return event;
  }

  function on<T extends EventType>(
    type: T,
    handler: EventHandler<Extract<DomainEvent, { type: T }>>,
  ): () => void {
    let set = typed.get(type);
    if (!set) {
      set = new Set();
      typed.set(type, set);
    }
    set.add(handler as EventHandler);
    return () => {
      set!.delete(handler as EventHandler);
    };
  }

  function onAny(handler: WildcardHandler): () => void {
    wildcard.add(handler);
    return () => {
      wildcard.delete(handler);
    };
  }

  function clear(): void {
    typed.clear();
    wildcard.clear();
  }

  function size(): { typed: number; wildcard: number } {
    let total = 0;
    for (const set of typed.values()) total += set.size;
    return { typed: total, wildcard: wildcard.size };
  }

  return { publish, on, onAny, clear, size };
}

/** Process-wide default bus. Tests should prefer `createEventBus()`. */
let defaultBus: EventBus | undefined;
export function getDefaultBus(): EventBus {
  if (!defaultBus) defaultBus = createEventBus();
  return defaultBus;
}

export function resetDefaultBus(): void {
  defaultBus = undefined;
}
