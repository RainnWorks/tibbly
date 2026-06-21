/**
 * Shared resolver for the `OPS_JWT_SECRET` HMAC key (RAI-39, closes C4).
 *
 * The admin login router AND the admin gate both read the same secret;
 * factoring this here keeps the rule in one place:
 *
 *   - Production (`NODE_ENV === "production"`): `OPS_JWT_SECRET` MUST be
 *     set, MUST be at least 32 characters. Boot refuses to continue
 *     otherwise. The previous behaviour (warn + per-process derived
 *     fallback) was scorable as a forged-cookie path against any operator
 *     who could read `process.pid` + `DATABASE_URL` — see audit C4.
 *
 *   - Dev / test: a missing / short secret falls back to a per-process
 *     derived key. The router logs a loud warning at boot so it cannot
 *     silently leak. Tests inject `jwtSecret` explicitly.
 */
import { env } from "../../env";
import { log } from "../../lib/log";

/** Minimum secret length, in characters. */
const MIN_SECRET_LENGTH = 32;

/**
 * Cached secret bytes. Holding this means subsequent admin requests
 * skip the env re-parse + warning log.
 */
let cached: Uint8Array | null = null;

/**
 * Resolve the production secret, or throw with a setup-grade error
 * message when production runs without a valid `OPS_JWT_SECRET`.
 *
 * Test callers can side-step the cache by passing an explicit
 * `jwtSecret` to the admin login + gate routers.
 */
export function resolveOpsJwtSecret(): Uint8Array {
  if (cached) return cached;

  const raw = env.OPS_JWT_SECRET;
  const valid = typeof raw === "string" && raw.length >= MIN_SECRET_LENGTH;

  if (!valid) {
    if (env.NODE_ENV === "production") {
      // Critical misconfiguration. The admin routes would otherwise either
      // (a) be hard-broken, or (b) silently fall back to a per-process key
      // derivable from PID + DATABASE_URL. Refuse to serve.
      throw new Error(
        "OPS_JWT_SECRET is required in production and must be at least 32 characters. " +
          "Refusing to start admin auth surface. See docs/architecture/IDENTITY.md.",
      );
    }
    log.warn(
      "OPS_JWT_SECRET unset or < 32 chars; using a dev-only fallback. Set OPS_JWT_SECRET for prod.",
    );
    cached = deriveDevSecret();
    return cached;
  }

  cached = new TextEncoder().encode(raw);
  return cached;
}

/**
 * Per-process derived dev secret. Deterministic across the lifetime of
 * one Bun process so refresh-on-the-fly login works, but unique per
 * boot so test runs do not share signing keys.
 */
function deriveDevSecret(): Uint8Array {
  const base = `dev-only-ops-jwt-${process.pid}-${env.DATABASE_URL}`;
  return new TextEncoder().encode(base);
}

/** Test reset hook — drop the cached secret. */
export function _resetOpsJwtSecretCache(): void {
  cached = null;
}
