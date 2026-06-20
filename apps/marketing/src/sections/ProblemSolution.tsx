export function ProblemSolution() {
  return (
    <section
      data-testid="problem-solution"
      className="border-b border-osrs-border bg-osrs-surface/40 px-6 py-20"
    >
      <div className="mx-auto grid max-w-6xl gap-12 md:grid-cols-2">
        <div>
          <h2 className="mb-6 text-3xl">The problem</h2>
          <p className="mb-4 text-osrs-text/90">
            Lorem ipsum: every quest, every boss, every clue scroll — answers
            live in a wiki tab that's never the one you're looking at. The
            attention cost compounds over a long session.
          </p>
          <ul className="space-y-3 text-osrs-muted">
            <li>- 555K players use Quest Helper because they need next-step prompts.</li>
            <li>- 302K rely on WikiSync to keep wiki data in sync with their account.</li>
            <li>- Boss mechanics live in 8+ hand-written plugins, one per fight.</li>
          </ul>
        </div>
        <div>
          <h2 className="mb-6 text-3xl">The solution</h2>
          <p className="mb-4 text-osrs-text/90">
            Lorem ipsum: a chat that already knows your bank, gear, inventory,
            quest log, slayer task, and location. Ask. Get an answer. Keep
            playing.
          </p>
          <ul className="space-y-3 text-osrs-muted">
            <li>+ Live game state, in-client, every turn.</li>
            <li>+ Highlights NPCs, marks tiles, points you at the right object.</li>
            <li>+ Works on every quest and every boss, including the ones released this week.</li>
          </ul>
        </div>
      </div>
    </section>
  );
}
