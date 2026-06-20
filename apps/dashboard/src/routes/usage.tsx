import type { ReactNode } from "react";
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

type UsagePoint = { day: string; tokens: number };

// Placeholder series — RAI-27 replaces with a TanStack Query against the
// real /usage endpoint. Numbers below are illustrative only.
const PLACEHOLDER_DATA: ReadonlyArray<UsagePoint> = [
  { day: "Mon", tokens: 1240 },
  { day: "Tue", tokens: 980 },
  { day: "Wed", tokens: 2110 },
  { day: "Thu", tokens: 1760 },
  { day: "Fri", tokens: 3050 },
  { day: "Sat", tokens: 2480 },
  { day: "Sun", tokens: 1920 },
];

export function RouteUsage(): ReactNode {
  const total = PLACEHOLDER_DATA.reduce((sum, point) => sum + point.tokens, 0);

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          Token usage
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Past 7 days. Backend wiring lands in RAI-27 — this is a static
          placeholder.
        </p>
      </header>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Last 7 days</CardDescription>
            <CardTitle>{total.toLocaleString()} tokens</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Active OSRS accounts</CardDescription>
            <CardTitle>1</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Credits remaining</CardDescription>
            <CardTitle>—</CardTitle>
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
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={[...PLACEHOLDER_DATA]}>
                <CartesianGrid strokeDasharray="3 3" stroke="#3a2f1f" />
                <XAxis dataKey="day" stroke="#c8b675" />
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
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
