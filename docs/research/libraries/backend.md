# Backend framework — recommendation

## Decision

**Use Hono on Bun.** It's small, Web-Standards-based, has first-class Bun
support including a WebSocket helper, and is the most-installed of the
modern Bun-friendly frameworks.

| Library | Verdict | Why |
|---|---|---|
| **Hono** | ✅ PICK | Tiny (<12kB tiny preset), Web Standards Request/Response, runs identically on Bun/Node/Workers, ships `hono/bun` WS helper, 31k stars |
| Elysia | ⚠️ Strong runner-up | Slightly faster on Bun synthetic benches, great DX, but Bun-only — locks us out of Workers and harder to port. 18.5k stars |
| Express | ❌ Skip | Legacy callback model, no native streaming/WS story, no first-class TS, slower release cadence |

Both Hono and Elysia are excellent. Hono wins because (a) we may later push the
chat orchestration to the edge, (b) middleware ecosystem is larger, (c) the
NORTH_STAR's "well-trodden library" rule favours the more-installed option.

## Packages

- `hono` — v4.12.x (latest 2026-06-18 per repo). 31k★.
  - https://hono.dev/docs/ (official)
  - https://github.com/honojs/hono

## Install

```bash
bun add hono
bun add -D @types/bun
```

## Hello-world: HTTP

Source: https://hono.dev/docs/

```ts
// apps/backend/src/index.ts
import { Hono } from 'hono';

const app = new Hono();

app.get('/', (c) => c.json({ message: 'Hello, Hono!' }));
app.get('/health', (c) => c.text('ok'));

export default app;
```

Run with `bun --hot src/index.ts`.

## Hello-world: WebSocket (chat link to plugin)

Source: https://hono.dev/docs/helpers/websocket

```ts
import { Hono } from 'hono';
import { upgradeWebSocket, websocket } from 'hono/bun';

const app = new Hono();

app.get(
  '/ws',
  upgradeWebSocket(() => ({
    onMessage(event, ws) {
      ws.send(`echo: ${event.data}`);
    },
    onClose() {
      console.log('connection closed');
    },
  })),
);

export default {
  fetch: app.fetch,
  websocket, // <-- required for Bun
  port: 3000,
};
```

## Recommended companion packages

- `@hono/zod-validator` — drop-in request validation (Zod schemas).
- `hono/cors`, `hono/logger`, `hono/secure-headers` — built-in middleware.
- `hono/jwt` — if we end up issuing our own session tokens.

## When NOT to use

- **The plugin↔backend hot path.** If raw WS throughput matters more than HTTP
  routing on the same connection, prefer `Bun.serve` directly — Bun's native WS
  is ~7x ws on Node per the Bun docs. See `realtime.md`. Acceptable to use Hono
  for HTTP API and Bun.serve for the high-volume WS, sharing the same process.
- **Heavy server-side rendering of React.** Hono can serve JSX but our front-end
  is Vite SPA; don't lean on Hono for SSR.

## Maintenance signals (verified 2026-06)

- Hono — v4.12.26 (2026-06-18). https://github.com/honojs/hono
- 31k stars, weekly cadence.
- Elysia — v1.4.29 (2026-06-16). https://github.com/elysiajs/elysia 18.5k★.
