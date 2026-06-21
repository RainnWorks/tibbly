import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PricingTiers } from "./PricingTiers";

describe("<PricingTiers />", () => {
  it("renders four tiers with one highlighted", () => {
    render(<PricingTiers />);
    const list = screen.getByTestId("pricing-tiers-list");
    const tierItems = Array.from(list.children).filter(
      (el) => el.tagName === "LI",
    ) as HTMLElement[];
    expect(tierItems).toHaveLength(4);

    const highlighted = tierItems.filter(
      (li) => li.getAttribute("data-highlighted") === "true",
    );
    expect(highlighted).toHaveLength(1);
  });

  describe("checkout wiring", () => {
    const originalFetch = global.fetch;
    const originalLocation = window.location;

    beforeEach(() => {
      Object.defineProperty(window, "location", {
        configurable: true,
        value: { href: "" },
      });
    });

    afterEach(() => {
      global.fetch = originalFetch;
      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
    });

    it("free tier has no checkout slug — its CTA is plain", () => {
      render(<PricingTiers />);
      const freeBtn = screen.getByTestId("pricing-cta-free");
      expect(freeBtn).toBeEnabled();
      // No onClick wired ⇒ clicking does nothing observable; fetch is untouched.
      const spy = vi.fn();
      global.fetch = spy;
      fireEvent.click(freeBtn);
      expect(spy).not.toHaveBeenCalled();
    });

    it("redirects to Stripe Checkout URL on a successful pro CTA click", async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ url: "https://checkout.stripe.test/c_pro" }), {
            status: 200,
            headers: { "content-type": "application/json" },
          }),
        );
      global.fetch = fetchMock;

      render(<PricingTiers />);
      fireEvent.click(screen.getByTestId("pricing-cta-pro"));

      await waitFor(() => {
        expect(window.location.href).toBe("https://checkout.stripe.test/c_pro");
      });

      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [calledUrl, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(calledUrl).toMatch(/\/v1\/billing\/checkout\/pro$/);
      expect(init?.method).toBe("POST");
    });

    it("surfaces an inline error when the backend rejects", async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ error: "tier_not_configured" }), {
            status: 503,
            headers: { "content-type": "application/json" },
          }),
        );
      global.fetch = fetchMock;

      render(<PricingTiers />);
      fireEvent.click(screen.getByTestId("pricing-cta-iron"));

      const alert = await screen.findByRole("alert");
      expect(alert.textContent).toMatch(/Checkout unavailable/);
      expect(window.location.href).toBe("");
    });
  });
});
