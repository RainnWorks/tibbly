# TypeScript strictness 001 — adversarial review of apps/backend/src/

Author: TypeScript-strictness hat (hat 2, loop M+14)
Branch: `agent/loop-mplus14/review-typescript-strictness`
Linear: RAI-60
Scope: `apps/backend/src/api/**`, `apps/backend/src/db/*`, `apps/backend/src/llm/**`, `apps/backend/src/events/**`, `apps/backend/src/billing/**`, `apps/backend/src/ws/**`, `apps/backend/src/lib/*`, `apps/backend/src/server.ts`, `apps/backend/src/app.ts`, `apps/backend/src/env.ts`. Tests excluded.

Reference: typescript-eslint `strict-type-checked` ruleset + Effective TypeScript items 7-9, 28-30, 33-35, 38-42, 56-60.

## Verdict

Would-merge-with-fixes. The codebase is type-aware and Zod-validated at most edges. The bulk of strictness violations cluster in three places: Stripe envelope re-casts that bypass the typed SDK union, the WS `server.ts` dispatch shim that erases `Bun.ServerWebSocket` typing through three layers of `as unknown as`, and a Stripe webhook fan-out where `event.data.object as Stripe.Subscription` lies to the compiler in 4 separate handlers. None of these are currently-exploitable runtime bugs at the type layer (Zod catches the inbound shapes; Stripe verifies the envelope), but each one will silently rot when Stripe ships a new dated API or Bun changes its WS typing. Two genuine bugs surface: a forced `!` on a same-statement nullable narrowing in `admin/catalog.ts:136`, and a `Tier` cast that bypasses validation in `admin/users.ts:125`.

Blockers: 3. Important findings: 11. Nits: 7.

## Blockers

### B1. Forced `!` on `r.retiredAt` after a same-statement nullable narrowing

`apps/backend/src/api/admin/catalog.ts:130-137`

```
const retired = rows
  .filter((r) => r.retiredAt !== null && r.retiredAt.getTime() >= since.getTime())
  .map((r) => ({
    id: r.id,
    provider: r.provider,
    displayName: r.displayName,
    retiredAt: r.retiredAt!.toISOString(),
  }));
```

`Array.prototype.filter` does NOT narrow the array element type. TypeScript's lib type for `filter` returns `T[]`, not `Exclude<T, ...>` (Effective TypeScript item 28 "Prefer types that always represent valid states"; typescript-eslint `no-non-null-assertion` is the surface symptom). The `!` on line 136 is the author silently admitting the compiler cannot prove the narrowing. The fix is either a user-defined type predicate:

```
.filter((r): r is typeof r & { retiredAt: Date } => r.retiredAt !== null && ...)
```

or, cleaner, fold the filter + map into a `flatMap` that returns `[]` for the absent case. Either path removes the `!` and removes the entire class of "I refactored the predicate and now this crashes at runtime" bugs.

Rule: `@typescript-eslint/no-non-null-assertion` + Effective TS item 9 ("Prefer type declarations to type assertions").

### B2. `c.req.query("tier") as "hobbyist" | "pro" | "iron" | "free" | undefined` — unsound narrowing from user input

`apps/backend/src/api/admin/users.ts:125-135`

```
const tier = c.req.query("tier") as
  | "hobbyist"
  | "pro"
  | "iron"
  | "free"
  | undefined;
const status = c.req.query("status") as
  | "active"
  | "banned"
  | "deleted"
  | undefined;
```

`c.req.query` returns `string | undefined`. The `as` assertion lies to the compiler about a raw HTTP query value. Downstream code at line 144 (`if (status === "active") ...`) is correct by accident: the runtime string compare still narrows safely. But the precedent encourages every future admin route to do the same thing. The day a `tier` value flows into a type-sensitive call (e.g. `TIER_BY_NAME[tier]` at lookup time), the `as` makes the bug invisible at compile time and surfaces at runtime as `undefined.property`.

Fix: parse with a Zod enum at the route boundary:

```
const TierFilter = z.enum(["hobbyist", "pro", "iron", "free"]).optional();
const tier = TierFilter.safeParse(c.req.query("tier")).data;
```

`billing-checkout.ts:53-69` already does exactly this. Adopt it here.

Rule: `@typescript-eslint/consistent-type-assertions` + Effective TS item 7 ("Think of types as sets of values") + item 9.

### B3. Stripe `Subscription` envelope re-cast bypasses the typed SDK union

`apps/backend/src/api/webhooks/stripe.ts:180-189` and parallel hits at `:86`, `:93`, `:96`, `:99`.

```
const subEnvelope = sub as unknown as {
  current_period_start: number;
  current_period_end: number;
  status: Stripe.Subscription.Status;
  cancel_at_period_end: boolean;
};
```

`sub` is already typed `Stripe.Subscription`. The double-cast (`as unknown as { … }`) means: "I know better than the SDK". The justification in the surrounding comment ("Stripe puts the subscription id on `parent.subscription_details` in 2026 API versions") is a real concern, but the fix is to read the field via a typed helper that consults the SDK's union, not to hand-roll a parallel envelope shape every call site. Every one of those four `event.data.object as Stripe.X` casts at `:86`, `:93`, `:96`, `:99` carries the same problem. `event.data.object` is `Stripe.Event.Data.Object`, a discriminated union, and the typed way to extract the inner shape is `if (event.type === "...") { event.data.object }` which the SDK union already narrows.

Fix: use the SDK's discriminated `Stripe.Event` union directly. Drop the `as` casts, switch on `event.type` (which the SDK narrows automatically), and read the typed inner object. Where a field is genuinely not in the snapshot the SDK pins (e.g. `current_period_start` on the new 2026 envelope), pull it via a typed extension that documents what it is compensating for, e.g.:

```
interface SubscriptionPeriod { current_period_start: number; current_period_end: number; }
function readSubscriptionPeriod(sub: Stripe.Subscription): SubscriptionPeriod { ... }
```

That signature is one place to fail-fast when the API moves; the current shape will silently parse a `null` as `0` (Date constructor accepts both) and credit zero tokens.

Rule: `@typescript-eslint/no-unsafe-type-assertion`, `@typescript-eslint/no-unnecessary-type-assertion`, `@typescript-eslint/consistent-type-assertions` + Effective TS item 9.

## Important findings

### I1. WS dispatch shim launders `Bun.ServerWebSocket` through `as unknown as { data: unknown }` three times

`apps/backend/src/server.ts:86-125`

```
async function dispatch<R>(
  ws: { data: unknown },
  fn: () => R | Promise<R>,
  inner: unknown,
): Promise<R> { ... }

void dispatch(ws as unknown as { data: unknown }, () => target.open?.(ws as never), inner);
```

The four `dispatch(ws as unknown as { data: unknown }, ..., ws as never)` calls (`:105`, `:111`, `:117`, `:123`) erase every guarantee Bun gives about the WS data shape. The `as never` is particularly bad: it tells the compiler "this can never be reached" purely to satisfy variance, which is a worse signal than `as any`. The "swap-data envelope on entry, restore on exit" trick is real and load-bearing for the single-port multi-handler routing, but it is implementable as a generic over `ServerWebSocket<DispatchData>` without ever escaping into `unknown`. The fix:

```
async function dispatch<R, T>(
  ws: ServerWebSocket<DispatchData>,
  innerData: T,
  fn: (innerWs: ServerWebSocket<T>) => R | Promise<R>,
): Promise<R> {
  const envelope = ws.data;
  (ws as ServerWebSocket<T>).data = innerData;
  try { return await fn(ws as ServerWebSocket<T>); }
  finally { (ws as ServerWebSocket<DispatchData>).data = envelope; }
}
```

One inner cast, contained to one helper, with a documented reason. Down from eight outer `as unknown / as never` casts in three call sites.

Rule: `@typescript-eslint/no-explicit-any` (informal), `@typescript-eslint/no-unsafe-argument` + Effective TS item 38 ("Use the narrowest possible scope for any `any`-equivalent").

### I2. `WebSocketHandler<DispatchData>` `open` callback consumes `ws.data` without proving discriminant

`apps/backend/src/server.ts:80-104`

```
interface DispatchData {
  kind: SocketKind;
  plugin?: ReturnType<typeof pluginWs.makeSocketData>;
  presence?: ReturnType<typeof presenceWs.makeSocketData>;
}
...
open(ws) {
  const data = ws.data;
  const target = data.kind === "plugin" ? pluginWs.websocket : presenceWs.websocket;
  const inner = data.kind === "plugin" ? data.plugin : data.presence;
```

Both `plugin` and `presence` are optional. When `data.kind === "plugin"`, the compiler does NOT know `data.plugin` is defined, because the type is not a discriminated union. Today this works because `fetch` always populates the matching field at `:132` and `:137`, but the type lies about the invariant.

Fix: make `DispatchData` a real union, e.g.

```
type DispatchData =
  | { kind: "plugin"; plugin: ReturnType<typeof pluginWs.makeSocketData> }
  | { kind: "presence"; presence: ReturnType<typeof presenceWs.makeSocketData> };
```

Then `data.kind === "plugin"` narrows `data.plugin` without an optional. Removes four `data.plugin!`-equivalent reads.

Rule: `@typescript-eslint/strict-boolean-expressions` (related) + Effective TS item 28 ("Prefer types that always represent valid states").

### I3. Repeated unsafe `Error` cast in `.catch(err => log({ message: (err as Error).message }, ...))`

`apps/backend/src/llm/catalog/scheduler.ts:71-75`, `:91-95`; `apps/backend/src/llm/catalog/ingest.ts:158-162`; `apps/backend/src/api/me.ts:194-197`; `apps/backend/src/api/billing-checkout.ts:96-101`.

```
.catch((err: unknown) =>
  log.error(
    { err: { message: (err as Error).message } },
    "model-catalog: boot refresh failed",
  ),
);
```

`err` is correctly typed `unknown`, then immediately cast to `Error`. The cast lies whenever the thrown value is not an Error: Promise rejection in JS can carry any value, including `null`, `undefined`, and Stripe's `StripeError` subclass (which derives from `Error` but adds `.code`, `.statusCode`, `.raw` that the current shape drops). Pino can serialize an `Error` directly. Let it.

Fix:

```
.catch((err: unknown) =>
  log.error({ err: serializeError(err) }, "model-catalog: boot refresh failed"),
);

function serializeError(err: unknown): { message: string; stack?: string; name?: string } {
  if (err instanceof Error) return { message: err.message, stack: err.stack, name: err.name };
  return { message: typeof err === "string" ? err : JSON.stringify(err) };
}
```

Or use pino's built-in error serializer (it accepts `unknown` and does this internally).

Rule: `@typescript-eslint/no-unsafe-member-access`, `@typescript-eslint/use-unknown-in-catch-callback-variable` + Effective TS item 42 ("Use type predicates to validate at runtime").

### I4. `(err as Error).message` after `try/catch (err)` — same problem, more sites

`apps/backend/src/api/me.ts:194` (`stripe customer detach failed`), `apps/backend/src/api/billing-checkout.ts:98`, `apps/backend/src/api/webhooks/stripe.ts:71`, `apps/backend/src/llm/catalog/scheduler.ts:71/91`.

Same pattern as I3 but inside `try/catch`. The catch variable is `unknown` in strict mode; the `as Error` assertion strips the safety net. `Stripe.errors.StripeError` is a real Error subclass but `instanceof Error` does not tell you it has `.code`; you have to test that explicitly. Worse, when `me.ts:194` logs `(err as Error).message` and the underlying error is a `StripeAPIError` with a 5xx body, the meaningful diagnostic (`err.raw.message`, `err.code`) goes to `/dev/null`.

Fix: narrow with `instanceof Error` (or `instanceof Stripe.errors.StripeError` where it matters) before reading. Or use a serializer as in I3.

Rule: `@typescript-eslint/use-unknown-in-catch-callback-variable`, `@typescript-eslint/no-unsafe-member-access`.

### I5. `PresenceListener` typed as `(snapshot) => void` accepts async functions without narrowing

`apps/backend/src/ws/presence-tracker.ts:28, :191-198`

```
export type PresenceListener = (snapshot: PresenceSnapshot) => void;
...
private broadcast(): void {
  const snap = this.snapshot();
  for (const listener of this.listeners) {
    try {
      listener(snap);
    } catch (err) {
      log.warn({ err }, "presence: listener threw");
    }
  }
}
```

TypeScript allows `() => Promise<void>` to be assigned to `() => void` (this is the classic `no-misused-promises` `checksVoidReturn` case). Today no async listener subscribes, so the rejection-dropped issue is latent. But the day someone wires the broadcast to push to Kafka or a webhook fan-out, the `listener(snap)` call drops the rejection on the floor and the surrounding `try/catch` does not catch async throws.

Fix: keep the `void`-returning signature but enforce it via the lint rule (`no-misused-promises` with `checksVoidReturn: true`). Type-system enforcement is structurally hard; rule-enforcement is the standard workaround.

Rule: `@typescript-eslint/no-misused-promises` (specifically the `checksVoidReturn` option).

### I6. `void tick()` in `setInterval` callbacks

`apps/backend/src/llm/catalog/scheduler.ts:79`, `apps/backend/src/events/aggregate.ts:321-323`, `apps/backend/src/jobs/retention-sweeper.ts:120-122`.

```
const interval = setInterval(() => {
  void tick();
}, intervalMs);
```

These uses are mostly correct: the `void tick()` is the canonical "I know it is a promise, do not await it" escape. But the `tick` function returns a `Promise<AggregateResult>` and the result is silently dropped. The current code logs internally on failure so this is fine in practice, but the `void` is doing double-duty: it suppresses both the lint rule AND the discard warning. Documenting the intent makes the next reader's life easier:

```
const interval = setInterval(() => {
  tick().catch(() => { /* logged inside */ });
}, intervalMs);
```

Same shape, explicit intent. The `void` operator across these three files is consistent at least; not a bug.

Rule: `@typescript-eslint/no-floating-promises` (currently passing because of `void`, but the convention is the weaker form).

### I7. `(u as { inputTokens?: number; promptTokens?: number }).inputTokens ?? ...` — duplicate cast plus silent zero billing

`apps/backend/src/llm/openrouter.ts:141-151`

```
promptTokens:
  (u as { inputTokens?: number; promptTokens?: number }).inputTokens ??
  (u as { promptTokens?: number }).promptTokens ??
  0,
```

`u` is the AI SDK's `usage` object. The double-cast tells the compiler "trust me twice", which means any SDK rename (e.g. v6 dropping `promptTokens` entirely) compiles cleanly and returns 0 silently. That 0 flows into `applyTurnCost` at `plugin.ts:419` and the user is billed for nothing, which is a Stripe meter loss bug. The cast also intersects with `Promise.resolve(result.usage).then((u) => ...)`: `result.usage` is `Promise<LanguageModelUsage>` in the AI SDK; resolving it then immediately discarding the typed shape is wasteful.

Fix: define the usage shape once in a typed alias, accept the SDK's typed object directly, and only fall back when the well-typed field is `undefined`:

```
function readUsage(u: LanguageModelUsage): { promptTokens: number; completionTokens: number } { ... }
```

Then when the SDK ships v6 the alias compile-fails and the fix is one place.

Rule: `@typescript-eslint/no-unsafe-type-assertion`, `@typescript-eslint/no-unnecessary-type-assertion` + Effective TS item 9.

### I8. `model: { kind: "fake", modelId } as unknown as RunStreamArgs["model"]` — production code uses a "test only" escape

`apps/backend/src/ws/plugin.ts:368-371`

```
try {
  model = getOpenRouter().chat(modelId);
} catch {
  // No API key (test env). Pass an opaque marker — the runner ignores it.
  model = { kind: "fake", modelId } as unknown as RunStreamArgs["model"];
}
```

This is in the `handleUserMessage` production path. When the OPENROUTER_API_KEY is missing in production (which is a misconfiguration but not impossible), the WS handler silently swaps in a fake sentinel and pretends the LLM call worked. The user gets an empty stream and the billing meter records `(0, 0)`. There is no log line, no observability, no error frame to the client. The `as unknown as` is the type lie that hides this.

Fix:

```
let model: LanguageModel;
try {
  model = getOpenRouter().chat(modelId);
} catch (err) {
  log.error({ err }, "ws: openrouter unavailable");
  sendError(ws, "internal", "llm_unavailable");
  sm.endTurn();
  return;
}
```

Tests should inject `llmRunner` via `deps.llmRunner` (which is already a port) rather than rely on the production code's fallback. The current shape conflates "no API key" with "test mode".

Rule: `@typescript-eslint/no-unsafe-type-assertion` + Effective TS item 38 ("Avoid `any` in your code").

### I9. `c.req.header("authorization")?.trim()` — coincidental length safe

`apps/backend/src/api/_auth.ts:32-37`

```
const auth = c.req.header("authorization")?.trim();
if (auth && auth.toLowerCase().startsWith("bearer ")) {
  const token = auth.slice("bearer ".length).trim();
```

Two soft bugs:

1. The optional-chain plus truthy `&&` check accepts the empty string as falsy (correct here, but only because `String.prototype.slice` produces `""`, which fails the length check). A stricter linter would flag `strict-boolean-expressions` here.
2. `auth.slice("bearer ".length)` uses the lowercase length, but the input case was already checked via `toLowerCase().startsWith`. If a caller sends `Bearer FOO`, the lowercase compare passes, but the slice removes the first 7 characters of the ORIGINAL string, which equals `"FOO"` only because both prefix and `bearer` are 7 chars. Future-proof against `"Token "` style prefixes.

Fix: use a regex with case-insensitive flag:

```
const match = c.req.header("authorization")?.match(/^bearer\s+(.+)$/i);
if (match) return match[1].trim();
```

One line, intent obvious, no length-coincidence dependency.

Rule: `@typescript-eslint/strict-boolean-expressions`, `@typescript-eslint/prefer-string-starts-ends-with`.

### I10. `payload[...] as ToolFamily` and friends in `events/aggregate.ts` read JSONB without runtime validation

`apps/backend/src/events/aggregate.ts:66-100`

```
const payload = (row.payload ?? {}) as Record<string, unknown>;

if (row.type === "chat.tool_call.completed") {
  const toolName = String(payload["toolName"] ?? "unknown");
  const family = String(payload["family"] ?? "unknown") as ToolFamily;
  ...
  const outputBytes = Number(payload["outputBytes"] ?? 0);
```

`row.payload` is typed `Record<string, unknown>` via `$type<>` in the schema. Reading `payload["family"] ?? "unknown"` and asserting `as ToolFamily` is the second occurrence in this codebase of "I will pretend `string` is `ToolFamily`" (first was the catalog price math). The aggregator already has `eventSchemas` (lines 156-194 of `events/types.ts`): they are literally the typed schemas for this exact payload. Use them.

Fix:

```
import { eventSchemas } from "./types";
const event = eventSchemas[row.type as EventType]?.safeParse({
  id: row.id, type: row.type, timestamp: row.createdAt,
  userId: row.userId ?? undefined, payload: row.payload,
});
if (!event?.success) { /* log + skip */ continue; }
// event.data is now fully typed.
```

Adds one safety net at the boundary and removes seven `as` / `String(...)` / `Number(...)` lies inside the loop.

Rule: `@typescript-eslint/no-unsafe-assignment`, `@typescript-eslint/no-unsafe-member-access` + Effective TS item 42 ("Use type predicates to validate at runtime").

### I11. `schema.parse(envelope) as DomainEvent` — unnecessary cast on already-typed parse

`apps/backend/src/events/bus.ts:62-93`

```
const envelope = {
  id: idFactory(),
  timestamp: now(),
  type: input.type,
  userId: input.userId,
  payload: input.payload,
};
const event = schema.parse(envelope) as DomainEvent;
```

`schema.parse` already returns `z.infer<typeof schema>`, which IS the typed `DomainEvent` member for that type literal. The trailing `as DomainEvent` is a no-op type lie. The reason it is there is the discriminated union `DomainEvent` does not narrow trivially when keyed by a runtime string at `eventSchemas[input.type]`. Effective TS item 33 ("Use union types instead of optional fields") shows the right shape:

```
function publish<T extends EventType>(input: { type: T; userId?: string; payload: ... }): Promise<DomainEventOf<T>>
```

Then the inner `eventSchemas[input.type]` is correctly typed and no cast is needed. The current `publish` signature `(input: EmitInput) => Promise<DomainEvent>` is fine but the inner cast is the cost; a typed overload removes it.

Rule: `@typescript-eslint/no-unnecessary-type-assertion`.

## Nits

### N1. Two-step `raw as { data?: unknown }` then `raw as OpenRouterModelsResponse`

`apps/backend/src/llm/catalog/ingest.ts:152-157`

Reads `raw` twice with a different shape claim each time. The first read (`Array.isArray((raw as { data?: unknown }).data)`) is genuinely defensive; the follow-up `payload = raw as OpenRouterModelsResponse` then takes the leap without filtering. Run `entry.id` and `entry.pricing?.prompt` through a Zod parse once, log + return zero on parse failure. The cost is one zod parse per nightly run.

### N2. `if (sub && sub.status === "active" && sub.tier === "iron")` — same row read three times

`apps/backend/src/api/account.ts:263, :268`

Two near-identical checks against the same `sub` row. Lift to `const isActiveOf = (t: string) => sub?.status === "active" && sub.tier === t` if you want; otherwise file under stylistic.

### N3. `as ReturnType<typeof eq>[]` in two admin routes

`apps/backend/src/api/admin/users.ts:141`, `apps/backend/src/api/admin/catalog.ts:62`

```
const filters = [] as ReturnType<typeof eq>[];
```

Drizzle exposes `SQL<unknown>` for filter expressions; `ReturnType<typeof eq>` is the right shape but the `[]` cast is the typescript-eslint smell. Use:

```
const filters: SQL[] = [];
```

(import `SQL` from `drizzle-orm`). Removes the cast and clarifies the type.

### N4. Discarded `void gte; void sql;`

`apps/backend/src/api/admin/catalog.ts:179-180`

```
void gte;
void sql;
```

The comment says "kept imported because future filters use them". An `eslint-disable-next-line @typescript-eslint/no-unused-vars` with the reason, or removal of the imports entirely until needed, would be clearer than a `void` reference.

### N5. `JSON.stringify` on a snapshot without try/catch

`apps/backend/src/ws/plugin.ts:373-375`

```
const snapshotJson = msg.context?.snapshot
  ? JSON.stringify(msg.context.snapshot)
  : null;
```

`msg.context.snapshot` is Zod-validated upstream so it is safe, but `JSON.stringify` throws on circular refs and BigInt. If the protocol schema ever loosens to `z.unknown()`, this becomes a crash. Worth a `safeJsonStringify` helper.

### N6. `let where: ReturnType<typeof and> | undefined;`

`apps/backend/src/api/admin/catalog.ts:67`

Same as N3. Use `SQL | undefined` from `drizzle-orm`.

### N7. `c.req.query(...) as ...` rampant in admin routes

Already covered as B2 + N3, but flagging the codebase-wide pattern: every Hono query param read in this scope is `string | undefined` and 8 of 10 admin routes cast it directly. A `parseQuery(c, zodSchema)` helper would clean every route.

## Rule pass matrix

```
| Rule                                                                     | Applies | Passes | Findings                          |
| ------------------------------------------------------------------------ | ------- | ------ | --------------------------------- |
| @typescript-eslint/no-explicit-any                                       | yes     | yes    | (none, the codebase avoids `any`) |
| @typescript-eslint/no-unsafe-assignment                                  | yes     | partial| I10                               |
| @typescript-eslint/no-unsafe-member-access                               | yes     | partial| I3, I4, I10                       |
| @typescript-eslint/no-unsafe-call                                        | yes     | yes    | (none material)                   |
| @typescript-eslint/no-unsafe-return                                      | yes     | yes    | (none material)                   |
| @typescript-eslint/no-unsafe-argument                                    | yes     | partial| I1                                |
| @typescript-eslint/no-unsafe-type-assertion                              | yes     | no     | B2, B3, I1, I7, I8                |
| @typescript-eslint/no-unnecessary-type-assertion                         | yes     | partial| B3, I11                           |
| @typescript-eslint/no-non-null-assertion                                 | yes     | no     | B1 (+ aggregate.ts:161,207,241,272 use `!` on `existing[0]`) |
| @typescript-eslint/consistent-type-assertions                            | yes     | no     | B2, B3                            |
| @typescript-eslint/no-floating-promises                                  | yes     | yes    | (covered with `void`; see I6)     |
| @typescript-eslint/await-thenable                                        | yes     | yes    | (none)                            |
| @typescript-eslint/no-misused-promises                                   | yes     | partial| I5                                |
| @typescript-eslint/use-unknown-in-catch-callback-variable                | yes     | no     | I3, I4                            |
| @typescript-eslint/restrict-template-expressions                         | yes     | yes    | (template strings use typed input)|
| @typescript-eslint/strict-boolean-expressions                            | yes     | partial| I9, account.ts:200, me.ts:178     |
| @typescript-eslint/prefer-nullish-coalescing                             | yes     | yes    | (consistent `??`)                 |
| @typescript-eslint/prefer-optional-chain                                 | yes     | yes    | (consistent `?.`)                 |
| @typescript-eslint/no-redundant-type-constituents                        | yes     | yes    | (none)                            |
| @typescript-eslint/no-duplicate-type-constituents                        | yes     | yes    | (none)                            |
| @typescript-eslint/no-confusing-void-expression                          | yes     | yes    | (none)                            |
| @typescript-eslint/no-meaningless-void-operator                          | yes     | partial| N4                                |
| @typescript-eslint/no-base-to-string                                     | yes     | yes    | (none)                            |
| @typescript-eslint/restrict-plus-operands                                | yes     | yes    | (cost math uses typed numbers)    |
| @typescript-eslint/prefer-string-starts-ends-with                        | yes     | yes    | (uses `.startsWith` consistently) |
| @typescript-eslint/no-array-delete                                       | n/a     | n/a    | (no `delete arr[i]`)              |
| @typescript-eslint/no-deprecated                                         | yes     | yes    | (none observed)                   |
| @typescript-eslint/no-duplicate-enum-values                              | yes     | yes    | (none)                            |
| @typescript-eslint/no-dynamic-delete                                     | yes     | yes    | (none)                            |
| @typescript-eslint/no-extra-non-null-assertion                           | yes     | yes    | (none)                            |
| @typescript-eslint/no-extraneous-class                                   | yes     | yes    | (state classes have state)        |
| @typescript-eslint/no-for-in-array                                       | yes     | yes    | (no `for..in` over arrays)        |
| @typescript-eslint/no-implied-eval                                       | yes     | yes    | (none)                            |
| @typescript-eslint/no-invalid-void-type                                  | yes     | yes    | (none)                            |
| @typescript-eslint/no-loss-of-precision                                  | yes     | yes    | (bigint used for money)           |
| @typescript-eslint/no-misused-new                                        | yes     | yes    | (none)                            |
| @typescript-eslint/no-mixed-enums                                        | yes     | yes    | (string enums only)               |
| @typescript-eslint/no-namespace                                          | yes     | yes    | (none)                            |
| @typescript-eslint/no-non-null-asserted-nullish-coalescing               | yes     | yes    | (none)                            |
| @typescript-eslint/no-non-null-asserted-optional-chain                   | yes     | yes    | (none)                            |
| @typescript-eslint/no-require-imports                                    | yes     | partial| presence-tracker.ts:68 (`require("geoip-lite")` with eslint-disable, documented) |
| @typescript-eslint/no-this-alias                                         | yes     | yes    | (none)                            |
| @typescript-eslint/no-unnecessary-type-arguments                         | yes     | yes    | (none)                            |
| @typescript-eslint/no-unsafe-declaration-merging                         | yes     | yes    | (none)                            |
| @typescript-eslint/no-unsafe-enum-comparison                             | yes     | yes    | (string enums)                    |
| @typescript-eslint/no-unsafe-function-type                               | yes     | yes    | (none)                            |
| @typescript-eslint/no-unsafe-unary-minus                                 | yes     | yes    | (none)                            |
| @typescript-eslint/no-useless-template-literals                          | yes     | yes    | (none)                            |
| @typescript-eslint/no-wrapper-object-types                               | yes     | yes    | (none)                            |
| @typescript-eslint/only-throw-error                                      | yes     | yes    | (custom Error subclasses)         |
| @typescript-eslint/prefer-as-const                                       | yes     | yes    | (used consistently)               |
| @typescript-eslint/prefer-promise-reject-errors                          | yes     | yes    | (none)                            |
| @typescript-eslint/prefer-reduce-type-parameter                          | yes     | yes    | (none)                            |
| @typescript-eslint/prefer-return-this-type                               | yes     | yes    | (none)                            |
| @typescript-eslint/related-getter-setter-pairs                           | yes     | yes    | (none)                            |
| @typescript-eslint/require-array-sort-compare                            | yes     | yes    | (no sort without compare)         |
| @typescript-eslint/require-await                                         | yes     | yes    | (none)                            |
| @typescript-eslint/return-await                                          | yes     | yes    | (none)                            |
| @typescript-eslint/unbound-method                                        | yes     | yes    | (no method-passing observed)      |
```

## Recommended fix order

By production-risk x ease-of-fix, fix in this order:

1. **B1** (`admin/catalog.ts:136` `r.retiredAt!`). One-line `flatMap` swap. Removes a guaranteed crash if any predicate change relaxes the filter. Time: 5 min.
2. **B2** (`admin/users.ts:125` query `as`). Adopt the existing `billing-checkout.ts` `z.enum().safeParse` pattern. Removes the largest class of "raw HTTP string lies about being a typed value". Time: 15 min.
3. **I8** (`ws/plugin.ts:370` fake-model fallthrough). Real production-risk: silent zero billing on misconfig. Fix is to fail loud rather than fabricate a fake. Time: 10 min.
4. **I7** (`llm/openrouter.ts:141` double SDK cast). The AI SDK v6 bump is on the roadmap (per `docs/research/libraries/llm.md`); the current cast will silently drop usage on bump. Time: 15 min for a typed `readUsage` helper.
5. **B3** (`webhooks/stripe.ts:180` plus 4 sibling casts). The biggest delta, but Stripe will ship a new dated API again. Adopt the SDK's `Stripe.Event` discriminated union. Time: 1-2 h including tests; do it next time the webhook handler is touched.
6. **I3 + I4** (catch / `(err as Error).message` everywhere). Replace with a 10-line `serializeError(err: unknown)`. Time: 30 min total, repeated 5+ places.
7. **I10** (`events/aggregate.ts` payload reads). Use the existing Zod schemas. Time: 30 min, removes 7 micro-casts.
8. **I11** (`bus.publish` typed publish overload). One signature change. Time: 30 min.
9. **I1 + I2** (`server.ts` WS dispatch shim). The biggest readability win in the codebase but the risk is small as long as nobody touches `Bun.ServerWebSocket`. Time: 1 h.
10. **I5** (presence-tracker listener signature). Type-tighten the listener and enforce via `no-misused-promises`. Time: 5 min.
11. **I6** (interval `void tick()`). Convention nudge. Time: 5 min x 3 sites.
12. **I9** (auth bearer slice). Replace with regex. Time: 5 min.
13. **N1-N7**. Stylistic; rolling cleanup.

Total: 4-6 hours of focused work removes every blocker and important finding.
