/**
 * Deterministic env for the E2E orchestrator.
 *
 * Reads NOTHING from the user's `.env` or shell. Every value is either a
 * literal or a per-run nanoid so two parallel runs cannot collide and a
 * test cannot accidentally talk to production Stripe / OpenRouter.
 *
 * If a real OpenRouter or Stripe key is genuinely required (e.g. BYOK
 * scenario), the test that needs it must explicitly opt in via the
 * `TIBBLY_E2E_*` env namespace and skip itself when the key is absent.
 */
import { nanoid } from "nanoid";

export interface E2EEnv {
  NODE_ENV: "test";
  DATABASE_URL: string;
  ADMIN_EMAIL: string;
  ADMIN_EMAILS: string;
  /** 64-char dev secret for the ops JWT. Stable per run; never written to disk. */
  OPS_JWT_SECRET: string;
  /** Tier price IDs are required by the dev stub even though Stripe is faked. */
  STRIPE_PRICE_HOBBYIST: string;
  STRIPE_PRICE_PRO: string;
  STRIPE_PRICE_IRON: string;
  /** Optional BYOK passthrough for scenario 02. Empty when unset. */
  OPENROUTER_API_KEY: string;
}

/**
 * Build a fresh deterministic env block. The PGLite URL is in-memory so the
 * database lives entirely in RAM and is torn down with the orchestrator.
 */
export function buildE2EEnv(): E2EEnv {
  const run = nanoid(8);
  return {
    NODE_ENV: "test",
    DATABASE_URL: "memory://",
    ADMIN_EMAIL: "admin@example.test",
    ADMIN_EMAILS: "admin@example.test",
    OPS_JWT_SECRET: `e2e-${run}-ops-secret-please-do-not-collide-with-prod-x`,
    STRIPE_PRICE_HOBBYIST: "price_e2e_hobbyist",
    STRIPE_PRICE_PRO: "price_e2e_pro",
    STRIPE_PRICE_IRON: "price_e2e_iron",
    OPENROUTER_API_KEY: process.env["TIBBLY_E2E_OPENROUTER_KEY"] ?? "",
  };
}
