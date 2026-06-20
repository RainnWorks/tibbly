/**
 * @osrs-llm-helper/tooling
 *
 * Holds shared lint, format, and tsconfig presets for the monorepo.
 * Consumers import via the package exports map (see package.json):
 *   - `@osrs-llm-helper/tooling/eslint`
 *   - `@osrs-llm-helper/tooling/prettier`
 *   - `@osrs-llm-helper/tooling/tsconfig.base`
 *
 * This file intentionally exports nothing runtime — it's a placeholder to
 * give the package a valid TS entry until concrete helpers land.
 */

export const TOOLING_PACKAGE_NAME = "@osrs-llm-helper/tooling" as const;
