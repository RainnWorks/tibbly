/**
 * Env loader contract tests. We feed `loadEnv` a fixture map instead of mutating
 * `process.env`, so tests stay isolated.
 */
import { describe, expect, it } from "bun:test";
import { ZodError } from "zod";

import { loadEnv } from "../src/env";

describe("loadEnv", () => {
  it("returns defaults when nothing is set", () => {
    const env = loadEnv({});
    expect(env.NODE_ENV).toBe("development");
    expect(env.DATABASE_URL).toBe("file:./local.db");
    expect(env.PORT).toBe(8787);
    expect(env.VERSION).toBe("0.0.0-dev");
    expect(env.LOG_LEVEL).toBe("info");
    expect(env.OPENROUTER_API_KEY).toBeUndefined();
  });

  it("coerces PORT from a string", () => {
    const env = loadEnv({ PORT: "9090" });
    expect(env.PORT).toBe(9090);
  });

  it("rejects an out-of-range PORT", () => {
    expect(() => loadEnv({ PORT: "70000" })).toThrow(ZodError);
  });

  it("rejects an empty required DATABASE_URL", () => {
    // empty string defeats Zod's default and trips the min(1) constraint
    expect(() => loadEnv({ DATABASE_URL: "" })).toThrow(ZodError);
  });

  it("rejects an invalid NODE_ENV", () => {
    expect(() => loadEnv({ NODE_ENV: "staging" })).toThrow(ZodError);
  });

  it("rejects an invalid LOG_LEVEL", () => {
    expect(() => loadEnv({ LOG_LEVEL: "loud" })).toThrow(ZodError);
  });

  it("accepts a postgres:// DATABASE_URL", () => {
    const env = loadEnv({
      DATABASE_URL: "postgres://u:p@h:5432/db",
      PORT: "8787",
    });
    expect(env.DATABASE_URL).toBe("postgres://u:p@h:5432/db");
  });

  it("accepts optional Stripe + OpenRouter creds when present", () => {
    const env = loadEnv({
      OPENROUTER_API_KEY: "sk-or-v1-test",
      STRIPE_SECRET_KEY: "sk_test_xx",
      STRIPE_WEBHOOK_SECRET: "whsec_xx",
    });
    expect(env.OPENROUTER_API_KEY).toBe("sk-or-v1-test");
    expect(env.STRIPE_SECRET_KEY).toBe("sk_test_xx");
    expect(env.STRIPE_WEBHOOK_SECRET).toBe("whsec_xx");
  });

  it("rejects an empty string for OPENROUTER_API_KEY when supplied", () => {
    expect(() => loadEnv({ OPENROUTER_API_KEY: "" })).toThrow(ZodError);
  });
});
