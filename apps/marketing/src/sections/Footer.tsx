import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";

export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer
      data-testid="footer"
      className="bg-osrs-bg px-6 py-16 text-sm text-osrs-muted"
    >
      <div className="mx-auto grid max-w-6xl gap-10 md:grid-cols-4">
        <div>
          <div className="mb-3 flex items-center gap-3">
            <img
              src={attackIcon}
              alt=""
              aria-hidden="true"
              className="h-8 w-8 border border-osrs-border bg-osrs-surface p-1"
            />
            <h3 className="font-heading text-2xl text-osrs-gold">
              Tibbly · the OSRS LLM Helper
            </h3>
          </div>
          <p className="text-osrs-text/80">
            The only OSRS co-pilot that sees your game live, billed monthly
            with a hard cap. No API keys, no copy-paste, no botting.
          </p>
          <p className="mt-3 font-mono text-[10px] uppercase tracking-widest text-osrs-muted">
            Helps you play. Never plays for you.
          </p>
        </div>

        <div>
          <h4 className="mb-3 font-heading text-base text-osrs-gold-dim">
            Product
          </h4>
          <ul className="space-y-2">
            <li>
              <a href="#features" className="hover:text-osrs-gold">
                Features
              </a>
            </li>
            <li>
              <a href="#pricing" className="hover:text-osrs-gold">
                Pricing
              </a>
            </li>
            <li>
              <a href="#demo" className="hover:text-osrs-gold">
                Demo
              </a>
            </li>
            <li>
              <a href="#faq" className="hover:text-osrs-gold">
                FAQ
              </a>
            </li>
            <li>
              <a
                href="https://github.com/RainnWorks/osrs-llm-helper"
                className="hover:text-osrs-gold"
                rel="noopener noreferrer"
                target="_blank"
              >
                GitHub
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h4 className="mb-3 font-heading text-base text-osrs-gold-dim">
            Legal
          </h4>
          <ul className="space-y-2">
            <li>
              <a href="/privacy" className="hover:text-osrs-gold">
                Privacy
              </a>
            </li>
            <li>
              <a href="/terms" className="hover:text-osrs-gold">
                Terms
              </a>
            </li>
            <li>
              <a href="/cookies" className="hover:text-osrs-gold">
                Cookies
              </a>
            </li>
            <li>
              <a href="/sub-processors" className="hover:text-osrs-gold">
                Sub-processors
              </a>
            </li>
            <li>
              <a href="/data-retention" className="hover:text-osrs-gold">
                Data retention
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h4 className="mb-3 font-heading text-base text-osrs-gold-dim">
            Attribution
          </h4>
          <p className="text-xs">
            <strong className="text-osrs-text/90">Not affiliated with Jagex.</strong>{" "}
            RuneScape and Old School RuneScape are trademarks of Jagex Limited.
          </p>
          <p className="mt-3 text-xs">
            Skill icons from the{" "}
            <a
              href="https://github.com/runelite/runelite"
              className="text-osrs-gold-dim hover:text-osrs-gold"
              rel="noopener noreferrer"
              target="_blank"
            >
              RuneLite project
            </a>{" "}
            (BSD-2-Clause). RuneScape font from{" "}
            <a
              href="https://github.com/RuneStar/fonts"
              className="text-osrs-gold-dim hover:text-osrs-gold"
              rel="noopener noreferrer"
              target="_blank"
            >
              RuneStar/fonts
            </a>{" "}
            (CC0 1.0). Full NOTICE on GitHub.
          </p>
        </div>
      </div>

      <div className="mx-auto mt-12 max-w-6xl border-t border-osrs-border pt-6 text-center text-xs">
        <p>
          &copy; {year} Tibbly · the OSRS LLM Helper. All product names,
          logos, and brands are property of their respective owners.
        </p>
      </div>
    </footer>
  );
}
