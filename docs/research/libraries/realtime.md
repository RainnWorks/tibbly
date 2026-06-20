# Realtime / WebSockets — recommendation

## Decision

**Use Bun's native `Bun.serve` WebSocket** for the high-volume plugin↔backend
chat link, **Hono's `upgradeWebSocket`** for any HTTP-route-adjacent WS
(simpler middleware), and **no Partykit / no third-party WS service** for v1.

| Option | Verdict | Why |
|---|---|---|
| **Bun.serve WebSocket** | ✅ PICK (hot path) | ~7x throughput vs Node `ws` per Bun docs; native pub/sub topics built-in for the "X agents online" feed |
| **hono/ws (hono/bun)** | ✅ PICK (HTTP-adjacent WS) | Trivial integration with our HTTP routes & middleware; under the hood it's still Bun's WS |
| Partykit | ❌ Skip for v1 | Edge multiplayer service. Nice but we want our own backend to keep credit accounting authoritative. Revisit if we hit scale |
| `ws` on Node | ❌ Skip | We're Bun-native. No reason to import a Node-only lib |

## Packages

- `hono` — already in `backend.md`. Provides `hono/bun` exporter.
- No extra dep needed for native Bun WS — it's part of the runtime.

Source: https://bun.com/docs/api/websockets

## Hello-world: pure Bun WebSocket with pub/sub

```ts
// apps/backend/src/realtime.ts
const server = Bun.serve({
  port: 3001,
  fetch(req, server) {
    const url = new URL(req.url);
    if (url.pathname === '/presence') {
      const userId = url.searchParams.get('uid') ?? 'anon';
      if (server.upgrade(req, { data: { userId } })) return;
      return new Response('upgrade failed', { status: 400 });
    }
    return new Response('not found', { status: 404 });
  },
  websocket: {
    data: {} as { userId: string },
    open(ws) {
      ws.subscribe('presence');
      server.publish('presence', JSON.stringify({ type: 'join', userId: ws.data.userId }));
    },
    message(ws, msg) {
      // echo / forward chat messages
      ws.send(`echo: ${msg}`);
    },
    close(ws) {
      server.publish('presence', JSON.stringify({ type: 'leave', userId: ws.data.userId }));
    },
  },
});

console.log(`realtime listening on ${server.port}`);
```

The `server.publish('presence', …)` call is what powers the marketing "X
agents online" widget — every join/leave is broadcast to all subscribers of
the `presence` topic.

## Hello-world: Hono WS (chat link)

```ts
import { Hono } from 'hono';
import { upgradeWebSocket, websocket } from 'hono/bun';

const app = new Hono();

app.get(
  '/chat',
  upgradeWebSocket((c) => {
    const deviceKey = c.req.header('authorization')?.replace('Bearer ', '');
    return {
      onOpen(_, ws) {
        // authenticate device key, attach userId
      },
      onMessage(event, ws) {
        // route to LLM, stream tokens back
        ws.send(JSON.stringify({ type: 'token', delta: '…' }));
      },
    };
  }),
);

export default { fetch: app.fetch, websocket, port: 3000 };
```

## Architecture note

Run **two Bun processes** (or one process with two `serve()` calls):
1. **API + chat WS** on port 3000 via Hono — auth, routing, billing, chat.
2. **Presence WS** on port 3001 via raw `Bun.serve` — high-fanout, no business
   logic, pub/sub only.

Splitting them means a presence spike can't starve the billing path.

## When NOT to use

- **Cross-region presence sync.** If we ever go multi-region, swap presence
  for Partykit or build a Redis pub/sub bus. Not tonight.
- **Persistent message history.** WS is transient. Persist to Postgres for the
  chat archive; WS is the wire.

## Maintenance signals

- Bun WS — part of Bun runtime. Backed by uWebSockets. Stable since 1.0.
  https://bun.com/docs/api/websockets
- Hono WS helper — current v4.x. https://hono.dev/docs/helpers/websocket
