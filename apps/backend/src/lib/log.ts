/**
 * Pino logger with PII/secret redaction baked in (see docs/research/libraries/logging.md).
 * Dev gets pretty output via pino-pretty; prod stays JSON for shipping to a
 * log aggregator. Never log raw OPENROUTER_API_KEY / stripe secrets / device keys.
 */
import pino from "pino";
import { env } from "../env";

const isDev = env.NODE_ENV !== "production";

export const log = pino({
  level: env.LOG_LEVEL,
  redact: {
    paths: [
      "req.headers.authorization",
      "req.headers.cookie",
      "deviceKey",
      "stripeSecret",
      "*.apiKey",
      "OPENROUTER_API_KEY",
      "STRIPE_SECRET_KEY",
      "STRIPE_WEBHOOK_SECRET",
      "OPS_JWT_SECRET",
      // RAI-39 H5: per-user PII routinely landed in stripe webhook logs in
      // cleartext (userId + stripeCustomerId paired). Pino's redact paths
      // include both shallow and nested forms so wrappers that emit
      // `{ event: { ...payload } }` still get scrubbed.
      "customerId",
      "stripeCustomerId",
      "*.customerId",
      "*.stripeCustomerId",
      "*.email",
      "email",
    ],
    censor: "[redacted]",
  },
  ...(isDev
    ? {
        transport: {
          target: "pino-pretty",
          options: { colorize: true, translateTime: "HH:MM:ss" },
        },
      }
    : {}),
});
