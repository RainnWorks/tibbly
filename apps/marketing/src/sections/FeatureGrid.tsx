import { type SkillIconKey } from "@osrs-llm-helper/osrs-assets";
import magicIcon from "@osrs-llm-helper/osrs-assets/skill_icons/magic.png";
import rangedIcon from "@osrs-llm-helper/osrs-assets/skill_icons/ranged.png";
import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import constructionIcon from "@osrs-llm-helper/osrs-assets/skill_icons/construction.png";
import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";
import thievingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/thieving.png";
import slayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/slayer.png";
import herbloreIcon from "@osrs-llm-helper/osrs-assets/skill_icons/herblore.png";

type Feature = {
  readonly title: string;
  readonly skill: SkillIconKey;
  readonly iconUrl: string;
  readonly copy: string;
};

/*
 * 4x2 inventory grid (eight cells, no empty cells).
 *
 * Per the canonical IA (docs/marketing/IA.md §2.6): reframe the FeatureGrid
 * as a literal OSRS inventory: square cells, hairline gold-on-warm-brown
 * border, one skill icon top-left of each cell, one line of plain copy
 * describing what the tool actually does.
 */
const FEATURES: readonly Feature[] = [
  {
    title: "Tile marking",
    skill: "magic",
    iconUrl: magicIcon,
    copy: "Tags the exact stand-here tile for boss rotations and clue steps.",
  },
  {
    title: "NPC highlights",
    skill: "ranged",
    iconUrl: rangedIcon,
    copy: "Outlines the NPC the wiki step actually wants, in gold.",
  },
  {
    title: "Quest data",
    skill: "prayer",
    iconUrl: prayerIcon,
    copy: "All 180 quests, every fork, every hidden prereq, walked end to end.",
  },
  {
    title: "Bank prep",
    skill: "construction",
    iconUrl: constructionIcon,
    copy: "Builds your gear set from what you own. Names the substitute when a piece is missing.",
  },
  {
    title: "Gear advice",
    skill: "attack",
    iconUrl: attackIcon,
    copy: "Yes or no in the first word, with the one exception called out.",
  },
  {
    title: "Clue solver",
    skill: "thieving",
    iconUrl: thievingIcon,
    copy: "Reads emote, anagram, cryptic. Names the NPC. Marks the tile.",
  },
  {
    title: "Slayer plan",
    skill: "slayer",
    iconUrl: slayerIcon,
    copy: "Reads the task. Picks the cannon spot. Calls the prayer flick.",
  },
  {
    title: "Potion lookups",
    skill: "herblore",
    iconUrl: herbloreIcon,
    copy: "Tells you the herb, the secondary, the level, and the price gap.",
  },
];

export function FeatureGrid() {
  return (
    <section
      data-testid="feature-grid"
      id="features"
      className="border-b border-osrs-border px-6 py-24"
    >
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 text-center">
          <p className="mb-3 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            inventory
          </p>
          <h2 className="text-3xl text-osrs-gold md:text-4xl">
            Eight live tools.
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-osrs-text/80">
            Each cell reads live game state and answers in one move. No
            screenshots. No copy-paste.
          </p>
        </div>
        <ul
          className="mx-auto grid grid-cols-2 gap-px border border-osrs-border bg-osrs-border sm:max-w-4xl md:grid-cols-4"
          data-testid="feature-grid-list"
        >
          {FEATURES.map((feature) => (
            <li
              key={feature.title}
              className="feature-card group relative aspect-square bg-osrs-surface p-4 transition hover:bg-osrs-parchment/60"
            >
              <div className="feature-icon mb-3 flex h-9 w-9 items-center justify-center border border-osrs-border bg-osrs-bg">
                <img
                  src={feature.iconUrl}
                  alt=""
                  className="h-6 w-6"
                  aria-hidden="true"
                />
              </div>
              <h3 className="mb-2 text-sm leading-tight text-osrs-gold">
                {feature.title}
              </h3>
              <p className="text-xs leading-snug text-osrs-text/85">
                {feature.copy}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
