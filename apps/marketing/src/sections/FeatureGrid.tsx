import { SKILL_ICONS, type SkillIconKey } from "@osrs-llm-helper/osrs-assets";

type Feature = {
  readonly title: string;
  readonly skill: SkillIconKey;
  readonly description: string;
};

const FEATURES: readonly Feature[] = [
  {
    title: "Knows your gear",
    skill: "attack",
    description:
      "Lorem ipsum: reads your worn equipment + inventory every turn. No more 'what do I have equipped' questions.",
  },
  {
    title: "Knows your quests",
    skill: "prayer",
    description:
      "Lorem ipsum: full quest log + prereq graph. Tells you what to do next on Song of the Elves or Desert Treasure II.",
  },
  {
    title: "Knows your slayer task",
    skill: "slayer",
    description:
      "Lorem ipsum: live slayer assignment + recommended gear. Suggests cannon spots and safe routes.",
  },
  {
    title: "Marks tiles, highlights NPCs",
    skill: "magic",
    description:
      "Lorem ipsum: in-client overlays. The assistant doesn't just tell you — it shows you on the world.",
  },
  {
    title: "Bank-aware",
    skill: "construction",
    description:
      "Lorem ipsum: scans your bank tabs. Plans gear loadouts from what you actually own.",
  },
  {
    title: "Boss-mechanic literate",
    skill: "ranged",
    description:
      "Lorem ipsum: every fight, including new ones. No hand-written per-boss plugin needed.",
  },
];

export function FeatureGrid() {
  return (
    <section
      data-testid="feature-grid"
      id="features"
      className="border-b border-osrs-border px-6 py-20"
    >
      <div className="mx-auto max-w-6xl">
        <h2 className="mb-12 text-center text-3xl md:text-4xl">
          Lorem ipsum feature heading
        </h2>
        <ul
          className="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
          data-testid="feature-grid-list"
        >
          {FEATURES.map((feature) => (
            <li
              key={feature.title}
              className="border border-osrs-border bg-osrs-surface p-6 transition hover:border-osrs-gold-dim"
            >
              <div
                className="mb-4 flex h-12 w-12 items-center justify-center border border-osrs-border bg-osrs-bg"
                aria-label={`${feature.skill} icon placeholder`}
                title={SKILL_ICONS[feature.skill].id}
              >
                <span className="font-mono text-xs text-osrs-gold-dim">
                  {feature.skill.slice(0, 3)}
                </span>
              </div>
              <h3 className="mb-2 text-xl">{feature.title}</h3>
              <p className="text-sm text-osrs-text/80">{feature.description}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
