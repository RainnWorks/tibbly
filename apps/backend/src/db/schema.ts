/**
 * Drizzle schema barrel — kept empty on RAI-14.
 *
 * The real schema (users, devices, chats, messages, billing events, …) lands in
 * RAI-15. Downstream agents should add tables here and re-export them so that
 * `import * as schema from "./db/schema"` continues to work for migrations.
 */
export {};
