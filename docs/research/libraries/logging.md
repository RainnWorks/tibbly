# Logging — recommendation

## Decision

**Use `pino`** for structured JSON logs on the backend, with **`pino-pretty`**
as a dev-only transport for human-readable output.

| Library | Verdict | Why |
|---|---|---|
| **pino** | ✅ PICK | Fastest JSON logger for Node/Bun, 17.9k★, structured logs are non-negotiable for billing audit trail |
| winston | ❌ Skip | Slower, more config surface, not aligned with Bun-native ethos |
| `console.log` | ❌ Skip | No structure, no level filtering, no redaction — billing path needs better than this |

## Packages

- `pino` — v10.3.1 (2026-02-09), 17.9k★.
  - https://getpino.io
  - https://github.com/pinojs/pino
- `pino-pretty` — dev-only transport.

## Install

```bash
bun add pino
bun add -D pino-pretty
```

## Hello-world

```ts
// apps/backend/src/lib/log.ts
import pino from 'pino';

const isDev = process.env.NODE_ENV !== 'production';

export const log = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  // redact PII + secrets before they ever hit a log line
  redact: {
    paths: [
      'req.headers.authorization',
      'req.headers.cookie',
      'deviceKey',
      'stripeSecret',
      '*.apiKey',
      'OPENROUTER_API_KEY',
    ],
    censor: '[redacted]',
  },
  transport: isDev
    ? { target: 'pino-pretty', options: { colorize: true, translateTime: 'HH:MM:ss' } }
    : undefined,
});
```

## Usage

```ts
import { log } from './lib/log';

log.info({ userId, tokens }, 'chat turn billed');
log.warn({ deviceKey: 'whatever' }, 'pairing code mismatch'); // deviceKey redacted
log.error({ err }, 'openrouter call failed');

// child loggers for request scoping
const reqLog = log.child({ requestId: nanoid() });
```

## With Hono request logging

```ts
import { Hono } from 'hono';
import { log } from './lib/log';

const app = new Hono();
app.use('*', async (c, next) => {
  const start = Date.now();
  await next();
  log.info(
    { method: c.req.method, path: c.req.path, status: c.res.status, ms: Date.now() - start },
    'req',
  );
});
```

## When NOT to use

- **In the plugin (Kotlin/JVM).** That's RuneLite's existing logger, not pino.
- **In the dashboard / marketing apps.** Frontend telemetry is a different
  problem — Sentry or PostHog later, `console.warn` for now.

## Security (SCOPE_GUARD)

- Never log raw `OPENROUTER_API_KEY`, `STRIPE_*`, or raw device keys. The
  `redact` config above covers them.
- Always log the **hash** of the device key, never the key itself.

## Maintenance signals

- pino — v10.3.1 (2026-02-09). https://github.com/pinojs/pino
- 318 releases, very active.
