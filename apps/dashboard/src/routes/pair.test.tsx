import { describe, expect, test } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RoutePair } from "@/routes/pair";

describe("RoutePair", () => {
  test("renders the pairing form", async () => {
    await renderRoute("/pair", RoutePair);

    expect(
      screen.getByRole("heading", { level: 1, name: /pair plugin/i }),
    ).toBeInTheDocument();
    expect(screen.getByPlaceholderText("ABC123")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /pair plugin/i }),
    ).toBeInTheDocument();
  });
});
