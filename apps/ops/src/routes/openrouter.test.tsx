import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteOpenRouter } from "./openrouter";

describe("RouteOpenRouter", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders spend tiles and per-model rows", async () => {
    restore = installFetchMock({
      "GET /api/admin/openrouter/spend": () => ({
        ok: true,
        windows: {
          today: { spendMicroUsd: 100 },
          week: { spendMicroUsd: 700 },
          month: { spendMicroUsd: 3000 },
        },
        byModelMonth: {
          "anthropic/claude-sonnet-4.6": {
            spendMicroUsd: 3000,
            promptTokens: 100,
            completionTokens: 50,
            messageCount: 2,
          },
        },
      }),
      "GET /api/admin/openrouter/revenue": () => ({
        ok: true,
        mrrUsdCents: 1900,
        activeSubscriptions: 1,
        tierCounts: { pro: 1 },
        tierPriceUsdCents: { hobbyist: 700, pro: 1900, iron: 4900 },
        approxToday: { revenueUsdCents: 63 },
        approxWeek: { revenueUsdCents: 442 },
        approxMonth: { revenueUsdCents: 1900 },
      }),
    });
    await renderRoute("/openrouter", "/openrouter", RouteOpenRouter);
    await waitFor(() => {
      expect(
        screen.getByText("anthropic/claude-sonnet-4.6"),
      ).toBeInTheDocument();
    });
    expect(screen.getByText(/per-model spend/i)).toBeInTheDocument();
  });
});
