import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteAccounts } from "@/routes/accounts";

const RESPONSE = {
  accounts: [
    {
      id: "acct_1",
      displayName: "Zezima",
      accountType: "main",
      status: "verified",
      lastVerifiedAt: null,
      createdAt: "2026-06-20T00:00:00.000Z",
    },
    {
      id: "acct_2",
      displayName: "B0aty",
      accountType: "ironman",
      status: "verified",
      lastVerifiedAt: null,
      createdAt: "2026-06-19T00:00:00.000Z",
    },
  ],
};

describe("RouteAccounts", () => {
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

  test("renders linked accounts list once the query resolves", async () => {
    await renderRoute("/accounts", RouteAccounts);

    expect(
      screen.getByRole("heading", { level: 1, name: /osrs accounts/i }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(/zezima/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/b0aty/i)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /unlink/i }).length).toBe(2);
  });
});
