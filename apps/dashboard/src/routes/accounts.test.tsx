import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RouteAccounts } from "@/routes/accounts";

describe("RouteAccounts", () => {
  test("renders linked accounts list", async () => {
    await renderRoute("/accounts", RouteAccounts);

    expect(
      screen.getByRole("heading", { level: 1, name: /osrs accounts/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/zezima/i)).toBeInTheDocument();
    expect(screen.getByText(/b0aty/i)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /unlink/i }).length).toBe(2);
  });
});
