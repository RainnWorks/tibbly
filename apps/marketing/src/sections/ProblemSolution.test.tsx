import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ProblemSolution } from "./ProblemSolution";

describe("<ProblemSolution />", () => {
  it("renders both columns", () => {
    render(<ProblemSolution />);
    expect(screen.getByTestId("problem-solution")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: /the problem/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: /the solution/i }),
    ).toBeInTheDocument();
  });
});
