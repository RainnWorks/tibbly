# Security design — reviewer audit guide

This is the one-page audit guide for the OSRS LLM Helper plugin. If you are a
RuneLite Plugin Hub reviewer, **read the four files in section 2 in order** and
you will have audited the entire egress surface.

## 1. The contract

- Player data leaves this plugin through **exactly one function**:
  `EgressGate.egress(payload, consent, cloudChatEnabled)`.
- That function refuses to run unless **all** of:
  1. the player has flipped the opt-in consent toggle, AND
  2. the player has enabled cloud chat, AND
  3. the configured backend URL is a `wss://` URL (enforced by `BackendUrl`'s
     value-class constructor), AND
  4. the backend WebSocket session is live.
- There is **no listening socket** in the production build. The
  `local/McpServerService` (the old MCP-over-HTTP implementation that PR
  #11453 was rejected for) is guarded by `developerMode = false` by default,
  is excluded from the legibility gates by package path, and is the only
  place in the tree that binds a port.

## 2. The four files to read (in order)

| # | File | What it proves |
|---|---|---|
| 1 | `src/main/kotlin/co/rowm/osrsllm/cloud/BackendUrl.kt` | URL scheme is `wss://` or construction throws. |
| 2 | `src/main/kotlin/co/rowm/osrsllm/cloud/ConsentState.kt` | Consent is captured once at startup; cannot be mutated mid-session. |
| 3 | `src/main/kotlin/co/rowm/osrsllm/cloud/OutboundPayload.kt` | Closed sealed class — every wire shape. Each subtype maps to a section of `DATA_DISCLOSURE.md`. |
| 4 | `src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt` | The single egress function. Search the tree for the send-call substring (`webSocket.send(`) — you'll find one hit, in this file. |

## 3. Grep audit (these MUST all return as shown)

```
$ grep -rn 'webSocket.send('   src/main/kotlin/co/rowm/osrsllm/cloud/   → 1 hit  (EgressGate.kt)
$ grep -rn 'ServerSocket('     src/main/kotlin/co/rowm/osrsllm/cloud/   → 0 hits
$ grep -rn 'ServerSocket('     src/main/kotlin/co/rowm/osrsllm/plugin/  → 0 hits
$ grep -rn 'embeddedServer('   src/main/kotlin/co/rowm/osrsllm/cloud/   → 0 hits
$ grep -rn 'embeddedServer('   src/main/kotlin/co/rowm/osrsllm/plugin/  → 0 hits
$ grep -rEn 'http://|ws://'    src/main/kotlin/co/rowm/osrsllm/cloud/   → 0 hits
$ grep -rEn 'http://|ws://'    src/main/kotlin/co/rowm/osrsllm/plugin/  → 0 hits
$ grep -rEn 'Class\.forName|URLClassLoader' src/main/kotlin/co/rowm/osrsllm/cloud/ → 0 hits
$ grep -rEn 'Class\.forName|URLClassLoader' src/main/kotlin/co/rowm/osrsllm/plugin/ → 0 hits
```

The Gradle tasks `:checkNoHttpServer`, `:checkNoReflection`, and
`:checkNoPlaintextUrls` enforce the same invariants on every build and are
wired into `:check`. `co/rowm/osrsllm/local/**` is the only excluded path,
because it is the developer-only code path documented in section 4.

## 3a. Tier-2 BYO direct egress (`DirectChatRunner`)

When the player picks a `Direct: …` chat mode and pastes their own LLM
API key, the plugin talks directly to the provider — Tibbly's backend
is not involved. This path is locked down with the same shape of
guarantees as the cloud path:

- All BYO HTTP requests go through **exactly one function**:
  `EgressGate.egressHttp(host, path, …)`. The host is checked against
  the exact-match allow-list `BYO_ALLOWED_HOSTS = { api.anthropic.com,
  api.openai.com, openrouter.ai }`. Any other host throws
  `EgressBlockedException` before a socket opens.
- The API key is read into a local val per send, attached to the
  provider's auth header (`x-api-key` for Anthropic; `Authorization:
  Bearer …` for OpenAI / OpenRouter), and then dropped. It never
  persists on the runner instance, never lands in `AuditLog`, and is
  redacted out of any error string surfaced to the chat panel.
- The Gradle gate `:checkNoKeyLeak` fails the build if a literal
  matching a BYO-style API key or env-var-shaped key name appears
  anywhere in production Kotlin source.
- Header sanitization on every BYO call rejects CR/LF in header names
  or values to defeat response-splitting / header-injection.
- Audit rows in BYO mode contain only `Http:POST <host><path>
  size=<bytes>` — never the body, never the headers, never the key.

See `apps/plugin/DATA_DISCLOSURE.md` §D-quater for the per-provider
wire shapes and `apps/plugin/docs/CONFIG.md` for the player-facing
description of the dropdown and the safer-key-handling guidance.

## 4. The developer-only path

`co/rowm/osrsllm/local/McpServerService.kt` keeps the original
MCP-over-HTTP implementation so we can still wire up a local `claude -p`
session during plugin development. It is started **only when both**
`developerMode = true` **and** `localMcpEnabled = true`. Both default to
off and the developer-mode section in the config UI is closed by default.
The Gradle gates exclude this path because the legibility claim applies to
what ships to the Plugin Hub, not what developers run locally.

## 5. What the player sees

- A dialog before any egress is possible (opt-in consent toggle).
- A list of every payload that has left, in the plugin panel, sourced from
  `cloud/AuditLog.kt`.
- A `Backend URL` field that visibly refuses to accept anything that isn't
  `wss://` (the plugin fails to load if the URL is wrong).

## 6. Where to file a security report

Email security@rowm.co with subject `[osrs-llm-helper]`. We rotate the
PGP key annually; the current fingerprint is published at
`https://rowm.co/.well-known/security.txt`.
