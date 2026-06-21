# RuneLite Plugin Hub — Security Audit

> **Purpose:** A reproducible, line-by-line audit of the osrs-llm-helper plugin
> against the RuneLite Plugin Hub policy and against the legibility contract in
> `apps/plugin/SECURITY_DESIGN.md`. Each rule is a grep, the verbatim output is
> captured below, and every rule MUST end PASS.
>
> If a rule ever flips to FAIL, treat that as a build break: fix the offending
> file, do not relax the audit.

## How this audit is run

All greps below are executed from `apps/plugin/` (the plugin's Gradle module
root). The production source set is `src/main/kotlin/`. Two categories of
files are deliberately excluded:

1. `src/test/kotlin/**` — test stubs and fixtures.
2. `src/main/kotlin/co/rowm/osrsllm/local/**` — the developer-only local
   MCP-over-HTTP path. Behind a runtime gate (`developerMode` +
   `localMcpEnabled`, both off by default), excluded from the Gradle grep
   gates (`checkNoHttpServer`, `checkNoReflection`, `checkNoPlaintextUrls`),
   excluded from the shipped Plugin Hub jar by explicit `shadowJar`
   `exclude(...)` lines (verified every build by `:checkLocalNotInJar`),
   and documented in `apps/plugin/SECURITY_DESIGN.md` section 4. RAI-40
   (this audit revision) tightened the jar exclusion from "package
   boundary" — which was aspirational and unverified — to "shadowJar
   exclude + checked unzip listing".

The runtime mirror of these greps lives at
`apps/plugin/src/test/kotlin/co/rowm/osrsllm/cloud/SourceTreeAuditTest.kt`
so a regression also breaks `./gradlew test`. The build-time mirror lives in
`apps/plugin/build.gradle.kts` as the `checkNoHttpServer`,
`checkNoReflection`, `checkNoPlaintextUrls`, `checkNoSubprocess`,
`secretsScan`, and `checkLocalNotInJar` tasks, all wired into `:check`.

## Audit table

| # | Rule | Command | Expected | Result |
|---|---|---|---|---|
| 1 | Exactly one `webSocket.send(` call site in production source (must be in `EgressGate.kt`) | `grep -rn 'webSocket.send(' src/main/kotlin/` | 1 hit in `cloud/EgressGate.kt` | PASS |
| 2 | No `ServerSocket(` in production source outside `local/` | `grep -rn 'ServerSocket(' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 3 | No `embeddedServer(` in production source outside `local/` | `grep -rn 'embeddedServer(' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 4 | No `Class.forName` in production source outside `local/` | `grep -rn 'Class.forName' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 5 | No `URLClassLoader` in production source outside `local/` | `grep -rn 'URLClassLoader' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 6 | No `http://` literal in production source outside `local/` | `grep -rn 'http://' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 7 | No `ws://` literal in production source outside `local/` | `grep -rn 'ws://' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 8 | No `Netty,` engine reference in production source outside `local/` | `grep -rn 'Netty,' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 9 | No `Runtime.exec` in production source | `grep -rn 'Runtime.exec' src/main/kotlin/` | no matches | PASS |
| 10 | No `ProcessBuilder` in production source | `grep -rn 'ProcessBuilder' src/main/kotlin/` | no matches | PASS — all uses removed in RAI-40. Enforced by `:checkNoSubprocess`. |
| 11 | No `java.awt.Robot` (no input synthesis) in production source | `grep -rn 'java.awt.Robot\|new Robot(' src/main/kotlin/` | no matches | PASS |
| 12 | No reflection escape via `setAccessible\|getDeclaredMethod` in production source | `grep -rEn 'setAccessible\|getDeclaredMethod' src/main/kotlin/ \| grep -v '/local/'` | no matches | PASS |
| 13 | No secrets in production source (see `:secretsScan`) | `./gradlew :secretsScan` | BUILD SUCCESSFUL | PASS |
| 14 | Shipped shadowJar contains no `co/rowm/osrsllm/local/` or `McpServerService*` entries | `./gradlew :checkLocalNotInJar` (runs `unzip -l` internally) | BUILD SUCCESSFUL | PASS — verified every build by `:checkLocalNotInJar`. Excluded by shadowJar `exclude("co/rowm/osrsllm/local/**")` + `exclude("**/McpServerService*")`. |

## Verbatim grep output (captured against branch `agent/rai-36/security-audit`)

### Rule 1 — `webSocket.send(`

```
$ grep -rn 'webSocket.send(' src/main/kotlin/
src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:67:            webSocket.send(Frame.Text(text))
```

Result: **PASS** — exactly one hit, in `EgressGate.egress()`, as documented in
`SECURITY_DESIGN.md` section 2 row 4.

### Rule 2 — `ServerSocket(`

```
$ grep -rn 'ServerSocket(' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 3 — `embeddedServer(`

```
$ grep -rn 'embeddedServer(' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 4 — `Class.forName`

```
$ grep -rn 'Class.forName' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 5 — `URLClassLoader`

```
$ grep -rn 'URLClassLoader' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 6 — `http://`

```
$ grep -rn 'http://' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

For reference, the two hits inside `local/` (which are NOT shipped to the
Plugin Hub and are gated behind `developerMode=true`):

```
$ grep -rn 'http://' src/main/kotlin/co/rowm/osrsllm/local/
src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt:127:        log.info("MCP server listening on http://{}:{}/mcp", host, port)
src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt:144:        return "http://$h:$p/mcp"
```

These are explicitly allowed by the Gradle gates' `local/**` exclusion and
documented in `SECURITY_DESIGN.md` section 4. They never run in a Plugin Hub
build because the `LocalMcpEnabled` and `developerMode` flags default to off
and the developer-mode config UI section is closed by default.

### Rule 7 — `ws://`

```
$ grep -rn 'ws://' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 8 — `Netty,` (engine reference)

```
$ grep -rn 'Netty,' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 9 — `Runtime.exec`

```
$ grep -rn 'Runtime.exec' src/main/kotlin/
(no matches)
```

Result: **PASS**.

### Rule 10 — `ProcessBuilder`

```
$ grep -rn 'ProcessBuilder' src/main/kotlin/
(no matches)
```

Result: **PASS** — all uses removed in RAI-40. The two prior call sites
(`chat/ClaudeRunner.kt` and `OsrsLlmHelperPanel.kt`'s "Install in Claude CLI"
button) were deleted outright rather than gated, on the principle that a
maintainer reading the source needs to see clean production code, not
commented-out booby traps. Enforced going forward by the
`:checkNoSubprocess` Gradle task, which fails the build on either
`ProcessBuilder(` or `Runtime.getRuntime().exec(`.

### Rule 11 — `java.awt.Robot` / `new Robot(`

```
$ grep -rEn 'java\.awt\.Robot|new Robot\(' src/main/kotlin/
(no matches)
```

Result: **PASS**.

### Rule 12 — `setAccessible` / `getDeclaredMethod`

```
$ grep -rEn 'setAccessible|getDeclaredMethod' src/main/kotlin/ | grep -v '/local/'
(no matches)
```

Result: **PASS**.

### Rule 13 — `:secretsScan`

```
$ ./gradlew :secretsScan
> Task :secretsScan
BUILD SUCCESSFUL
```

Result: **PASS** — see `apps/plugin/build.gradle.kts` for the patterns
checked (OPENROUTER_API_KEY, `sk-…`, `AKIA…`, generic `password=`, `api_key=`).

### Rule 14 — `co/rowm/osrsllm/local/` and `McpServerService*` not in shipped jar

```
$ ./gradlew :checkLocalNotInJar
> Task :shadowJar
> Task :checkLocalNotInJar
checkLocalNotInJar: jar clean (no local/ or McpServerService entries in osrs-llm-helper-0.1.0.jar)
BUILD SUCCESSFUL
```

Result: **PASS** — verified every build. The shadowJar task in
`apps/plugin/build.gradle.kts` carries explicit
`exclude("co/rowm/osrsllm/local/**")` and `exclude("**/McpServerService*")`
lines. `:checkLocalNotInJar` opens the produced jar with `ZipFile` and fails
the build if any entry under that prefix or with that name slips in. This
closes the gap noted in `docs/reviews/plugin-hub-readiness-001.md` blocker
3 — previously the runtime gate prevented the listener from binding but the
class was still in the jar.

## Cross-references

- `apps/plugin/SECURITY_DESIGN.md` — reviewer-facing legibility contract.
- `apps/plugin/SECURITY.md` — public disclosure policy.
- `docs/runelite-hub/THREAT_MODEL.md` — what we mitigate and what we accept.
- `docs/runelite-hub/SUBMISSION_CHECKLIST.md` — sections 3 and 4 reference this
  audit for the "no HTTP server", "no reflection", "no plaintext URLs" items.
- `apps/plugin/build.gradle.kts` — `checkNoHttpServer`, `checkNoReflection`,
  `checkNoPlaintextUrls`, `secretsScan` enforce these rules at build time.
- `apps/plugin/src/test/kotlin/co/rowm/osrsllm/cloud/SourceTreeAuditTest.kt`
  — runtime mirror in the test report.
