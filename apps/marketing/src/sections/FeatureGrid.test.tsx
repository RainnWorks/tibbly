import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FeatureGrid } from "./FeatureGrid";

describe("<FeatureGrid />", () => {
  it("renders the 4x2 inventory grid with exactly eight cells", () => {
    render(<FeatureGrid />);
    const list = screen.getByTestId("feature-grid-list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(8);
  });

  it("uses the 'Eight live tools.' headline", () => {
    render(<FeatureGrid />);
    expect(
      screen.getByRole("heading", { level: 2, name: /eight live tools/i }),
    ).toBeInTheDocument();
  });
});
