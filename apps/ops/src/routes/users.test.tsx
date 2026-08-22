import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteUsers } from "./users";

describe("RouteUsers", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders an empty state when there are no users", async () => {
    restore = installFetchMock({
      "GET /admin/users": () => ({
        ok: true,
        rows: [],
        total: 0,
        limit: 50,
        offset: 0,
      }),
    });
    await renderRoute("/users", "/users", RouteUsers);
    await waitFor(() => {
      expect(
        screen.getByText(/no users match this filter\./i),
      ).toBeInTheDocument();
    });
  });

  it("renders rows with email and balance", async () => {
    restore = installFetchMock({
      "GET /admin/users": () => ({
        ok: true,
        rows: [
          {
            id: "u1234567890",
            email: "alice@example.com",
            stripeCustomerId: null,
            status: "active",
            tier: "pro",
            subscriptionStatus: "active",
            balanceTokens: 5_000,
            createdAt: new Date().toISOString(),
            deletedAt: null,
          },
        ],
        total: 1,
        limit: 50,
        offset: 0,
      }),
    });
    await renderRoute("/users", "/users", RouteUsers);
    await waitFor(() => {
      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    });
    expect(screen.getByText(/5,000/)).toBeInTheDocument();
  });
});
