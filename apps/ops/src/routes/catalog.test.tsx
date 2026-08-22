import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteCatalog } from "./catalog";

describe("RouteCatalog", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders the table from the mocked /admin/catalog/models response", async () => {
    restore = installFetchMock({
      "GET /admin/catalog/models": () => ({
        ok: true,
        count: 2,
        models: [
          {
            id: "anthropic/claude-sonnet-4.6",
            provider: "anthropic",
            displayName: "Claude Sonnet 4.6",
            contextLength: 200_000,
            inputPriceMicroUsdPerMillion: 3_000_000,
            outputPriceMicroUsdPerMillion: 15_000_000,
            inputModalities: ["text"],
            capabilities: {},
            firstSeenAt: new Date().toISOString(),
            lastSeenAt: new Date().toISOString(),
            retiredAt: null,
          },
          {
            id: "openai/gpt-5",
            provider: "openai",
            displayName: "GPT-5",
            contextLength: 128_000,
            inputPriceMicroUsdPerMillion: 10_000_000,
            outputPriceMicroUsdPerMillion: 30_000_000,
            inputModalities: ["text"],
            capabilities: {},
            firstSeenAt: new Date().toISOString(),
            lastSeenAt: new Date().toISOString(),
            retiredAt: null,
          },
        ],
      }),
      "GET /admin/catalog/diff": () => ({
        ok: true,
        since: new Date().toISOString(),
        threshold: 0.1,
        added: [],
        retired: [],
        priceChanges: [],
      }),
    });
    await renderRoute("/catalog", "/catalog", RouteCatalog);
    await waitFor(() => {
      expect(screen.getByText("anthropic/claude-sonnet-4.6")).toBeInTheDocument();
    });
    expect(screen.getByText("openai/gpt-5")).toBeInTheDocument();
    // "in / 1M" is unique to the table header.
    expect(screen.getByText(/in \/ 1M/i)).toBeInTheDocument();
  });

  it("shows added / retired / price-change groups from the diff endpoint", async () => {
    restore = installFetchMock({
      "GET /admin/catalog/models": () => ({
        ok: true,
        count: 0,
        models: [],
      }),
      "GET /admin/catalog/diff": () => ({
        ok: true,
        since: new Date().toISOString(),
        threshold: 0.1,
        added: [
          {
            id: "anthropic/new-model-9",
            provider: "anthropic",
            displayName: "New Model 9",
            firstSeenAt: new Date().toISOString(),
          },
        ],
        retired: [
          {
            id: "openai/legacy-model",
            provider: "openai",
            displayName: "Legacy Model",
            retiredAt: new Date().toISOString(),
          },
        ],
        priceChanges: [
          {
            id: "anthropic/claude-sonnet-4.6",
            provider: "anthropic",
            displayName: "Claude Sonnet 4.6",
            inputPriceMicroUsdPerMillion: 2_500_000,
            outputPriceMicroUsdPerMillion: 12_000_000,
          },
        ],
      }),
    });
    await renderRoute("/catalog", "/catalog", RouteCatalog);
    await waitFor(() => {
      expect(screen.getByText("anthropic/new-model-9")).toBeInTheDocument();
    });
    expect(screen.getByText("openai/legacy-model")).toBeInTheDocument();
    expect(screen.getByText("anthropic/claude-sonnet-4.6")).toBeInTheDocument();
  });

  it("triggers a POST when the refresh button is clicked", async () => {
    let refreshCount = 0;
    restore = installFetchMock({
      "GET /admin/catalog/models": () => ({
        ok: true,
        count: 0,
        models: [],
      }),
      "GET /admin/catalog/diff": () => ({
        ok: true,
        since: new Date().toISOString(),
        threshold: 0.1,
        added: [],
        retired: [],
        priceChanges: [],
      }),
      "POST /admin/catalog/refresh": () => {
        refreshCount += 1;
        return { ok: true, added: 2, updated: 5, retired: 1 };
      },
    });
    await renderRoute("/catalog", "/catalog", RouteCatalog);
    const button = await screen.findByTestId("catalog-refresh");
    fireEvent.click(button);
    await waitFor(() => {
      expect(refreshCount).toBe(1);
    });
  });
});
