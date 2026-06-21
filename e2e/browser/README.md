# Browser scripts (Claude in Chrome)

These scripts are the second tier of the E2E suite. They drive the real
marketing and ops UIs through a running Chrome session via the
`claude-in-chrome` MCP server.

They are intentionally NOT executed by the main `bun test e2e/scenarios/`
run because Chrome is not always connected (no headless CI fallback yet).
Instead, an operator (a Claude Code session with the claude-in-chrome
MCP enabled) runs them manually:

```
bun run e2e/browser/marketing-checkout.ts
bun run e2e/browser/ops-login-and-debug.ts
```

Each script boots the orchestrator with the relevant Vite dev server
spawned (`withMarketing: true` or `withOps: true`) and prints a numbered
runbook the agent walks through using the MCP tools:

- `tabs_context_mcp` to learn which tabs exist.
- `tabs_create_mcp` to open a fresh tab.
- `navigate`, `computer`, `read_page`, `read_console_messages`,
  `read_network_requests` to drive and assert.

The runbook deliberately spells out the expected network calls and DOM
text so a fresh agent can pick it up without context.

## Why a runbook rather than direct MCP calls

`bun run` (this process) does not have access to the MCP tools — those
live in the agent's tool surface. The agent reads the printed steps and
calls the MCP tools itself. Splitting the orchestration this way keeps
the harness self-contained.

## What lives outside the orchestrator

- Real Chrome session, attached to the same machine, with the
  `claude-in-chrome` extension granted to `127.0.0.1` and `localhost`.
- The operator's MCP-enabled chat session.

Everything else (backend, marketing, ops, DB, presence) is fresh per
invocation. Two parallel runs do not collide because the orchestrator
picks ephemeral ports and PGLite lives in RAM.
