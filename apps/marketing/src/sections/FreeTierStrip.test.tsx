import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { FreeTierStrip } from "./FreeTierStrip";

describe("<FreeTierStrip />", () => {
  it("renders the free-tier headline and CTA", () => {
    render(<FreeTierStrip />);
    expect(screen.getByTestId("free-tier-strip")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: /start free/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /install the plugin and start free/i }),
    ).toHaveAttribute("href", "#pricing");
  });

  it("mentions the thirty-a-day allowance and the BYO key escape hatch", () => {
    render(<FreeTierStrip />);
    expect(
      screen.getByText(/thirty messages a day/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/bring your own provider key/i),
    ).toBeInTheDocument();
  });
});
