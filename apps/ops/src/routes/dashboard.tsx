import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { type ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatTile } from "@/components/ui/stat-tile";
import { RelativeTime } from "@/components/ui/relative-time";
import { Badge } from "@/components/ui/badge";
import { apiFetch } from "@/lib/api";
import {
  compactInteger,
  formatMicroUsd,
  formatUsdCents,
} from "@/lib/format";

interface RealtimeResponse {
  ok: true;
  windowSeconds: number;
  eventCount: number;
  byType: Record<string, number>;
  connectedPlugins: number;
}

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
  mrrUsdCents: number;
  activeSubscriptions: number;
  tierCounts: Record<string, number>;
  tierPriceUsdCents: Record<string, number>;
  approxToday: { revenueUsdCents: number };
  approxWeek: { revenueUsdCents: number };
}

interface ChatDailyResponse {
  ok: true;
  range: { since: string; until: string };
  rows: ReadonlyArray<{
    date: string;
    messageCount: number;
    tokensIn: number;
    tokensOut: number;
    costMicroUsd: number;
  }>;
  summary: {
    totalMessages: number;
    totalCostMicroUsd: number;
  };
}

export function RouteDashboard(): ReactNode {
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
  const realtime = useQuery({
    queryKey: ["admin.realtime"],
    queryFn: () => apiFetch<RealtimeResponse>("/admin/realtime"),
    refetchInterval: 5_000,
  });
  const chatDaily = useQuery({
    queryKey: ["admin.chat-daily"],
    queryFn: () => apiFetch<ChatDailyResponse>("/admin/chat-daily"),
    refetchInterval: 60_000,
  });

  return (
    <div className="flex flex-col gap-5">
      {/* Asymmetric top: revenue+cost chart left (2 cols), stat column right (1 col) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>revenue vs cost (last 30 days)</CardTitle>
            <Badge tone="neutral">live from messages + subscriptions</Badge>
          </CardHeader>
          <CardContent className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={(chatDaily.data?.rows ?? []).map((r) => ({
                  date: r.date,
                  costMicroUsd: Number(r.costMicroUsd),
                  messages: Number(r.messageCount),
                }))}
              >
                <CartesianGrid stroke="rgba(255,255,255,0.04)" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 10, fill: "var(--color-ops-text-faint)" }}
                  stroke="var(--color-ops-border)"
                />
                <YAxis
                  tick={{ fontSize: 10, fill: "var(--color-ops-text-faint)" }}
                  stroke="var(--color-ops-border)"
                  width={48}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--color-ops-surface-2)",
                    border: "1px solid var(--color-ops-border)",
                    fontSize: 12,
                  }}
                  labelStyle={{ color: "var(--color-ops-text-muted)" }}
                />
                <Line
                  type="monotone"
                  dataKey="costMicroUsd"
                  stroke="var(--color-ops-accent)"
                  strokeWidth={1.5}
                  dot={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <div className="grid grid-cols-1 gap-3">
          <StatTile
            label="mrr"
            value={
              revenue.data ? formatUsdCents(revenue.data.mrrUsdCents) : "..."
            }
            hint={
              revenue.data
                ? `${revenue.data.activeSubscriptions} active subscriptions`
                : undefined
            }
            tone="ok"
          />
          <StatTile
            label="openrouter spend today"
            value={
              spend.data
                ? formatMicroUsd(spend.data.windows.today.spendMicroUsd)
                : "..."
            }
            hint={
              spend.data
                ? `month: ${formatMicroUsd(spend.data.windows.month.spendMicroUsd)}`
                : undefined
            }
          />
          <StatTile
            label="revenue today (approx)"
            value={
              revenue.data
                ? formatUsdCents(revenue.data.approxToday.revenueUsdCents)
                : "..."
            }
            hint="mrr / 30 approximation"
          />
        </div>
      </div>

      {/* Secondary row: stat tiles */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatTile
          label="connected plugins"
          value={
            realtime.data ? compactInteger(realtime.data.connectedPlugins) : "..."
          }
          hint="last minute"
          tone="ok"
        />
        <StatTile
          label="events / min"
          value={realtime.data ? compactInteger(realtime.data.eventCount) : "..."}
        />
        <StatTile
          label="hobbyist"
          value={
            revenue.data ? compactInteger(revenue.data.tierCounts.hobbyist ?? 0) : "..."
          }
        />
        <StatTile
          label="pro + iron"
          value={
            revenue.data
              ? compactInteger(
                  (revenue.data.tierCounts.pro ?? 0) +
                    (revenue.data.tierCounts.iron ?? 0),
                )
              : "..."
          }
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>spend by model (last 30 days)</CardTitle>
          </CardHeader>
          <CardContent className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={Object.entries(spend.data?.byModelMonth ?? {}).map(
                  ([model, v]) => ({
                    model: model.replace("anthropic/claude-", ""),
                    spendMicroUsd: v.spendMicroUsd,
                  }),
                )}
              >
                <CartesianGrid stroke="rgba(255,255,255,0.04)" />
                <XAxis
                  dataKey="model"
                  tick={{ fontSize: 10, fill: "var(--color-ops-text-faint)" }}
                  stroke="var(--color-ops-border)"
                />
                <YAxis
                  tick={{ fontSize: 10, fill: "var(--color-ops-text-faint)" }}
                  stroke="var(--color-ops-border)"
                  width={48}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--color-ops-surface-2)",
                    border: "1px solid var(--color-ops-border)",
                    fontSize: 12,
                  }}
                  labelStyle={{ color: "var(--color-ops-text-muted)" }}
                />
                <Bar
                  dataKey="spendMicroUsd"
                  fill="var(--color-ops-accent)"
                  radius={[2, 2, 0, 0]}
                  isAnimationActive={false}
                />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>realtime feed</CardTitle>
            <Badge tone="ok">5s</Badge>
          </CardHeader>
          <CardContent>
            {realtime.data ? (
              <ul className="flex flex-col divide-y divide-[var(--color-ops-border)]">
                {Object.entries(realtime.data.byType).length === 0 ? (
                  <li className="py-2 text-xs text-[var(--color-ops-text-faint)] font-mono">
                    no events in the last minute.
                  </li>
                ) : (
                  Object.entries(realtime.data.byType)
                    .sort(([, a], [, b]) => Number(b) - Number(a))
                    .map(([type, count]) => (
                      <li
                        key={type}
                        className="py-2 flex items-center justify-between font-mono text-xs"
                      >
                        <span className="text-[var(--color-ops-text-muted)]">
                          {type}
                        </span>
                        <span className="text-[var(--color-ops-text)]">{count}</span>
                      </li>
                    ))
                )}
              </ul>
            ) : (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                loading
              </div>
            )}
            <div className="mt-3 text-[11px] text-[var(--color-ops-text-faint)] font-mono">
              window: 60s ·{" "}
              <RelativeTime
                value={new Date()}
                className="text-[var(--color-ops-text-faint)]"
              />
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
