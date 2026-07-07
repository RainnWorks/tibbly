import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteUserDetail } from "./user-detail";

const SAMPLE = {
  ok: true,
  user: {
    id: "u123",
    email: "alice@example.com",
    stripeCustomerId: "cus_test_1",
    status: "active",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    deletedAt: null,
  },
  devices: [
    {
      id: "d1",
      displayName: "Tom's laptop",
      playerName: "Zezima",
      lastSeenAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
    },
  ],
  osrsAccounts: [
    {
      id: "o1",
      displayName: "Zezima",
      accountType: "main",
      status: "verified",
      lastVerifiedAt: null,
    },
  ],
  subscriptions: [
    {
      id: "s1",
      stripeSubscriptionId: "sub_xxx",
      tier: "pro",
      status: "active",
      monthlyQuotaTokens: 500_000,
      currentPeriodStart: new Date().toISOString(),
      currentPeriodEnd: new Date(Date.now() + 30 * 86_400_000).toISOString(),
    },
  ],
  activeSubscription: { id: "s1", tier: "pro", status: "active" },
  balance: { balanceTokens: 12_345 },
  recentChats: [],
  recentToolCalls: [],
  invoices: [],
};

describe("RouteUserDetail", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders identity, balance and subscription tier", async () => {
    restore = installFetchMock({
      "GET /admin/users/u123": () => SAMPLE,
    });
    await renderRoute("/users/$id", "/users/u123", RouteUserDetail);
    await waitFor(() => {
      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    });
    expect(screen.getByText("12,345")).toBeInTheDocument();
    expect(screen.getAllByText(/Zezima/).length).toBeGreaterThan(0);
  });

  it("shows error state when API rejects", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = (async () =>
      new Response(JSON.stringify({ ok: false }), {
        status: 500,
        headers: { "content-type": "application/json" },
      })) as unknown as typeof fetch;
    await renderRoute("/users/$id", "/users/u123", RouteUserDetail);
    await waitFor(() => {
      expect(screen.getByText(/could not load user/i)).toBeInTheDocument();
    });
    globalThis.fetch = originalFetch;
  });
});
