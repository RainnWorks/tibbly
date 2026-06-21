import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { type ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { apiFetch } from "@/lib/api";

interface ToolUsageResponse {
  ok: true;
  summary: {
    totalCalls: number;
    byFamily: Record<string, number>;
    byTool: Record<string, number>;
  };
}

interface FunnelResponse {
  ok: true;
  summary: { byStep: Record<string, number> };
}

interface ErrorsResponse {
  ok: true;
  summary: { byKind: Record<string, number> };
}

export function RouteAnalytics(): ReactNode {
  const tools = useQuery({
    queryKey: ["admin.tool-usage"],
    queryFn: () => apiFetch<ToolUsageResponse>("/admin/tool-usage"),
  });
  const funnel = useQuery({
    queryKey: ["admin.funnel"],
    queryFn: () => apiFetch<FunnelResponse>("/admin/funnel"),
  });
  const errors = useQuery({
    queryKey: ["admin.errors"],
    queryFn: () => apiFetch<ErrorsResponse>("/admin/errors"),
  });

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle>tool usage by family (30d)</CardTitle>
          <Badge tone="neutral">
            {tools.data ? `${tools.data.summary.totalCalls} total` : "loading"}
          </Badge>
        </CardHeader>
        <CardContent className="h-64">
          <BarChartFromMap data={tools.data?.summary.byFamily ?? {}} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>funnel (30d)</CardTitle>
        </CardHeader>
        <CardContent className="h-64">
          <BarChartFromMap data={funnel.data?.summary.byStep ?? {}} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>errors by kind (30d)</CardTitle>
        </CardHeader>
        <CardContent className="h-64">
          <BarChartFromMap data={errors.data?.summary.byKind ?? {}} accent="danger" />
        </CardContent>
      </Card>

      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle>top tools (30d)</CardTitle>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
              <tr className="border-b border-[var(--color-ops-border)]">
                <th className="text-left px-2 py-1 font-medium">tool</th>
                <th className="text-right px-2 py-1 font-medium">calls</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(tools.data?.summary.byTool ?? {})
                .sort(([, a], [, b]) => Number(b) - Number(a))
                .slice(0, 20)
                .map(([tool, n]) => (
                  <tr
                    key={tool}
                    className="border-b border-[var(--color-ops-border)]/40"
                  >
                    <td className="px-2 py-1 font-mono text-[var(--color-ops-text)]">
                      {tool}
                    </td>
                    <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                      {n}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}

function BarChartFromMap({
  data,
  accent = "ok",
}: {
  data: Record<string, number>;
  accent?: "ok" | "danger";
}): ReactNode {
  const fill =
    accent === "danger" ? "var(--color-ops-danger)" : "var(--color-ops-accent)";
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart
        data={Object.entries(data).map(([k, v]) => ({ k, v: Number(v) }))}
      >
        <CartesianGrid stroke="rgba(255,255,255,0.04)" />
        <XAxis
          dataKey="k"
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
          dataKey="v"
          fill={fill}
          radius={[2, 2, 0, 0]}
          isAnimationActive={false}
        />
      </BarChart>
    </ResponsiveContainer>
  );
}
