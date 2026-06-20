import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Footer } from "./Footer";

describe("<Footer />", () => {
  it("renders the footer with copyright", () => {
    render(<Footer />);
    expect(screen.getByTestId("footer")).toBeInTheDocument();
    expect(
      screen.getByText(/osrs llm helper/i, { selector: "h3" }),
    ).toBeInTheDocument();
  });
});
