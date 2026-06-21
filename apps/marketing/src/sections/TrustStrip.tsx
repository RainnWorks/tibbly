/**
 * Trust strip: the no-automation guarantee.
 *
 * Job per the canonical IA (docs/marketing/IA.md §2.3): close the Jagex-ban
 * objection within 200ms of the hero finishing. Three monospace claims,
 * separated by hairlines, each linking out to the doc or file that proves it.
 *
 * Layout family: full-width parchment strip, no cards, hairline dividers.
 * Lives directly after Hero. Trust before features (Principle 1).
 */

type Claim = {
  readonly key: string;
  readonly body: string;
  readonly href: string;
  readonly label: string;
};

const CLAIMS: readonly Claim[] = [
  {
    key: "read-only",
    body: "read-only. reads game state via the same RuneLite plugin APIs every other RuneLite plugin uses.",
    href: "https://github.com/runelite/runelite/wiki/Using-the-RuneLite-API",
    label: "RuneLite API docs",
  },
  {
    key: "no-input",
    body: "no input synthesis. never moves your mouse, never types in chat, never clicks a tile.",
    href: "https://github.com/RainnWorks/osrs-llm-helper",
    label: "plugin source",
  },
  {
    key: "outbound-only",
    body: "outbound only. one TLS connection out to our backend. no localhost server, no listening port.",
    href: "https://github.com/RainnWorks/osrs-llm-helper",
    label: "network spec",
  },
];

export function TrustStrip() {
  return (
    <section
      data-testid="trust-strip"
      id="trust"
      aria-labelledby="trust-strip-heading"
      className="border-b border-osrs-border bg-osrs-parchment/20 px-6 py-16"
    >
      <div className="mx-auto max-w-6xl">
        <h2
          id="trust-strip-heading"
          className="mb-10 text-2xl text-osrs-gold md:text-3xl"
        >
          You play. Tibbly watches.
        </h2>
        <ul
          data-testid="trust-strip-list"
          className="grid divide-y divide-osrs-border border-y border-osrs-border md:grid-cols-3 md:divide-x md:divide-y-0"
        >
          {CLAIMS.map((claim) => (
            <li key={claim.key} className="px-5 py-6">
              <p className="font-mono text-sm leading-relaxed text-osrs-text/95 md:text-[0.95rem]">
                {claim.body}
              </p>
              <a
                href={claim.href}
                rel="noopener noreferrer"
                target="_blank"
                className="mt-3 inline-block font-mono text-[11px] uppercase tracking-widest text-osrs-gold-dim transition hover:text-osrs-gold"
              >
                {claim.label} {"->"}
              </a>
            </li>
          ))}
        </ul>
        <p className="mt-8 max-w-3xl text-sm text-osrs-muted">
          The plugin source is on GitHub. Every egress is logged inside the
          plugin.{" "}
          <a
            href="https://github.com/RainnWorks/osrs-llm-helper"
            rel="noopener noreferrer"
            target="_blank"
            className="text-osrs-gold-dim hover:text-osrs-gold"
          >
            Read the no-automation guarantee.
          </a>
        </p>
      </div>
    </section>
  );
}
