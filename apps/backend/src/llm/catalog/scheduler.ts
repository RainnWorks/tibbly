/**
 * Nightly catalog-refresh scheduler.
 *
 * Two cadences:
 *   - Boot refresh: 5 seconds after start. Cheap insurance that a fresh
 *     deploy picks up the latest OpenRouter list without waiting for the
 *     03:17 UTC tick.
 *   - Nightly: 03:17 UTC. Jittered minute so we don't align with the
 *     retention sweeper (also UTC midnight-ish) or with OpenRouter's own
 *     pricing-page bulk refresh. setInterval fires every 60s; we trigger
 *     when (hour, minute) match.
 *
 * Returns a `{ stop }` handle so server shutdown can cancel the timers.
 */
import type { DbClient } from "../../db/client";
import { log } from "../../lib/log";
import { refreshModelCatalog } from "./ingest";

/** UTC time of the nightly refresh. */
export const NIGHTLY_HOUR_UTC = 3;
export const NIGHTLY_MINUTE_UTC = 17;

/** Delay before the boot refresh fires (ms). */
export const BOOT_REFRESH_DELAY_MS = 5_000;

/** Tick cadence for the nightly check. */
export const TICK_MS = 60_000;

export interface SchedulerHandle {
  stop(): void;
}

export interface StartOptions {
  /**
   * Inject a clock for tests so the nightly trigger fires when we want.
   * Returns the current `Date`.
   */
  now?: () => Date;
  /**
   * Inject a refresh function for tests. Defaults to `refreshModelCatalog`.
   */
  refresh?: (db: DbClient) => Promise<unknown>;
  /** Override the boot delay (handy for tests that want immediate fire). */
  bootDelayMs?: number;
  /** Override the tick interval. */
  tickMs?: number;
}

export interface StartArgs extends StartOptions {
  db: DbClient;
}

/**
 * Start the boot + nightly schedule. Both timers are unref'd-safe (Bun's
 * `setInterval` works the same way Node does). Stop via the returned handle.
 */
export function startModelCatalogScheduler(args: StartArgs): SchedulerHandle {
  const {
    db,
    now = () => new Date(),
    refresh = (d) => refreshModelCatalog(d),
    bootDelayMs = BOOT_REFRESH_DELAY_MS,
    tickMs = TICK_MS,
  } = args;

  let lastFiredMinute: string | null = null;
  let stopped = false;

  const bootTimer = setTimeout(() => {
    if (stopped) return;
    Promise.resolve(refresh(db)).catch((err: unknown) =>
      log.error(
        { err: { message: (err as Error).message } },
        "model-catalog: boot refresh failed",
      ),
    );
  }, bootDelayMs);

  const tickTimer = setInterval(() => {
    if (stopped) return;
    const t = now();
    if (
      t.getUTCHours() === NIGHTLY_HOUR_UTC &&
      t.getUTCMinutes() === NIGHTLY_MINUTE_UTC
    ) {
      // De-dupe within the same minute so a sub-minute clock skew doesn't
      // double-fire. Stamp the minute we just fired.
      const stamp = `${t.getUTCFullYear()}-${t.getUTCMonth()}-${t.getUTCDate()}T${t.getUTCHours()}:${t.getUTCMinutes()}`;
      if (lastFiredMinute === stamp) return;
      lastFiredMinute = stamp;
      Promise.resolve(refresh(db)).catch((err: unknown) =>
        log.error(
          { err: { message: (err as Error).message } },
          "model-catalog: nightly refresh failed",
        ),
      );
    }
  }, tickMs);

  return {
    stop(): void {
      stopped = true;
      clearTimeout(bootTimer);
      clearInterval(tickTimer);
    },
  };
}
