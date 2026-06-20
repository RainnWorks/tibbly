/**
 * Backend entrypoint.
 *
 * Boots the Hono app on `env.PORT` via Bun's native HTTP server. The skeleton
 * exposes `/health` and `/version` only — chat, auth, billing land in later
 * RAI issues but plug into the app factory in `app.ts`.
 *
 * Run: `bun --hot src/server.ts`
 */
import { createApp } from "./app";
import { env } from "./env";
import { log } from "./lib/log";

const app = createApp();

const server = Bun.serve({
  port: env.PORT,
  fetch: app.fetch,
});

log.info({ port: server.port, version: env.VERSION, env: env.NODE_ENV }, "backend: listening");

if (!env.OPENROUTER_API_KEY && env.NODE_ENV !== "test") {
  log.warn("OPENROUTER_API_KEY missing — chat routes will fail when they land (RAI-17).");
}

const shutdown = (signal: string): void => {
  log.info({ signal }, "backend: shutting down");
  server.stop();
  process.exit(0);
};

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

export { app, server };
