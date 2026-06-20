/**
 * @osrs-llm-helper/osrs-assets
 *
 * Catalog of OSRS sprite/image URLs and helpers for rendering them.
 * The marketing site and dashboard import from this package so that the
 * asset list lives in one place and license attribution can be enforced.
 *
 * Sources we are allowed to use commercially:
 *   - RuneLite client assets — BSD 2-Clause
 *   - RuneStar cache dumps — CC0
 *
 * Sources we are NOT allowed to use commercially:
 *   - OSRS Wiki (CC-BY-NC-SA) — non-commercial only. Do NOT add URLs from
 *     `oldschool.runescape.wiki` or `runescape.wiki` here.
 *
 * Concrete sprite files will be added by the asset-pipeline agent. For now
 * we export the public surface so downstream apps can wire imports without
 * waiting on the asset pipeline.
 */

export const OSRS_ASSETS_PACKAGE_NAME = "@osrs-llm-helper/osrs-assets" as const;

export type OsrsAsset = {
  readonly id: string;
  readonly url: string;
  readonly attribution: string;
};

/**
 * OSRS skill icon catalog. URLs intentionally left empty until the asset
 * pipeline lands the RuneLite/RuneStar exports. Consumers should treat an
 * empty `url` as "render fallback glyph".
 */
export type SkillIconKey =
  | "attack"
  | "strength"
  | "defence"
  | "ranged"
  | "prayer"
  | "magic"
  | "runecraft"
  | "construction"
  | "hitpoints"
  | "agility"
  | "herblore"
  | "thieving"
  | "crafting"
  | "fletching"
  | "slayer"
  | "hunter"
  | "mining"
  | "smithing"
  | "fishing"
  | "cooking"
  | "firemaking"
  | "woodcutting"
  | "farming"
  | "sailing";

export const SKILL_ICONS: Readonly<Record<SkillIconKey, OsrsAsset>> = {
  attack: { id: "skill.attack", url: "", attribution: "RuneLite BSD-2" },
  strength: { id: "skill.strength", url: "", attribution: "RuneLite BSD-2" },
  defence: { id: "skill.defence", url: "", attribution: "RuneLite BSD-2" },
  ranged: { id: "skill.ranged", url: "", attribution: "RuneLite BSD-2" },
  prayer: { id: "skill.prayer", url: "", attribution: "RuneLite BSD-2" },
  magic: { id: "skill.magic", url: "", attribution: "RuneLite BSD-2" },
  runecraft: { id: "skill.runecraft", url: "", attribution: "RuneLite BSD-2" },
  construction: { id: "skill.construction", url: "", attribution: "RuneLite BSD-2" },
  hitpoints: { id: "skill.hitpoints", url: "", attribution: "RuneLite BSD-2" },
  agility: { id: "skill.agility", url: "", attribution: "RuneLite BSD-2" },
  herblore: { id: "skill.herblore", url: "", attribution: "RuneLite BSD-2" },
  thieving: { id: "skill.thieving", url: "", attribution: "RuneLite BSD-2" },
  crafting: { id: "skill.crafting", url: "", attribution: "RuneLite BSD-2" },
  fletching: { id: "skill.fletching", url: "", attribution: "RuneLite BSD-2" },
  slayer: { id: "skill.slayer", url: "", attribution: "RuneLite BSD-2" },
  hunter: { id: "skill.hunter", url: "", attribution: "RuneLite BSD-2" },
  mining: { id: "skill.mining", url: "", attribution: "RuneLite BSD-2" },
  smithing: { id: "skill.smithing", url: "", attribution: "RuneLite BSD-2" },
  fishing: { id: "skill.fishing", url: "", attribution: "RuneLite BSD-2" },
  cooking: { id: "skill.cooking", url: "", attribution: "RuneLite BSD-2" },
  firemaking: { id: "skill.firemaking", url: "", attribution: "RuneLite BSD-2" },
  woodcutting: { id: "skill.woodcutting", url: "", attribution: "RuneLite BSD-2" },
  farming: { id: "skill.farming", url: "", attribution: "RuneLite BSD-2" },
  sailing: { id: "skill.sailing", url: "", attribution: "RuneLite BSD-2" },
};

/**
 * Font stack chosen to evoke the OSRS visual language without redistributing
 * Jagex font files. RuneScape's in-game UI font is proprietary; we lean on
 * royalty-free pixel/serif analogues. The asset pipeline can replace these
 * with self-hosted CC0 alternatives later.
 */
export type FontStack = {
  readonly heading: string;
  readonly body: string;
  readonly mono: string;
};

export const FONTS: FontStack = {
  heading:
    '"IM Fell English SC", "Trajan Pro", "Cinzel", Georgia, "Times New Roman", serif',
  body: '"Inter", "Segoe UI", system-ui, -apple-system, sans-serif',
  mono: '"JetBrains Mono", "Fira Code", ui-monospace, SFMono-Regular, Menlo, monospace',
};

/**
 * Brand palette derived from RuneLite's default theme and the gold-on-black
 * UI of the OSRS client. Hex values only — Tailwind config can extend from
 * this list.
 */
export const PALETTE = {
  background: "#0f0a06",
  surface: "#1a1410",
  border: "#3a2f1f",
  goldDim: "#c8b675",
  gold: "#ffcc00",
  textPrimary: "#f4e9c1",
  textMuted: "#a08a5a",
  danger: "#b22222",
  success: "#5a8a3a",
} as const;

export const ASSETS: readonly OsrsAsset[] = Object.values(SKILL_ICONS);
