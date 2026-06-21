import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { apiFetch } from "@/lib/api";

export interface UsageDailyPoint {
  readonly date: string;
  readonly tokens: number;
}

export interface UsageSummary {
  readonly balance: number;
  readonly lastRenewal: string | null;
  readonly dailyTokens: ReadonlyArray<UsageDailyPoint>;
}

/** Exported so tests can swap the fetcher. */
export async function fetchUsageSummary(): Promise<UsageSummary> {
  return apiFetch<UsageSummary>("/v1/usage/summary", { authenticated: true });
}

export function RouteUsage(): ReactNode {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["usage", "summary"],
    queryFn: fetchUsageSummary,
  });

  const total =
    data?.dailyTokens.reduce((sum, point) => sum + point.tokens, 0) ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          Token usage
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Past 30 days of tokens (prompt + completion) across every linked
          OSRS account.
        </p>
      </header>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Last 30 days</CardDescription>
            <CardTitle>
              {isLoading ? "—" : total.toLocaleString()} tokens
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Balance</CardDescription>
            <CardTitle>
              {isLoading ? "—" : (data?.balance ?? 0).toLocaleString()}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Last renewal</CardDescription>
            <CardTitle>
              {isLoading
                ? "—"
                : data?.lastRenewal
                  ? new Date(data.lastRenewal).toLocaleDateString()
                  : "Never"}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Daily tokens</CardTitle>
          <CardDescription>
            Hover the line for per-day totals.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div data-testid="usage-chart" className="h-72 w-full">
            {isError ? (
              <div
                role="alert"
                className="flex h-full items-center justify-center text-sm text-[color:var(--color-osrs-danger)]"
              >
                Failed to load usage:{" "}
                {error instanceof Error ? error.message : "unknown error"}
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data ? [...data.dailyTokens] : []}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#3a2f1f" />
                  <XAxis dataKey="date" stroke="#c8b675" />
                  <YAxis stroke="#c8b675" />
                  <Tooltip
                    contentStyle={{
                      background: "#1a1410",
                      border: "1px solid #c8b675",
                      color: "#fef3c7",
                    }}
                  />
                  <Line
                    type="monotone"
                    dataKey="tokens"
                    stroke="#f5c542"
                    strokeWidth={2}
                    dot={{ stroke: "#f5c542", fill: "#0b0a08", strokeWidth: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
