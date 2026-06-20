import { SKILL_ICONS } from "@osrs-llm-helper/osrs-assets";

export function Hero() {
  const featuredSkills = [
    SKILL_ICONS.attack,
    SKILL_ICONS.magic,
    SKILL_ICONS.slayer,
    SKILL_ICONS.prayer,
  ];

  return (
    <section
      data-testid="hero"
      className="border-b border-osrs-border px-6 py-24 text-center"
    >
      <div className="mx-auto max-w-4xl">
        <p className="mb-4 font-mono text-sm uppercase tracking-widest text-osrs-gold-dim">
          Lorem ipsum tagline placeholder
        </p>
        <h1 className="mb-6 text-5xl font-bold text-osrs-gold md:text-7xl">
          Stop alt-tabbing. Start playing.
        </h1>
        <p className="mb-10 text-lg text-osrs-text/90 md:text-xl">
          Lorem ipsum dolor sit amet, consectetur adipiscing elit. Your bank
          knows. Your assistant should too. A live AI co-pilot for Old School
          RuneScape — placeholder copy pending brand-voice agent.
        </p>
        <div className="flex flex-col items-center justify-center gap-4 sm:flex-row">
          <a
            href="#pricing"
            className="inline-block border-2 border-osrs-gold bg-osrs-gold/10 px-8 py-3 font-heading text-osrs-gold transition hover:bg-osrs-gold/20"
          >
            Get started
          </a>
          <a
            href="#demo"
            className="inline-block border border-osrs-border px-8 py-3 font-heading text-osrs-text transition hover:border-osrs-gold-dim"
          >
            See demo
          </a>
        </div>
        <ul
          aria-label="Featured skills"
          className="mt-12 flex justify-center gap-6 opacity-70"
        >
          {featuredSkills.map((asset) => (
            <li
              key={asset.id}
              className="flex h-12 w-12 items-center justify-center border border-osrs-border bg-osrs-surface"
              title={asset.id}
            >
              <span className="font-mono text-xs text-osrs-gold-dim">
                {asset.id.replace("skill.", "").slice(0, 3)}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
