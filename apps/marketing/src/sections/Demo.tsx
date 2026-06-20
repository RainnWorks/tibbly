export function Demo() {
  return (
    <section
      data-testid="demo"
      id="demo"
      className="border-b border-osrs-border bg-osrs-surface/40 px-6 py-20"
    >
      <div className="mx-auto max-w-5xl">
        <h2 className="mb-4 text-center text-3xl md:text-4xl">See it in action</h2>
        <p className="mb-12 text-center text-osrs-muted">
          Lorem ipsum demo subhead — final copy + video TBD.
        </p>
        <div className="grid gap-8 md:grid-cols-2">
          <div className="border border-osrs-border bg-osrs-bg p-6">
            <p className="mb-2 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
              You
            </p>
            <p className="mb-6 text-osrs-text/90">
              "what do I need for the next step of desert treasure 2?"
            </p>
            <p className="mb-2 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
              Helper
            </p>
            <p className="text-osrs-text/90">
              Lorem ipsum: you're at Vardorvis fight, phase 2. Your gear is fine,
              but you're missing a divine super combat — pulled from your bank
              tab 4. Tagging the bank booth in Edgeville. Walk over.
            </p>
          </div>
          <div
            className="flex items-center justify-center border border-osrs-border bg-osrs-bg p-6"
            aria-label="Demo video placeholder"
          >
            <div className="text-center">
              <div className="mb-4 font-heading text-5xl text-osrs-gold">
                {"▶"}
              </div>
              <p className="text-sm text-osrs-muted">
                Demo video placeholder — RAI-29 polish
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
