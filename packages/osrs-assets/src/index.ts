/**
 * @osrs-llm-helper/osrs-assets
 *
 * Catalog of OSRS sprite/image URLs and helpers for rendering them.
 * The marketing site and dashboard import from this package so that the
 * asset list lives in one place and license attribution can be enforced.
 *
 * NOTE: OSRS Wiki content is CC-BY-NC-SA 3.0. See docs/agents/OPEN_QUESTIONS.md
 * Q-4 for the unresolved license question on commercial use.
 */

export const OSRS_ASSETS_PACKAGE_NAME = "@osrs-llm-helper/osrs-assets" as const;

export type OsrsAsset = {
  readonly id: string;
  readonly url: string;
  readonly attribution: string;
};

export const ASSETS: readonly OsrsAsset[] = [];
