# Backend auth (post RAI-39)

The backend exposes two authenticated surfaces:

1. **`/v1/*`** — used by the RuneLite plugin (and, transitively, by the
   future dashboard). Trust root: a per-device key bound to a `users`
   row via the pairing flow.
2. **`/admin/*`** — used by the ops console. Trust root: an HMAC-SHA256
   JWT cookie (`ops_session`) issued by `POST /admin/login`.

Both replace forgeable header-trust paths that audit `security-audit-001`
scored 10/10 (critical findings C1 and C2). Neither path is
backwards-compatible: the legacy headers (`x-user-id`,
`Authorization: Bearer <userId>`, `x-admin-email`) are rejected with 401.

## `/v1/*` — device-key Bearer

Plugin:

```kotlin
val raw    = deviceKey.getOrCreate()          // long-lived, 240-bit nanoid
val hashed = DeviceKey.hashForTransport(raw)  // SHA-256 hex
egress.egressHttp(
    method = "GET",
    path   = "/v1/account/summary",
    headers = listOf("Authorization" to "Bearer $hashed"),
)
```

Backend (`apps/backend/src/api/_auth.ts`):

1. Read `Authorization: Bearer <token>`.
2. Look up the row in `devices` whose `device_key_hash` verifies (argon2id)
   against `<token>`. Argon2 includes a per-row salt so we cannot index by
   hash — we scan and verify, matching the existing WS handshake in
   `auth/pairing.ts` (`authenticateDeviceKey`).
3. On hit: cache the `(sha256(token) -> {userId, deviceId})` mapping in a
   bounded LRU. Subsequent requests skip the argon2 scan and pay an O(1)
   `Map` lookup. The cache stores the SHA-256 of the bearer, never the
   bearer itself.
4. On miss: 401.

Threat model:

- A network observer who captures the bearer in transit has full access
  to that device's account until the device key is rotated. Hence: every
  request goes over TLS, and the plugin never logs the raw key or the
  hash beyond a 4-char prefix.
- A DB compromise yields the argon2 digest only. Re-hashing the
  corresponding raw key is infeasible (memoryCost = 64 MiB, timeCost = 3,
  parallelism = 4).
- A heap dump of the live backend yields the in-memory LRU cache. We
  hold `sha256(bearer)` rather than the bearer; an attacker who reads
  the cache cannot replay against a different argon2 backend, but CAN
  resume sessions against THIS backend until the cache evicts. Cache
  bound: 5000 entries.

### Dev-only escape hatch

For local scripts / tests that need an authenticated endpoint without
seeding a device row:

```sh
NODE_ENV=development ALLOW_DEV_HEADERS=true bun run dev
```

then:

```sh
curl http://localhost:8787/v1/me/export -H "x-dev-user-id: user_abc..."
```

Both conditions must hold:

- `env.NODE_ENV` is **not** `"production"`.
- `env.ALLOW_DEV_HEADERS === "true"`.

`server.ts` calls `warnIfDevHeadersOn()` at boot; the diagnostic is loud
so a misconfigured prod image cannot silently accept the bypass.
Production code paths ignore the header even if the env vars are set.

## `/admin/*` — `ops_session` JWT cookie

1. Operator POSTs `{ email }` to `/admin/login`.
2. Backend checks `email` against `env.ADMIN_EMAILS`. On match:
   - Mint a 12h-lifetime HMAC-SHA256 JWT (`jose.SignJWT`).
   - Set as `Set-Cookie: ops_session=<jwt>; HttpOnly; SameSite=Strict;
     Secure (in prod); Max-Age=43200`.
3. Every subsequent admin request carries the cookie. The shared
   `adminGate` middleware (`apps/backend/src/api/admin/_gate.ts`):
   - Reads the `ops_session` cookie.
   - `jose.jwtVerify`s it against the secret.
   - Asserts the embedded `email` is still in the allow-list (revocation
     is "remove the email from `ADMIN_EMAILS` and restart").
   - Sets `c.var.adminEmail` for downstream audit logging.
4. Anything missing / malformed / expired → 401.

### `OPS_JWT_SECRET`

- Production (`NODE_ENV === "production"`): **required**, must be at
  least 32 characters. `resolveOpsJwtSecret` throws at first use when
  unset — boot refuses to serve the admin surface. This closes audit C4.
- Dev / test: a per-process derived fallback runs after a loud warning.
  Tests inject `jwtSecret` explicitly via the router options.

## Migrating client code

When in doubt, the test fixtures in `apps/backend/test/_auth-fixture.ts`
are canonical:

```ts
const seeded = await seedDevice(handle, { email: "tom@rowm.co" });
const res = await app.fetch(
  new Request("http://localhost/v1/me/export", {
    headers: bearerHeaders(seeded.rawDeviceKey),
  }),
);
```

```ts
const cookie = await opsSessionCookieHeader("tom@rowm.co", OPS_SESSION_SECRET);
const res = await app.fetch(
  new Request("http://localhost/admin/users", { headers: cookie }),
);
```
