import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteDashboard } from "./dashboard";

describe("RouteDashboard", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders MRR and spend stat tiles", async () => {
    restore = installFetchMock({
      "GET /admin/openrouter/spend": () => ({
        ok: true,
        windows: {
          today: { spendMicroUsd: 1_234_567 },
          week: { spendMicroUsd: 9_876_543 },
          month: { spendMicroUsd: 50_000_000 },
        },
        byModelMonth: {
          "anthropic/claude-haiku-4.5": {
            spendMicroUsd: 50_000_000,
            promptTokens: 1000,
            completionTokens: 500,
            messageCount: 5,
          },
        },
      }),
      "GET /admin/openrouter/revenue": () => ({
        ok: true,
        currency: "gbp",
        mrrPence: 4_900,
        activeSubscriptions: 1,
        tierCounts: { iron: 1 },
        tierPricePence: { hobbyist: 700, pro: 1900, iron: 4900 },
        approxToday: { revenuePence: 163 },
        approxWeek: { revenuePence: 1142 },
        approxMonth: { revenuePence: 4900 },
      }),
      "GET /admin/realtime": () => ({
        ok: true,
        windowSeconds: 60,
        eventCount: 7,
        byType: { "chat.message.sent": 5, "auth.pairing.claimed": 2 },
        connectedPlugins: 4,
      }),
      "GET /admin/chat-daily": () => ({
        ok: true,
        range: { since: "2026-05-22", until: "2026-06-21" },
        rows: [],
        summary: {
          totalMessages: 0,
          totalTokensIn: 0,
          totalTokensOut: 0,
          totalCostMicroUsd: 0,
        },
      }),
    });
    await renderRoute("/", "/", RouteDashboard);
    await waitFor(() => {
      expect(screen.getByText("£49.00")).toBeInTheDocument();
    });
    expect(screen.getByText(/connected plugins/i)).toBeInTheDocument();
  });
});
