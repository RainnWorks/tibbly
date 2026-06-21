/**
 * Convenience wrapper around the per-context cleanup. Tests usually call
 * `ctx.cleanup()` directly in their `afterEach`; this re-export exists so
 * scripts that want a symmetric `boot` / `teardown` pair stay readable.
 */
import type { OrchestratorContext } from "./boot";

export async function teardown(ctx: OrchestratorContext): Promise<void> {
  await ctx.cleanup();
}
