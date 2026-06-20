/**
 * @osrs-llm-helper/osrs-assets
 *
 * License-cleared OSRS-themed visual assets for the marketing site and dashboard.
 *
 * Sources we are allowed to use commercially:
 *   - RuneLite client assets — BSD 2-Clause
 *   - RuneStar cache dumps / fonts — CC0
 *
 * Sources we are NOT allowed to use commercially:
 *   - OSRS Wiki (CC-BY-NC-SA) — non-commercial only. Do NOT add URLs from
 *     `oldschool.runescape.wiki` or `runescape.wiki` here.
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

/**
 * Subset of {@link Skill} used by the marketing site / dashboard UI. Same string
 * literal type — kept as a separate alias because some consumers (Hero,
 * FeatureGrid) only need the named-skill keys, not the "combat" / "overall"
 * pseudo-skills.
 */
export type SkillIconKey = Exclude<Skill, "combat" | "overall">;

const RUNELITE_RAW =
  "https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/resources";

const RUNELITE_ATTR =
  "Icon from the RuneLite project (BSD-2-Clause, Adam <Adam@sigterm.info> 2016-2017).";

const RUNESTAR_ATTR =
  "RuneScape font from the RuneStar/fonts project (CC0 1.0, public domain).";

export const SKILL_ICONS_LIST: readonly OsrsAsset[] = SKILLS.map((skill) => ({
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

/**
 * Record-keyed skill icon catalog. Consumers (marketing Hero / FeatureGrid)
 * can do `SKILL_ICONS.attack` to fetch a single icon without scanning the
 * array. Keys exclude the "combat" / "overall" pseudo-skills.
 */
export const SKILL_ICONS: Readonly<Record<SkillIconKey, OsrsAsset>> = (() => {
  const out = {} as Record<SkillIconKey, OsrsAsset>;
  for (const asset of SKILL_ICONS_LIST) {
    const key = asset.id.replace("skill_icon_", "") as Skill;
    if (key !== "combat" && key !== "overall") {
      out[key as SkillIconKey] = asset;
    }
  }
  return out;
})();

export const FONTS_LIST: readonly OsrsAsset[] = [
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

/**
 * CSS font stack for the marketing site / dashboard. Pixel/serif analogues
 * are listed first so the OSRS visual language reads through even before the
 * self-hosted CC0 RuneStar fonts (see {@link FONTS_LIST}) finish loading.
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

export const ASSETS: readonly OsrsAsset[] = [
  ...SKILL_ICONS_LIST,
  ...SKILL_ICONS_SMALL,
  ...FONTS_LIST,
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
