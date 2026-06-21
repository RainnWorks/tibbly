/**
 * Free tier strip: standalone, full-width, single giant CTA.
 *
 * Per the canonical IA (docs/marketing/IA.md §2.5): the Free tier exists to
 * convert cold Reddit traffic that will not commit to a paid tier in the
 * first scroll. Placing it under the demo (after the voice has proved itself)
 * and before the inventory grid maximises that conversion.
 *
 * Layout family: full-width parchment panel, smaller pixel-display header,
 * one giant CTA, BYOK escape hatch in mono below.
 */

import overallIcon from "@osrs-llm-helper/osrs-assets/skill_icons/overall.png";

export function FreeTierStrip() {
  return (
    <section
      data-testid="free-tier-strip"
      id="free"
      aria-labelledby="free-tier-heading"
      className="border-b border-osrs-border bg-osrs-parchment/30 px-6 py-24"
    >
      <div className="mx-auto max-w-4xl text-center">
        <div className="mb-8 flex items-center justify-center gap-3">
          <img
            src={overallIcon}
            alt=""
            aria-hidden="true"
            className="h-7 w-7"
          />
          <p className="font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            free, forever
          </p>
        </div>
        <h2
          id="free-tier-heading"
          className="mb-6 text-4xl text-osrs-gold sm:text-5xl"
          style={{ fontFamily: "var(--font-display)" }}
        >
          Start free.
        </h2>
        <p className="mx-auto mb-10 max-w-2xl text-base text-osrs-text/90 md:text-lg">
          Thirty messages a day on the routing-tier model. All the live tools
          work, every quest, every clue, every slayer task. No card, no
          login. Pair the plugin to your account when you want more.
        </p>
        <a
          href="#pricing"
          data-cta="install-plugin"
          data-section="free-tier-strip"
          aria-label="Install the plugin and start free"
          className="inline-flex items-center gap-3 border-2 border-osrs-gold bg-osrs-gold/15 px-10 py-4 text-lg font-bold text-osrs-gold transition hover:bg-osrs-gold/30 hover:shadow-[0_0_28px_rgba(243,199,90,0.4)]"
        >
          <span aria-hidden="true">⚔</span> install the plugin
        </a>
        <p className="mt-8 font-mono text-xs uppercase tracking-widest text-osrs-muted">
          or bring your own provider key for unlimited use. that is a config
          dropdown, not a tier.
        </p>
      </div>
    </section>
  );
}
