import { CompanionSprite } from "./CompanionSprite";
import { COMPANION_MAGICAL_LINES } from "./Hero";

/*
 * Companion section: the magical-moment payoff.
 *
 * The hero teases the companion with a rotating sticker. This section
 * pays it off with five concrete vignettes (player situation → one
 * companion line in voice) and the small "what makes it alive"
 * capsule. The intent is to make the reader say "I want that" before
 * they reach pricing.
 *
 * Layout discipline (taste-skill §4.3 / §4.7):
 * - Vertical staggered vignettes, deliberately asymmetric. Not a 3x2
 *   grid. The asymmetry is the visual variety the IA wants at
 *   DESIGN_VARIANCE 7.
 * - Single eyebrow on the section header. The hero already spent its
 *   eyebrow allowance for the page; this is the second of the allowed
 *   ceil(9/3)=3 eyebrows.
 * - The capsule is a small four-bullet aside, not a feature grid.
 * - The opinionated take ("Tibbly is the only OSRS plugin you'd say
 *   goodbye to.") lands as a pulled quote, in voice.
 */

type Vignette = {
  /** A short description of the in-game situation, set in small-caps. */
  readonly situation: string;
  /** The companion's one line in voice. From EMBODIED_COMPANION §1 + Hero list. */
  readonly line: string;
  /**
   * Vignette alignment. Deliberately mixed so the layout reads as
   * varied rather than as five identical chat rows.
   */
  readonly side: "left" | "right";
};

const VIGNETTES: readonly Vignette[] = [
  {
    situation: "Vorkath, zombified spawn just dropped",
    line: COMPANION_MAGICAL_LINES[0]!,
    side: "left",
  },
  {
    situation: "Fight Caves wave 47, two brews left",
    line: COMPANION_MAGICAL_LINES[1]!,
    side: "right",
  },
  {
    situation: "Re-opens Sins of the Father after a week off",
    line: COMPANION_MAGICAL_LINES[2]!,
    side: "left",
  },
  {
    situation: "Banking for another Vorkath trip",
    line: COMPANION_MAGICAL_LINES[3]!,
    side: "right",
  },
  {
    situation: "Final hit on a hellhound, 99 Slayer level-up just fired",
    line: COMPANION_MAGICAL_LINES[4]!,
    side: "left",
  },
];

/*
 * "What makes it alive" capsule. Four short bullets, each one sentence,
 * names the design floor for the companion. Pulled from
 * docs/product/EMBODIED_COMPANION.md §1, §5.
 */
const ALIVE_BULLETS: readonly { readonly head: string; readonly body: string }[] = [
  {
    head: "Grounded in real state",
    body:
      "It reads what you read. Inventory, prayer, the NPC you are talking to. The line is about what just happened, not about nothing.",
  },
  {
    head: "Remembers across sessions",
    body:
      "Per OSRS account. Your nickname, the quests you said not to spoil, the bosses you've been grinding. Forgets cleanly when you ask.",
  },
  {
    head: "Has dead air",
    body:
      "It does not interrupt. It speaks when there is something useful or honest to say, and otherwise sits and reads a book.",
  },
  {
    head: "Voice changes over time",
    body:
      "Tell it to stop using British slang. Tell it to call you Boaty. A month in it sounds like itself, not like a generic assistant.",
  },
];

export function Companion() {
  return (
    <section
      data-testid="companion"
      id="companion"
      className="relative overflow-hidden border-b border-osrs-border px-6 py-24"
    >
      <div className="mx-auto max-w-5xl">
        {/* Section header. Eyebrow + H2 in the brand voice. */}
        <header className="mb-16 text-center">
          <p className="mb-3 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            the magical moment
          </p>
          <h2 className="text-3xl text-osrs-gold md:text-4xl">
            Your OSRS friend who actually knows what is going on.
          </h2>
          <p className="mx-auto mt-4 max-w-2xl text-osrs-text/85">
            Tibbly is a small floating Probe that walks beside you in the
            RuneLite overlay. It watches what you watch. It speaks when
            there is something to say.
          </p>
          {/*
            Honest framing (RAI-73 / audit §What bounces me #7). The
            visual is one Probe in four chassis tints; the differentiation
            is voice. Surfaced up front so the in-plugin config panel is
            not a surprise.
          */}
          <p
            data-testid="companion-honest-framing"
            className="mx-auto mt-3 max-w-2xl font-mono text-xs uppercase tracking-[0.32em] text-osrs-gold-dim"
          >
            same Probe. four voices. you pick how it talks to you.
          </p>
        </header>

        {/*
         * Five vignettes in a staggered single-column rhythm. Alternating
         * sides keeps the layout asymmetric without becoming a zigzag
         * spec sheet. The exchange shape on each row mimics a chat:
         * dimmed small-caps situation, then the companion line.
         */}
        <ol
          data-testid="companion-vignettes"
          className="relative grid gap-12 md:gap-16"
        >
          {VIGNETTES.map((v, idx) => (
            <li
              key={v.line}
              data-testid="companion-vignette"
              data-side={v.side}
              className={[
                "grid gap-5 md:grid-cols-12 md:items-start",
                v.side === "left" ? "md:text-left" : "md:text-right",
              ].join(" ")}
            >
              {v.side === "left" ? (
                <>
                  <div className="md:col-span-1 md:flex md:justify-start">
                    <CompanionSprite
                      size={56}
                      label=""
                      idle={false}
                      className="opacity-80"
                    />
                  </div>
                  <div className="md:col-span-11">
                    <p className="mb-2 font-mono text-[11px] uppercase tracking-[0.32em] text-osrs-muted">
                      <span aria-hidden="true" className="mr-2 text-osrs-gold-dim">
                        {String(idx + 1).padStart(2, "0")}
                      </span>
                      {v.situation}
                    </p>
                    <p
                      data-testid="companion-vignette-line"
                      className="text-xl text-osrs-text/95 md:text-2xl md:leading-snug"
                    >
                      <span className="text-osrs-gold">&ldquo;</span>
                      {v.line}
                      <span className="text-osrs-gold">&rdquo;</span>
                    </p>
                  </div>
                </>
              ) : (
                <>
                  <div className="md:col-span-11 md:col-start-1">
                    <p className="mb-2 font-mono text-[11px] uppercase tracking-[0.32em] text-osrs-muted">
                      <span aria-hidden="true" className="mr-2 text-osrs-gold-dim">
                        {String(idx + 1).padStart(2, "0")}
                      </span>
                      {v.situation}
                    </p>
                    <p
                      data-testid="companion-vignette-line"
                      className="text-xl text-osrs-text/95 md:text-2xl md:leading-snug"
                    >
                      <span className="text-osrs-gold">&ldquo;</span>
                      {v.line}
                      <span className="text-osrs-gold">&rdquo;</span>
                    </p>
                  </div>
                  <div className="md:col-span-1 md:flex md:justify-end">
                    <CompanionSprite
                      size={56}
                      label=""
                      idle={false}
                      className="opacity-80"
                    />
                  </div>
                </>
              )}
            </li>
          ))}
        </ol>

        {/*
         * What makes it alive. Four short bullets, two-column grid that
         * collapses to one on mobile. Named the design floor, not a
         * feature list, an aside.
         */}
        <aside
          data-testid="companion-alive-capsule"
          aria-labelledby="companion-alive-heading"
          className="mt-20 border border-osrs-border bg-osrs-parchment/25 p-8 shadow-[inset_0_0_24px_rgba(0,0,0,0.45)]"
        >
          <h3
            id="companion-alive-heading"
            className="mb-6 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim"
          >
            what makes it feel alive
          </h3>
          <ul
            data-testid="companion-alive-bullets"
            className="grid gap-x-10 gap-y-6 sm:grid-cols-2"
          >
            {ALIVE_BULLETS.map((b) => (
              <li key={b.head}>
                <p className="mb-1 text-base font-bold text-osrs-gold">
                  {b.head}
                </p>
                <p className="text-sm text-osrs-text/85">{b.body}</p>
              </li>
            ))}
          </ul>
        </aside>

        {/*
         * Opinionated take. One line, no support copy. The "you'd say
         * goodbye to" framing names the relationship product directly.
         */}
        <p
          data-testid="companion-opinion"
          className="mx-auto mt-16 max-w-3xl text-center text-2xl text-osrs-gold md:text-3xl"
        >
          Tibbly is the only OSRS plugin you'd say goodbye to.
        </p>
      </div>
    </section>
  );
}
