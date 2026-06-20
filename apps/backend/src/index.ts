/**
 * @osrs-llm-helper/backend
 *
 * Bun service that drives the LLM chat loop via OpenRouter, exposes a
 * WebSocket bridge to the RuneLite plugin, and bills usage through Stripe.
 *
 * This file is a placeholder. The Backend Core agent (B1) replaces it with
 * the real Hono app + WebSocket handler.
 */
import { SHARED_TYPES_PACKAGE_NAME } from "@osrs-llm-helper/shared-types";

const port = Number(process.env["PORT"] ?? 3000);

export const BACKEND_BOOT_MESSAGE = `osrs-llm-helper backend placeholder — wired against ${SHARED_TYPES_PACKAGE_NAME}, will listen on :${port}.` as const;

if (import.meta.main) {
  console.info(BACKEND_BOOT_MESSAGE);
}
