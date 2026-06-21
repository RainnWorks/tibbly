type FaqItem = {
  readonly question: string;
  readonly answer: string;
};

/*
 * Eight FAQ entries (IA §2.8 cap). Lowercase Discord-style question
 * wording. Bot question first. Each answer leads with the answer.
 * No model names, no token math, no localhost-server references that
 * suggest anything but the outbound TLS shape.
 */
const FAQS: readonly FaqItem[] = [
  {
    question: "is this a bot? will i get banned?",
    answer:
      "No. Tibbly never synthesises mouse or keyboard input, never moves your character, never clicks for you. The plugin reads game state and shows you advice, exactly the same shape as Quest Helper or WikiSync, which 555K and 302K players run respectively. Jagex's third-party-client posture has tolerated this category for ~10 years. The Terms carry an explicit \"helps you play, never plays for you\" line.",
  },
  {
    question: "is tibbly the companion or the chat?",
    answer:
      "Both. The companion is the surface: a small character that walks with you, watches what you watch, and speaks when there is something to say. The chat is one of the things you can do with it. Power users live in the sidebar chat panel; everyone else lives next to the companion.",
  },
  {
    question: "will tibbly say things i didn't tell it to?",
    answer:
      "Sometimes. Not often. Only when it has something useful or honest to say. You can turn proactive lines off if you'd rather it stay quiet. The default is on but rate-limited, so the companion never becomes Clippy.",
  },
  {
    question: "does it remember me across sessions?",
    answer:
      "Yes, for as long as your subscription is active. Delete your data any time from inside RuneLite or via the data-export endpoint. The companion forgets you cleanly if you ask: /tibbly forget clears remembered facts, /tibbly start over resets the whole companion profile with a confirmation step.",
  },
  {
    question: "how does it know my game state?",
    answer:
      "It runs as a normal RuneLite plugin. The plugin reads the state the client already gives every other plugin (inventory, equipment, quest log, slayer task, location) and ships only what your current question needs over a single outbound TLS WebSocket to our backend. Nothing is screen-scraped. Nothing is keystroke-logged. Full data disclosure lives in the submission package.",
  },
  {
    question: "how much does it cost?",
    answer:
      "Free for 30 messages a day with a watermark. Hobbyist £7 a month for casual daily play. Pro £19 a month for heavy daily play, with deep mode on harder questions. Iron £49 a month for all-in players, with the deepest model escalation on the hardest steps. Each tier has a hard cap sized to how you play. Auto top-up is off by default, so you never get a surprise bill.",
  },
  {
    question: "what about my privacy?",
    answer:
      "Minimum to answer the question and bill the customer. Chat content is retained 30 days, then deleted. Game-state snapshots are in-flight only: they leave RAM the moment the reply ships. You can export everything (GDPR Art. 15) or delete everything (Art. 17) from the dashboard. Full policy at /privacy.",
  },
  {
    question: "do you offer refunds?",
    answer:
      "Yes. UK Consumer Contracts Regulations give you 14 days to cancel for a full refund. After that, Stripe Billing prorates. Email refunds@tibbly.app and it gets handled the same day.",
  },
  {
    question: "is it on the runelite plugin hub?",
    answer:
      "Built for it. The plugin runs a single outbound WSS (no localhost HTTP server, no port binding), exactly the shape the Hub maintainers asked for in the rejection notes on the previous \"OSRS MCP plugin\" PR. Cycling through the submission checklist now; expect Hub presence shortly after launch. Sideload available in the interim.",
  },
  {
    question: "can multiple osrs accounts share one subscription?",
    answer:
      "Yes. One Stripe customer can bind any number of OSRS accounts via pairing codes in the dashboard. Useful for ironman mains + DMM accounts + alts, and for GIM groups that want one shared bill.",
  },
  {
    question: "does it work for leagues and dmm?",
    answer:
      "Yes. Live game state is live game state. Seasonal modes (Leagues 6 Demonic Pacts, DMM tournaments, Speedrunning worlds) work out of the box, and Tibbly knows the relevant relic and area rules when they ship.",
  },
];

export function FAQ() {
  return (
    <section
      data-testid="faq"
      id="faq"
      className="border-b border-osrs-border px-6 py-24"
    >
      <div className="mx-auto max-w-3xl">
        <div className="mb-12 text-center">
          <h2 className="text-3xl text-osrs-gold md:text-4xl">
            Frequently asked.
          </h2>
        </div>
        {/* Vertical accordion inside the chatbox frame (IA §2.8). */}
        <div className="border border-osrs-border bg-osrs-parchment/25 p-2 shadow-[inset_0_0_18px_rgba(0,0,0,0.35)]">
          <ul className="space-y-px" data-testid="faq-list">
            {FAQS.map((item) => (
              <li
                key={item.question}
                className="bg-osrs-surface transition hover:bg-osrs-parchment/40"
              >
                <details className="group">
                  <summary className="flex cursor-pointer list-none items-center justify-between p-4 font-bold text-osrs-gold transition group-open:bg-osrs-bg">
                    <span>{item.question}</span>
                    <span
                      aria-hidden="true"
                      className="ml-4 font-mono text-xs text-osrs-gold-dim transition group-open:rotate-45"
                    >
                      +
                    </span>
                  </summary>
                  <div className="border-t border-osrs-border p-4 text-osrs-text/85">
                    {item.answer}
                  </div>
                </details>
              </li>
            ))}
          </ul>
        </div>
        <p className="mt-10 text-center text-sm text-osrs-muted">
          More questions?{" "}
          <a
            href="mailto:hello@tibbly.app"
            className="text-osrs-gold-dim hover:text-osrs-gold"
          >
            hello@tibbly.app
          </a>
          .
        </p>
      </div>
    </section>
  );
}
