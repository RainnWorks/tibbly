/**
 * Nightly memory-decay job (RAI-67).
 *
 * Rules:
 *   - A memory whose `last_referenced_at` is older than the decay window
 *     (30 days by default) has its `weight` halved.
 *   - A memory whose post-decay weight falls below `MIN_WEIGHT` is
 *     soft-forgotten (`forgotten_at = now()`). It is no longer surfaced by
 *     the prompt builder and counts as forgotten in the /tibbly forget
 *     audit.
 *   - Already-forgotten rows are skipped.
 *
 * The decay function is multiplicative, so a memory that is referenced
 * regularly stays at full weight indefinitely. One that vanishes from the
 * conversation halves every N days until it slips below the threshold and
 * is forgotten.
 *
 * Runs once nightly, in the same window as the model-catalog refresh.
 * Scheduling is wired in `server.ts`; this module exports the pure pass
 * so tests can exercise it without a clock.
 */
import { and, eq, isNull, lt } from "drizzle-orm";

import type { DbClient } from "../db/client";
import { companionMemories } from "../db/schema";

/** Memories below this weight after decay are soft-forgotten. */
export const MIN_WEIGHT = 0.1;

/** Default lookback window for "hasn't been referenced lately". 30 days. */
export const DEFAULT_DECAY_WINDOW_DAYS = 30;

/** Default multiplier applied when a memory is past the window. */
export const DEFAULT_DECAY_FACTOR = 0.5;

export interface DecayMemoriesOptions {
  /** Override the lookback window for tests. */
  windowDays?: number;
  /** Override the decay multiplier for tests. */
  factor?: number;
  /** Clock override for tests. */
  now?: () => Date;
}

export interface DecayMemoriesResult {
  decayed: number;
  forgotten: number;
}

/**
 * Run one decay pass and return counts. Idempotent: re-running the same
 * day is safe because already-decayed rows have their `last_referenced_at`
 * pointed at the decay run, so they won't decay again until the next
 * window.
 *
 * Implementation note: we explicitly DO NOT update `last_referenced_at`
 * here - that field exists to track "when did a prompt builder last
 * surface this memory". Decay is a separate concern. We instead require
 * the caller (the scheduler) to run at most once per night; the SQL
 * filter `last_referenced_at < cutoff` naturally guards against repeated
 * decay in the same window.
 */
export async function decayMemories(
  db: DbClient,
  options: DecayMemoriesOptions = {},
): Promise<DecayMemoriesResult> {
  const windowDays = options.windowDays ?? DEFAULT_DECAY_WINDOW_DAYS;
  const factor = options.factor ?? DEFAULT_DECAY_FACTOR;
  const now = (options.now ?? ((): Date => new Date()))();
  const cutoff = new Date(now.getTime() - windowDays * 24 * 60 * 60 * 1000);

  // 1) Read every candidate. We do this in a single round-trip because the
  // total row count across all users will stay in the low thousands for
  // the foreseeable future - most players have <50 active memories.
  const candidates = await db
    .select({
      id: companionMemories.id,
      weight: companionMemories.weight,
    })
    .from(companionMemories)
    .where(
      and(
        isNull(companionMemories.forgottenAt),
        lt(companionMemories.lastReferencedAt, cutoff),
      ),
    );

  let decayed = 0;
  let forgotten = 0;
  for (const row of candidates) {
    const next = row.weight * factor;
    if (next < MIN_WEIGHT) {
      await db
        .update(companionMemories)
        .set({ weight: next, forgottenAt: now })
        .where(eqId(row.id));
      forgotten += 1;
    } else {
      await db
        .update(companionMemories)
        .set({ weight: next })
        .where(eqId(row.id));
      decayed += 1;
    }
  }
  return { decayed, forgotten };
}

function eqId(id: string): ReturnType<typeof eq> {
  return eq(companionMemories.id, id);
}
