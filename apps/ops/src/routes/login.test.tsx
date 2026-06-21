import { afterEach, beforeEach, describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { screen, waitFor } from "@testing-library/react";
import { renderRoute } from "@/test/renderRoute";
import { installFetchMock } from "@/test/mockFetch";
import { RouteLogin } from "./login";

describe("RouteLogin", () => {
  let restore: () => void;

  beforeEach(() => {
    restore = () => {};
  });
  afterEach(() => {
    restore();
  });

  it("renders the email field and the access copy", async () => {
    restore = installFetchMock({});
    await renderRoute("/login", "/login", RouteLogin);
    expect(screen.getByText(/internal access only/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/you@example.com/i)).toBeInTheDocument();
  });

  it("shows 'not authorised' on 401 from the backend", async () => {
    restore = installFetchMock({
      "POST /api/admin/login": () => ({ ok: false, error: "unauthorized" }),
    });
    // 401 path: override mock to return a non-OK response.
    const originalFetch = globalThis.fetch;
    globalThis.fetch = (async () =>
      new Response(JSON.stringify({ ok: false }), {
        status: 401,
        headers: { "content-type": "application/json" },
      })) as unknown as typeof fetch;

    await renderRoute("/login", "/login", RouteLogin);
    const input = screen.getByPlaceholderText(/you@example.com/i);
    await userEvent.type(input, "evil@example.com");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => {
      expect(screen.getByText(/not authorised/i)).toBeInTheDocument();
    });

    globalThis.fetch = originalFetch;
  });
});
