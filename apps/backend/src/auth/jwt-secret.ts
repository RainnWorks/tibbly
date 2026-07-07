/**
 * Resolves the symmetric secret used to sign the `tibbly_session` JWT
 * (end-user magic-link sessions).
 *
 * Mirrors `api/admin/jwt-secret.ts` but uses `AUTH_JWT_SECRET` — keeping
 * the two audiences separate means a rotation of one doesn't invalidate
 * the other, and a hypothetical key leak stays scoped.
 *
 * Production: AUTH_JWT_SECRET is required and must be ≥ 32 chars.
 * Dev / test: per-process derived fallback with a loud warning.
 */
import { createHmac, randomBytes } from "node:crypto";

import { env } from "../env";
import { log } from "../lib/log";

let cachedDevSecret: Uint8Array | undefined;

export function resolveAuthJwtSecret(): Uint8Array {
  const raw = env.AUTH_JWT_SECRET;
  if (raw && raw.length >= 32) {
    return new TextEncoder().encode(raw);
  }
  if (env.NODE_ENV === "production") {
    throw new Error(
      "AUTH_JWT_SECRET must be set (and ≥ 32 chars) in production. " +
        "Generate one with `openssl rand -base64 48`.",
    );
  }
  if (!cachedDevSecret) {
    const seed = randomBytes(32);
    cachedDevSecret = new Uint8Array(
      createHmac("sha256", "tibbly-dev-auth").update(seed).digest(),
    );
    log.warn(
      "AUTH_JWT_SECRET unset or < 32 chars; using a dev-only fallback. Set AUTH_JWT_SECRET for prod.",
    );
  }
  return cachedDevSecret;
}
