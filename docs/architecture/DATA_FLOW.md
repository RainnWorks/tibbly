# Data Flow — RuneLite client → plugin → backend → OpenRouter → user

This document is the visual companion to `docs/runelite-hub/DATA_DISCLOSURE.md`.
It shows what packet leaves where, in what order, in response to what trigger.

## Compliance anchor

The Plugin Hub rejected PR #11453 ("Add OSRS MCP plugin") explicitly because
*"Plugins which expose player information over HTTP"* is on the rolled-back
list. That plugin ran a localhost HTTP/MCP server that external programs
connected to. **Our design inverts that flow** — the plugin makes a single
outbound WebSocket connection to our backend; tool calls arrive *into* the
plugin as RPC messages on that socket; the plugin returns results on the same
socket. No HTTP server is ever bound on the client.

## High-level lifecycle

```mermaid
sequenceDiagram
    autonumber
    participant U as User (in OSRS)
    participant RL as RuneLite client
    participant P as osrs-llm-helper plugin
    participant BE as Our backend (osrsllm.app)
    participant OR as OpenRouter
    participant LLM as LLM (Claude / etc.)

    Note over P: Plugin starts in LOCAL TOOLS ONLY mode.<br/>Zero network egress.

    U->>P: Toggles "Enable cloud chat" in config
    P->>U: First-run consent dialog (lists every field that will leave)
    U->>P: Accepts ("Enable")

    P->>BE: WSS connect: device_key + plugin version + RL version (TLS)
    BE-->>P: ack + session_id

    U->>P: Types "What should I bank for Vorkath?"
    P->>BE: chat.message { session_id, text, player_name, account_type, combat_level, world }
    BE->>OR: POST /chat/completions { model, messages, tools }
    OR->>LLM: forward
    LLM-->>OR: tool_call: bank(query="dragonfire", limit=20)
    OR-->>BE: tool_call message
    BE->>P: rpc.tool_call { id, name: "bank", args: { query, limit } }

    Note over P: Plugin reads local game state for ONLY this query.<br/>Bank is filtered server-side of the plugin by query string.

    P->>BE: rpc.tool_result { id, result: [<filtered items>] }
    BE->>OR: tool_result
    OR->>LLM: forward
    LLM-->>OR: assistant message: "Bank these 7 items..."
    OR-->>BE: stream tokens
    BE-->>P: chat.delta { tokens }
    P-->>U: streams text into the chat panel

    Note over BE: Backend tallies token cost,<br/>writes to billing ledger.

    U->>P: Closes chat panel
    P->>BE: chat.end
    BE-->>P: ack + token_cost
    P->>U: Shows "+ N tokens charged this turn"
```

## Idle-state behaviour (no chat in progress)

```mermaid
sequenceDiagram
    participant RL as RuneLite client
    participant P as Plugin
    participant BE as Backend

    Note over P: Cloud chat ON, no active chat.
    P->>BE: WSS heartbeat every 30s (presence)
    BE-->>P: ack
    Note over P,BE: No game state crosses the wire.<br/>Heartbeat is empty (just keepalive).
```

## Tool-only mode (cloud chat OFF — install default)

```mermaid
sequenceDiagram
    participant U as User
    participant P as Plugin
    participant RL as RuneLite client

    U->>P: Opens panel
    P->>RL: read game state via injected client APIs
    RL-->>P: state
    P->>U: renders local-only views (bank tags, gear stats, etc.)
    Note over P: ZERO network egress.<br/>No socket opened. No device key registered.
```

## What never happens

```mermaid
sequenceDiagram
    participant External as External MCP client
    participant P as Plugin
    External-x P: HTTP GET /tools/get_inventory
    Note over P: Plugin does NOT bind any listening socket.<br/>This is the design change vs PR #11453.
```

## Network endpoint summary

| Direction | Source | Destination | Protocol | Trigger |
|---|---|---|---|---|
| **Outbound** | plugin | `wss://api.osrsllm.app` | WSS (TLS 1.3) | Cloud chat enabled |
| Inbound — RPC | backend | plugin (over established WSS) | JSON-RPC over WSS | LLM tool call |
| **No inbound from non-backend sources** | — | plugin | — | — |
| Outbound | plugin | — | — | If cloud chat OFF, nothing else. |

## Per-tool egress matrix

See `docs/runelite-hub/DATA_DISCLOSURE.md` §D for the full per-tool table.
Each tool call returns *only* the fields needed to answer the LLM's question;
default `limit`s on bank/nearby/quest tools cap payload size.

## Backend → OpenRouter

The backend authenticates to OpenRouter with a single server-side API key
(loaded from `OPENROUTER_API_KEY` env var, never shipped to the plugin).
Per-user attribution is internal to our backend; OpenRouter sees only our
account.

## TLS / pinning

- The plugin trusts the system truststore for `api.osrsllm.app`. (Cert pinning
  is on the "later" list — it complicates RuneLite hot-deploy.)
- The plugin will refuse to connect if the TLS handshake fails. There is no
  insecure fallback.

## Failure modes

| Failure | Plugin behaviour |
|---|---|
| WSS connect fails | Show panel banner "Cloud chat unavailable — local tools still work". Retry with backoff. |
| Backend returns 5xx | Surface error to user; offer "retry". |
| Backend returns 402 (out of credits) | Surface to user with deep link to dashboard top-up. No silent retries. |
| Backend offline > 10 min | Show banner suggesting user disable cloud chat. Local tools keep working. |
| Plugin out of date | Backend may close the socket with `version_unsupported`; plugin shows "update available" banner. |
