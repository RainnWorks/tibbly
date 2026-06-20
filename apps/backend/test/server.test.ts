/**
 * Smoke tests for the Hono app. We use `app.fetch` against a Web Standards
 * Request so the test never has to bind a real port.
 */
import { describe, expect, it } from "bun:test";

import { createApp } from "../src/app";

describe("backend skeleton routes", () => {
  it("GET /health returns ok with version + uptime", async () => {
    const app = createApp({ version: "test-1.2.3", bootAt: Date.now() - 1500 });
    const res = await app.fetch(new Request("http://localhost/health"));
    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      ok: boolean;
      version: string;
      uptime: number;
    };
    expect(body.ok).toBe(true);
    expect(body.version).toBe("test-1.2.3");
    expect(body.uptime).toBeGreaterThanOrEqual(1);
    expect(body.uptime).toBeLessThan(60);
  });

  it("GET /version echoes the configured version", async () => {
    const app = createApp({ version: "test-1.2.3" });
    const res = await app.fetch(new Request("http://localhost/version"));
    expect(res.status).toBe(200);
    const body = (await res.json()) as { version: string };
    expect(body.version).toBe("test-1.2.3");
  });

  it("unknown route returns a structured 404", async () => {
    const app = createApp();
    const res = await app.fetch(new Request("http://localhost/nope"));
    expect(res.status).toBe(404);
    const body = (await res.json()) as { ok: boolean; error: string };
    expect(body.ok).toBe(false);
    expect(body.error).toBe("not_found");
  });
});
