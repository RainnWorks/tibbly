type FaqItem = {
  readonly question: string;
  readonly answer: string;
};

const FAQS: readonly FaqItem[] = [
  {
    question: "How does it know my game state?",
    answer:
      "Tibbly runs as a normal RuneLite plugin. The plugin reads the state the client already gives every other plugin — inventory, equipment, quest log, slayer task, location — and ships only what your current question needs over a single outbound TLS WebSocket to our backend. Nothing is screen-scraped. Nothing is keystroke-logged. Full data disclosure lives in our submission package.",
  },
  {
    question: "Is this a bot? Will I get banned?",
    answer:
      "No. Tibbly never synthesises mouse or keyboard input, never moves your character, never clicks for you. The plugin reads game state and shows you advice — exactly the same shape as Quest Helper or WikiSync, which 555K and 302K players run respectively. Jagex's third-party-client posture has tolerated this category for ~10 years. We carry an explicit \"helps you play, never plays for you\" line in the Terms.",
  },
  {
    question: "How much does it cost?",
    answer:
      "Free for 30 messages/day with a watermark. Hobbyist £7/month for casual daily play. Pro £19/month for heavy daily play, with Sonnet 4.6 on harder questions. Iron £49/month for all-in players, with Opus 4.7 escalation on the hardest steps. Each tier has a hard cap sized to how you play, and the in-plugin panel tells you when you've used what's in today's bucket. Auto top-up off by default, so you never get a surprise bill.",
  },
  {
    question: "What about my privacy?",
    answer:
      "We collect the minimum to answer the question and bill the customer. Chat content is retained 30 days, then deleted. Game-state snapshots are in-flight only — they leave RAM the moment the reply ships. You can export everything (GDPR Art. 15) or delete everything (Art. 17) from the dashboard. Full policy at /privacy.",
  },
  {
    question: "Which models do you use?",
    answer:
      "Haiku 4.5 routes every turn (cheap, fast intent detection). Sonnet 4.6 answers most turns on Hobbyist and Pro. Opus 4.7 escalates on the Iron tier for quest walkthroughs, raid prep, and complex gear planning. Everything goes through OpenRouter — no provider lock-in, no BYO API key.",
  },
  {
    question: "Do you offer refunds?",
    answer:
      "Yes. UK Consumer Contracts Regulations give you 14 days to cancel for a full refund. After that, Stripe Billing prorates. Email refunds@tibbly.app and we'll handle it in the same day.",
  },
  {
    question: "Is it on the RuneLite Plugin Hub?",
    answer:
      "We're built for it. The plugin runs a single outbound WSS — no localhost HTTP server, no port binding — which is exactly the shape the Hub maintainers asked for in the rejection notes on the previous \"OSRS MCP plugin\" PR. We're cycling through the submission checklist now; expect Hub presence shortly after launch. Sideload available in the interim.",
  },
  {
    question: "Can multiple OSRS accounts share one subscription?",
    answer:
      "Yes. One Stripe customer can bind any number of OSRS accounts via pairing codes in the dashboard. Useful for ironman mains + DMM accounts + alts, and for GIM groups that want one shared bill.",
  },
  {
    question: "Does it work for Leagues and DMM?",
    answer:
      "Yes. Live game state is live game state. Seasonal modes — Leagues 6 Demonic Pacts, DMM tournaments, Speedrunning worlds — work out of the box, and Tibbly knows the relevant relic/area rules when they ship.",
  },
  {
    question: "What if a quest drops next Wednesday?",
    answer:
      "Tibbly leans on live retrieval (wiki API + tool calls) rather than a fine-tuned snapshot. A new quest is queryable the moment the wiki has a page. Beats a frozen weight file by ~3 days every Wednesday update.",
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
          <p className="mb-3 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            Things players ask
          </p>
          <h2 className="text-3xl md:text-4xl">Frequently asked.</h2>
        </div>
        <ul className="space-y-3" data-testid="faq-list">
          {FAQS.map((item) => (
            <li
              key={item.question}
              className="border border-osrs-border bg-osrs-surface transition hover:border-osrs-gold-dim"
            >
              <details className="group">
                <summary className="flex cursor-pointer list-none items-center justify-between p-4 font-heading text-osrs-gold transition group-open:bg-osrs-bg">
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
        <p className="mt-10 text-center text-sm text-osrs-muted">
          More questions?{" "}
          <a href="mailto:hello@tibbly.app" className="text-osrs-gold-dim hover:text-osrs-gold">
            hello@tibbly.app
          </a>
          .
        </p>
      </div>
    </section>
  );
}
