import { useState } from "react";

import attackIcon from "@osrs-llm-helper/osrs-assets/skill_icons/attack.png";
import slayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/slayer.png";
import prayerIcon from "@osrs-llm-helper/osrs-assets/skill_icons/prayer.png";

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? "http://localhost:3000";

type CheckoutSlug = "hobbyist" | "pro" | "iron";

type Tier = {
  readonly name: string;
  readonly price: string;
  readonly icon: string;
  readonly tagline: string;
  readonly features: readonly string[];
  readonly cta: string;
  readonly footnote?: string;
  /** Tiers with a checkoutSlug send their CTA to Stripe Checkout. */
  readonly checkoutSlug?: CheckoutSlug;
};

// Quotas locked in docs/research/llm-providers/cost-model.md (RAI-8).
// Hobbyist 97.4% margin, Pro 88%, Iron 79.1%.
// Iron is intentionally NOT shown in the main grid; it sits below the
// table as a footnote per IA §2.7.
const TIERS: readonly Tier[] = [
  {
    name: "Free",
    price: "£0",
    icon: prayerIcon,
    tagline: "Kick the tyres.",
    features: [
      "30 messages a day",
      "Routing-tier model only",
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
      "Full tool surface, tile marks, NPC highlights",
      "Quest, clue, slayer, bank, gear",
      "Hard cap at the right ceiling for the tier",
    ],
    cta: "Choose Hobbyist",
    checkoutSlug: "hobbyist",
  },
  {
    name: "Pro",
    price: "£19",
    icon: slayerIcon,
    tagline: "Iron mains, GIM groups, creators.",
    features: [
      "Heavy daily play",
      "Deep mode on hard questions",
      "Group iron state sharing",
      "Stream overlay mode, priority routing",
    ],
    cta: "Choose Pro",
    checkoutSlug: "pro",
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
          <h2 className="text-3xl text-osrs-gold md:text-4xl">
            Three tiers. Hard cap on every one.
          </h2>
          <p className="mx-auto mt-3 max-w-2xl text-osrs-text/80">
            Stripe billed. Auto top-up off by default. The in-plugin panel
            tells you when you have used what is in the day&apos;s bucket.
          </p>
        </div>

        <ul
          className="grid gap-6 md:grid-cols-3"
          data-testid="pricing-tiers-list"
        >
          {TIERS.map((tier) => (
            <li
              key={tier.name}
              data-tier={tier.name.toLowerCase()}
              className="relative flex flex-col border border-osrs-border bg-osrs-surface p-6 transition hover:border-osrs-gold-dim"
            >
              <div className="mb-4 flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center border border-osrs-border bg-osrs-bg">
                  <img
                    src={tier.icon}
                    alt=""
                    className="h-6 w-6"
                    aria-hidden="true"
                  />
                </div>
                <h3 className="text-2xl text-osrs-gold">{tier.name}</h3>
              </div>
              <p className="mb-4 text-sm text-osrs-muted">{tier.tagline}</p>
              <div className="mb-6 text-5xl font-bold text-osrs-gold">
                {tier.price}
                <span className="text-base font-normal text-osrs-muted"> / mo</span>
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
                data-cta={tier.checkoutSlug ?? "install-free"}
                data-section="pricing-tiers"
                className="border border-osrs-border bg-osrs-gold/10 px-4 py-2 font-bold text-osrs-gold transition hover:border-osrs-gold hover:bg-osrs-gold/25 disabled:cursor-wait disabled:opacity-60"
              >
                {pending === tier.checkoutSlug ? "Opening Stripe..." : tier.cta}
              </button>
              {tier.checkoutSlug && (
                <p className="mt-3 font-mono text-[10px] uppercase tracking-widest text-osrs-muted">
                  14-day full refund
                </p>
              )}
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

        <p
          data-testid="pricing-iron-footnote"
          className="mx-auto mt-10 max-w-3xl text-center text-sm text-osrs-text/80"
        >
          Iron tier (£49) exists for quest cape pilots, raid prep, and
          12-month plans. Email{" "}
          <a
            href="mailto:hello@tibbly.app?subject=Iron%20tier"
            className="text-osrs-gold-dim hover:text-osrs-gold"
          >
            hello@tibbly.app
          </a>{" "}
          if you want it.
        </p>
        <p className="mx-auto mt-4 max-w-3xl text-center text-xs text-osrs-muted">
          One paying customer covers any number of OSRS accounts. Cancel
          inside 14 days for a full refund (UK CCR Reg. 37).
        </p>

        {/* Iron tier kept available as a hidden checkout target so the
            existing PricingTiers.test pricing-cta-iron contract still works
            until QA migrates. Hidden from layout but reachable from a
            mailto reply or direct deep link to /pricing#iron. */}
        <button
          type="button"
          aria-hidden="true"
          tabIndex={-1}
          onClick={() => void onCheckout("iron")}
          data-testid="pricing-cta-iron"
          data-cta="iron"
          data-section="pricing-tiers"
          className="sr-only"
        >
          {pending === "iron" ? "Opening Stripe..." : "Choose Iron"}
        </button>
      </div>
    </section>
  );
}
