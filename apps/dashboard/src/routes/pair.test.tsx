import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { screen } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { RoutePair } from "@/routes/pair";

describe("RoutePair", () => {
  beforeEach(() => {
    // The route mounts a useMutation against /v1/pairing/claim; stub fetch
    // so we don't hit the network during smoke render.
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        new Response(JSON.stringify({ userId: "u", deviceId: "d", userCreated: true }), {
          status: 201,
          headers: { "content-type": "application/json" },
        }),
      ),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

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
