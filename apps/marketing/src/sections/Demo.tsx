import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import hitpointsIcon from "@osrs-llm-helper/osrs-assets/skill_icons/hitpoints.png";
import herbloreIcon from "@osrs-llm-helper/osrs-assets/skill_icons/herblore.png";

type Tool = {
  readonly name: string;
  readonly icon: string;
  readonly summary: string;
};

const TOOLS: readonly Tool[] = [
  {
    name: "get_quest_state",
    icon: prayerIcon,
    summary: "Dragon Slayer II · Phase 2",
  },
  {
    name: "get_inventory",
    icon: hitpointsIcon,
    summary: "27 sharks · 4 prayer pots",
  },
  {
    name: "get_bank_items",
    icon: herbloreIcon,
    summary: "tab 7 · anti-dragon shield",
  },
];

export function Demo() {
  return (
    <section
      data-testid="demo"
      id="demo"
      className="border-b border-osrs-border bg-osrs-surface/40 px-6 py-24"
    >
      <div className="mx-auto max-w-5xl">
        <div className="mb-12 text-center">
          <p className="mb-3 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            Live chat preview
          </p>
          <h2 className="text-3xl md:text-4xl">See it in action.</h2>
          <p className="mx-auto mt-3 max-w-2xl text-osrs-text/80">
            A real Dragon Slayer II turn, with real tool calls. Pure CSS. No
            video, no copy-paste.
          </p>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
          {/* Fake chat panel */}
          <div className="border border-osrs-border bg-osrs-bg p-6 shadow-[inset_0_0_24px_rgba(0,0,0,0.4)]">
            {/* Player message */}
            <div className="demo-message mb-6" style={{ animationDelay: "0s" }}>
              <p className="mb-1 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
                You
              </p>
              <p className="text-osrs-text/95">
                stuck on dragon slayer 2 after the vorkath cutscene, where do i
                go
              </p>
            </div>

            {/* Tool-call ribbon */}
            <div
              className="demo-message mb-5 flex flex-wrap gap-2"
              style={{ animationDelay: "0.6s" }}
              aria-label="Tibbly is calling tools"
            >
              {TOOLS.map((tool) => (
                <span
                  key={tool.name}
                  className="inline-flex items-center gap-2 border border-osrs-border bg-osrs-surface px-2 py-1 text-xs"
                >
                  <img
                    src={tool.icon}
                    alt=""
                    className="h-4 w-4"
                    aria-hidden="true"
                  />
                  <span className="font-mono text-osrs-gold-dim">
                    {tool.name}
                  </span>
                  <span className="text-osrs-muted">·</span>
                  <span className="text-osrs-text/90">{tool.summary}</span>
                </span>
              ))}
            </div>

            {/* Tibbly reply */}
            <div className="demo-message" style={{ animationDelay: "1.2s" }}>
              <p className="mb-1 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
                Tibbly
              </p>
              <p className="text-osrs-text/95">
                Back to Ava in Draynor Manor. She has the next step. You will
                want an anti-dragon shield and an antifire in your inventory;
                the next leg has a couple of nasty hits. Your shield is
                already in bank tab 7, I have tagged it. Walk over.
                <span className="demo-cursor ml-1" aria-hidden="true" />
              </p>
              <p className="mt-3 font-mono text-[10px] uppercase tracking-widest text-osrs-muted">
                Routed to deep mode for this question.
              </p>
            </div>
          </div>

          {/* Live activity panel: player-friendly proxy, no model name, no token math. */}
          <aside
            className="border border-osrs-border bg-osrs-bg p-6"
            aria-label="Live activity"
          >
            <p className="mb-1 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
              This turn
            </p>
            <p className="mb-5 text-3xl text-osrs-gold live-glow">
              Deep mode.
            </p>

            <dl className="space-y-3 text-sm">
              <div className="flex justify-between border-b border-osrs-border pb-2">
                <dt className="text-osrs-muted">Tools called</dt>
                <dd className="font-mono text-osrs-text">3</dd>
              </div>
              <div className="flex justify-between border-b border-osrs-border pb-2">
                <dt className="text-osrs-muted">Tier</dt>
                <dd className="font-mono text-osrs-text">Hobbyist</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-osrs-muted">Today</dt>
                <dd className="font-mono text-osrs-success">Plenty left</dd>
              </div>
            </dl>

            <p className="mt-6 font-mono text-[10px] uppercase tracking-widest text-osrs-muted">
              Capped per tier. No surprise bills.
            </p>
          </aside>
        </div>
      </div>
    </section>
  );
}
