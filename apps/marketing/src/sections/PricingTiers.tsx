import { useState } from "react";

import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";
import slayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/slayer.png";
import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";
import constructionIcon from "@osrs-llm-helper/osrs-assets/skill_icons/construction.png";

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? "http://localhost:3000";

type CheckoutSlug = "hobbyist" | "pro" | "iron";

type Tier = {
  readonly name: string;
  readonly price: string;
  readonly icon: string;
  readonly tagline: string;
  readonly features: readonly string[];
  readonly cta: string;
  readonly highlighted?: boolean;
  readonly footnote?: string;
  /** Tiers with a checkoutSlug send their CTA to Stripe Checkout. */
  readonly checkoutSlug?: CheckoutSlug;
};

// Quotas locked in docs/research/llm-providers/cost-model.md (RAI-8).
// Hobbyist 97.4% margin, Pro 88%, Iron 79.1%.
const TIERS: readonly Tier[] = [
  {
    name: "Free",
    price: "£0",
    icon: prayerIcon,
    tagline: "Kick the tyres.",
    features: [
      "30 messages / day",
      "Haiku model · routing-tier only",
      "Watermarked replies",
      "Core tool surface (find_item, ge_price, wiki)",
    ],
    cta: "Install free",
    footnote: "No card. No login. Just install the plugin.",
  },
  {
    name: "Hobbyist",
    price: "£7",
    icon: attackIcon,
    tagline: "Your main account, sorted.",
    features: [
      "Casual daily play, no maths",
      "Haiku 4.5 · fast turns",
      "Full tool surface · tile marks, NPC highlights",
      "Quest, clue, slayer, bank, gear",
    ],
    cta: "Choose Hobbyist",
    highlighted: true,
    checkoutSlug: "hobbyist",
  },
  {
    name: "Pro",
    price: "£19",
    icon: slayerIcon,
    tagline: "Iron mains, GIM groups, creators.",
    features: [
      "Heavy daily play",
      "Sonnet 4.6 · deeper reasoning on hard questions",
      "Group iron state sharing",
      "Stream overlay mode · priority routing",
    ],
    cta: "Choose Pro",
    checkoutSlug: "pro",
  },
  {
    name: "Iron",
    price: "£49",
    icon: constructionIcon,
    tagline: "Hardcore. Quest cape pilots.",
    features: [
      "All-in play, no scary counter",
      "Opus 4.7 on the hardest steps",
      "Custom prompt slot · long-horizon plans",
      "Direct support · early access to new tools",
    ],
    cta: "Choose Iron",
    checkoutSlug: "iron",
  },
];

async function startCheckout(slug: CheckoutSlug): Promise<{ url?: string; error?: string }> {
  try {
    const res = await fetch(`${BACKEND_URL}/v1/billing/checkout/${slug}`, {
      method: "POST",
      headers: { accept: "application/json" },
    });
    const body = (await res.json().catch(() => ({}))) as { url?: string; error?: string };
    if (!res.ok || !body.url) {
      return { error: body.error ?? `http_${res.status}` };
    }
    return { url: body.url };
  } catch (err) {
    return { error: err instanceof Error ? err.message : "network_error" };
  }
}

export function PricingTiers() {
  const [pending, setPending] = useState<CheckoutSlug | null>(null);
  const [errorTier, setErrorTier] = useState<CheckoutSlug | null>(null);

  async function onCheckout(slug: CheckoutSlug): Promise<void> {
    setPending(slug);
    setErrorTier(null);
    const { url, error } = await startCheckout(slug);
    if (url) {
      window.location.href = url;
      return;
    }
    setPending(null);
    setErrorTier(slug);
    if (typeof window !== "undefined" && error) {
      // Loud but recoverable: the player tried to pay and we couldn't
      // hand them off to Stripe. Use a soft alert rather than crashing.
      // eslint-disable-next-line no-console
      console.warn(`Tibbly checkout failed for ${slug}:`, error);
    }
  }

  return (
    <section
      data-testid="pricing-tiers"
      id="pricing"
      className="border-b border-osrs-border px-6 py-24"
    >
      <div className="mx-auto max-w-6xl">
        <div className="mb-14 text-center">
          <p className="mb-3 font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            Pricing
          </p>
          <h2 className="text-3xl md:text-4xl">
            Pay monthly. Each tier sized for how you play. Never surprised.
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-osrs-text/80">
            Stripe-billed. Hard cap at the right ceiling for the tier. Auto
            top-up off by default. The in-plugin panel tells you when you've
            used what's in the day's bucket.
          </p>
        </div>

        <ul
          className="grid gap-6 md:grid-cols-2 lg:grid-cols-4"
          data-testid="pricing-tiers-list"
        >
          {TIERS.map((tier) => (
            <li
              key={tier.name}
              data-highlighted={tier.highlighted ? "true" : "false"}
              className={`relative flex flex-col border bg-osrs-surface p-6 transition ${
                tier.highlighted
                  ? "border-osrs-gold shadow-[0_0_24px_rgba(255,204,0,0.25)]"
                  : "border-osrs-border hover:border-osrs-gold-dim"
              }`}
            >
              {tier.highlighted && (
                <span className="absolute -top-3 left-1/2 -translate-x-1/2 border border-osrs-gold bg-osrs-bg px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-osrs-gold">
                  Most picked
                </span>
              )}
              <div className="mb-4 flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center border border-osrs-border bg-osrs-bg">
                  <img
                    src={tier.icon}
                    alt=""
                    className="h-6 w-6"
                    aria-hidden="true"
                  />
                </div>
                <h3 className="text-2xl">{tier.name}</h3>
              </div>
              <p className="mb-4 text-sm text-osrs-muted">{tier.tagline}</p>
              <div className="mb-6 font-heading text-5xl text-osrs-gold">
                {tier.price}
                <span className="text-base text-osrs-muted"> / mo</span>
              </div>
              <ul className="mb-6 flex-1 space-y-2 text-sm">
                {tier.features.map((feature) => (
                  <li
                    key={feature}
                    className="flex gap-2 text-osrs-text/90 before:text-osrs-gold-dim before:content-['+']"
                  >
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>
              {tier.footnote && (
                <p className="mb-3 font-mono text-[10px] uppercase tracking-widest text-osrs-muted">
                  {tier.footnote}
                </p>
              )}
              <button
                type="button"
                onClick={
                  tier.checkoutSlug
                    ? () => void onCheckout(tier.checkoutSlug as CheckoutSlug)
                    : undefined
                }
                disabled={tier.checkoutSlug !== undefined && pending !== null}
                aria-busy={pending === tier.checkoutSlug ? "true" : "false"}
                data-testid={
                  tier.checkoutSlug
                    ? `pricing-cta-${tier.checkoutSlug}`
                    : "pricing-cta-free"
                }
                className={`border px-4 py-2 font-heading transition disabled:cursor-wait disabled:opacity-60 ${
                  tier.highlighted
                    ? "border-osrs-gold bg-osrs-gold/10 text-osrs-gold hover:bg-osrs-gold/25"
                    : "border-osrs-border text-osrs-text hover:border-osrs-gold-dim hover:text-osrs-gold"
                }`}
              >
                {pending === tier.checkoutSlug ? "Opening Stripe…" : tier.cta}
              </button>
              {errorTier === tier.checkoutSlug && (
                <p
                  role="alert"
                  className="mt-2 font-mono text-[10px] uppercase tracking-widest text-red-400"
                >
                  Checkout unavailable. Please try again.
                </p>
              )}
            </li>
          ))}
        </ul>

        <p className="mx-auto mt-10 max-w-3xl text-center text-xs text-osrs-muted">
          One paying customer covers any number of OSRS accounts. Cancel inside
          14 days for a full refund (UK CCR Reg. 37).
        </p>
      </div>
    </section>
  );
}
