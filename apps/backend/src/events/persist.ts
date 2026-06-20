/**
 * Persist domain events to the `events` Drizzle table.
 *
 * Subscribes to `bus.onAny` and writes each event as-is. The payload column
 * is jsonb so analytics can `payload ->> 'toolName'` without a column-per-
 * event-type explosion.
 *
 * We use a single connection from `getDb()` by default; tests pass an
 * explicit `db` to keep instances isolated. Failures are logged but never
 * thrown — analytics must never break the request path.
 */
import { log } from "../lib/log";
import { events as eventsTable } from "../db/schema";
import type { DbClient } from "../db/client";
import { getDb } from "../db/client";
import type { DomainEvent, EventType } from "./types";
import type { EventBus } from "./bus";

export interface PersistOptions {
  /** Drizzle client. Defaults to the process singleton. */
  db?: DbClient;
  /** Override for tests that want to inspect the write path. */
  onError?: (err: unknown, event: DomainEvent) => void;
}

export function attachEventPersister(bus: EventBus, options: PersistOptions = {}): () => void {
  const db = options.db ?? getDb().db;
  const onError =
    options.onError ??
    ((err, event) => log.error({ err, eventType: event.type }, "events: persist failed"));

  return bus.onAny(async (event) => {
    try {
      await db.insert(eventsTable).values({
        id: event.id,
        type: event.type,
        userId: event.userId ?? null,
        payload: event.payload as Record<string, unknown>,
        createdAt: event.timestamp,
      });
    } catch (err) {
      onError(err, event);
    }
  });
}

/** Synchronous insert helper for batch backfills + tests. */
export async function persistEvent(db: DbClient, event: DomainEvent): Promise<void> {
  await db.insert(eventsTable).values({
    id: event.id,
    type: event.type,
    userId: event.userId ?? null,
    payload: event.payload as Record<string, unknown>,
    createdAt: event.timestamp,
  });
}

/** Convenience for tests: how many rows of a given event type exist. */
export async function countEventsOfType(db: DbClient, type: EventType): Promise<number> {
  const rows = await db.select().from(eventsTable);
  return rows.filter((r) => r.type === type).length;
}
