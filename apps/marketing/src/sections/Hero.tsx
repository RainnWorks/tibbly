import { type SkillIconKey } from "@osrs-llm-helper/osrs-assets";
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
  { key: "attack", left: "5%", delaySec: 0, durationSec: 18, scale: 0.6, opacity: 0.14 },
  { key: "slayer", left: "12%", delaySec: 4, durationSec: 22, scale: 0.9, opacity: 0.16 },
  { key: "prayer", left: "20%", delaySec: 8, durationSec: 20, scale: 0.7, opacity: 0.12 },
  { key: "magic", left: "28%", delaySec: 2, durationSec: 24, scale: 1.0, opacity: 0.18 },
  { key: "ranged", left: "36%", delaySec: 11, durationSec: 19, scale: 0.6, opacity: 0.12 },
  { key: "hitpoints", left: "44%", delaySec: 5, durationSec: 21, scale: 0.8, opacity: 0.15 },
  { key: "agility", left: "52%", delaySec: 14, durationSec: 23, scale: 0.7, opacity: 0.13 },
  { key: "herblore", left: "60%", delaySec: 1, durationSec: 26, scale: 0.9, opacity: 0.15 },
  { key: "smithing", left: "68%", delaySec: 9, durationSec: 20, scale: 0.6, opacity: 0.12 },
  { key: "mining", left: "76%", delaySec: 3, durationSec: 25, scale: 0.8, opacity: 0.16 },
  { key: "fishing", left: "84%", delaySec: 12, durationSec: 22, scale: 0.7, opacity: 0.13 },
  { key: "farming", left: "92%", delaySec: 6, durationSec: 24, scale: 0.9, opacity: 0.15 },
];

// Tool-call chips shown in the placeholder hero-right illustration slot.
// This is the worn-parchment chatbox snippet called out in IA Phase 4 as a
// fall-back until the commissioned hero art lands.
const HERO_SAMPLE_TOOLS: ReadonlyArray<{ name: string; icon: string }> = [
  { name: "get_quest_state", icon: prayerIcon },
  { name: "get_inventory", icon: hitpointsIcon },
];

export function Hero() {
  return (
    <section
      data-testid="hero"
      className="relative overflow-hidden border-b border-osrs-border px-6 pt-20 pb-24"
    >
      {/* Animated background: falling skill icons. CSS keyframes defined in styles.css. */}
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

      {/* Asymmetric chatbox-frame left at ~60% / illustration slot right at ~40%. */}
      <div className="relative mx-auto grid max-w-6xl gap-10 lg:grid-cols-[3fr_2fr] lg:items-center">
        <div>
          <p className="mb-4 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            tibbly · the OSRS co-pilot
          </p>
          <h1 className="mb-6 text-4xl text-osrs-gold drop-shadow-[0_2px_0_rgba(0,0,0,0.6)] sm:text-5xl md:text-6xl">
            Stop alt-tabbing. Start playing.
          </h1>
          <p className="mb-10 max-w-2xl text-base text-osrs-text/90 md:text-lg">
            Tibbly reads your bank, your quest log, and your inventory. It
            never moves your character, never clicks a tile, never types in
            chat. The same plugin shape as Quest Helper, with a wiki-fluent
            helper sitting next to you.
          </p>
          <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
            <a
              href="#pricing"
              className="inline-flex items-center gap-2 border-2 border-osrs-gold bg-osrs-gold/15 px-7 py-3 text-base font-bold text-osrs-gold transition hover:bg-osrs-gold/30 hover:shadow-[0_0_24px_rgba(243,199,90,0.35)]"
              aria-label="Install the RuneLite plugin"
              data-cta="install-plugin"
              data-section="hero"
            >
              <span aria-hidden="true">⚔</span> install plugin
            </a>
            <a
              href="#demo"
              className="inline-flex items-center gap-2 border border-osrs-border bg-osrs-surface/60 px-7 py-3 text-base font-bold text-osrs-text transition hover:border-osrs-gold-dim hover:text-osrs-gold"
              aria-label="See the demo"
              data-cta="see-the-demo"
              data-section="hero"
            >
              <span aria-hidden="true">▶</span> see the demo
            </a>
          </div>
        </div>

        {/*
         * Hero-right placeholder per IA Phase 4: worn-parchment chatbox snippet
         * with a real exchange in the brand voice. One-file swap when the
         * commissioned 1600x1200 WebP arrives.
         */}
        <aside
          aria-label="Sample chat with Tibbly"
          className="relative hidden border border-osrs-border bg-osrs-parchment/30 p-6 shadow-[inset_0_0_28px_rgba(0,0,0,0.45)] lg:block"
        >
          <p className="mb-4 font-mono text-[10px] uppercase tracking-[0.32em] text-osrs-gold-dim">
            sample · brand voice
          </p>
          <div className="mb-4">
            <p className="mb-1 font-mono text-[11px] uppercase tracking-widest text-osrs-muted">
              You
            </p>
            <p className="text-sm text-osrs-text/95">
              stuck on dragon slayer 2 after the vorkath cutscene, where do
              i go
            </p>
          </div>
          <div className="mb-4 flex flex-wrap gap-2">
            {HERO_SAMPLE_TOOLS.map((tool) => (
              <span
                key={tool.name}
                className="inline-flex items-center gap-2 border border-osrs-border bg-osrs-surface px-2 py-1 text-[11px]"
              >
                <img
                  src={tool.icon}
                  alt=""
                  className="h-3.5 w-3.5"
                  aria-hidden="true"
                />
                <span className="font-mono text-osrs-gold-dim">
                  {tool.name}
                </span>
              </span>
            ))}
          </div>
          <div>
            <p className="mb-1 font-mono text-[11px] uppercase tracking-widest text-osrs-gold-dim">
              Tibbly
            </p>
            <p className="text-sm text-osrs-text/95">
              Back to Ava in Draynor Manor. She has the next step. Anti-dragon
              shield is in your bank tab 7, I tagged it.
            </p>
          </div>
        </aside>
      </div>
    </section>
  );
}
