import { useQuery } from "@tanstack/react-query";
import overallIcon from "@osrs-llm-helper/osrs-assets/skill_icons/overall.png";

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

export function LiveCounter() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["presence"],
    queryFn: ({ signal }) => fetchPresence(signal),
    refetchInterval: 5000,
    refetchIntervalInBackground: false,
    staleTime: 4000,
  });

  return (
    <section
      data-testid="live-counter"
      id="live"
      className="border-b border-osrs-border bg-osrs-surface/60 px-6 py-20 text-center"
    >
      <div className="mx-auto max-w-3xl">
        <div className="mb-5 flex items-center justify-center gap-3">
          <img
            src={overallIcon}
            alt=""
            aria-hidden="true"
            className="h-7 w-7"
          />
          <p className="font-mono text-xs uppercase tracking-[0.4em] text-osrs-gold-dim">
            Live network
          </p>
        </div>
        <h2 className="mb-4 text-3xl md:text-4xl">
          Players online with Tibbly, right now.
        </h2>
        <div
          data-testid="live-counter-value"
          className="mb-2 font-heading text-6xl text-osrs-gold live-glow md:text-8xl"
          aria-live="polite"
        >
          {isLoading || data === undefined ? (
            <span data-testid="live-counter-loading" className="text-osrs-gold-dim">
              Connecting…
            </span>
          ) : isError ? (
            <span data-testid="live-counter-error" className="text-osrs-danger">
              Offline
            </span>
          ) : (
            <span data-testid="live-counter-count">
              {data.count.toLocaleString()}
            </span>
          )}
        </div>
        <p className="mb-8 font-mono text-xs uppercase tracking-widest text-osrs-muted">
          Updated every 5 seconds · presence is opt-in
        </p>
        {data && data.regions.length > 0 && (
          <ul
            data-testid="live-counter-regions"
            className="flex flex-wrap justify-center gap-3"
          >
            {data.regions.map((region) => (
              <li
                key={region.name}
                className="flex items-center gap-2 border border-osrs-border bg-osrs-bg px-3 py-1 text-sm text-osrs-text/90"
              >
                <span className="font-mono text-osrs-gold-dim">
                  {region.name}
                </span>
                <span aria-hidden="true" className="text-osrs-muted">
                  ·
                </span>
                <span className="font-mono text-osrs-gold">
                  {region.count.toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
