# Test quality review 001

- Reviewer: Test quality hat (hat 5).
- Date: 2026-06-21.
- Scope: 28 backend bun tests, 7 ops vitest routes, 10 marketing vitest sections, 22 plugin JUnit files (Kotlin), 5 e2e scenarios.
- Frame: Kent C. Dodds testing trophy plus the 18 RTL common mistakes, plus assertion-strength heuristics. Linear: RAI-62.

## Verdict

The backend suite is genuinely strong on auth, billing webhooks, and the meter; the plugin suite has the best test in the repo (the audit-shield sentinel pattern); but at least one paid-customer surface ships green with broken coverage (e2e scenario 04 still sends the legacy `x-admin-email` header that the gate now rejects), the WS handler test reuses module-scoped mutable state across `it` blocks, and the marketing sections lean on `data-testid` and `getByText` where `getByRole` would do, which trades real regression coverage for a green dot.

## Tests that would miss a real regression

### 1. The admin ban+refund e2e is dead on arrival; it asserts 200 against a 401 path

`e2e/scenarios/04-admin-ban-refund.spec.ts:34` builds the admin header as `{"x-admin-email": ctx.env.ADMIN_EMAIL}` and posts it to `/admin/users/.../ban` (line 61), then asserts `banRes.status).toBe(200)` (line 66). The route is mounted via `adminUsers` (`e2e/orchestrator/boot.ts:339`), which uses `adminGate` from `apps/backend/src/api/admin/_gate.ts:48`; that gate explicitly rejects the legacy header (the `users` unit test at `apps/backend/test/admin-users.test.ts:50` pins this with the comment "audit C2 closed"). The scenario only stays green because the e2e suite cannot currently resolve `drizzle-orm` from the repo root (`bun test e2e/scenarios/04-admin-ban-refund.spec.ts` fails with `Cannot find package 'drizzle-orm'` before any assertion runs). A real regression that re-enabled the legacy admin header would not get caught because the e2e never executes; a real regression that fixed the e2e setup would catch the test in its current broken state. **Strengthened assertion:** open the same flow with a proper `ops_session` cookie (use `signOpsSession` from `apps/backend/test/_auth-fixture.ts` or fetch `/admin/login` first), and add a separate `it` that explicitly asserts the legacy header still 401s so the audit-C2 invariant survives at the e2e layer too.

### 2. WS protocol test leaks `balance`, `balanceCalls`, and `events` across `it` blocks

`apps/backend/test/ws.test.ts:49-67` declares `let balance = 100_000`, `const balanceCalls = []`, `const events = []` at module scope and never resets them between tests. The "rejects unknown device keys" `it` at line 269 happens to not care, but the order of execution is not pinned: if `beforeAll` were ever paired with a re-ordered `it` set, a previous test would leave 17+123 tokens consumed and the assertion at line 258 (`expect(done.balanceTokens).toBe(100_000 - 123 - 17)`) would silently pass or fail based on which test ran first. The `events.some(...)` assertions at line 262-264 would also pass on a stale event from an earlier test. **Strengthened assertion:** move `balance`, `balanceCalls`, `events` into a `beforeEach` initializer so the round-trip test is provably isolated; assert `balanceCalls).toHaveLength(1)` *after* clearing.

### 3. SourceTreeAudit silently skips when run outside `apps/plugin/`

`apps/plugin/src/test/kotlin/co/rowm/osrsllm/cloud/SourceTreeAuditTest.kt:81` defines `skipIfNoTree()` which calls `Assume.assumeTrue(productionTree.isNotEmpty())`. When the test runs from any cwd other than `apps/plugin/`, the `src/main/kotlin/co/rowm/osrsllm` directory does not exist (line 16), the file list is empty, and all four egress-shield tests silently skip. The runtime mirror of the Gradle grep audit, including `webSocket.send(` must appear exactly once (line 78) and the no-plaintext-URL invariant (line 27), is exactly the kind of audit that must NEVER silently disappear; a CI runner that invoked Gradle from the repo root would drop these tests with no failure. **Strengthened assertion:** replace `Assume.assumeTrue(...)` with `fail(...)` if the production tree cannot be located; teach the test to look up the workspace root from a known marker (e.g. `apps/plugin/build.gradle.kts`) and fail loud if missing.

### 4. `usage.test.ts` happy-path asserts shape, not content, after seeding two days

`apps/backend/test/usage.test.ts:117-134` seeds three `usageRecords` across two distinct dates, then only asserts `Array.isArray(body.dailyTokens).toBe(true)` (line 134). The grouping logic that this test ostensibly covers (the day boundary plus the per-day sum) is untested at this code path; an off-by-one timezone bug or a bug that drops one of the days entirely would still pass. The next test ("groups same-day rows", line 137-171) covers same-day grouping but the realistic 2-day case is what users will hit on a renewal-boundary day. **Strengthened assertion:** `expect(body.dailyTokens.length).toBe(2)`, and assert both day labels in ISO form plus their summed token counts (today: 425, yesterday: 750).

### 5. Marketing PricingTiers free-tier test only asserts that fetch was NOT called

`apps/marketing/src/sections/PricingTiers.test.tsx:42-51` clicks `pricing-cta-free` and asserts `expect(spy).not.toHaveBeenCalled()`. Negative assertions on a freshly-installed spy pass when nothing happens, when the click was ignored, when the button was not found (`getByTestId` returned a stale node), or when the user wires a `href` instead of `onClick`. A real regression that fired a checkout request to `/v1/billing/checkout/free` would surface via this test only if the network was attempted; if the dev wired the wrong endpoint slug ("free" mapped to a 404), the test would still pass. **Strengthened assertion:** assert the free button exposes a `href` to `#install` (or whatever the free path actually is), or that an `onClick` is absent (`expect(freeBtn).not.toHaveAttribute("onclick")` plus a more meaningful behaviour check), not just that `fetch` is silent.

### 6. `bus.size + clear + unsubscribe` test asserts return values, not behaviour

`apps/backend/test/events.test.ts:104-112` subscribes, unsubscribes, then asserts the *counters* (`{ typed: 0, wildcard: 0 }`) instead of asserting that the unsubscribed handler does NOT fire when a matching event is published. The counter is implementation detail; a refactor that left handlers attached but decremented the counter (a real bug) would pass this test. **Strengthened assertion:** after `off1()`, publish a `chat.cap_hit` and assert the handler never received it.

### 7. Aggregation idempotency test names the wrong contract

`apps/backend/test/aggregate.test.ts:208-222` is titled "running the same window twice doubles the counts" and asserts exactly that. The contract the comment explains (the cursor in the production driver advances and prevents the second pass) is NOT what is tested here; the test pins the unsafe behaviour as if it were the spec. A future refactor to make `aggregateOnce` idempotent (a desirable change) would be flagged as a regression by this test. The test is a memo for a known limitation; a `// TODO: this asserts current sharp edges, not the desired contract` comment exists at line 8-17 but the test name itself reads like a positive invariant. **Strengthened assertion:** rename to "documents that double-running the same window in tests will double-count; production avoids this via cursor advance" so an aggregator change does not get blocked.

### 8. Stripe webhook `firstPaid` event assertion is presence-only

`apps/backend/test/billing-stripe-webhook.test.ts:282-284` finds the `funnel.first_paid` event with `expect(firstPaid).toBeDefined()` but never asserts the embedded `userId` or `tier`. A regression that emitted `funnel.first_paid` with a wrong userId (e.g. the Stripe customer id instead of the user id) would pass. The "billing.subscription.created" assertion above at line 277-281 does check tier and quota, so the pattern exists; it just is not applied here. **Strengthened assertion:** `if (firstPaid?.type === "funnel.first_paid") { expect(firstPaid.userId).toBeTruthy(); expect(firstPaid.payload.tier).toBe("pro"); }`.

### 9. `account.test.ts` "returns null subscription + empty arrays" only asserts on the null branches

`apps/backend/test/account.test.ts:72-88` asserts `body.tier).toBeNull()`, `body.subscriptionStatus).toBeNull()`, and `body.pairedOsrsAccounts).toEqual([])`, but the freshly-minted user actually has a bound device, and line 88 only checks `(body.pairedDevices as unknown[]).length).toBe(1)`. It does not assert `isCurrent`, `displayName`, or that the response masks the `deviceKeyHash`. A regression that surfaced the raw hash on the freshly-minted-user path (most likely path, fewest covering tests) would not be caught by this test; it IS caught later at line 149-172 via the "never leaks" body-text assertion, but only because the assertion looks at every test fixture's user. **Strengthened assertion:** in the freshly-minted-user test, also assert `body.pairedDevices[0].deviceKeyHash` is undefined and `body.pairedDevices[0].displayName` matches the seeded fixture.

### 10. `_db-fixture` not shown; tests assume a clean PGLite, but admin-login is shared per-app

`apps/backend/test/admin-login.test.ts` uses `app()` factory per test (line 16-21) but the env-derived JWT secret resolver `resolveOpsJwtSecret` (`apps/backend/src/api/admin/_gate.ts:54`) is not overridden in this file. If `OPS_JWT_SECRET` is empty in test env, the production resolver would normally throw on import. The test passes the override `jwtSecret: SECRET` (line 17), so the gate is fine, but `/admin/session` (line 56-74) only checks the cookie payload echo. It does not test what happens when the cookie is signed by a non-test secret and the runtime falls back to `resolveOpsJwtSecret`. **Strengthened assertion:** add a test that asserts `resolveOpsJwtSecret` itself refuses to return a default secret in production (NODE_ENV=production).

## Brittle tests

### 1. Marketing tests count `<li>` children via `el.tagName === "LI"`

`apps/marketing/src/sections/PricingTiers.test.tsx:10-14` walks `Array.from(list.children).filter((el) => el.tagName === "LI")` instead of using `within(list).getAllByRole("listitem")`. A future a11y wrapping (`<li><article>...</article></li>`) is robust to this query, but a refactor that swaps `<ul>` for a CSS-grid `<div>` of tiles will silently break this test even though the visible UI is identical. The same pattern works correctly in `FeatureGrid.test.tsx:8`, `FAQ.test.tsx:9`, `TrustStrip.test.tsx:10`, which use `within().getAllByRole("listitem")`. Clone that pattern here.

### 2. PricingTiers reaches into `window.location` via `Object.defineProperty`

`apps/marketing/src/sections/PricingTiers.test.tsx:25-39` redefines `window.location` so it can assert `window.location.href === "https://checkout.stripe.test/c_pro"`. This is intrinsically brittle; jsdom's `location` proxy semantics are not stable across Vitest minor versions. A safer approach is to inject the navigator (e.g. accept an `onCheckout` prop) and assert that the component called it with the URL. Lower-priority if the team consciously chose to test the real `location.assign` plumbing.

### 3. LiveCounter accepts BOTH `regions[]` and `byRegion{}` shapes

`apps/marketing/src/sections/LiveCounter.test.tsx:36-83` exercises both response shapes ("renders count and per-region breakdown" line 36 and "accepts the new `byRegion` map shape" line 64). This is fine for transition correctness but the test names suggest both shapes are supported indefinitely. If the backend ever drops `regions[]` (the RAI-21 surface), the older test still passes because the component handles both; it cannot tell you which shape is the live one. Add a backend-level assertion that the live API responds with `byRegion`.

### 4. `installFetchMock` route-string keys

The ops `installFetchMock({"GET /api/admin/users": ...})` pattern (`apps/ops/src/routes/users.test.tsx:18`) is a route-string literal. Any drift in the URL prefix (e.g. `/api/admin/users` becomes `/api/v1/admin/users`) makes the mock silently miss and the component renders the loading state forever; the test asserts the success state via `waitFor` so it times out 50 seconds in. The harness should error LOUDLY when a request goes unmatched. Lower-priority; consider an "unmatched fetch must throw" mode for the harness.

### 5. Demo / Footer / ProblemSolution tests assert headings exist; nothing else

`apps/marketing/src/sections/Demo.test.tsx:6-13`, `Footer.test.tsx:6-13`, `ProblemSolution.test.tsx:6-15` are one-liner smokes that assert a `data-testid` is present and a heading exists. They will pass on a component that renders just the heading and nothing else. The actual demo content (script blocks, dialog flow), footer links (privacy, terms, contact), and problem/solution copy could be deleted and the tests would still pass. These are weak tests, not no-test, but they should be promoted to assert the load-bearing content.

## Tests that pass for the wrong reason

### 1. RTL mistake 12: PricingTiers uses `fireEvent.click` where `userEvent.click` would do

`apps/marketing/src/sections/PricingTiers.test.tsx:49, 65, 89` use `fireEvent.click`. The Kent C. Dodds article enumerates this as mistake 12 because `fireEvent` dispatches a single synthetic event whereas a real user interaction goes through pointer-down, focus, mouse-up, click. A button that responds to `pointerdown` (e.g. a custom focus-trap or a debounce) will pass this test but fail in a real browser. `ops/src/routes/catalog.test.tsx:138` has the same issue. The fixture in `ops/src/routes/login.test.tsx:40` correctly uses `userEvent.click`, so the team knows the right pattern.

### 2. RTL mistake 7: marketing tests reach for `data-testid` where `getByRole` would do

`apps/marketing/src/sections/Hero.test.tsx:8`, `LiveCounter.test.tsx:31, 53, 76`, `Demo.test.tsx:8`, `Footer.test.tsx:8`, `FreeTierStrip.test.tsx:8`, `TrustStrip.test.tsx:8`, `FeatureGrid.test.tsx:8`, `FAQ.test.tsx:8`, `ProblemSolution.test.tsx:8` all use `screen.getByTestId("section")` as the existence check. Existence-by-testid is what Kent calls "the test of the test infrastructure"; the visible CTA text or the heading is the user-facing contract. Most of these tests then DO assert visible text immediately after, so the harm is limited; but the existence-by-testid lines could be dropped or replaced with role queries.

### 3. RTL mistake 13: `query*` outside of non-existence assertions in PricingTiers

`apps/marketing/src/sections/PricingTiers.test.tsx:13` uses `screen.queryByText(/most picked/i)` in `.not.toBeInTheDocument()` which is correct usage. No misuses observed across the suite; flagging just to confirm the rule was checked.

### 4. The login.test.tsx fetch override leaks the previous mock

`apps/ops/src/routes/login.test.tsx:25-47` first calls `installFetchMock({"POST /api/admin/login": () => ({ ok: false, error: "unauthorized" })})` (line 26), then OVERRIDES `globalThis.fetch` again at line 31 to return a 401 Response directly. The first mock is now dead code; the test "shows 'not authorised' on 401" actually exercises the second mock only. If the harness is later changed to throw on stale mocks, this test breaks for the wrong reason. **Fix:** drop line 26-29 entirely, install only the 401 path.

### 5. `events.test.ts` "size + clear + unsubscribe" passes if the bus implementation no-ops on `off()`

`apps/backend/test/events.test.ts:104-112` asserts `bus.size()` returns the right shape. A buggy implementation of `off1()` that decrements the counter but leaves the handler attached would pass this test. (Same root cause as finding 6 in "regressions that would slip through"; listing here too because it is an active wrong-reason-pass.)

### 6. `installFetchMock({})` in login test

`apps/ops/src/routes/login.test.tsx:19` passes an empty fetch-mock and asserts only that the input and copy render. Any unmocked `fetch` call from the component would not surface here; the test asserts the component lifts to React but tells you nothing about the data-fetching state. Acceptable as a pure-render smoke.

## Missing tests

### 1. WS reconnect storm (auth fix H4 deferred)

The plugin `ReconnectTest.kt` covers single-drop reconnect (line 48-120) and the strategy unit (line 122-150). No test covers a reconnect *storm*: server drops 10 times in 500ms, and the backoff cap is honoured. The strategy test pins backoff numerics but the runner-level integration does not. Risk: the runner re-auths immediately on every drop, hammering the backend during a real outage.

### 2. Concurrent token meter, race against subscription credit

`apps/backend/test/billing-meter.test.ts:91-109` proves N=10 concurrent reservations sum exactly. No test covers a concurrent reservation racing against a `meter.credit(userId, X)` from a Stripe webhook firing at the same instant. PGLite serialises these in the harness, but Postgres in prod will interleave them; a missing FOR UPDATE on the credit path would only surface under prod concurrency.

### 3. Stripe webhook out-of-order delivery

`apps/backend/test/billing-stripe-webhook.test.ts:286-298` covers "updated does not re-emit created" but does NOT cover: a `subscription.updated` arriving BEFORE the `subscription.created`. Stripe explicitly warns about this. The webhook handler appears to fetch the existing row by `stripeSubscriptionId` (the .find at line 271-275 only proves a row exists); if the updated path silently creates the row without firing the `created` event, the funnel.first_paid metric would be lost forever.

### 4. GDPR delete then re-pair (auth fix H1 deferred)

`e2e/scenarios/03-gdpr-export-delete.spec.ts:99-103` asserts the second DELETE is idempotent. There is no test for: pair → delete → pair again with the same device key. Per `DATA_RETENTION.md`, the soft-deleted user row's email is null but the row exists; a new pair attempt should either re-activate or create a new user. Without a test, either path could ship.

### 5. Auth middleware cache invalidation on ban

`apps/backend/test/auth-middleware.test.ts:84-95` warms the `DeviceKeyCache` and asserts the second request is still 200. There is no test for: warm the cache, then `users.status = banned`, then request. Does the cache return the stale "active" verdict? The Ban endpoint at `apps/backend/test/admin-ban.test.ts:51-89` ends sessions but does not invalidate the DeviceKeyCache. A banned user could continue chatting until the cache TTL expires.

### 6. Stripe webhook signature replay across event types

The idempotency test (`apps/backend/test/billing-stripe-webhook.test.ts:337-355`) uses the same event id 3 times for `invoice.payment_succeeded`. A real replay-attack vector is: replay a `customer.subscription.created` event with an OLD `created` timestamp (e.g. one second after the real `cancelled`). Does the handler keep the cancellation? No coverage.

### 7. Plugin egress audit log capacity ordering

`EgressGateTest.kt:127-135` asserts the audit log is bounded but only that the oldest is dropped. There is no test for *which* of the dropped entries can be reconstructed; an audit log that loses the most recent entries instead of the oldest would pass. Add an explicit "the dropped entries are the OLDEST" assertion using payload kinds rather than `sizeBytes >= 50`.

### 8. ToolGatingTelemetry: no test that the audit row never crosses the WS

`ToolGatingTelemetryTest.kt:11` (RAI-25) verifies the AuditLog is written. There is no test that asserts the telemetry NEVER egresses; the SourceTreeAuditTest covers it for grep ("webSocket.send appears exactly once"), but a finer unit test that proves `ToolGatingTelemetry.recordExposed` does not call the transport would catch a regression at unit speed.

### 9. Presence tracker memory leak from repeated reconnect

`apps/backend/test/presence.test.ts:93-104` covers a single reconnect-cancels-grace. There is no test for: connect/disconnect cycle repeated 10k times for the same session id; do internal maps grow unbounded?

### 10. Marketing checkout: missing CSP / open-redirect test

`apps/marketing/src/sections/PricingTiers.test.tsx:67-69` asserts `window.location.href` ends up at the Stripe URL but does not verify the URL is `https://`. A backend regression that returned `javascript:alert(1)` as the checkout URL would happily redirect.

## Wins (clone these patterns)

### 1. Audit-shield sentinel: `DirectChatRunnerTest.kt`

`apps/plugin/src/test/kotlin/co/rowm/osrsllm/cloud/byo/DirectChatRunnerTest.kt:30-107` defines a fixed `SENTINEL_KEY = "sk-LEAK-DIRECTRUNNER-SENTINEL-abcdef"` and an `assertSentinelNeverLeaked()` helper that runs after every test; it walks the audit log entries plus the callback transcript and asserts the sentinel is absent. Every test that touches a key runs this. **Clone this for:** the backend Stripe webhook (sentinel signing secret never echoed), the OpenRouter client (sentinel API key never echoed), the GDPR export endpoint (sentinel email never leaked back to a different user).

### 2. SourceTreeAuditTest: build-shield mirrored to unit-test report

`SourceTreeAuditTest.kt:25-79` re-runs the Gradle grep audits inside JUnit so the legibility invariants show up in the CI test report, not just the build log. Pattern is a strict win; fix finding 3 (silent-skip) and this becomes excellent. **Clone this for:** the backend `/admin/users` mounting (assert exactly one `app.use("*", adminGate(...))` per admin router file).

### 3. `OutboundPayload` sealed-hierarchy pin (`EgressGateTest.kt:104-124`)

The test reflects on `OutboundPayload::class.sealedSubclasses` and asserts the set of names equals `DATA_DISCLOSURE.md` exactly. Any new wire shape forces both the test AND the docs to update. Excellent example of a test that doubles as a docs-compliance lock.

### 4. Stripe webhook idempotency at the schema level

`apps/backend/test/billing-stripe-webhook.test.ts:337-355` does not just assert "no double-credit" by reading the balance; it also reads `processedStripeEvents` and asserts exactly one row. Two angles on the same invariant; if either drifts, the test fails. Clone this two-angle pattern.

### 5. Pairing auth fixture (`apps/backend/test/_auth-fixture.ts`)

The `seedDevice` + `bearerHeaders` + `opsSessionCookieHeader` fixture set means every auth test in the backend uses the same real argon2 path and the same real jose JWT path; the only thing mocked is the DB. This is the testing-trophy "integration" sweet spot and it shows in the strength of `auth.test.ts`, `auth-middleware.test.ts`, and `admin-*.test.ts`.

## Per-tier assertion strength score

| Directory | Score / 10 | Why |
|---|---|---|
| `apps/backend/test/` | 8 | Real argon2, real jose, real PGLite, real event bus. Good two-angle invariants on billing. Loses points for the ws.test.ts module-state leak, the usage.test.ts shape-only happy-path, and the events.test.ts size-counter assertion that masks bugs. |
| `apps/plugin/src/test/kotlin/` | 9 | Best in the repo. Sentinel pattern, sealed-hierarchy pin, runtime mirror of Gradle audits. Loses one point for the `Assume.assumeTrue` silent-skip in SourceTreeAuditTest and the under-tested reconnect storm. |
| `apps/ops/src/routes/` | 6 | Real route mounting via TanStack Router test harness, real fetch mock. Loses points for: `fireEvent` in catalog test, the dead `installFetchMock({})` in login test, route-detail tests assert only happy-path content not edge states (empty subs, banned user). |
| `apps/marketing/src/sections/` | 5 | Mostly testid-existence smokes; Demo/Footer/ProblemSolution barely assert content. PricingTiers does the most work but uses `fireEvent` and a brittle `window.location` override. Has some good `within(list).getAllByRole("listitem")` patterns to clone. |
| `e2e/scenarios/` | 4 | Scenarios 01/03/05 are solid integration covers. Scenario 02 (BYOK) is half-skipped pending wiring. Scenario 04 is broken against current middleware AND the suite cannot run from repo root. The promise of e2e (real integration coverage) is not currently delivered. |

Weighted average across the surfaces, biased toward the paid-customer paths: 7/10. The build is green but a substantial fraction of the green dots are not testing what their names claim.

## Recommended fix order

1. **Repair e2e scenario 04** (`e2e/scenarios/04-admin-ban-refund.spec.ts`) to use the cookie-based admin auth, and fix the workspace resolution so `bun test e2e/scenarios/` actually runs. This is the single highest-leverage fix because it restores integration coverage of the audit-C2 invariant.
2. **De-leak `ws.test.ts`** module state. Move `balance`, `balanceCalls`, `events` into a `beforeEach`. This is small and immediately upgrades the strongest backend test.
3. **Fail loud in `SourceTreeAuditTest`** instead of `Assume.assumeTrue`. The audit shield must never silently disappear.
4. **Strengthen `usage.test.ts` 2-day case** to assert the actual grouping output, not just `Array.isArray(...)`. The renewal-boundary regression is exactly what the test should catch.
5. **Add the missing-tests list 1-5** (reconnect storm, concurrent credit, out-of-order Stripe, GDPR re-pair, DeviceKeyCache invalidation on ban). These are the H-tier auth-fix follow-ups that PR #69 left deferred.

## Things I checked and was happy with

- Auth lens: `auth.test.ts`, `auth-middleware.test.ts`, `admin-login.test.ts`, `admin-users.test.ts`, `admin-ban.test.ts`, `admin-refund.test.ts`. Strong real-crypto paths.
- Billing lens: `billing-stripe-webhook.test.ts`, `billing-meter.test.ts`. Strong concurrency check, strong idempotency, weak only on out-of-order delivery.
- Plugin egress lens: `EgressGateTest.kt`, `EgressGateHostAllowListTest.kt`, `SourceTreeAuditTest.kt`, `DirectChatRunnerTest.kt`. The strongest tier in the repo modulo finding 3.
- Event bus + persistence: `events.test.ts`, `aggregate.test.ts`. Good Zod-at-boundary, good window math; weak on the "size counter" assertion shape.
- Presence lens: `presence.test.ts`. Good grace-window + reconnect-cancels-grace coverage; missing the multi-thousand-reconnect leak case.
- Marketing role-based queries: `Hero.test.tsx`, `FreeTierStrip.test.tsx`, `FAQ.test.tsx`, `FeatureGrid.test.tsx`, `TrustStrip.test.tsx` all correctly use `screen.getByRole("heading", ...)` after the testid existence check.
