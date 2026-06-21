# Harness: fake-plugin emulator

This folder is the heart of the E2E testbed. The fake-plugin speaks the
real Tibbly WSS protocol verbatim, so the backend cannot tell it apart
from the production RuneLite plugin at the wire level. The only thing
stubbed is the actual game-state read: those values come from
`game-state-fixtures.ts` rather than a live `Client.localPlayer`.

## Files

- `fake-plugin.ts` opens the WSS, sends `auth`, drives `user_message`,
  answers `tool_call_request`, and exposes a small Promise-based API to
  scenarios.
- `tool-responder.ts` maps tool names to canned outputs. Tests override
  individual responses via `responder.override("name", value)`.
- `game-state-fixtures.ts` is the canned-state catalog. Add a new fixture
  here and the responder picks it up automatically.

## Wire safety

Every outbound frame is built from a `ClientToServer` Zod schema, and
every inbound is parsed against `ServerToClient`. A drift on either side
fails loud at the boundary instead of silently mis-typing the rest of the
test.

## Adding a fixture

1. Add the field to `GameStateFixtures` in `game-state-fixtures.ts`.
2. Add a default value to `DEFAULT_FIXTURES`.
3. Register a handler in `defaultHandlers()` in `tool-responder.ts`.
4. Reference it from a scenario with `responder.override(name, ...)` if
   you need a non-default value for one test.

## Running the fake-plugin standalone

The harness can run as a long-lived process — useful when you want to
attach a browser to the marketing site and watch the live presence
counter increment.

```
bun run e2e/harness/fake-plugin.ts ws://localhost:8787/ws/plugin
```

(Standalone CLI lands in a follow-up; the orchestrator covers all the
test paths today.)
