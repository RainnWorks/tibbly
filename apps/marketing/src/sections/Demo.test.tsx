import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { Demo } from "./Demo";

describe("<Demo />", () => {
  it("renders the demo block", () => {
    render(<Demo />);
    expect(screen.getByTestId("demo")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: /see it in action/i }),
    ).toBeInTheDocument();
  });
});
