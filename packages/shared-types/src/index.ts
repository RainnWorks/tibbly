/**
 * @osrs-llm-helper/shared-types
 *
 * Shared TypeScript types crossing the backend ↔ dashboard ↔ marketing ↔ plugin
 * boundary. Today this is a placeholder; concrete types (chat protocol,
 * billing tiers, tool catalog) will land here as their owning agents define
 * the contracts.
 */

export const SHARED_TYPES_PACKAGE_NAME = "@osrs-llm-helper/shared-types" as const;

/** Placeholder identifier for an end user's RuneLite device key binding. */
export type DeviceKeyId = string & { readonly __brand: "DeviceKeyId" };

/** Placeholder identifier for an OSRS player name. */
export type PlayerName = string & { readonly __brand: "PlayerName" };
