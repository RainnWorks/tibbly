/**
 * Install a route-matched fetch mock for the duration of a test. The
 * `handlers` map is { "GET /admin/users": () => responseJson, ... }.
 *
 * Match key is `${METHOD} ${url.pathname}` so query strings are
 * ignored.
 */
export function installFetchMock(
  handlers: Record<string, (req: Request) => unknown | Promise<unknown>>,
) {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = (async (
    input: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> => {
    const urlStr =
      input instanceof Request ? input.url : input instanceof URL ? input.toString() : input;
    const method =
      input instanceof Request
        ? input.method.toUpperCase()
        : (init?.method ?? "GET").toUpperCase();
    const url = new URL(urlStr, "http://localhost");
    const key = `${method} ${url.pathname}`;
    const handler = handlers[key];
    if (!handler) {
      return new Response(
        JSON.stringify({ ok: false, error: `no-mock:${key}` }),
        { status: 500, headers: { "content-type": "application/json" } },
      );
    }
    const req =
      input instanceof Request ? input : new Request(urlStr, init);
    const body = await handler(req);
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }) as typeof fetch;

  return () => {
    globalThis.fetch = originalFetch;
  };
}
