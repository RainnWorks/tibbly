import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteRoot } from "@/routes/root";

describe("RouteRoot", () => {
  test("renders the welcome heading and feature cards", async () => {
    await renderRoute("/", RouteRoot);

    expect(
      screen.getByRole("heading", { level: 1, name: /welcome, adventurer/i }),
    ).toBeInTheDocument();

    expect(screen.getAllByText(/pair plugin/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/token usage/i)).toBeInTheDocument();
    expect(screen.getByText(/osrs accounts/i)).toBeInTheDocument();
    expect(screen.getAllByText(/billing/i).length).toBeGreaterThan(0);
  });
});
