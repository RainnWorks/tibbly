# Tibbly E2E test harness

End-to-end testbed for the whole Tibbly stack: backend, marketing site,
ops console. The only thing stubbed is the outermost RuneLite layer
(actual game-state reads). Everything else runs for real:

- WSS plugin protocol (parsed against the shared-types Zod schemas).
- Pairing flow (`/v1/pairing/request`, `/v1/pairing/claim`).
- Billing meter against an in-memory PGLite with the production
  migrations applied.
- Auth gate, status machine, tool-call round trip, event bus, presence.
- Stripe via the dev stub from `apps/backend/src/api/admin/users.ts`
  plus a checkout-session stub that returns the dev fixture URL.

## Run the programmatic suite

```
bun test e2e/scenarios/
```

Five spec files run in under 60s on a laptop:

| File                                | Covers                                           |
|-------------------------------------|--------------------------------------------------|
| `01-pair-chat-bill.spec.ts`         | pair, auth, chat turn, tool round-trip, billing  |
| `02-byok-direct-chat.spec.ts`       | BYOK / zero-cost turn settles cleanly            |
| `03-gdpr-export-delete.spec.ts`     | /v1/me/export + DELETE /v1/me cascade            |
| `04-admin-ban-refund.spec.ts`       | /admin/users/:id/ban, refund stub, unban         |
| `05-presence-counter.spec.ts`       | /v1/presence increments and clears               |

## Run the browser-driven suite

The Claude-in-Chrome scripts under `browser/` are runbooks; an operator
boots one, then drives Chrome from a Claude Code session that has the
`claude-in-chrome` MCP enabled. See `browser/README.md`.

## Directory map

```
e2e/
├── harness/         # fake-plugin WSS emulator + tool responder + fixtures
├── scenarios/       # bun:test specs that exercise the real backend
├── browser/         # Claude-in-Chrome runbooks (marketing + ops)
└── orchestrator/    # boot, teardown, deterministic env, seeding helpers
```

## What the fake-plugin replaces

The real RuneLite plugin lives in `apps/plugin/` and pulls live game
state from the running RuneLite client. The harness replaces the
outermost layer (the actual `Client.localPlayer` reads) with
`harness/game-state-fixtures.ts` — canned snapshots that cover the same
surface area as the Tier 0 unblockers (account identity, raid layout,
target projectiles, active prayers, farming summary, farming patches)
plus the legacy bank / inventory / quest / slayer fixtures.

Every wire frame the fake-plugin sends or receives is parsed against
the shared-types Zod schemas (`ClientToServer`, `ServerToClient`), so
the moment the protocol drifts the suite fails loud.

## What we can NOT test without RuneLite

These are documented manual-verification items:

- Real OSRS account login binding (we can validate the binding rows,
  but not the actual login).
- Live world hop / region changes coming from the client.
- Audio cue handling at the plugin layer.

The fixtures cover the data shapes; the manual list is for the
post-handback session.
