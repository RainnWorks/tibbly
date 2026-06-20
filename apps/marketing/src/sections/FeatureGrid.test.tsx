import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FeatureGrid } from "./FeatureGrid";

describe("<FeatureGrid />", () => {
  it("renders all six features", () => {
    render(<FeatureGrid />);
    const list = screen.getByTestId("feature-grid-list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(6);
  });
});
