import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteUsage } from "@/routes/usage";

const RESPONSE = {
  balance: 12345,
  lastRenewal: "2026-06-01T00:00:00.000Z",
  dailyTokens: [
    { date: "2026-06-19", tokens: 800 },
    { date: "2026-06-20", tokens: 1200 },
  ],
};

describe("RouteUsage", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify(RESPONSE), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      ),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("renders the usage heading and chart container", async () => {
    await renderRoute("/usage", RouteUsage);

    expect(
      screen.getByRole("heading", { level: 1, name: /token usage/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/daily tokens/i)).toBeInTheDocument();
    expect(screen.getByTestId("usage-chart")).toBeInTheDocument();
  });

  test("renders balance and total once the query resolves", async () => {
    await renderRoute("/usage", RouteUsage);

    await waitFor(() => {
      expect(screen.getByText("12,345")).toBeInTheDocument();
    });
    // Total = 800 + 1200 = 2,000
    expect(screen.getByText(/2,000 tokens/)).toBeInTheDocument();
  });
});
