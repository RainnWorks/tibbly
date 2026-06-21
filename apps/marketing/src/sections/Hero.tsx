import { SKILL_ICONS, type SkillIconKey } from "@osrs-llm-helper/osrs-assets";
import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";
import strengthIcon from "@osrs-llm-helper/osrs-assets/skill_icons/strength.png";
import defenceIcon from "@osrs-llm-helper/osrs-assets/skill_icons/defence.png";
import rangedIcon from "@osrs-llm-helper/osrs-assets/skill_icons/ranged.png";
import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import magicIcon from "@osrs-llm-helper/osrs-assets/skill_icons/magic.png";
import runecraftIcon from "@osrs-llm-helper/osrs-assets/skill_icons/runecraft.png";
import constructionIcon from "@osrs-llm-helper/osrs-assets/skill_icons/construction.png";
import agilityIcon from "@osrs-llm-helper/osrs-assets/skill_icons/agility.png";
import herbloreIcon from "@osrs-llm-helper/osrs-assets/skill_icons/herblore.png";
import thievingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/thieving.png";
import craftingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/crafting.png";
import fletchingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/fletching.png";
import slayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/slayer.png";
import hunterIcon from "@osrs-llm-helper/osrs-assets/skill_icons/hunter.png";
import miningIcon from "@osrs-llm-helper/osrs-assets/skill_icons/mining.png";
import smithingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/smithing.png";
import fishingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/fishing.png";
import cookingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/cooking.png";
import firemakingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/firemaking.png";
import woodcuttingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/woodcutting.png";
import farmingIcon from "@osrs-llm-helper/osrs-assets/skill_icons/farming.png";
import hitpointsIcon from "@osrs-llm-helper/osrs-assets/skill_icons/hitpoints.png";

// Map every skill key to its imported PNG so Vite bundles them and ships hashed URLs.
const SKILL_PNGS: Readonly<Record<SkillIconKey, string>> = {
  attack: attackIcon,
  strength: strengthIcon,
  defence: defenceIcon,
  ranged: rangedIcon,
  prayer: prayerIcon,
  magic: magicIcon,
  runecraft: runecraftIcon,
  construction: constructionIcon,
  hitpoints: hitpointsIcon,
  agility: agilityIcon,
  herblore: herbloreIcon,
  thieving: thievingIcon,
  crafting: craftingIcon,
  fletching: fletchingIcon,
  slayer: slayerIcon,
  hunter: hunterIcon,
  mining: miningIcon,
  smithing: smithingIcon,
  fishing: fishingIcon,
  cooking: cookingIcon,
  firemaking: firemakingIcon,
  woodcutting: woodcuttingIcon,
  farming: farmingIcon,
  sailing: woodcuttingIcon, // placeholder; sailing icon arrives with R3 follow-up
};

// Background rain of skill icons. Position + delay are deterministic so the
// rendered HTML is stable for SSR / snapshot tests.
const BACKGROUND_SKILLS: ReadonlyArray<{
  readonly key: SkillIconKey;
  readonly left: string;
  readonly delaySec: number;
  readonly durationSec: number;
  readonly scale: number;
  readonly opacity: number;
}> = [
  { key: "attack", left: "5%", delaySec: 0, durationSec: 18, scale: 0.6, opacity: 0.18 },
  { key: "slayer", left: "12%", delaySec: 4, durationSec: 22, scale: 0.9, opacity: 0.22 },
  { key: "prayer", left: "20%", delaySec: 8, durationSec: 20, scale: 0.7, opacity: 0.16 },
  { key: "magic", left: "28%", delaySec: 2, durationSec: 24, scale: 1.0, opacity: 0.24 },
  { key: "ranged", left: "36%", delaySec: 11, durationSec: 19, scale: 0.6, opacity: 0.15 },
  { key: "hitpoints", left: "44%", delaySec: 5, durationSec: 21, scale: 0.8, opacity: 0.2 },
  { key: "agility", left: "52%", delaySec: 14, durationSec: 23, scale: 0.7, opacity: 0.17 },
  { key: "herblore", left: "60%", delaySec: 1, durationSec: 26, scale: 0.9, opacity: 0.2 },
  { key: "smithing", left: "68%", delaySec: 9, durationSec: 20, scale: 0.6, opacity: 0.16 },
  { key: "mining", left: "76%", delaySec: 3, durationSec: 25, scale: 0.8, opacity: 0.22 },
  { key: "fishing", left: "84%", delaySec: 12, durationSec: 22, scale: 0.7, opacity: 0.18 },
  { key: "farming", left: "92%", delaySec: 6, durationSec: 24, scale: 0.9, opacity: 0.2 },
];

const FEATURED_KEYS: ReadonlyArray<SkillIconKey> = [
  "attack",
  "magic",
  "slayer",
  "prayer",
  "agility",
  "herblore",
];

export function Hero() {
  return (
    <section
      data-testid="hero"
      className="relative overflow-hidden border-b border-osrs-border px-6 py-28 text-center"
    >
      {/* Animated background — falling skill icons. CSS keyframes defined in styles.css. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 select-none"
      >
        {BACKGROUND_SKILLS.map((sprite, idx) => (
          <img
            key={`${sprite.key}-${idx}`}
            src={SKILL_PNGS[sprite.key]}
            alt=""
            className="hero-rain absolute h-10 w-10"
            style={{
              left: sprite.left,
              top: "-3rem",
              opacity: sprite.opacity,
              transform: `scale(${sprite.scale})`,
              animationDuration: `${sprite.durationSec}s`,
              animationDelay: `${sprite.delaySec}s`,
            }}
          />
        ))}
        {/* Vignette so foreground copy stays readable */}
        <div className="absolute inset-0 bg-gradient-to-b from-osrs-bg/60 via-osrs-bg/30 to-osrs-bg/95" />
      </div>

      <div className="relative mx-auto max-w-4xl">
        <p className="mb-4 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
          Tibbly · the OSRS co-pilot
        </p>
        <h1 className="mb-6 text-5xl font-bold text-osrs-gold drop-shadow-[0_2px_0_rgba(0,0,0,0.6)] md:text-7xl">
          Stop alt-tabbing. Start playing.
        </h1>
        <p className="mx-auto mb-4 max-w-3xl text-lg text-osrs-text/90 md:text-xl">
          The only OSRS co-pilot that sees your game live, billed monthly with
          a hard cap. No API keys, no copy-paste, no botting.
        </p>
        <p className="mx-auto mb-10 max-w-2xl text-base text-osrs-muted md:text-lg">
          You don't have to describe your inventory. Tibbly already sees it.
        </p>
        <div className="flex flex-col items-center justify-center gap-4 sm:flex-row">
          <a
            href="#pricing"
            className="inline-flex items-center gap-2 border-2 border-osrs-gold bg-osrs-gold/10 px-8 py-3 font-heading text-lg text-osrs-gold transition hover:bg-osrs-gold/25 hover:shadow-[0_0_24px_rgba(255,204,0,0.35)]"
            aria-label="Get started — install the RuneLite plugin"
          >
            <span aria-hidden="true">⚔</span> Get started · install plugin
          </a>
          <a
            href="#demo"
            className="inline-flex items-center gap-2 border border-osrs-border bg-osrs-surface/60 px-8 py-3 font-heading text-lg text-osrs-text transition hover:border-osrs-gold-dim hover:text-osrs-gold"
            aria-label="See demo — see it in action"
          >
            <span aria-hidden="true">▶</span> See demo · see it in action
          </a>
        </div>
        <ul
          aria-label="Featured skills"
          className="mt-14 flex flex-wrap justify-center gap-3"
        >
          {FEATURED_KEYS.map((key) => {
            const asset = SKILL_ICONS[key];
            return (
              <li
                key={asset.id}
                className="flex h-14 w-14 items-center justify-center border border-osrs-border bg-osrs-surface/80 shadow-[inset_0_0_12px_rgba(255,204,0,0.06)] transition hover:border-osrs-gold-dim"
                title={key}
              >
                <img
                  src={SKILL_PNGS[key]}
                  alt={`${key} skill icon`}
                  className="h-8 w-8"
                />
              </li>
            );
          })}
        </ul>
        <p className="mt-6 font-mono text-xs uppercase tracking-widest text-osrs-muted">
          Helps you play. Never plays for you.
        </p>
      </div>
    </section>
  );
}
