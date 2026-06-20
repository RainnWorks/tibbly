# Charts — recommendation

## Decision

**Use `recharts` for the dashboard usage charts.** It's declarative React,
ships responsive containers, plays well with Tailwind theming, and is by far
the most-installed option.

| Library | Verdict | Why |
|---|---|---|
| **recharts** | ✅ PICK | Declarative React API, 27.3k★, ships responsive containers, easy theming |
| visx | ⚠️ For bespoke | Low-level D3 primitives. Use only when we want a custom OSRS-themed visual (e.g. wiki-style chart). Steeper learning curve |
| nivo | ❌ Skip | Beautiful but heavier bundle and Server-component / SSR story is messier on Vite |

## Packages

- `recharts` — v3.8.1 (released 2026-03-25), 27.3k★.
  - https://recharts.org/en-US/guide/getting-started
  - https://github.com/recharts/recharts

## Install

```bash
bun add recharts
```

## Hello-world: token usage line chart

```tsx
// apps/dashboard/src/components/UsageChart.tsx
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';

type Point = { day: string; tokens: number };

export function UsageChart({ data }: { data: Point[] }) {
  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="#3a2f1f" />
        <XAxis dataKey="day" stroke="#c8b675" />
        <YAxis stroke="#c8b675" />
        <Tooltip
          contentStyle={{ background: '#1a1410', border: '1px solid #c8b675' }}
        />
        <Line
          type="monotone"
          dataKey="tokens"
          stroke="#ffcc00"
          strokeWidth={2}
          dot={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
```

## When NOT to use recharts

- **Animated, custom visualisations.** For the "X agents online" globe on the
  marketing site, recharts is wrong — that's a globe.gl / three.js or a custom
  SVG. See `realtime.md` for the presence feed; rendering is separate.
- **Heavy interactive D3 work.** Drop to visx (`@visx/*` modular packages).

## Maintenance signals

- recharts — v3.8.1 (2026-03-25). https://github.com/recharts/recharts
- 27.3k★, MIT, actively maintained.
- visx — owned by Airbnb, slower cadence but stable.
- nivo — active but slower releases.
