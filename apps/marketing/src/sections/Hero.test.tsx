import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Hero } from "./Hero";

describe("<Hero />", () => {
  it("renders the headline and primary CTAs", () => {
    render(<Hero />);
    expect(screen.getByTestId("hero")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1 }),
    ).toHaveTextContent(/stop alt-tabbing/i);
    expect(
      screen.getByRole("link", { name: /install the runelite plugin/i }),
    ).toHaveAttribute("href", "#pricing");
    expect(screen.getByRole("link", { name: /see the demo/i })).toHaveAttribute(
      "href",
      "#demo",
    );
  });

  it("uses the no-automation guarantee as the sub-hero copy", () => {
    render(<Hero />);
    expect(
      screen.getByText(/never moves your character/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/never clicks a tile/i),
    ).toBeInTheDocument();
  });
});
