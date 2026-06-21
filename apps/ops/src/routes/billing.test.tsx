import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteBilling } from "@/routes/billing";

describe("RouteBilling", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify({ url: "https://stripe.test/portal" }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      ),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("renders all three pricing tiers and the Stripe portal CTA", async () => {
    await renderRoute("/billing", RouteBilling);

    expect(
      screen.getByRole("heading", { level: 1, name: /^billing$/i }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { level: 3, name: /^hobbyist$/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: /^pro$/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 3, name: /^iron$/i }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /open stripe portal/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /choose hobbyist/i }),
    ).toBeInTheDocument();
  });
});
