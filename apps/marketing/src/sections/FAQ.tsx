type FaqItem = {
  readonly question: string;
  readonly answer: string;
};

const FAQS: readonly FaqItem[] = [
  {
    question: "Is this a bot? Will I get banned?",
    answer:
      "Lorem ipsum: no. We never synthesize mouse/keyboard input. The plugin reads game state and shows you advice. You play the game.",
  },
  {
    question: "Which client does it run in?",
    answer:
      "Lorem ipsum: RuneLite. It's a normal plugin, sandboxed by the client.",
  },
  {
    question: "How does billing work?",
    answer:
      "Lorem ipsum: monthly subscription via Stripe. You can also top up credits. Hard cap means you never get a surprise bill.",
  },
  {
    question: "Can multiple accounts share one subscription?",
    answer:
      "Lorem ipsum: yes. Bind any number of OSRS accounts to one paying customer through the dashboard.",
  },
  {
    question: "Which LLM does it use?",
    answer:
      "Lorem ipsum: Haiku for fast turns, Sonnet for complex ones, Opus on the premium tier for hard quest reasoning.",
  },
  {
    question: "Does it work for Leagues / DMM?",
    answer:
      "Lorem ipsum: yes. Live game state is live game state. Seasonal modes work out of the box.",
  },
];

export function FAQ() {
  return (
    <section
      data-testid="faq"
      id="faq"
      className="border-b border-osrs-border px-6 py-20"
    >
      <div className="mx-auto max-w-3xl">
        <h2 className="mb-12 text-center text-3xl md:text-4xl">
          Frequently asked
        </h2>
        <ul className="space-y-4" data-testid="faq-list">
          {FAQS.map((item) => (
            <li
              key={item.question}
              className="border border-osrs-border bg-osrs-surface"
            >
              <details className="group">
                <summary className="cursor-pointer list-none p-4 font-heading text-osrs-gold transition group-open:bg-osrs-bg">
                  {item.question}
                </summary>
                <div className="border-t border-osrs-border p-4 text-osrs-text/85">
                  {item.answer}
                </div>
              </details>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
