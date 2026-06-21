import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatTile } from "@/components/ui/stat-tile";
import { useToast } from "@/components/ui/toast";
import { apiFetch } from "@/lib/api";
import { formatMicroUsd } from "@/lib/format";

interface CatalogModel {
  id: string;
  provider: string;
  displayName: string;
  contextLength: number;
  inputPriceMicroUsdPerMillion: number;
  outputPriceMicroUsdPerMillion: number;
  inputModalities: string[];
  capabilities: Record<string, unknown>;
  firstSeenAt: string;
  lastSeenAt: string;
  retiredAt: string | null;
}

interface ModelsResponse {
  ok: true;
  count: number;
  models: CatalogModel[];
}

interface DiffResponse {
  ok: true;
  since: string;
  threshold: number;
  added: Array<{ id: string; provider: string; displayName: string; firstSeenAt: string }>;
  retired: Array<{ id: string; provider: string; displayName: string; retiredAt: string }>;
  priceChanges: Array<{
    id: string;
    provider: string;
    displayName: string;
    inputPriceMicroUsdPerMillion: number;
    outputPriceMicroUsdPerMillion: number;
  }>;
}

interface RefreshResponse {
  ok: true;
  added: number;
  updated: number;
  retired: number;
}

type SortKey = "provider" | "context" | "input" | "output";

export function RouteCatalog(): ReactNode {
  const qc = useQueryClient();
  const toast = useToast();
  const [sortKey, setSortKey] = useState<SortKey>("provider");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  const models = useQuery({
    queryKey: ["admin.catalog.models"],
    queryFn: () => apiFetch<ModelsResponse>("/admin/catalog/models?retired=false"),
    refetchInterval: 60_000,
  });

  const since = useMemo(() => {
    const d = new Date(Date.now() - 86_400_000);
    return d.toISOString();
  }, []);

  const diff = useQuery({
    queryKey: ["admin.catalog.diff", since],
    queryFn: () =>
      apiFetch<DiffResponse>(
        `/admin/catalog/diff?since=${encodeURIComponent(since)}`,
      ),
    refetchInterval: 60_000,
  });

  const refreshMut = useMutation({
    mutationFn: () =>
      apiFetch<RefreshResponse>("/admin/catalog/refresh", { method: "POST" }),
    onSuccess: (data) => {
      toast.push({
        title: "catalog refreshed",
        description: `added ${data.added} | updated ${data.updated} | retired ${data.retired}`,
        variant: "success",
      });
      qc.invalidateQueries({ queryKey: ["admin.catalog.models"] });
      qc.invalidateQueries({ queryKey: ["admin.catalog.diff"] });
    },
    onError: () => {
      toast.push({
        title: "refresh failed",
        description: "see backend logs for the underlying fetch error",
        variant: "danger",
      });
    },
  });

  const sortedModels = useMemo(() => {
    const list = [...(models.data?.models ?? [])];
    const dir = sortDir === "asc" ? 1 : -1;
    list.sort((a, b) => {
      switch (sortKey) {
        case "provider":
          return a.provider.localeCompare(b.provider) * dir || a.id.localeCompare(b.id);
        case "context":
          return (a.contextLength - b.contextLength) * dir;
        case "input":
          return (
            (a.inputPriceMicroUsdPerMillion - b.inputPriceMicroUsdPerMillion) * dir
          );
        case "output":
          return (
            (a.outputPriceMicroUsdPerMillion - b.outputPriceMicroUsdPerMillion) * dir
          );
        default:
          return 0;
      }
    });
    return list;
  }, [models.data?.models, sortKey, sortDir]);

  const toggleSort = (key: SortKey): void => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const providerCount = useMemo(() => {
    const seen = new Set<string>();
    for (const m of models.data?.models ?? []) seen.add(m.provider);
    return seen.size;
  }, [models.data?.models]);

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatTile label="live models" value={models.data?.count ?? 0} />
        <StatTile label="providers" value={providerCount} />
        <StatTile
          label="added (24h)"
          value={diff.data?.added.length ?? 0}
          tone={diff.data && diff.data.added.length > 0 ? "ok" : "neutral"}
        />
        <StatTile
          label="retired (24h)"
          value={diff.data?.retired.length ?? 0}
          tone={diff.data && diff.data.retired.length > 0 ? "warn" : "neutral"}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>diff since yesterday</CardTitle>
          <Button
            variant="secondary"
            size="sm"
            disabled={refreshMut.isPending}
            onClick={() => refreshMut.mutate()}
            data-testid="catalog-refresh"
          >
            {refreshMut.isPending ? "refreshing..." : "refresh now"}
          </Button>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <DiffColumn
              title="added"
              empty="no new models"
              items={(diff.data?.added ?? []).map((r) => ({
                key: r.id,
                primary: r.id,
                secondary: r.displayName,
              }))}
              tone="ok"
            />
            <DiffColumn
              title="retired"
              empty="no retirements"
              items={(diff.data?.retired ?? []).map((r) => ({
                key: r.id,
                primary: r.id,
                secondary: r.displayName,
              }))}
              tone="warn"
            />
            <DiffColumn
              title="price changes"
              empty="no price moves"
              items={(diff.data?.priceChanges ?? []).map((r) => ({
                key: r.id,
                primary: r.id,
                secondary: `${formatMicroUsd(r.inputPriceMicroUsdPerMillion)} in / ${formatMicroUsd(r.outputPriceMicroUsdPerMillion)} out per 1M`,
              }))}
              tone="neutral"
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>live models</CardTitle>
          <Badge tone="neutral">openrouter catalog</Badge>
        </CardHeader>
        <CardContent>
          <table className="w-full text-sm">
            <thead className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
              <tr className="border-b border-[var(--color-ops-border)]">
                <SortHeader
                  onClick={() => toggleSort("provider")}
                  active={sortKey === "provider"}
                  dir={sortDir}
                  align="left"
                >
                  provider / id
                </SortHeader>
                <SortHeader
                  onClick={() => toggleSort("context")}
                  active={sortKey === "context"}
                  dir={sortDir}
                  align="right"
                >
                  context
                </SortHeader>
                <SortHeader
                  onClick={() => toggleSort("input")}
                  active={sortKey === "input"}
                  dir={sortDir}
                  align="right"
                >
                  in / 1M
                </SortHeader>
                <SortHeader
                  onClick={() => toggleSort("output")}
                  active={sortKey === "output"}
                  dir={sortDir}
                  align="right"
                >
                  out / 1M
                </SortHeader>
              </tr>
            </thead>
            <tbody>
              {sortedModels.length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    className="px-2 py-6 text-center text-xs text-[var(--color-ops-text-faint)] font-mono"
                  >
                    catalog empty. press refresh now to seed it.
                  </td>
                </tr>
              ) : (
                sortedModels.map((m) => (
                  <tr
                    key={m.id}
                    className="border-b border-[var(--color-ops-border)]/40"
                  >
                    <td className="px-2 py-1">
                      <div className="font-mono text-[var(--color-ops-text)]">{m.id}</div>
                      <div className="text-[11px] text-[var(--color-ops-text-faint)] font-mono">
                        {m.displayName}
                      </div>
                    </td>
                    <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text-muted)] tabular-nums">
                      {m.contextLength.toLocaleString("en-US")}
                    </td>
                    <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                      {formatMicroUsd(m.inputPriceMicroUsdPerMillion)}
                    </td>
                    <td className="px-2 py-1 text-right font-mono text-[var(--color-ops-text)] tabular-nums">
                      {formatMicroUsd(m.outputPriceMicroUsdPerMillion)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
          <div className="mt-3 text-[11px] text-[var(--color-ops-text-faint)] font-mono">
            ingested nightly at 03:17 utc plus on boot. prices stored as
            micro-usd per 1m tokens for integer math; the display widens
            to four decimals so the smallest models are still legible.
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

interface DiffColumnProps {
  title: string;
  empty: string;
  tone: "ok" | "warn" | "neutral";
  items: Array<{ key: string; primary: string; secondary: string }>;
}

function DiffColumn({ title, empty, tone, items }: DiffColumnProps): ReactNode {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <Badge tone={tone}>{title}</Badge>
        <span className="text-[11px] text-[var(--color-ops-text-faint)] font-mono tabular-nums">
          {items.length}
        </span>
      </div>
      {items.length === 0 ? (
        <div className="text-xs text-[var(--color-ops-text-faint)] font-mono py-3">
          {empty}
        </div>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {items.map((item) => (
            <li key={item.key} className="flex flex-col gap-0.5">
              <span className="font-mono text-xs text-[var(--color-ops-text)]">
                {item.primary}
              </span>
              <span className="font-mono text-[11px] text-[var(--color-ops-text-faint)]">
                {item.secondary}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface SortHeaderProps {
  onClick: () => void;
  active: boolean;
  dir: "asc" | "desc";
  align: "left" | "right";
  children: ReactNode;
}

function SortHeader({ onClick, active, dir, align, children }: SortHeaderProps): ReactNode {
  return (
    <th
      className={`px-2 py-1 font-medium select-none cursor-pointer ${
        align === "right" ? "text-right" : "text-left"
      } ${active ? "text-[var(--color-ops-text)]" : ""}`}
      onClick={onClick}
    >
      <span className="inline-flex items-center gap-1">
        {children}
        {active ? (
          <span className="text-[var(--color-ops-text-faint)]">
            {dir === "asc" ? "^" : "v"}
          </span>
        ) : null}
      </span>
    </th>
  );
}
