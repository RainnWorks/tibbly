import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
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
});
