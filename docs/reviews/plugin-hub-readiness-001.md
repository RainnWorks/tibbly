# Plugin Hub readiness review 001

Reviewer hat: senior `runelite/plugin-hub` maintainer. Reading `apps/plugin/` as
if it were the PR I open tomorrow. Tone: adversarial. I am paid (notionally) to
keep junk out of the hub. I have rejected dozens. I remember PR #11453
("OSRS MCP plugin") and #7459 (RuneGPT) by heart.

## Verdict

**Would reject hard.** The same rule that closed PR #11453 closes this PR, and
on top of that the production source set ships `ProcessBuilder` and a JAR that
still contains the loopback HTTP/MCP listener class. This is not a "fix one
sentence" review, it is a "you have not done the refactor you claim to have
done" review.

## Blockers (must fix before submission)

1. **`ProcessBuilder` is present in the production source set.** Policy:
   POLICY_SUMMARY.md lines 134-135 ("`ProcessBuilder` of any kind -- explicitly
   called out in PR #11453"); precedent: PR #11453 maintainer raiyni: *"I meant
   all uses of process builder."* Locations:
   - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ClaudeRunner.kt:184`
     (`ProcessBuilder(shell, "-l", "-c", cmd)` shelling out to `claude -p`)
   - `apps/plugin/src/main/kotlin/co/rowm/osrsllm/OsrsLlmHelperPanel.kt:239`
     (`ProcessBuilder(shell, "-l", "-c", cmd)` for "Install in Claude CLI"
     button at line 79)

   Suggested fix: delete `ClaudeRunner` and `LocalClaudeBackend` and the
   "Install in Claude CLI" panel section. They are dev-only conveniences; the
   shipped plugin must not contain them. Remove the references in
   `OsrsLlmHelperPlugin.kt:138-165` (the `ClaudeRunner` field) and the install
   button block in `OsrsLlmHelperPanel.kt:38-89, 150-180`.

2. **The "Install in Claude CLI" button is visible to every player by default
   and shells out on click.** The panel is registered unconditionally in
   `OsrsLlmHelperPlugin.kt:206` (`clientToolbar.addNavigation(button)`) and the
   button is always rendered (`OsrsLlmHelperPanel.kt:79`). The button fires
   `runInstall` which runs `claude mcp remove osrs; claude mcp add osrs`
   through a login shell (`OsrsLlmHelperPanel.kt:139-141, 155-180`). A
   hub-installed plugin must not execute arbitrary user shell commands --
   period -- and certainly not as a default-visible button. Policy:
   POLICY_SUMMARY.md line 99 ("No `ProcessBuilder`"). Suggested fix: as in
   blocker 1, delete the entire install/copy block.

3. **The local HTTP/MCP server class is in the shipped JAR.** SECURITY_DESIGN.md
   section 4 and THREAT_MODEL.md threat T5 both claim the
   `co/rowm/osrsllm/local/McpServerService` is "excluded from the shipped jar
   by package boundary". That claim is **false**. `build.gradle.kts:69-76`
   (`shadowJar`) does not exclude `co/rowm/osrsllm/local/**`; only the grep
   gates exclude that path. The class compiles and is injected by Guice (it is
   the parameter type at `OsrsLlmHelperPlugin.kt:79`, instantiated and used by
   `OsrsLlmHelperPanel.kt:27`). The Ktor `Netty` engine and
   `io.modelcontextprotocol:kotlin-sdk` ride in via the implementation
   dependencies at `build.gradle.kts:32-35`. The maintainer's read of PR
   #11453 was that "plugins which expose player information over HTTP" is the
   line; a JAR containing the listener class plus a config toggle to start it
   is exactly that, even if `developerMode` is off by default. Policy:
   POLICY_SUMMARY.md lines 75-78, 104; precedent: PR #11453 (riktenx, May
   2026). Suggested fix: actually remove `src/main/kotlin/co/rowm/osrsllm/local/`
   from the production source set (move to `src/dev/kotlin/` with a separate
   source set NOT included in `shadowJar`), drop the
   `io.modelcontextprotocol:kotlin-sdk` and `ktor-server-*` `implementation`
   dependencies, and rewire `OsrsLlmHelperPanel.kt`, `OsrsLlmHelperPlugin.kt`,
   and `ClaudeRunner.kt` to remove their `McpServerService` references.

4. **The plugin name advertises author-rank ordering, not function.** Plugin
   name is `"00_OSRS LLM Helper"` (`OsrsLlmHelperPlugin.kt:60`). The `00_`
   prefix is a developer trick to sort first in the RuneLite plugin list. The
   hub maintainers explicitly call this out as spammy and reject for it (it
   reads as either obfuscation or self-promotion). Policy: POLICY_SUMMARY.md
   lines 24-29 ("not malicious… no obfuscation that hides what the plugin
   does", and the general bar of "doesn't look reviewed and maintained").
   Suggested fix: set name to `"OSRS LLM Helper"` (or the chosen product
   name, e.g. `"Tibbly"`). Also update `runelite-plugin.properties:1`
   `displayName` to match.

5. **Manifest `warning=` line is missing entirely from
   `runelite-plugin.properties`.** Current file is 6 lines, no `warning=`
   field. Policy: POLICY_SUMMARY.md lines 145-152 (verbatim manifest
   requirements; "required for any plugin that performs network egress to a
   third party"); precedent: every Wise Old Man / LeaguesSync / RuneProfile
   / ScapeGPT / WikiSync entry on the hub today carries one. Iprodigy
   explicitly told the RuneGPT author this on PR #7459. The submission
   checklist's row 2.4 still lists this as TODO. Suggested fix: write the
   `warning=` line in the canonical 2026 shape now (the draft text in
   `SUBMISSION_CHECKLIST.md:34` is a usable starting point), and ship it in
   `runelite-plugin.properties` so the same artifact a reviewer pulls down
   carries the disclosure.

6. **Author field is `tom`, support URL is `github.com/tom/...`.**
   `runelite-plugin.properties:2,4`. The hub requires real, contactable
   authorship -- both because takeover policy needs a stable identity and
   because the reviewer needs to know who they are merging a paid SaaS
   integration from. `support=https://github.com/tom/osrs-llm-helper`
   currently resolves to a user that does not own this repo. Suggested fix:
   set `author` to the real GitHub handle (`thomasnairn` per
   `SUBMISSION_CHECKLIST.md:33`) and `support` to the actual repo URL.

7. **Plugin description mis-describes what the shipped plugin does.**
   `OsrsLlmHelperPlugin.kt:61` and `runelite-plugin.properties:3` both say
   "MCP server exposing live game state". That is the description of the
   rejected PR #11453 design. The current architecture (per
   SECURITY_DESIGN.md and DATA_DISCLOSURE.md) is an outbound WSS chat client
   driven from a paid backend, optionally Tibbly cloud or BYO key. Shipping
   the rejected-design description is the single most predictable way to
   trigger a maintainer to grep for the rejected pattern. Suggested fix:
   rewrite to match `docs/runelite-hub/PLUGIN_DESCRIPTION.md` once it is
   finalised; in the meantime use the SUBMISSION_CHECKLIST.md 2.4 draft text
   modulated to fit in `description=`.

## Important concerns (would request changes)

1. **The build gates exclude `local/**` from the grep checks but the JAR does
   not.** `build.gradle.kts:136-138` defines `productionKotlinTree` as
   `src/main/kotlin` minus `co/rowm/osrsllm/local/**`. Every legibility gate
   (`checkNoHttpServer`, `checkNoReflection`, `checkNoPlaintextUrls`,
   `checkMcpServerGated`, `checkNoKeyLeak`, `secretsScan`) runs over that
   tree. Meanwhile the production JAR (`tasks.shadowJar` at
   `build.gradle.kts:69-76`) has no such exclusion. The "audit by grep"
   story in `SECURITY_DESIGN.md:32-44` and the SECURITY_AUDIT.md PASS table
   are therefore measuring a different artifact from what gets uploaded to
   the hub. A reviewer running the build, taking the produced JAR, and
   `javap`-ing it will find `co.rowm.osrsllm.local.McpServerService`
   immediately and lose all trust in the audit. Fix as blocker 3.

2. **`McpServerService.kt:121-127` is reachable via the public injectable
   path at any time.** Even with `developerMode = false` the class is
   constructed by Guice (injected at `OsrsLlmHelperPlugin.kt:79`) and
   `start(host, port)` is a public synchronized method. The runtime guard
   in `OsrsLlmHelperPlugin.kt:230` is only enforced from the plugin's own
   `startUp`. A second plugin in the same RuneLite session, or a future
   internal caller in this codebase, can reach in and call `.start(...)`
   directly. The Gradle `checkMcpServerGated` task only scans for
   `mcpServerService\.(start|restartWith)` literal call sites within 20
   lines of a `developerMode()` call (`build.gradle.kts:190-223`); it does
   not stop reflection or DI-driven instantiation. The threat-model T8 ("a
   second plugin reads `AuditLog`") is acknowledged as "out of scope" but
   does not cover this richer attack: another plugin pulls
   `McpServerService` out of the injector and calls `start("0.0.0.0",
   8888)`. Fix as blocker 3 (remove from production source set).

3. **`EgressGate.egress` uses `runBlocking` from arbitrary callers
   (`EgressGate.kt:68`).** Any code path that triggers an egress -- the
   chat send, the heartbeat, the tool-result reply -- will pin the calling
   thread until the WSS write completes. `CloudChatRunner.send` is
   documented as "safe to call from any thread" (`CloudChatRunner.kt:218`)
   and explicitly uses `runBlocking` again at line 241. If the chat panel
   calls `send` from the EDT, the EDT freezes for `turnTimeoutMillis =
   120_000L`. LlemonDuck's RuneGPT review explicitly flagged this pattern
   (POLICY_SUMMARY.md cross-references in
   `SUBMISSION_CHECKLIST.md` row 5.3). The `ChatPanel` plumbing is not in
   my read window; if the panel hops off the EDT before calling `send`
   this is moot. If not, the plugin will visibly freeze RuneLite for two
   minutes on any stuck turn. Fix: make `EgressGate.egress` and
   `CloudChatRunner.send` non-blocking, or assert at the boundary that
   they are never called from the EDT.

4. **`AccountPanel.runInBackground` spawns a raw daemon `Thread`
   (`AccountPanel.kt:633-637`).** PR #7459 reviewer iProdigy flagged
   exactly this on RuneGPT: prefer the injected
   `ScheduledExecutorService`. Same with
   `OsrsLlmHelperPanel.kt:155, 264` and the `SwingWorker` inside
   `AccountPanel.kt:163`. None of these break correctness, but a senior
   maintainer reads this as "the author has not internalised the plugin
   coding conventions yet". Fix: use the injected
   `ScheduledExecutorService` (RuneLite ships one) for all
   off-EDT work.

5. **`Desktop.getDesktop().browse(...)` is invoked from
   `AccountPanel.kt:233`.** Opens a system browser from inside RuneLite to
   the Stripe customer portal URL. This is precedent-ed (some plugins do
   it for support docs) but is borderline for a paid SaaS feature; the
   maintainer will at minimum want a confirmation dialog ("This opens
   your default browser to `<host>`. Continue?") so a typo in
   `backendUrl` does not drive a player to an attacker-controlled URL.
   Same goes for `JFileChooser` writing the export file at
   `AccountPanel.kt:263-274` -- fine, but flag it in the disclosure.

6. **`config.cloudChatEnabled()` and `chatMode` are two competing sources of
   truth.** `OsrsLlmHelperConfig.kt:36-42` describes
   `cloudChatEnabled` as "legacy" (also `docs/CONFIG.md:15`) but the
   startup logic at `OsrsLlmHelperPlugin.kt:179-185, 246` honours both. A
   player who set `chatMode = ByoOpenAi` and never untoggled
   `cloudChatEnabled` will see a warning in the logs but the cloud runner
   path still does the WSS handshake (line 298 `runner.start()`). A hub
   reviewer reading the audit story ("one egress door") will spot the two
   conflicting toggles and ask "what actually happens when both are on?".
   The answer in the code (lines 246-300) is "both happen": the cloud
   runner starts AND the BYO branch logs that the runner is unimplemented.
   Fix: collapse to a single source of truth or make the conflict
   impossible at the config layer (don't render `cloudChatEnabled` when
   `chatMode != Cloud`).

7. **The plugin descriptor has 5 `@PluginDependency` declarations
   (`OsrsLlmHelperPlugin.kt:64-68`).** That makes installing this plugin
   force-enable five stock plugins (BankTags, XpTracker, Slayer,
   ClueScrolls, Party) for every user. The maintainers will ask why; the
   right answer is "we re-use their config / state stores" which is fine,
   but it should be in the description so users are not surprised by five
   side-effects of installing.

8. **The chat panel surface ships always, including a "Tibbly account"
   sidebar with subscribe/manage-billing affordances.**
   `OsrsLlmHelperPlugin.kt:366-387` adds the account nav button
   unconditionally. The panel itself does the right thing when
   `cloudChatEnabled=false` (`AccountPanel.kt:151-152`) -- it shows an
   explainer -- but a maintainer looking for "is the in-plugin chat a thin
   advertisement for the paid service" will see "Subscription / Pair / Data
   privacy" sections wired up before any chat has been sent, and read it as
   a billing surface rather than a feature. The precedent (ScapeGPT, PR
   #4271) shipped with no account panel at all and ScapeGPT was the most
   permissive maintainer outcome on file for a paid LLM plugin. Suggested
   fix: do not register the account panel at all when
   `!cloudChatEnabled || !consentAccepted` -- drop the explainer too.
   Otherwise the surface area for "this is just an upsell" reads as
   intentional.

9. **Generated device key is a UUID with 8 hex chars appended
   (`OsrsLlmHelperPlugin.kt:480-482`).** Functionally fine, but please use a
   single `SecureRandom` source and document the entropy in
   DATA_DISCLOSURE.md row A3. As written this looks like ad-hoc crypto and
   reviewers correctly read ad-hoc crypto as a yellow flag in a paid SaaS.
   Use `SecureRandom().nextBytes(32)` and hex-encode; or just use one
   `UUID.randomUUID()`.

10. **`secret = true` is set on `byoApiKey` (`OsrsLlmHelperConfig.kt:82`) --
    good -- but the config is stored in plaintext in the RuneLite profile
    file regardless.** The `secret` attribute only masks the rendered field
    in the UI. The disclosure in `docs/CONFIG.md:44-48` says "Stored
    locally in RuneLite config" but does not say "in plaintext on disk".
    Add the explicit disclaimer; otherwise a security researcher will file
    a CVE-shape report and you will lose hours triaging it.

11. **`AccountSummaryClient` sends the **same** hashed device key as both
    `Authorization: Bearer` AND `x-device-key` headers
    (`AccountSummaryClient.kt:78-80`).** That is not authentication, that
    is a self-identification string in two headers. If the bearer token
    has any compromise (transit log, error trace, log line on the
    backend), the attacker gets the user's account. This is "auth bypass"
    grade. The KDoc at lines 28-37 even calls this out as a temporary
    state. The hub reviewer will not let a paid SaaS ship with that as
    its production identity mechanism. Fix at the backend before this
    plugin goes to hub review.

12. **`THREAT_MODEL.md` T5 status says "Mitigated" with the false claim
    "excluded from the shipped jar by package boundary".** This is the
    document a reviewer leans on hardest. The misstatement is the kind of
    finding that converts a "would request changes" into a "would reject
    hard" because the maintainer no longer trusts any other claim in the
    document. Fix the gap (blocker 3), then fix the doc.

13. **`PluginDescriptor` `tags = ["llm", "ai", "mcp", "assistant"]` includes
    `"mcp"`.** That tag is the literal label of the rejected PR #11453 and
    will pattern-match for any maintainer running `grep -l mcp
    plugin-hub/plugins/*`. There is no benefit to the user from this tag.
    Suggested fix: `tags = ["chat", "assistant", "external"]` to match
    Wise Old Man / Dink / ScapeGPT precedent.

## Nits

1. `OsrsLlmHelperPlugin.kt:60` plugin display name has the `00_` prefix --
   already in blocker 4; flagging again because it is also visible in the
   in-game plugin list.
2. `OsrsLlmHelperPlugin.kt:137` `private var byoChatRunner: Any? = null` --
   shipping a `null` placeholder field with `@Suppress("unused")` is the
   kind of "we're not done yet" signal that triggers maintainers. Delete
   the field and the comment; add it back in the PR that lands the
   runner.
3. `OsrsLlmHelperPlugin.kt:255` plugin version is hard-coded to
   `"0.1.0"`. That value also lives in `build.gradle.kts:9` and
   `McpServerService.kt:162` as a literal string. Make it one source of
   truth (BuildConfig or a generated resource).
4. `EgressGate.kt:101-159` accepts arbitrary header maps but only validates
   for CRLF; it does not normalise header name case or check for duplicate
   Authorization headers added by callers. Minor; consider an allow-list of
   header names.
5. `AccountPanel.kt:163-203` uses `SwingWorker` for the read fetch but the
   action handlers go through `runInBackground` (raw `Thread`). Pick one.
6. `StateProbes.kt:182-189` data class has a doc-comment mismatch:
   `remainingTicks` has a KDoc comment about cycle conversion attached to
   the wrong field (the conversion belongs on the field, the
   `targetIsLocalPlayer` line gets the leftover comment). Cosmetic but a
   reviewer reading top-to-bottom will misread it.
7. `FarmingTables.kt` is wonderful work but the patch-state value bands
   (e.g. `decodeHerb` at line 280) are aggregated heuristics. The KDoc
   acknowledges this as v1; please add a `version = "v1"` field to the
   `FarmingPatchesReport` shape so the LLM-side prompt can tell the player
   "v1 farming, may be off by one stage" rather than silently being
   wrong.
8. `OsrsLlmHelperConfig.kt:115-145` developer-mode section uses
   `closedByDefault` is implicit at the top level and explicit at the
   chat-mode section (`OsrsLlmHelperConfig.kt:163`). The developer-mode
   block at lines 113-144 is not collapsed by default -- set
   `closedByDefault = true` so a casual user never sees the local-MCP
   knobs.
9. `runelite-plugin.properties` does not include a `version=` field.
   Optional in the manifest format but good practice; pulls from
   `build.gradle.kts:9`.

## Things that look good

1. The `EgressGate` / `BackendUrl` / `ConsentState` / `OutboundPayload`
   four-file legibility story is a real engineering effort. The single
   `webSocket.send(` call site (`EgressGate.kt:70`) is a clean choke
   point. If the rest of the plugin actually matched the story this
   tells, I would be far more positive.
2. The Gradle gates (`checkNoHttpServer`, `checkNoReflection`,
   `checkNoPlaintextUrls`, `secretsScan`, `checkNoKeyLeak`,
   `checkAccountPanelNoRawTokens`, `checkMcpServerGated`) are unusually
   thorough for a hub submission. Wire them into a CI badge and link it
   from the PR.
3. `BackendUrl`'s value-class init throw (line 20) is the right shape.
4. `AccountPanel`'s decision to never do token arithmetic in the plugin
   and to surface the backend's tier-aware proxy verbatim
   (`AccountPanel.kt:30-58, 383-421`) is a smart compliance posture. The
   tier-proxy `UsageProxy` sealed hierarchy in `AccountSummaryClient.kt:301-319`
   is good design.
5. `FarmingTables.kt` mirroring the package-private upstream table because
   the maintainer cannot consume it cross-package is the right call and
   the v2 deferral KDoc is honest about the limits.
6. `DATA_DISCLOSURE.md` is the most complete data-disclosure I have read
   for a hub submission. The D-bis and D-tris sections preempt the
   reviewer's "what about the panel?" and "what about BYO?" questions.
7. `THREAT_MODEL.md`'s STRIDE table is real STRIDE not box-ticking. T15
   ("OSRS bank in OpenRouter training data") is a question I would have
   asked anyway; pre-answering it earns trust.
8. `SECURITY.md` has a reasonable disclosure SLA and a safe-harbour
   clause. Most hub plugins have neither.

## Things I checked but cannot verify without running it

1. Whether `ChatPanel.send` hops off the EDT before calling
   `CloudChatRunner.send`. If it does not, blocker concern 3 becomes a
   hard reject because RuneLite visibly freezes for `turnTimeoutMillis =
   120_000L`. Please confirm by attaching a thread dump captured during
   a stuck turn, or by including the EDT-assert in the send path.
2. Whether the produced shadowJar actually contains
   `co/rowm/osrsllm/local/McpServerService.class`. I am 99% sure it does
   from reading `build.gradle.kts:69-76`, but a `jar tf` on the artifact
   would close the loop. (If not, my blocker 3 narrows to "fix the
   docs"; if yes, it stays as written.)
3. Whether the `MenuAction.RUNELITE` right-click entries in
   `OverlayChatController.kt:115-126` ever close over a state-mutating
   tool call (vs only opening a chat). I scanned but did not exhaustively
   trace every `onClick` callback.
4. Whether the `runeliteFormattedMessage` path in
   `ChatOutput.kt:38-43` is fed from the LLM verbatim (in which case
   prompt-injection could write inflammatory `[AI]` lines into the
   player's chatbox). I assume yes; add a basic content filter on the
   way out, or document the risk.
5. Whether `client.localPlayer.name` (used in
   `CloudChatRunner.kt:254`, `AccountPanel.kt:165, 240`) is null-safe
   from the EDT versus the client thread. RuneLite's API generally
   permits localPlayer reads off the client thread but null-handling has
   bitten contributors before. Verify with a `clientThread.invoke`
   wrapper if necessary.

## Specific files I want to see updated

- `apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ClaudeRunner.kt` --
  delete (blocker 1).
- `apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatBackend.kt` --
  remove `LocalClaudeBackend`; `ChatBackendSelector` becomes the cloud
  path or nothing.
- `apps/plugin/src/main/kotlin/co/rowm/osrsllm/OsrsLlmHelperPanel.kt` --
  delete the install/copy/MCP-status UI; keep the pairing UI.
- `apps/plugin/src/main/kotlin/co/rowm/osrsllm/local/McpServerService.kt`
  -- move out of `src/main/kotlin/` (blocker 3).
- `apps/plugin/build.gradle.kts` -- drop `io.modelcontextprotocol:kotlin-sdk`
  and the `ktor-server-*` `implementation` dependencies once the local
  server is gone; the only legitimate ktor dep is the WS client.
- `apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt`
  -- remove `mcpServerService`, `claudeRunner`, `byoChatRunner` fields and
  their wiring; rename the plugin without the `00_` prefix; trim
  `tags=`.
- `apps/plugin/src/main/resources/runelite-plugin.properties` -- add
  `warning=`, fix `displayName`, `author`, `support`, `description`, add
  `version=`.
- `apps/plugin/SECURITY_DESIGN.md` -- rewrite section 4 to reflect the
  actual JAR exclusion, OR delete the section entirely once the local
  path is moved out of `src/main/`.
- `docs/runelite-hub/THREAT_MODEL.md` -- fix T5 to match reality.
- `docs/runelite-hub/SECURITY_AUDIT.md` -- re-run after the JAR audit
  (`jar tf shadowJar.jar | grep local`) and add that row.
- `docs/runelite-hub/SUBMISSION_CHECKLIST.md` -- row 3.3 still says TODO;
  flip to DONE only after blocker 1 lands.

---

This is fixable. The bones are there. But the gap between what the docs
claim and what the code does is the kind of gap a maintainer reads as
"the author has not actually audited their own plugin". Tighten that gap
before the hub PR opens.
