import { useQuery } from "@tanstack/react-query";

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
  const payload = data as PresenceResponse;
  return {
    count: payload.count,
    regions: Array.isArray(payload.regions) ? payload.regions : [],
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
      className="border-b border-osrs-border bg-osrs-surface/60 px-6 py-16 text-center"
    >
      <div className="mx-auto max-w-3xl">
        <p className="mb-2 font-mono text-xs uppercase tracking-widest text-osrs-gold-dim">
          Live network
        </p>
        <h2 className="mb-4 text-3xl md:text-4xl">Agents online right now</h2>
        <div
          data-testid="live-counter-value"
          className="mb-6 font-heading text-6xl text-osrs-gold md:text-7xl"
          aria-live="polite"
        >
          {isLoading || data === undefined ? (
            <span data-testid="live-counter-loading">Connecting…</span>
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
        {data && data.regions.length > 0 && (
          <ul
            data-testid="live-counter-regions"
            className="flex flex-wrap justify-center gap-3"
          >
            {data.regions.map((region) => (
              <li
                key={region.name}
                className="border border-osrs-border bg-osrs-bg px-3 py-1 text-sm text-osrs-text/90"
              >
                <span className="text-osrs-gold-dim">{region.name}</span>{" "}
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
