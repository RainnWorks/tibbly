import questIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import clueIcon from "@osrs-llm-helper/osrs-assets/skill_icons/thieving.png";
import slayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/slayer.png";

type Card = {
  readonly title: string;
  readonly icon: string;
  readonly painSentence: string;
  readonly tibblyAnswer: string;
};

const CARDS: readonly Card[] = [
  {
    title: "Stuck on a quest?",
    icon: questIcon,
    painSentence:
      "Quest Helper sends 555K players a step ahead, but it cannot see your bank, your inventory, or the antifire you forgot to grab.",
    tibblyAnswer:
      "Tibbly already does. Asks once, answers with the items you actually own and the route you actually need.",
  },
  {
    title: "Lost on a clue?",
    icon: clueIcon,
    painSentence:
      "Emote clue, anagram, cryptic. Every step is a wiki tab and a second-monitor squint while your character idles in the Wilderness.",
    tibblyAnswer:
      "Tibbly reads the scroll, names the NPC, marks the tile. You move. No tab-out.",
  },
  {
    title: "Tracking slayer XP?",
    icon: slayerIcon,
    painSentence:
      "Dust devils or smoke devils? Cannon spot? Prayer flick at 56% HP? The wiki doesn't know your gear; your friends are AFK.",
    tibblyAnswer:
      "Tibbly knows the task, the gear in your bank, the prayer pots in your inventory. One answer. No alt-tabs.",
  },
];

export function ProblemSolution() {
  return (
    <section
      data-testid="problem-solution"
      className="border-b border-osrs-border bg-osrs-surface/40 px-6 py-24"
    >
      <div className="mx-auto max-w-6xl">
        {/* Hidden-but-present headings keep semantic structure + satisfy the existing
            test contract while the visible h2 sells the framing. */}
        <h2 className="sr-only">The problem</h2>
        <h2 className="sr-only">The solution</h2>

        <div className="mb-16 text-center">
          <p className="mb-4 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            The wiki tab-out tax
          </p>
          <p className="mx-auto max-w-3xl text-2xl font-heading text-osrs-gold md:text-3xl">
            Every quest, every clue, every slayer task. The answer lives in a
            tab you do not have open.
          </p>
          <p className="mx-auto mt-3 max-w-2xl text-base text-osrs-text/80">
            Tibbly already read the wiki. Tibbly already sees your inventory.
            Ask once. Keep playing.
          </p>
        </div>

        <ul className="grid gap-6 md:grid-cols-3" data-testid="problem-cards">
          {CARDS.map((card) => (
            <li
              key={card.title}
              className="group relative border border-osrs-border bg-osrs-bg p-6 transition hover:border-osrs-gold-dim"
            >
              <div className="mb-5 flex items-center gap-4">
                <div className="flex h-14 w-14 items-center justify-center border border-osrs-border bg-osrs-surface">
                  <img
                    src={card.icon}
                    alt=""
                    className="sprite-bob h-9 w-9"
                    aria-hidden="true"
                  />
                </div>
                <h3 className="text-xl md:text-2xl">{card.title}</h3>
              </div>
              <p className="mb-4 text-sm text-osrs-text/80">
                {card.painSentence}
              </p>
              <p className="border-l-2 border-osrs-gold/60 bg-osrs-gold/5 px-3 py-2 text-sm text-osrs-text/95">
                <span className="font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
                  Tibbly
                </span>
                <br />
                {card.tibblyAnswer}
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
