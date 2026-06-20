import type { ReactNode } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Link } from "@tanstack/react-router";

const FEATURES: ReadonlyArray<{ to: string; title: string; blurb: string }> = [
  {
    to: "/pair",
    title: "Pair plugin",
    blurb: "Bind your RuneLite device key to this account using a one-time code.",
  },
  {
    to: "/usage",
    title: "Token usage",
    blurb: "Track every chat turn — model, tokens, and credit cost — over time.",
  },
  {
    to: "/accounts",
    title: "OSRS accounts",
    blurb: "Manage which characters share your subscription.",
  },
  {
    to: "/billing",
    title: "Billing",
    blurb: "Plan, top-up credits, and open the Stripe customer portal.",
  },
];

export function RouteRoot(): ReactNode {
  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          Welcome, adventurer
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Your live in-game OSRS co-pilot lives here. Pair your plugin, top up
          credits, and watch the agent in action.
        </p>
        <div className="osrs-divider" />
        <div className="flex gap-2">
          <Link
            to="/pair"
            className="inline-flex h-10 items-center justify-center rounded-md border border-[color:var(--color-osrs-gold)] bg-[color:var(--color-osrs-gold)] px-4 text-sm font-medium text-[color:var(--color-osrs-bg)] hover:brightness-110"
          >
            Pair plugin
          </Link>
          <Link
            to="/billing"
            className="inline-flex h-10 items-center justify-center rounded-md border border-[color:var(--color-osrs-panel-border)] bg-[color:var(--color-osrs-panel)] px-4 text-sm font-medium text-[color:var(--color-osrs-gold-soft)] hover:border-[color:var(--color-osrs-gold-soft)]"
          >
            Manage billing
          </Link>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {FEATURES.map((feature) => (
          <Link key={feature.to} to={feature.to} className="group">
            <Card className="transition-colors group-hover:border-[color:var(--color-osrs-gold-soft)]">
              <CardHeader>
                <CardTitle>{feature.title}</CardTitle>
                <CardDescription>{feature.blurb}</CardDescription>
              </CardHeader>
              <CardContent>
                <span className="text-sm text-[color:var(--color-osrs-gold)] group-hover:underline">
                  Open &rarr;
                </span>
              </CardContent>
            </Card>
          </Link>
        ))}
      </section>
    </div>
  );
}
