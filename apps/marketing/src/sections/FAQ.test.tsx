import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FAQ } from "./FAQ";

describe("<FAQ />", () => {
  it("renders multiple FAQ entries", () => {
    render(<FAQ />);
    const list = screen.getByTestId("faq-list");
    const items = within(list).getAllByRole("listitem");
    expect(items.length).toBeGreaterThanOrEqual(5);
  });
});
