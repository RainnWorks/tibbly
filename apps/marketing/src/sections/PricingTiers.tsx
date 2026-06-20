type Tier = {
  readonly name: string;
  readonly price: string;
  readonly tagline: string;
  readonly features: readonly string[];
  readonly highlighted?: boolean;
};

const TIERS: readonly Tier[] = [
  {
    name: "Free",
    price: "$0",
    tagline: "Lorem ipsum entry tier.",
    features: [
      "30 messages / day",
      "Haiku model",
      "Watermarked replies",
      "Core tool surface only",
    ],
  },
  {
    name: "Hobbyist",
    price: "$7",
    tagline: "Lorem ipsum casual main account.",
    features: [
      "1,500 messages / month",
      "Sonnet model",
      "Full tool surface",
      "Quest + slayer history",
    ],
    highlighted: true,
  },
  {
    name: "Pro",
    price: "$19",
    tagline: "Lorem ipsum GIM groups and content creators.",
    features: [
      "Unlimited messages",
      "Sonnet + Opus mix",
      "Group iron state sharing",
      "Stream-friendly overlay mode",
    ],
  },
  {
    name: "Iron",
    price: "$49",
    tagline: "Lorem ipsum hardcore tier with Opus access.",
    features: [
      "Unlimited Opus",
      "Priority routing",
      "Custom prompt slot",
      "Direct support",
    ],
  },
];

export function PricingTiers() {
  return (
    <section
      data-testid="pricing-tiers"
      id="pricing"
      className="border-b border-osrs-border px-6 py-20"
    >
      <div className="mx-auto max-w-6xl">
        <h2 className="mb-4 text-center text-3xl md:text-4xl">Pick your tier</h2>
        <p className="mb-12 text-center text-osrs-muted">
          Lorem ipsum pricing subhead — final copy TBD.
        </p>
        <ul
          className="grid gap-6 md:grid-cols-2 lg:grid-cols-4"
          data-testid="pricing-tiers-list"
        >
          {TIERS.map((tier) => (
            <li
              key={tier.name}
              data-highlighted={tier.highlighted ? "true" : "false"}
              className={`flex flex-col border bg-osrs-surface p-6 ${
                tier.highlighted
                  ? "border-osrs-gold"
                  : "border-osrs-border"
              }`}
            >
              <h3 className="mb-1 text-2xl">{tier.name}</h3>
              <p className="mb-4 text-sm text-osrs-muted">{tier.tagline}</p>
              <div className="mb-6 font-heading text-4xl text-osrs-gold">
                {tier.price}
                <span className="text-base text-osrs-muted"> / mo</span>
              </div>
              <ul className="mb-6 flex-1 space-y-2 text-sm">
                {tier.features.map((feature) => (
                  <li key={feature} className="text-osrs-text/85">
                    - {feature}
                  </li>
                ))}
              </ul>
              <button
                type="button"
                className={`border px-4 py-2 font-heading transition ${
                  tier.highlighted
                    ? "border-osrs-gold bg-osrs-gold/10 text-osrs-gold hover:bg-osrs-gold/20"
                    : "border-osrs-border text-osrs-text hover:border-osrs-gold-dim"
                }`}
              >
                Choose {tier.name}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
