import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { TrustStrip } from "./TrustStrip";

describe("<TrustStrip />", () => {
  it("renders the three no-automation claims", () => {
    render(<TrustStrip />);
    expect(screen.getByTestId("trust-strip")).toBeInTheDocument();
    const list = screen.getByTestId("trust-strip-list");
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(3);
  });

  it("each claim links to a defending doc or file", () => {
    render(<TrustStrip />);
    const list = screen.getByTestId("trust-strip-list");
    const links = within(list).getAllByRole("link");
    expect(links).toHaveLength(3);
    links.forEach((link) => {
      expect(link).toHaveAttribute("href");
      expect(link.getAttribute("href")).toMatch(/^https?:\/\//);
    });
  });

  it("declares the 'You play. Tibbly watches.' header", () => {
    render(<TrustStrip />);
    expect(
      screen.getByRole("heading", { level: 2, name: /you play\. tibbly watches\./i }),
    ).toBeInTheDocument();
  });
});
