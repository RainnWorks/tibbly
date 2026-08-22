import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteAnalytics } from "./analytics";

describe("RouteAnalytics", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders the tool-usage and funnel cards", async () => {
    restore = installFetchMock({
      "GET /admin/tool-usage": () => ({
        ok: true,
        summary: {
          totalCalls: 42,
          byFamily: { inventory: 30, bank: 12 },
          byTool: { get_inventory: 30, get_bank: 12 },
        },
      }),
      "GET /admin/funnel": () => ({
        ok: true,
        summary: { byStep: { first_message: 14, first_paid: 3 } },
      }),
      "GET /admin/errors": () => ({
        ok: true,
        summary: { byKind: { openrouter: 1 } },
      }),
    });
    await renderRoute("/analytics", "/analytics", RouteAnalytics);
    await waitFor(() => {
      expect(screen.getByText(/42 total/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/funnel/i)).toBeInTheDocument();
    expect(screen.getByText(/get_inventory/i)).toBeInTheDocument();
  });
});
