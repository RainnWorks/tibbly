# Auth — recommendation

## Decision

**Use `better-auth` for any "real" auth surface**, plus a small custom
**device-key + pairing-code** flow that the plugin uses to bind a Stripe
customer to a long-lived device key. The custom flow is intentional — it is
what makes onboarding frictionless (no browser login on the plugin side).

| Library | Verdict | Why |
|---|---|---|
| **better-auth** | ✅ PICK | TypeScript-first, framework-agnostic, plugin ecosystem, automatic DB migrations, Hono/Bun friendly, 28.8k★ |
| Lucia | ❌ Skip | Now in maintenance / "no new features" mode in favour of better-auth's pattern |
| Clerk | ❌ Skip | Hosted, vendor lock-in, monthly active user pricing on top of Stripe — extra spend we don't need |
| Stack Auth | ⚠️ Watch | Open-source Clerk-alternative, fine but smaller community than better-auth |

## Packages

- `better-auth` — auth framework.
  - https://www.better-auth.com/docs (official)
  - https://github.com/better-auth/better-auth — v1.6.20 (2026-06-20), 28.8k★

## Install

```bash
bun add better-auth
```

## Hello-world: dashboard email + magic link

Source: https://www.better-auth.com/docs

```ts
// apps/backend/src/auth.ts
import { betterAuth } from 'better-auth';
import { magicLink } from 'better-auth/plugins';
import { db } from './db';

export const auth = betterAuth({
  database: { provider: 'pg', db },
  emailAndPassword: { enabled: true },
  plugins: [
    magicLink({
      sendMagicLink: async ({ email, url }) => {
        // wire up Resend / Postmark here later
        console.log(`magic link for ${email}: ${url}`);
      },
    }),
  ],
});
```

Mount on Hono:

```ts
import { Hono } from 'hono';
import { auth } from './auth';

const app = new Hono();
app.on(['POST', 'GET'], '/api/auth/*', (c) => auth.handler(c.req.raw));
```

## Custom: device-key + pairing-code (intentional)

This is the hot path. The plugin never opens a browser.

1. On first run, plugin generates `device_key = nanoid(40)`. Stored locally in
   the RuneLite profile dir. Never leaves the box except as `Authorization:
   Bearer <device_key>` over TLS.
2. Plugin asks backend `POST /api/devices/register` with `{ device_key,
   player_name }`. Backend stores `sha256(device_key)` in `devices`, **with no
   user_id yet** — unclaimed.
3. To bind to a paying customer: dashboard generates a 6-digit pairing code.
   Plugin shows a side-panel "enter pairing code from dashboard." User enters
   it; plugin calls `POST /api/devices/pair { device_key, code }`; backend sets
   `devices.user_id = current_user`.
4. From then on, every plugin call → backend authenticates by hashing
   `device_key`, looking up the row, taking `user_id`, checking credits.

Pairing-code schema:

```ts
// apps/backend/src/db/schema.ts
export const pairingCodes = pgTable('pairing_codes', {
  code: text('code').primaryKey(),           // 6-char base32
  userId: text('user_id').notNull(),
  expiresAt: timestamp('expires_at').notNull(),
  usedAt: timestamp('used_at'),
});
```

Pairing codes:
- 6 chars from a 32-char alphabet (no 0/O/1/I) → 1,073,741,824 codes.
- Expire after 10 minutes.
- One-shot: marked used on first successful pair.
- Hard rate-limit `POST /api/devices/pair` by IP and by code.

## When NOT to use better-auth

- **Plugin-side auth.** Plugin never sees better-auth at all — it lives behind
  the dashboard URL only. The plugin uses the device-key bearer scheme above.
- **Multi-tenant org features tonight.** better-auth supports it via plugin
  but it's out of scope.

## Security notes (SCOPE_GUARD)

- Never log `device_key`.
- Always store `sha256(device_key)`, not the raw key.
- Rate-limit `/api/devices/pair` and `/api/chat/*` by device-key hash.
- Hard cap requests per minute per device, regardless of credit balance, to
  short-circuit a leaked key.

## Maintenance signals

- better-auth — v1.6.20, 2026-06-20. https://github.com/better-auth/better-auth
- Active core team, weekly releases.
