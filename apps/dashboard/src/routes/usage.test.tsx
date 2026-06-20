import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteUsage } from "@/routes/usage";

describe("RouteUsage", () => {
  test("renders the usage heading and chart container", async () => {
    await renderRoute("/usage", RouteUsage);

    expect(
      screen.getByRole("heading", { level: 1, name: /token usage/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/daily tokens/i)).toBeInTheDocument();
    expect(screen.getByTestId("usage-chart")).toBeInTheDocument();
  });
});
