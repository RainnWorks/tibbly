import type { ReactNode } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { apiFetch } from "@/lib/api";

type Tier = {
  readonly id: "hobbyist" | "pro" | "iron";
  readonly name: string;
  readonly price: string;
  readonly blurb: string;
  readonly features: ReadonlyArray<string>;
};

// Defaults from NORTH_STAR — adjust as we learn pricing.
const TIERS: ReadonlyArray<Tier> = [
  {
    id: "hobbyist",
    name: "Hobbyist",
    price: "$7 / month",
    blurb: "For the after-work skiller.",
    features: ["Haiku 4.5 model", "300 turns / day", "1 OSRS account"],
  },
  {
    id: "pro",
    name: "Pro",
    price: "$19 / month",
    blurb: "For serious mains and content creators.",
    features: ["Sonnet 4.6 model", "Unlimited turns", "3 OSRS accounts"],
  },
  {
    id: "iron",
    name: "Iron",
    price: "$49 / month",
    blurb: "For GIM groups, quest cape pushers, deep PvM.",
    features: ["Opus 4.7 model", "Unlimited turns", "10 OSRS accounts"],
  },
];

interface PortalResponse {
  readonly url: string;
}

export async function openBillingPortal(): Promise<PortalResponse> {
  return apiFetch<PortalResponse>("/v1/billing/portal", {
    method: "POST",
    authenticated: true,
  });
}

export function RouteBilling(): ReactNode {
  const { push } = useToast();

  const portalMutation = useMutation({
    mutationFn: openBillingPortal,
    onSuccess: (data) => {
      if (typeof window !== "undefined" && data.url) {
        window.location.href = data.url;
      }
    },
    onError: (err) => {
      push({
        title: "Couldn't open Stripe portal",
        description: err instanceof Error ? err.message : "Try again shortly.",
        variant: "danger",
      });
    },
  });

  function selectTier(tier: Tier): void {
    push({
      title: `Selected ${tier.name}`,
      description: "Checkout link arrives once /v1/billing/checkout lands.",
      variant: "success",
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          Billing
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Pick a plan, top up credits, or jump into the Stripe customer portal.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Manage subscription</CardTitle>
          <CardDescription>
            Update payment method, see past invoices, pause or cancel.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex justify-end">
          <Button
            variant="secondary"
            onClick={() => portalMutation.mutate()}
            disabled={portalMutation.isPending}
          >
            {portalMutation.isPending ? "Opening…" : "Open Stripe portal"}
          </Button>
        </CardContent>
      </Card>

      <section className="grid gap-4 md:grid-cols-3">
        {TIERS.map((tier) => (
          <Card key={tier.id} className="flex flex-col">
            <CardHeader>
              <CardTitle>{tier.name}</CardTitle>
              <CardDescription>{tier.blurb}</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-1 flex-col gap-3">
              <div className="font-osrs text-2xl text-[color:var(--color-osrs-gold)]">
                {tier.price}
              </div>
              <ul className="text-sm text-[color:var(--color-osrs-gold-soft)] space-y-1">
                {tier.features.map((feature) => (
                  <li key={feature}>&middot; {feature}</li>
                ))}
              </ul>
              <Button className="mt-auto" onClick={() => selectTier(tier)}>
                Choose {tier.name}
              </Button>
            </CardContent>
          </Card>
        ))}
      </section>
    </div>
  );
}
