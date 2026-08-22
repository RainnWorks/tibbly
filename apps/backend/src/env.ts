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
  /**
   * Comma-separated allow-list of email addresses for /admin/* routes.
   * Empty → admin routes always 403. See `src/api/admin/usage.ts`.
   */
  ADMIN_EMAILS: z.string().default(""),
  /**
   * HMAC-SHA256 secret for the ops_session JWT cookie. Required in
   * production — boot refuses to start when unset and NODE_ENV is
   * `"production"`. In dev / test the admin login router falls back to
   * a per-process derived key (loud warning at boot).
   *
   * Length: at least 32 characters. Anything shorter is treated as
   * unset.
   */
  OPS_JWT_SECRET: z.string().optional(),
  /**
   * Dev-only ergonomics escape hatch. When `"true"` AND `NODE_ENV` is
   * NOT `"production"`, the v1 auth middleware accepts a plain
   * `x-dev-user-id` header so local tests + scratch scripts can hit
   * authenticated endpoints without spinning up a real device row.
   *
   * Production deploys MUST leave this unset (or explicitly false).
   * `requireUser` ignores the header when either condition is unmet
   * and the boot path logs a loud warning when it is on.
   */
  ALLOW_DEV_HEADERS: z.string().optional(),
  /**
   * Resend API key for transactional email (magic-link sign-in, billing
   * notifications). When unset, the email sender falls back to a
   * console-logging stub — fine for local dev where you'd otherwise
   * just read the printed link from the terminal. Prod refuses to send
   * if unset.
   */
  RESEND_API_KEY: z.string().optional(),
  /**
   * From-address used for every transactional email. Must be on a
   * domain with verified SPF + DKIM + DMARC inside the Resend dashboard,
   * otherwise Gmail / Outlook will spam-folder every link.
   * Default is a placeholder so local dev still boots.
   */
  EMAIL_FROM: z.string().email().default("Tibbly <noreply@tibbly.dev>"),
  /**
   * HMAC-SHA256 secret for the `tibbly_session` JWT cookie that magic-link
   * verify mints. Distinct from `OPS_JWT_SECRET` (different audience —
   * ops console vs end-user marketing site / plugin) so a key rotation
   * of one doesn't invalidate the other.
   * Length: at least 32 characters. Required in production.
   */
  AUTH_JWT_SECRET: z.string().optional(),
  /**
   * Base URL the user's browser will hit when they click a magic link.
   * Backend prefixes verify-token paths with this. Default points at
   * the local marketing dev server.
   */
  WEB_BASE_URL: z.string().url().default("http://localhost:5173"),
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
