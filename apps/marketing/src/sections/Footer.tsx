import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";

export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer
      data-testid="footer"
      className="bg-osrs-bg px-6 py-16 text-sm text-osrs-muted"
    >
      <div className="mx-auto grid max-w-6xl gap-10 md:grid-cols-2">
        {/* Left: wordmark + voice line + Jagex disclaimer. */}
        <div>
          <div className="mb-3 flex items-center gap-3">
            <img
              src={attackIcon}
              alt=""
              aria-hidden="true"
              className="h-8 w-8 border border-osrs-border bg-osrs-surface p-1"
            />
            <h3 className="text-2xl text-osrs-gold">
              Tibbly · the OSRS LLM Helper
            </h3>
          </div>
          <p className="text-osrs-text/80">
            Helps you play. Never plays for you.
          </p>
          <p className="mt-4 text-xs">
            <strong className="text-osrs-text/90">
              Not affiliated with Jagex Ltd.
            </strong>{" "}
            RuneScape and Old School RuneScape are trademarks of Jagex
            Limited.
          </p>
        </div>

        {/* Right: contact + links + legal. */}
        <div className="md:text-right">
          <ul className="space-y-2 md:flex md:flex-col md:items-end">
            <li>
              <a
                href="mailto:hello@tibbly.app"
                className="hover:text-osrs-gold"
              >
                hello@tibbly.app
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
          </ul>
        </div>
      </div>

      <div className="mx-auto mt-12 max-w-6xl border-t border-osrs-border pt-6 text-center text-xs">
        <p>
          &copy; {year} Tibbly. RuneStar CC0 fonts. RuneLite BSD-2 sprites.
        </p>
      </div>
    </footer>
  );
}
