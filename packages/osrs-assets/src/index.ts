/**
 * @osrs-llm-helper/osrs-assets
 *
 * License-cleared OSRS-themed visual assets for the marketing site and dashboard.
 *
 * Primary source: RuneLite project (BSD-2-Clause). Fonts: RuneStar (CC0).
 * The OSRS Wiki is research-only — CC-BY-NC-SA 3.0 forbids commercial use.
 *
 * See docs/research/osrs-wiki/licensing.md for the full risk analysis.
 */

export const OSRS_ASSETS_PACKAGE_NAME = "@osrs-llm-helper/osrs-assets" as const;

export type AssetLicense = "BSD-2-Clause" | "CC0-1.0" | "MIT";

export type OsrsAsset = {
  readonly id: string;
  readonly path: string;
  readonly source: string;
  readonly license: AssetLicense;
  readonly attribution: string;
};

export const SKILLS = [
  "attack", "strength", "defence", "ranged", "prayer", "magic",
  "runecraft", "construction", "hitpoints", "agility", "herblore",
  "thieving", "crafting", "fletching", "slayer", "hunter", "mining",
  "smithing", "fishing", "cooking", "firemaking", "woodcutting",
  "farming", "sailing", "combat", "overall",
] as const;

export type Skill = (typeof SKILLS)[number];

const RUNELITE_RAW =
  "https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources";

const RUNELITE_ATTR =
  "Icon from the RuneLite project (BSD-2-Clause, Adam <Adam@sigterm.info> 2016-2017).";

const RUNESTAR_ATTR =
  "RuneScape font from the RuneStar/fonts project (CC0 1.0, public domain).";

export const SKILL_ICONS: readonly OsrsAsset[] = SKILLS.map((skill) => ({
  id: `skill_icon_${skill}`,
  path: `skill_icons/${skill}.png`,
  source: `${RUNELITE_RAW}/skill_icons/${skill}.png`,
  license: "BSD-2-Clause",
  attribution: RUNELITE_ATTR,
}));

export const SKILL_ICONS_SMALL: readonly OsrsAsset[] = SKILLS.map((skill) => ({
  id: `skill_icon_small_${skill}`,
  path: `skill_icons_small/${skill}.png`,
  source: `${RUNELITE_RAW}/skill_icons_small/${skill}.png`,
  license: "BSD-2-Clause",
  attribution: RUNELITE_ATTR,
}));

export const FONTS: readonly OsrsAsset[] = [
  {
    id: "font_runescape",
    path: "fonts/runescape.ttf",
    source: `${RUNELITE_RAW}/net/runelite/client/ui/runescape.ttf`,
    license: "CC0-1.0",
    attribution: RUNESTAR_ATTR,
  },
  {
    id: "font_runescape_bold",
    path: "fonts/runescape_bold.ttf",
    source: `${RUNELITE_RAW}/net/runelite/client/ui/runescape_bold.ttf`,
    license: "CC0-1.0",
    attribution: RUNESTAR_ATTR,
  },
  {
    id: "font_runescape_small",
    path: "fonts/runescape_small.ttf",
    source: `${RUNELITE_RAW}/net/runelite/client/ui/runescape_small.ttf`,
    license: "CC0-1.0",
    attribution: RUNESTAR_ATTR,
  },
];

export const ASSETS: readonly OsrsAsset[] = [
  ...SKILL_ICONS,
  ...SKILL_ICONS_SMALL,
  ...FONTS,
];

/**
 * Hostnames forbidden for asset URLs. CC-BY-NC-SA / all-rights-reserved.
 */
export const FORBIDDEN_ASSET_HOSTS: readonly string[] = [
  "oldschool.runescape.wiki",
  "runescape.wiki",
  "secure.runescape.com",
  "www.runescape.com",
];

export function isForbiddenAssetUrl(url: string): boolean {
  try {
    const host = new URL(url).hostname;
    return FORBIDDEN_ASSET_HOSTS.includes(host);
  } catch {
    return false;
  }
}
