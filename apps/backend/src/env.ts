/**
 * Zod-validated environment loader for the backend.
 *
 * Required vars:
 *   - DATABASE_URL: PGLite path (`file:./local.db`) for dev, `postgres://…` for prod.
 *   - PORT: HTTP port the Hono app listens on.
 *
 * Optional vars:
 *   - OPENROUTER_API_KEY: required at runtime once we make LLM calls, but kept
 *     optional here so the bare skeleton + tests can run without it. We surface
 *     a warning at boot if it's missing in non-test environments.
 *   - STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET: only required once billing is wired.
 *
 * Build-time vars:
 *   - VERSION: surfaced from `/health` so deploys are traceable. Defaults to
 *     `package.json`'s version when present, else `0.0.0-dev`.
 *   - NODE_ENV: standard.
 *
 * Per SCOPE_GUARD: NEVER log raw values. Per logging.md the redact paths
 * already cover `OPENROUTER_API_KEY` and `stripe*`.
 */
import { z } from "zod";

const EnvSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  DATABASE_URL: z.string().min(1).default("file:./local.db"),
  PORT: z.coerce.number().int().positive().max(65535).default(8787),
  VERSION: z.string().default("0.0.0-dev"),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),
  OPENROUTER_API_KEY: z.string().min(1).optional(),
  STRIPE_SECRET_KEY: z.string().min(1).optional(),
  STRIPE_WEBHOOK_SECRET: z.string().min(1).optional(),
});

export type Env = z.infer<typeof EnvSchema>;

export type LoadEnvSource = Record<string, string | undefined>;

/**
 * Parse the given source (defaults to `process.env`) into a validated Env.
 * Throws a ZodError with a clean issue list on failure — server.ts catches
 * and pretty-prints it; tests assert on `error.issues`.
 */
export function loadEnv(source: LoadEnvSource = process.env): Env {
  return EnvSchema.parse(source);
}

/**
 * Lazy singleton so tests can call `loadEnv({...})` with fixtures without
 * polluting process.env. Real runtime entrypoints import `env` directly.
 */
let cached: Env | undefined;
export const env: Env = (() => {
  if (cached) return cached;
  cached = loadEnv();
  return cached;
})();
