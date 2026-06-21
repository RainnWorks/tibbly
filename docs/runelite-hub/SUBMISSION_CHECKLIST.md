# RuneLite Plugin Hub -- Submission Checklist

> **Goal:** A single page where every item is either DONE or has a clear owner
> and unblocking step. We do not open a PR against `runelite/plugin-hub` until
> every row in this checklist is green.
>
> See POLICY_SUMMARY.md for the rule each row maps to. See PRECEDENT.md for the
> plugin we modeled each row on.

## Status legend

- DONE -- verified in code today.
- WIP -- work in progress on a feature branch.
- TODO -- not started; ownership noted.
- BLOCKED -- needs Tom on wakeup; logged in `docs/agents/OPEN_QUESTIONS.md`.

## 1. License & repository hygiene

| # | Requirement | Status | Notes |
|---|---|---|---|
| 1.1 | Public GitHub repo for the plugin code, separate from backend | TODO | When we split monorepo for hub submission, plugin code lives at github.com/RainnWorks/tibbly-plugin per REPO_SPLIT.md |
| 1.2 | OSI-approved license (MIT, BSD-2-Clause, Apache-2.0) | TODO | LICENSE file in `apps/plugin/`. Default: MIT per Tom's brief. Confirm acceptable to hub maintainers (BSD-2 is the README default). |
| 1.3 | All dependencies open-source, public, with Maven coordinates | DONE | We depend on RuneLite client + Kotlin stdlib + kotlinx.serialization only. |
| 1.4 | Gradle dependency verification metadata included if `gradle` build mode | TODO | If we keep Kotlin we are in `gradle` mode → need `gradle/verification-metadata.xml`. |
| 1.5 | `runelite-plugin.properties` with `displayName`, `author`, `description`, `tags`, `plugins`, `version` | DONE | Already present from initial plugin. Update `description` to match PLUGIN_DESCRIPTION.md before submission. |

## 2. Manifest file (`plugin-hub/plugins/osrs-llm-helper`)

| # | Requirement | Status | Notes |
|---|---|---|---|
| 2.1 | `repository=` HTTPS URL of plugin repo | TODO | After repo split. |
| 2.2 | `commit=` full 40-char SHA of the reviewed commit | TODO | Set at PR time. |
| 2.3 | `authors=` non-empty | TODO | `thomasnairn` at minimum. |
| 2.4 | `warning=` describing every piece of data sent off the client | TODO | Draft: `This plugin connects to osrsllm.app to drive an LLM chat. While the cloud chat is enabled it transmits your in-game username, IP address, and -- when relevant to your question -- your stats, equipment, inventory, bank contents, location, quest progress, and chat input to osrsllm.app (controlled by the plugin author) which forwards prompts to LLM providers via OpenRouter. Cloud chat is opt-in and off by default; the plugin works in local tools-only mode without any network egress.` |

## 3. Forbidden features -- confirm we ship NONE of these

| # | Forbidden item (from "Rejected or Rolled-Back Features") | Status | Notes |
|---|---|---|---|
| 3.1 | No mouse/keyboard synthesis, no `Robot`, no `MenuAction` invocation that simulates user input | DONE | Plugin only paints overlays + emits chat suggestions. Verified via `grep -r 'Robot\|menuAction\|invokeMenuAction' src/main/kotlin/` -- no hits. |
| 3.2 | No auto-typing / programmatic chatbox text | DONE | Chat panel writes to a side panel, never `client.runScript(ScriptID.CHAT_PROMPT_*)`. |
| 3.3 | No `ProcessBuilder`, no `Runtime.exec` | TODO | Confirm before submission. Grep current source. |
| 3.4 | No runtime code download (data download OK) | DONE | We only fetch JSON over WSS. |
| 3.5 | No reflection / JNI | DONE | None present. |
| 3.6 | No credential storage in plugin | DONE | Auth is a one-time pairing code; no OSRS credentials touched. |
| 3.7 | No HTTP server exposing player info | **NOT YET -- REQUIRED CHANGE** | Current `McpServerService.kt` binds a localhost HTTP MCP server. That pattern was rejected on PR #11453. Must be removed before submission. Replace with outbound WebSocket only. |
| 3.8 | No cloud relay / SSH tunnel / ngrok-style egress | DONE | Outbound WSS to one domain (`wss://api.osrsllm.app`). |
| 3.9 | Not a boss helper / freeze timer / PvP indicator | DONE | Not in scope. |
| 3.10 | Not an auto-flipper / RWT helper | DONE | Not in scope. |
| 3.11 | No adult content | DONE | Not in scope. |
| 3.12 | No removal of attack menu entries / left-click swaps that could trivialise mechanics | DONE | We do not modify menu entries; we only render advice. |

## 4. Disclosure & consent (the Wise Old Man / LeaguesSync pattern)

| # | Requirement | Status | Notes |
|---|---|---|---|
| 4.1 | Manifest `warning=` covers every datum sent | TODO | See 2.4. Cross-reference DATA_DISCLOSURE.md. |
| 4.2 | First-run consent dialog inside the plugin | TODO | Modal on `onStartUp` while `cloudChatEnabled` is null. User picks "Local tools only" or "Enable cloud chat -- sends data per docs". |
| 4.3 | Public privacy policy reachable from the plugin panel | TODO | Hosted at `osrsllm.app/privacy`; link button in panel. |
| 4.4 | `docs/architecture/DATA_FLOW.md` documents every byte that leaves the client | DONE (this PR) | See `docs/architecture/DATA_FLOW.md`. |
| 4.5 | Cloud chat toggle is OFF by default | TODO | Plugin config default `cloudChatEnabled = false`. Local tool surface still works. |
| 4.6 | When OFF, no network egress beyond what stock RuneLite already does | TODO | Audit at PR time. |

## 5. Architecture / code rules

| # | Requirement | Status | Notes |
|---|---|---|---|
| 5.1 | Use injected `OkHttpClient` and `Gson` -- never `new` them | TODO | Verify in transport service. PR #4271 reviewer Ben-Colwell flagged this on ScapeGPT. |
| 5.2 | Use injected `ScheduledExecutorService` -- do not spawn raw `Thread`s | TODO | PR #7459 reviewer iProdigy flagged this on RuneGPT. |
| 5.3 | Network I/O uses OkHttp `enqueue` (async) not `execute` (blocking) | TODO | LlemonDuck on PR #7459: *"Don't block the shared ScheduledExecutorService"*. |
| 5.4 | Resources loaded with `getResourceAsStream()`, never `getResource()` | TODO | Audit. |
| 5.5 | All transitive dependencies have public Maven coordinates | DONE | Kotlin stdlib, kotlinx.serialization, OkHttp (already in RL). |

## 6. Plugin description (in the .properties file + hub page)

| # | Requirement | Status | Notes |
|---|---|---|---|
| 6.1 | Description matches what the plugin actually does | TODO | Use PLUGIN_DESCRIPTION.md. |
| 6.2 | No marketing-spam, no "the best ever" framing | TODO | Match the tone of Wise Old Man / Dink. |
| 6.3 | Explicitly mentions cloud component + opt-in | TODO | One sentence in description. |
| 6.4 | Tags align with hub conventions (e.g. `chat`, `helper`, `external`) | TODO | Use existing precedent tags. |

## 7. PR & review hygiene

| # | Requirement | Status | Notes |
|---|---|---|---|
| 7.1 | Single commit on the plugin repo, full 40-char SHA in manifest | TODO | At submission. |
| 7.2 | PR description links DATA_FLOW.md + privacy policy | TODO | At submission. |
| 7.3 | Repository has issues enabled for user bug reports | TODO | Required by takeover policy + maintainers want users to file with us, not them. |
| 7.4 | Maintainer (Tom) responsive within 7 days of any review comment | DONE | Solo dev; PR will be watched. |
| 7.5 | No new dependencies unless essential | DONE | None planned. |

## 8. Things that BLOCK submission today

These are the gating items. None of the others matter until these clear.

1. **Remove the localhost MCP HTTP server.** The current
   `McpServerService.kt` runs an HTTP MCP server on `127.0.0.1:8282`.
   PR #11453 was rejected against exactly this pattern. Replace with a
   single outbound WebSocket connection to our backend. Tool calls flow
   *into* the plugin from the backend, not *out of* the plugin to a
   localhost listener. **This is the one architectural change Tom must
   approve before we open a hub PR.** Logged in OPEN_QUESTIONS.

2. **Add the first-run consent dialog and the "Local tools only"
   default.** Until cloud chat is opt-in with the explicit dialog, the
   plugin behaves as if every install agreed to ship data to our backend.
   That fails the disclosure bar.

3. **Confirm the manifest `warning=` line covers every datum we send.**
   Cross-check with DATA_DISCLOSURE.md monthly as the tool surface grows.

4. **Confirm MIT vs BSD-2 with maintainer.** Default ship MIT; flip to
   BSD-2 if a maintainer asks. Both have shipped on the hub.

Once these four are cleared, the rest of the checklist is mechanical.
