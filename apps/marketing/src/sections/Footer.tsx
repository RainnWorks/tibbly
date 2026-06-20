export function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer
      data-testid="footer"
      className="bg-osrs-bg px-6 py-12 text-sm text-osrs-muted"
    >
      <div className="mx-auto grid max-w-6xl gap-8 md:grid-cols-4">
        <div>
          <h3 className="mb-3 font-heading text-lg text-osrs-gold">
            OSRS LLM Helper
          </h3>
          <p>Lorem ipsum: tagline placeholder.</p>
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
          </ul>
        </div>
        <div>
          <h4 className="mb-3 font-heading text-base text-osrs-gold-dim">
            Compliance
          </h4>
          <p>
            Lorem ipsum: not affiliated with Jagex. RuneScape and Old School
            RuneScape are trademarks of Jagex Limited.
          </p>
        </div>
      </div>
      <div className="mx-auto mt-10 max-w-6xl border-t border-osrs-border pt-6 text-center text-xs">
        &copy; {year} OSRS LLM Helper. Lorem ipsum rights reserved.
      </div>
    </footer>
  );
}
