import { useQuery } from "@tanstack/react-query";
import { type ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatTile } from "@/components/ui/stat-tile";
import { Badge } from "@/components/ui/badge";
import { apiFetch } from "@/lib/api";
import { formatMicroUsd, formatPence, formatTokens } from "@/lib/format";

interface SpendResponse {
  ok: true;
  windows: {
    today: { spendMicroUsd: number };
    week: { spendMicroUsd: number };
    month: { spendMicroUsd: number };
  };
  byModelMonth: Record<
    string,
    {
      spendMicroUsd: number;
      promptTokens: number;
      completionTokens: number;
      messageCount: number;
    }
  >;
}

interface RevenueResponse {
  ok: true;
  currency: "gbp";
  mrrPence: number;
  activeSubscriptions: number;
  tierCounts: Record<string, number>;
  tierPricePence: Record<string, number>;
  approxToday: { revenuePence: number };
  approxWeek: { revenuePence: number };
  approxMonth: { revenuePence: number };
}

export function RouteOpenRouter(): ReactNode {
  const spend = useQuery({
    queryKey: ["admin.openrouter.spend"],
    queryFn: () => apiFetch<SpendResponse>("/admin/openrouter/spend"),
    refetchInterval: 60_000,
  });
  const revenue = useQuery({
    queryKey: ["admin.openrouter.revenue"],
    queryFn: () => apiFetch<RevenueResponse>("/admin/openrouter/revenue"),
    refetchInterval: 60_000,
  });

  const todaySpend = spend.data?.windows.today.spendMicroUsd ?? 0;
  const monthSpend = spend.data?.windows.month.spendMicroUsd ?? 0;
  const monthRevenuePence = revenue.data?.approxMonth.revenuePence ?? 0;
  // Spend is micro-USD; revenue is pence GBP. Convert revenue to micro-USD
  // for the margin ratio at a flat 1.25 USD/GBP (rough mid-rate; the ops
  // console shows margin as an indicator, not a finance-grade figure).
  // See docs/research/llm-providers/cost-model.md follow-ups for live FX.
  const USD_PER_GBP_RATE = 1.25;
  const monthRevenueMicroUsd = Math.round(
    monthRevenuePence * 10_000 * USD_PER_GBP_RATE,
  );
  const margin =
    monthRevenueMicroUsd > 0
      ? Math.round(((monthRevenueMicroUsd - monthSpend) / monthRevenueMicroUsd) * 1000) / 10
      : null;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatTile
          label="spend today"
          value={formatMicroUsd(todaySpend)}
          tone="warn"
        />
        <StatTile
          label="spend (30d)"
          value={formatMicroUsd(monthSpend)}
        />
        <StatTile
          label="revenue (30d, approx)"
          value={formatPence(monthRevenuePence)}
          tone="ok"
        />
        <StatTile
          label="gross margin (30d)"
          value={margin === null ? "..." : `${margin}%`}
          tone={margin !== null && margin > 0 ? "ok" : "danger"}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>per-model spend (30d)</CardTitle>
          <Badge tone="neutral">cost.ts price book</Badge>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
              <tr className="border-b border-[var(--color-ops-border)]">
                <th className="text-left px-2 py-1 font-medium">model</th>
                <th className="text-right px-2 py-1 font-medium">messages</th>
                <th className="text-right px-2 py-1 font-medium">tokens in</th>
                <th className="text-right px-2 py-1 font-medium">tokens out</th>
                <th className="text-right px-2 py-1 font-medium">spend</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(spend.data?.byModelMonth ?? {}).length === 0 ? (
                <tr>
                  <td
                    colSpan={5}
                    className="px-2 py-6 text-center text-xs text-[var(--color-ops-text-faint)] font-mono"
                  >
                    no spend in the last 30 days.
                  </td>
                </tr>
              ) : (
                Object.entries(spend.data?.byModelMonth ?? {})
                  .sort(([, a], [, b]) => b.spendMicroUsd - a.spendMicroUsd)
                  .map(([model, v]) => (
                    <tr
                      key={model}
                      className="border-b border-[var(--color-ops-border)]/40"
                    >
                      <td className="px-2 py-1 font-mono text-[var(--color-ops-text)]">
                        {model}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                        {formatTokens(v.messageCount)}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text-muted)] tabular-nums">
                        {formatTokens(v.promptTokens)}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text-muted)] tabular-nums">
                        {formatTokens(v.completionTokens)}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                        {formatMicroUsd(v.spendMicroUsd)}
                      </td>
                    </tr>
                  ))
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>subscriptions by tier</CardTitle>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
              <tr className="border-b border-[var(--color-ops-border)]">
                <th className="text-left px-2 py-1 font-medium">tier</th>
                <th className="text-right px-2 py-1 font-medium">active</th>
                <th className="text-right px-2 py-1 font-medium">price</th>
                <th className="text-right px-2 py-1 font-medium">contribution</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(revenue.data?.tierCounts ?? {}).map(
                ([tier, count]) => {
                  const price = revenue.data?.tierPricePence[tier] ?? 0;
                  return (
                    <tr
                      key={tier}
                      className="border-b border-[var(--color-ops-border)]/40"
                    >
                      <td className="px-2 py-1 font-mono text-[var(--color-ops-text)]">
                        {tier}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                        {count}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text-muted)] tabular-nums">
                        {formatPence(price)}
                      </td>
                      <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                        {formatPence(count * price)}
                      </td>
                    </tr>
                  );
                },
              )}
            </tbody>
          </table>
          <div className="mt-3 text-[11px] text-[var(--color-ops-text-faint)] font-mono">
            mrr derived from active + trialing subscriptions whose
            current_period_end is in the future. per-day revenue is
            mrr/30; per-charge actuals need stripe charge events.
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
