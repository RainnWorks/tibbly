import { useQuery } from "@tanstack/react-query";

/**
 * Presence payload shared with the backend (RAI-21).
 *
 * Backend serves `{ count, byRegion: Record<string, number> }`. We normalize
 * to a sorted array for rendering so the UI is deterministic.
 */
export type PresenceResponse = {
  readonly count: number;
  readonly regions: ReadonlyArray<{
    readonly name: string;
    readonly count: number;
  }>;
};

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? "http://localhost:3000";

export async function fetchPresence(
  signal?: AbortSignal,
): Promise<PresenceResponse> {
  const res = await fetch(`${BACKEND_URL}/v1/presence`, {
    signal: signal ?? null,
  });
  if (!res.ok) {
    throw new Error(`presence request failed: ${res.status}`);
  }
  const data = (await res.json()) as unknown;
  if (
    typeof data !== "object" ||
    data === null ||
    typeof (data as { count?: unknown }).count !== "number"
  ) {
    throw new Error("invalid presence payload");
  }
  const payload = data as {
    count: number;
    // Both shapes supported during the rollout: the new `byRegion` map and
    // the legacy `regions` array. New backends only emit `byRegion`.
    byRegion?: Record<string, number>;
    regions?: ReadonlyArray<{ name: string; count: number }>;
  };
  const regions = payload.byRegion
    ? Object.entries(payload.byRegion)
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
    : Array.isArray(payload.regions)
      ? payload.regions
      : [];
  return {
    count: payload.count,
    regions,
  };
}

/*
 * Thin top status-bar strip per canonical IA §2 ("the standalone LiveCounter
 * section becomes a thin nav-adjacent indicator instead").
 *
 * Renders a single mono line above the page: green dot when the backend
 * answers, dim dot when it does not. Never the hero-sized count: a small
 * count in a hero-sized slot actively hurts at launch (IA §5 verdict #10).
 *
 * The detailed region breakdown stays available for the dashboard, served
 * via the same fetchPresence helper which is exported above.
 */
export function LiveCounter() {
  const { data, isError, isLoading } = useQuery({
    queryKey: ["presence"],
    queryFn: ({ signal }) => fetchPresence(signal),
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
    staleTime: 4000,
  });

  const isOnline = data !== undefined && !isError;

  return (
    <aside
      data-testid="live-counter"
      id="live"
      aria-label="Live network status"
      className="border-b border-osrs-border bg-osrs-bg px-6 py-2"
    >
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-3">
        <p className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.32em] text-osrs-muted">
          <span
            aria-hidden="true"
            className={`inline-block h-2 w-2 rounded-full ${
              isOnline
                ? "bg-osrs-success shadow-[0_0_8px_rgba(90,138,58,0.65)]"
                : "bg-osrs-muted/40"
            }`}
          />
          <span aria-live="polite">
            {data === undefined && isLoading ? (
              <span data-testid="live-counter-loading">connecting</span>
            ) : isError || data === undefined ? (
              <span data-testid="live-counter-error">offline</span>
            ) : (
              <>
                <span data-testid="live-counter-count">
                  {data.count.toLocaleString()}
                </span>{" "}
                players online
              </>
            )}
          </span>
        </p>
        {data && data.regions.length > 0 && (
          <ul
            data-testid="live-counter-regions"
            className="hidden items-center gap-3 font-mono text-[11px] uppercase tracking-widest text-osrs-muted md:flex"
          >
            {data.regions.slice(0, 3).map((region) => (
              <li key={region.name} className="flex items-center gap-1">
                <span className="text-osrs-gold-dim">{region.name}</span>
                <span className="text-osrs-text/80">
                  {region.count.toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </aside>
  );
}
