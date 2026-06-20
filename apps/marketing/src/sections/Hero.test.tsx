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
    expect(screen.getByRole("link", { name: /get started/i })).toHaveAttribute(
      "href",
      "#pricing",
    );
    expect(screen.getByRole("link", { name: /see demo/i })).toHaveAttribute(
      "href",
      "#demo",
    );
  });
});
