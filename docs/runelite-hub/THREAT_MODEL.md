# osrs-llm-helper — Threat Model

> **Scope:** the shipped RuneLite plugin (`apps/plugin/`), the backend it talks
> to (`apps/backend/`), and the trust boundaries between them, the player's
> machine, the OSRS client, and OpenRouter / LLM providers.
>
> **Audience:** RuneLite Plugin Hub reviewers, future contributors, and the
> security disclosure inbox.
>
> **Method:** loosely STRIDE — Spoofing, Tampering, Repudiation, Information
> disclosure, Denial of service, Elevation of privilege — applied per trust
> boundary. Each finding lists what we mitigate and how, and what we
> explicitly accept as out of scope.

## Trust boundaries

```
+----------------------------------------------------------+
|  Player machine                                          |
|  +---------------+     +-----------------------+         |
|  | RuneLite      |     | osrs-llm-helper       |         |
|  | client + game | <-> | plugin (this repo)    |         |
|  +---------------+     |   in-JVM only         |         |
|                        |   no listening socket |         |
|                        +-----------+-----------+         |
|                                    |                     |
|                                    | wss:// (1 host)     |
+------------------------------------|---------------------+
                                     v
                       +----------------------------+
                       |  Backend (apps/backend/)   |
                       |  api.osrsllm.app           |
                       |  Hono + Bun on Fly.io      |
                       +-------------+--------------+
                                     |
                                     | https://
                                     v
                       +----------------------------+
                       |  OpenRouter -> LLM         |
                       +----------------------------+
```

Boundaries (each is a separate review surface):

- **B1: game client <-> plugin.** The plugin only reads from the RuneLite
  client API; it never invokes `MenuAction`, never synthesises input, never
  calls `Robot`. This protects Jagex's third-party client rules.
- **B2: plugin <-> backend.** Single outbound WSS to one configured host.
  Enforced by `BackendUrl`'s value-class constructor (`wss://` only) and by
  `EgressGate.egress(…)`'s preconditions.
- **B3: backend <-> OpenRouter.** Backend owns the OpenRouter key. The plugin
  never sees it. The plugin does not, and structurally cannot, ship the
  OpenRouter key (see `:secretsScan` Gradle task).
- **B4: player <-> backend (web auth).** The player pairs the in-game
  identity with a billing account via a one-time pairing code rendered
  in-game. The plugin never handles OSRS credentials.

## Assets

| Asset | Where it lives | Why an attacker would want it |
|---|---|---|
| Player's OSRS account credentials | Jagex login flow — NEVER touched by plugin | Account takeover |
| Player's in-game username | RuneLite `Client.localPlayer.name` | De-anon, social engineering |
| Player's stats / inventory / bank | Game memory via RuneLite API | Stalking, scams, RWT targeting |
| Player's chat input to the LLM | Plugin chat panel | Sensitive content disclosure |
| OpenRouter API key | Backend `.env` only | Resale, abuse, billing fraud |
| Pairing token / device key | Backend DB + plugin config | Account takeover at the SaaS layer |
| Stripe customer / subscription data | Stripe + backend DB | PII / payment data theft |

## STRIDE table

| # | Threat | Category | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|---|---|
| T1 | A compromised backend host injects malicious tool-call instructions that exfiltrate bank contents | T (Tampering) / I (Info disclosure) | Low | High | Plugin only sends payloads from a sealed `OutboundPayload` hierarchy. Inbound messages are decoded by `InboundCodec`, sealed `InboundMessage`. Tool execution is gated by `ToolFamily` and `EnableToolsTool` keyword routing — no arbitrary code execution path exists. Audit log surfaces every egress to the player in real time (`AuditLog`, `NetworkAuditLogger`). | Mitigated |
| T2 | A malicious plugin update sneaks in a second egress path via a new `webSocket.send(`, `ServerSocket`, or `URLConnection` | T | Medium | High | Build-time gates `checkNoHttpServer`, `checkNoReflection`, `checkNoPlaintextUrls`, `secretsScan` (build.gradle.kts) fail the build. Runtime mirror in `SourceTreeAuditTest`. Sealed `OutboundPayload` + single `EgressGate.egress` chokepoint. PR reviewer reads four files (see `SECURITY_DESIGN.md`). | Mitigated |
| T3 | MITM between plugin and backend reads or modifies player data in flight | S/T/I | Low (TLS) | High | `BackendUrl`'s value-class init throws unless the URL begins with `wss://`. Ktor's OkHttp engine uses the platform truststore. No plaintext fallback exists in the production tree (see SECURITY_AUDIT rule 6 + 7). | Mitigated |
| T4 | OpenRouter key leaks via plugin source, logs, or compiled jar | I | Medium | High | Key never enters plugin code path; backend owns it. `:secretsScan` Gradle task fails the build on any string matching `OPENROUTER_API_KEY`, `sk-…`, `AKIA…`, generic `api_key=` / `password=` patterns under `src/main/`. `.env` is `.gitignore`d. Payloads are not logged via SLF4J; only payload kind + size goes to the in-memory `AuditLog`. | Mitigated |
| T5 | RuneLite Hub policy violation — embedded HTTP server exposing player info on localhost | E (Elevation) / I | Was real (PR #11453) | Submission-blocking | The shippable production tree contains zero `ServerSocket(` / `embeddedServer(` references (SECURITY_AUDIT rules 2-3). The old `local/McpServerService` survives only behind `developerMode=true` AND `localMcpEnabled=true`, both off by default, both excluded from the shipped jar by package boundary, and called out explicitly in `SECURITY_DESIGN.md` section 4. | Mitigated |
| T6 | Player loses consent and data is exfiltrated anyway | I / E | Low | High | `EgressGate.egress` requires `consent.accepted && cloudChatEnabled` on every call. `ConsentState` is write-once — frozen at startup and `freeze()` throws on the second call (verified by `EgressGateTest.consent state is genuinely write-once`). | Mitigated |
| T7 | An LLM tool response containing arbitrary HTML/JS escapes the chat panel and runs in the RuneLite Swing UI | E | Low | Medium | Chat panel renders via Commonmark with the GitHub-flavoured-markdown extension; raw HTML passthrough is not enabled. No `WebView`, no `JEditorPane` with HTML content type. | Mitigated |
| T8 | A second plugin in the same RuneLite session reads `co.rowm.osrsllm.cloud.AuditLog` via reflection to learn what the user is asking | I | Very low | Low | Plugin classloaders are isolated by RuneLite; cross-plugin reflection would itself be a Hub policy violation. We accept this as the platform's responsibility. | Accepted (platform) |
| T9 | Replay / spoofing of a player's session token to charge their account | S | Low | Medium | Pairing tokens are single-use and rotated. Device key is bound at install time. Backend rate-limits by device key. Stripe is the source of truth for subscription state; the plugin never quotes price. | Mitigated (backend-side) |
| T10 | Denial of service against a single player by flooding inbound WSS messages | D | Low | Low | Backend enforces per-device-key rate limit. Plugin has bounded `ReconnectStrategy` with exponential backoff. UI thread is not blocked: WS reads run on a Ktor coroutine, not the AWT event thread. | Mitigated |
| T11 | A malicious user runs a fork of the plugin with `developerMode=true` and points it at their own backend | E | High (anyone can do it) | Low (only affects them) | Out of scope. The legibility claim covers what we ship to the Plugin Hub, not what anyone runs locally. A fork is the user's own code, not ours. | Accepted (out of scope) |
| T12 | Repudiation: player denies that a chat message they sent triggered an action | R | Low | Low | Backend stores chat history per session. Plugin surfaces in-memory `AuditLog` to the player. No legal-grade non-repudiation claim is made; out of scope for a paid SaaS chat. | Accepted (out of scope) |
| T13 | Multi-account abuse: one player binds 100 alt accounts to a single Stripe subscription | E (privilege) | Medium | Low (billing-side) | Backend enforces per-Stripe-subscription account caps. Documented in `docs/architecture/BILLING.md`. Not a plugin-side concern. | Mitigated (backend-side) |
| T14 | A network observer infers the player's activity by traffic analysis even if TLS hides content | I | Medium | Low | Out of scope. TLS metadata leakage is inherent to WSS and we make no attempt to pad or shape traffic. Documented for transparency. | Accepted (out of scope) |
| T15 | Player's bank contents end up in OpenRouter / LLM provider training data | I | Medium | Medium-High | Backend uses OpenRouter routes with `train=false` flag where available and selects models per `docs/research/llm-providers/`. The disclosure in `runelite-plugin.properties#warning=` and `apps/plugin/DATA_DISCLOSURE.md` tells the player explicitly that LLM provider terms apply. Player consent is informed consent. | Mitigated + disclosed |

## What we deliberately accept

These are NOT mitigations gaps to fix; they are documented to make the trust
model explicit.

1. **Fork-and-modify (T11).** A user with the source can rebuild the plugin with
   any backend URL, can enable the developer-only `local/` HTTP path, can
   remove the consent gate. We do not attempt to make the plugin
   tamper-proof. Our legibility claim is about the artifact shipped via the
   Plugin Hub.
2. **TLS traffic analysis (T14).** Padding strategies have a poor cost / benefit
   ratio for a chat plugin and would themselves be a Hub policy red flag
   (looks like obfuscation).
3. **Cross-plugin attacks (T8).** Plugin sandboxing is the host's responsibility.
4. **Non-repudiation of chat content (T12).** A best-effort `AuditLog` is
   enough for a paid chat product; legal-grade signing isn't.
5. **LLM provider terms (T15).** We disclose; the player consents; we do not
   attempt to renegotiate provider terms on their behalf.

## Defense-in-depth summary

| Defense | Where it lives | What it prevents |
|---|---|---|
| `BackendUrl` value class requires `wss://` | `cloud/BackendUrl.kt` | Plaintext + scheme confusion |
| Sealed `OutboundPayload` hierarchy | `cloud/OutboundPayload.kt` | Surprise wire shapes |
| Single egress chokepoint | `cloud/EgressGate.egress(…)` | Hidden exfiltration paths |
| Write-once `ConsentState` | `cloud/ConsentState.kt` | Mid-session consent flipping |
| In-memory `AuditLog` + `NetworkAuditLogger` | `cloud/AuditLog.kt`, `cloud/NetworkAuditLogger.kt` | Invisible egress |
| Build-time `checkNoHttpServer` | `build.gradle.kts` | HTTP-server regression |
| Build-time `checkNoReflection` | `build.gradle.kts` | Reflection escape hatches |
| Build-time `checkNoPlaintextUrls` | `build.gradle.kts` | `http://` / `ws://` regression |
| Build-time `:secretsScan` | `build.gradle.kts` | Committed credentials |
| Runtime mirror `SourceTreeAuditTest` | `src/test/kotlin/.../cloud/` | Build gates being skipped |
| `:check` depends on all of the above | `build.gradle.kts` | Forgetting to run any one of them |

## Cross-references

- `apps/plugin/SECURITY_DESIGN.md` — the four-file reviewer audit guide.
- `apps/plugin/SECURITY.md` — public-facing disclosure policy.
- `docs/runelite-hub/SECURITY_AUDIT.md` — the verbatim grep audit.
- `docs/runelite-hub/POLICY_SUMMARY.md` — what the Hub forbids.
- `docs/runelite-hub/SUBMISSION_CHECKLIST.md` — what we must verify before
  opening the Plugin Hub PR.
- `apps/plugin/DATA_DISCLOSURE.md` — what data the player consents to send.
