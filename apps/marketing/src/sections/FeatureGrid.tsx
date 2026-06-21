import { type SkillIconKey } from "@osrs-llm-helper/osrs-assets";
import magicIcon from "@osrs-llm-helper/osrs-assets/skill_icons/magic.png";
import rangedIcon from "@osrs-llm-helper/osrs-assets/skill_icons/ranged.png";
import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import constructionIcon from "@osrs-llm-helper/osrs-assets/skill_icons/construction.png";
import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";
import thievingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/thieving.png";

type Feature = {
  readonly title: string;
  readonly skill: SkillIconKey;
  readonly iconUrl: string;
  readonly headline: string;
  readonly detail: string;
};

const FEATURES: readonly Feature[] = [
  {
    title: "Tile marking",
    skill: "magic",
    iconUrl: magicIcon,
    headline: "Where to stand. Lit up.",
    detail:
      "Vorkath corner, Zulrah rotation tile, Akkha squares — Tibbly tags the exact tile and the camera handles the rest. No grid math at 3am.",
  },
  {
    title: "NPC highlights",
    skill: "ranged",
    iconUrl: rangedIcon,
    headline: "The right NPC, glowing.",
    detail:
      "Master Crafter? Random emote-clue rando? Tibbly outlines them in gold so you stop running past them like it's your first quest.",
  },
  {
    title: "Quest data",
    skill: "prayer",
    iconUrl: prayerIcon,
    headline: "Knows what's next.",
    detail:
      "All 180 quests, all their forks, all their hidden prereqs. Tibbly walks you from start to cape — using the items already in your bank.",
  },
  {
    title: "Bank prep",
    skill: "construction",
    iconUrl: constructionIcon,
    headline: "Your loadout, on demand.",
    detail:
      'Ask "bank for Bandos" and Tibbly builds the gear set from what you own, names the substitute when a piece is missing, and tells you the gp gap.',
  },
  {
    title: "Gear advice",
    skill: "attack",
    iconUrl: attackIcon,
    headline: "Yes or no, first word.",
    detail:
      "Fang over rapier at 80 attack? Tibbly answers yes-or-no in the first word, names the one exception, then gets out of your way.",
  },
  {
    title: "Clue solver",
    skill: "thieving",
    iconUrl: thievingIcon,
    headline: "Emote, anagram, cryptic — all of it.",
    detail:
      'Reads the scroll, picks the NPC, marks the spot. Tells you the emote. Tells you "the bald guy with the broom" so you don\'t have to guess.',
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
            What Tibbly does
          </p>
          <h2 className="text-3xl md:text-4xl">Six in-client superpowers.</h2>
          <p className="mx-auto mt-3 max-w-2xl text-osrs-text/80">
            Every feature runs from live game state. No screenshots, no
            copy-paste, no API keys.
          </p>
        </div>
        <ul
          className="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          data-testid="feature-grid-list"
        >
          {FEATURES.map((feature) => (
            <li
              key={feature.title}
              className="feature-card group relative border border-osrs-border bg-osrs-surface p-6 transition hover:border-osrs-gold-dim"
            >
              <div className="feature-icon mb-5 flex h-14 w-14 items-center justify-center border border-osrs-border bg-osrs-bg">
                <img
                  src={feature.iconUrl}
                  alt=""
                  className="h-9 w-9"
                  aria-hidden="true"
                />
              </div>
              <h3 className="mb-1 text-xl text-osrs-gold">{feature.title}</h3>
              <p className="mb-3 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
                {feature.headline}
              </p>
              <p className="text-sm text-osrs-text/85">{feature.detail}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
