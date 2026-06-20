import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { LiveCounter } from "./LiveCounter";

function renderWithClient(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

describe("<LiveCounter />", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('shows "Connecting…" on first paint before the request resolves', () => {
    (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(
      () => new Promise(() => {}),
    );
    renderWithClient(<LiveCounter />);
    expect(screen.getByTestId("live-counter-loading")).toHaveTextContent(
      /connecting/i,
    );
  });

  it("renders count and per-region breakdown when the backend responds", async () => {
    (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        count: 1337,
        regions: [
          { name: "EU", count: 800 },
          { name: "US", count: 500 },
          { name: "OCE", count: 37 },
        ],
      }),
    });

    renderWithClient(<LiveCounter />);

    await waitFor(() => {
      expect(screen.getByTestId("live-counter-count")).toHaveTextContent(
        "1,337",
      );
    });

    const regions = screen.getByTestId("live-counter-regions");
    expect(regions).toHaveTextContent("EU");
    expect(regions).toHaveTextContent("US");
    expect(regions).toHaveTextContent("OCE");
  });
});
