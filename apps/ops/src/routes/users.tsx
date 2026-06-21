import { Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Search } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { CopyId } from "@/components/ui/copy-id";
import { RelativeTime } from "@/components/ui/relative-time";
import { apiFetch } from "@/lib/api";
import { formatTokens } from "@/lib/format";
import { cn } from "@/lib/cn";

interface UserRow {
  id: string;
  email: string | null;
  stripeCustomerId: string | null;
  status: string;
  tier: string;
  subscriptionStatus: string | null;
  balanceTokens: number;
  createdAt: string;
  deletedAt: string | null;
}

interface UsersResponse {
  ok: true;
  rows: UserRow[];
  total: number;
  limit: number;
  offset: number;
}

const STATUSES = ["any", "active", "banned", "deleted"] as const;
const TIERS = ["any", "free", "hobbyist", "pro", "iron"] as const;
type Status = (typeof STATUSES)[number];
type Tier = (typeof TIERS)[number];

export function RouteUsers(): ReactNode {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<Status>("any");
  const [tier, setTier] = useState<Tier>("any");
  const [focusIndex, setFocusIndex] = useState(0);
  const tableRef = useRef<HTMLTableElement>(null);

  const params = useMemo(() => {
    const u = new URLSearchParams();
    if (query.trim()) u.set("q", query.trim());
    if (status !== "any") u.set("status", status);
    if (tier !== "any") u.set("tier", tier);
    u.set("limit", "50");
    return u.toString();
  }, [query, status, tier]);

  const data = useQuery({
    queryKey: ["admin.users.list", params],
    queryFn: () =>
      apiFetch<UsersResponse>(`/admin/users${params ? `?${params}` : ""}`),
  });

  const rows = data.data?.rows ?? [];

  // Keyboard: j/k to move focus, Enter to open.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable)
      ) {
        return;
      }
      if (event.key === "j") {
        event.preventDefault();
        setFocusIndex((i) => Math.min(rows.length - 1, i + 1));
      }
      if (event.key === "k") {
        event.preventDefault();
        setFocusIndex((i) => Math.max(0, i - 1));
      }
      if (event.key === "Enter" && rows[focusIndex]) {
        event.preventDefault();
        const id = rows[focusIndex].id;
        window.location.assign(`/users/${id}`);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [rows, focusIndex]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[280px] max-w-[480px]">
          <Search
            size={14}
            strokeWidth={1.75}
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-ops-text-faint)]"
            aria-hidden
          />
          <Input
            data-ops-search
            placeholder="search email, user id, stripe customer id"
            className="pl-8"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <PillSelect
          label="status"
          options={STATUSES}
          value={status}
          onChange={setStatus}
        />
        <PillSelect
          label="tier"
          options={TIERS}
          value={tier}
          onChange={setTier}
        />
        <div className="ml-auto text-xs text-[var(--color-ops-text-faint)] font-mono">
          {data.data ? `${data.data.total} total` : "loading"}
        </div>
      </div>

      <Card>
        <table ref={tableRef} className="w-full text-sm">
          <thead className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
            <tr className="border-b border-[var(--color-ops-border)]">
              <th className="text-left font-medium px-4 py-2">id</th>
              <th className="text-left font-medium px-4 py-2">email</th>
              <th className="text-left font-medium px-4 py-2">tier</th>
              <th className="text-left font-medium px-4 py-2">status</th>
              <th className="text-right font-medium px-4 py-2">balance</th>
              <th className="text-left font-medium px-4 py-2">created</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && !data.isLoading ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-4 py-8 text-center text-xs text-[var(--color-ops-text-faint)] font-mono"
                >
                  no users match this filter.
                </td>
              </tr>
            ) : null}
            {rows.map((r, i) => (
              <tr
                key={r.id}
                data-focused={i === focusIndex ? "true" : "false"}
                className={cn(
                  "ops-row border-b border-[var(--color-ops-border)]/60",
                )}
              >
                <td className="px-4 py-2">
                  <CopyId value={r.id} label="user id" truncate={14} />
                </td>
                <td className="px-4 py-2 text-[var(--color-ops-text)]">
                  <Link
                    to="/users/$id"
                    params={{ id: r.id }}
                    className="hover:text-[var(--color-ops-accent)]"
                  >
                    {r.email ?? <span className="font-mono text-[var(--color-ops-text-faint)]">no email</span>}
                  </Link>
                </td>
                <td className="px-4 py-2">
                  <Badge tone={r.tier === "free" ? "neutral" : "ok"}>
                    {r.tier}
                  </Badge>
                </td>
                <td className="px-4 py-2">
                  <Badge
                    tone={
                      r.deletedAt
                        ? "warn"
                        : r.status === "banned"
                          ? "danger"
                          : "ok"
                    }
                  >
                    {r.deletedAt ? "deleted" : r.status}
                  </Badge>
                </td>
                <td className="px-4 py-2 text-right font-mono tabular-nums text-[var(--color-ops-text)]">
                  {formatTokens(r.balanceTokens)}
                </td>
                <td className="px-4 py-2">
                  <RelativeTime value={r.createdAt} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <div className="text-[11px] text-[var(--color-ops-text-faint)] font-mono">
        j / k to move row · Enter to open · / to focus search
      </div>
    </div>
  );
}

function PillSelect<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: readonly T[];
  value: T;
  onChange: (next: T) => void;
}): ReactNode {
  return (
    <label className="inline-flex items-center gap-2 text-xs text-[var(--color-ops-text-muted)]">
      <span>{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
        className="h-8 rounded-[4px] border border-[var(--color-ops-border)] bg-[var(--color-ops-surface-2)] text-[var(--color-ops-text)] text-xs px-2 py-0.5 font-mono focus:outline-none focus:border-[var(--color-ops-accent)]"
      >
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    </label>
  );
}
