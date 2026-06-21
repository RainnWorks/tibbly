# Kotlin idiomatic review 001

- Reviewer: Kotlin idiomatic hat.
- Date: 2026-06-21.
- Scope: `apps/plugin/src/main/kotlin/` (full read on `cloud/*`, `chat/*`, `plugin/OsrsLlmHelperPlugin.kt`, `ChatMode.kt`, `OsrsLlmHelperPanel.kt`; sampled `bank/`, `events/`, `integrations/`).
- Sources consulted: `apps/plugin/build.gradle.kts` (Kotlin 2.3.21, JVM 11, kotlinx-serialization, kotlinx-coroutines, Ktor 3.0.3 client), no `checkstyle.xml` under `apps/plugin/`.

## Verdict

The code is largely idiomatic Kotlin and reads well; the cloud surface in particular leans on value classes, sealed hierarchies, `data class`, scope functions, and explicit nullability. The biggest single drag is the `runCatching {}` swallow pattern, which is pervasive wherever a Throwable could plausibly escape and at scale violates Effective Kotlin item 7 (prefer null or a Result-like type to exceptions for expected failure modes) and detekt's `SwallowedException` plus `TooGenericExceptionCaught` rules. The second drag is `runBlocking` inside Swing-adjacent or DI-managed code (`CloudChatRunner.start`, `EgressGate.egress`, `BackendWsClient`), which works today only because the Ktor calls inside the block are short, but reads as "Java thread programming dressed in coroutines". The biggest single win for the hub maintainer is to (a) replace blanket `runCatching` swallows in `cloud/` with named exception types, and (b) lift `runBlocking` calls out of synchronous methods or document why the bridge is unavoidable.

## Blockers

None. Nothing here is a correctness bug or a hub-rejection risk on its own. The duplicate device-key generator at `apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:514` is close (see Important 1).

## Important

### 1. Duplicate device-key generation paths

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:514-521` re-implements device-key generation as a private function `deviceKeyForAuth()` that talks to `ConfigManager` directly and uses two concatenated `UUID.randomUUID()` strings. `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DeviceKey.kt:53-97` is the SecureRandom-backed, length-checked, alphabet-validated source of truth, already `@Inject`-ed elsewhere. Effective Kotlin item 19 (do not repeat knowledge): a second key generator silently diverges from the one the audit, hash format, and tests are built around. The plugin should inject `DeviceKey` and call `deviceKey.getOrCreate()` instead of carrying its own implementation. Fix: delete the private helper, inject `DeviceKey`, use `deviceKey.getOrCreate()`.

### 2. `runBlocking` inside synchronized DI-managed methods

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:117` (`connectInternal`), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:68-72` (`egress`), and `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:200, 241` all call `runBlocking { ... }`. The egress one is callable from the EDT through `AccountSummaryClient` chains. `runBlocking` from a Swing-or-EDT-adjacent surface is a Kotlin smell of the highest order; coroutines documentation (and the standard JetBrains style guide) says it must be confined to `main()` and tests. Effective Kotlin item 8 (handle async properly). Fix: keep the public method synchronous if you must, but document the bridge with a comment and pin the dispatcher to an explicit non-`Dispatchers.Main` thread; ideally lift the methods to `suspend fun` and have the EDT callers post to a worker.

### 3. Pervasive `runCatching` swallow pattern

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:134-135, 142, 156, 199, 209-212, 232-262, 268, 275`; `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:97-99, 128-130, 165-167, 199-201, 226-228, 251-253`; `apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:432-489, 515` use `runCatching { ... }` as a generic catch-all, then either `.getOrNull()` or fire-and-forget. detekt's `SwallowedException` and `TooGenericExceptionCaught` would flag every one. Effective Kotlin item 7 says to prefer null or a Result-like type for expected failure modes; the current pattern hides real bugs (e.g. a `JsonDecodingException` in `AccountSummaryClient.openCustomerPortal` returns `null` indistinguishable from a 4xx). Fix: narrow the catches to the specific exceptions that can actually occur (`IOException`, `kotlinx.serialization.SerializationException`, `IllegalArgumentException`), log at the appropriate level, and return a typed Result or sealed-class outcome where the caller cares.

### 4. `runCatching` around `::isInitialized` is the wrong primitive

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:273-289, 339-352` wrap `HarnessContext.build(...)` in `runCatching { if (::gameStateStore.isInitialized && ::eventLogService.isInitialized) ... }`. `lateinit` `isInitialized` cannot throw on a managed `@Inject` field that DI has already set, so the `runCatching` is a noise envelope. Effective Kotlin item 8 (handle nulls properly) and item 2 (minimize variable scope): if the field is set by DI before `startUp` returns, the guards never fire; if it is not, you should fail loudly. Fix: drop the per-call `runCatching` and the `isInitialized` guards, or extract a `buildHarness(): String?` helper that documents the precondition.

### 5. Generic `catch (e: Exception)` cluster

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:194` (`catch (e: Exception)` inside `SwingWorker.done()`); `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:97, 106, 128, 137, 165, 174, 199, 225, 251`; `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingFlow.kt:115`. detekt's `TooGenericExceptionCaught` rule covers this directly. Fix: replace `Exception` with the narrowest applicable type (`IOException`, `SerializationException`, `IllegalArgumentException`).

### 6. `catch (t: Throwable)` in production paths

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:257` (`catch (t: Throwable)` in `send`), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatRunner.kt:254, 281`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:132` (`catch (t: Throwable)` in inbound loop), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/integrations/ChatOutput.kt:44`. Catching `Throwable` is detekt `TooGenericExceptionCaught` plus a real correctness hazard: an `OutOfMemoryError` or coroutine `CancellationException` gets swallowed. Effective Kotlin item 7. Fix: narrow to `Exception`, and for the coroutine surfaces explicitly re-throw `CancellationException` first.

### 7. `runBlocking` from the BYO and Account synchronous paths is reachable from the EDT

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:68-72` and `egressHttp` both block. `EgressGate.egressHttp` is called from `AccountSummaryClient.fetchSummary` etc., which the `AccountPanel`'s `SwingWorker` schedules off-EDT. That works today, but `EgressGate.egress` (the WSS variant) does `runBlocking { webSocket.send(Frame.Text(text)) }` and is reached from `CloudChatRunner.send` which calls `runBlocking { withTimeoutOrNull(...) { ... } }` itself. There is therefore a single thread spending its life inside two nested `runBlocking` calls per turn. Effective Kotlin chapter on async plus the coroutines guide flag nested `runBlocking` as a smell because of deadlock risk on shared dispatchers. Fix: make `CloudChatRunner.send` and `EgressGate.egress` `suspend fun` so the outer caller picks the bridge once.

### 8. `requireNotNull` / `check` would read better than `?: error("...")`

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:64-65` uses `?: error("EgressGate refused: backend WSS not connected")`. Effective Kotlin item 5 (specify expectations with require / check / error): the idiomatic Kotlin call for this preview is `checkNotNull(transport.currentSession()) { "..." }`. `check` signals a state precondition; `error` is a generic throw. Same pattern at `apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatStore.kt:104` (`?: error("chat $id not found")`) where `checkNotNull(read(id)) { ... }` reads as the precondition it is.

### 9. Public visibility on non-public-API classes

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatRunner.kt:54, 83, 86, 103, 106, 110, 125, 126, 131, 134, 148, 157, 163, 170, 197`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:197-279`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/byo/ByoProvider.kt:44-222`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatBackend.kt:25-57`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/ChatMode.kt:15-97`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/ChatModeChoice.kt:13-38` explicitly mark every member `public`. Kotlin's default visibility IS `public`, so the keyword is noise; JetBrains coding conventions explicitly say "Do not specify the `public` modifier". Effective Kotlin item 30 (minimize element visibility): callers also lose the benefit of accidental tightening. Fix: drop every `public` keyword. Several of these types (`HttpTransport`, `Callbacks`, `SendResult`) likely want `internal` anyway since the class is single-module.

### 10. `Json` instances allocated per-class

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:65` (`private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }`), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:39-46`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:51-54`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingFlow.kt:39`. Each per-class `Json` is a fresh allocation and the `ignoreUnknownKeys` policy is duplicated. `CloudChatRunner.json` is never used in the file (declared, never referenced in the body; confirmed by re-reading 64-369). detekt's `UnusedPrivateMember` would flag it. Effective Kotlin item 19 (do not repeat knowledge): a single shared `InboundCodec.json` or sibling already exists. Fix: delete the unused `CloudChatRunner.json`; consolidate the remaining instances behind one named factory.

### 11. `private val log = LoggerFactory.getLogger(<Self>::class.java)` boilerplate × 30+

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:64`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:38`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:44`, and dozens of others. Java idiom that Kotlin codebases usually solve once with a top-level `inline fun <reified T> T.logger(): Logger`. Not a bug, but it is the most repeated 1-liner in the repo and a hub maintainer reads it as "Java in Kotlin". Effective Kotlin item 19. Fix: extract a `co/rowm/osrsllm/logging/Logging.kt` with an inline reified helper or use kotlin-logging.

### 12. Manual `MessageDigest` plus StringBuilder hex loop

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DeviceKey.kt:104-114` hand-codes the bytes-to-hex loop. Idiomatic Kotlin uses `joinToString("") { "%02x".format(it) }` or, on JDK 17+, `HexFormat.of().formatHex(bytes)`. The plugin targets JVM 11 so `HexFormat` is out, but `joinToString` keeps the manual loop off the page. Same pattern at `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DeviceKey.kt:90-97` for the random-alphabet draw, which would read as `(1..LENGTH).map { ALPHABET[rng.nextInt(ALPHABET.length)] }.joinToString("")`. Effective Kotlin item 26 (single level of abstraction): the StringBuilder mechanics are below the abstraction level of "generate a 40-char id".

### 13. `MutableList<...>` plus `mutableListOf()` where `buildList { ... }` reads cleaner

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:82-88` (`extraHeaders = mutableListOf<Pair<String, String>>(...); if (...) extraHeaders += "x-current-player" to currentPlayerName`); `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatRunner.kt:207-209`. Effective Kotlin item 1 (limit mutability) plus the standard `buildList { ... }` idiom. Fix:

```kotlin
val extraHeaders = buildList {
    add("Authorization" to "Bearer $hashed")
    add("x-device-key" to hashed)
    if (!currentPlayerName.isNullOrBlank()) add("x-current-player" to currentPlayerName)
}
```

### 14. `Json.decodeFromString(SerializerClass.serializer(), s)` instead of the reified overload

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:105, 136, 171` (`json.decodeFromString(AccountSummary.serializer(), resp.body)`), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/InboundCodec.kt:24`. kotlinx-serialization's reified `decodeFromString<T>(s)` exists precisely so callers do not have to name the serializer. The reified form documents intent at the call site (`json.decodeFromString<AccountSummary>(resp.body)`) and survives a class rename without a second touch. Effective Kotlin item 26.

### 15. `for (frame in opened.incoming)` plus `if (frame !is Frame.Text) continue` reads as Java

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:125-136`. Idiomatic Kotlin filters at the channel: `opened.incoming.consumeEach { frame -> (frame as? Frame.Text)?.let { ... } }`. detekt's `ReturnCount` would flag the multiple `continue` paths. Effective Kotlin item 26 (single level of abstraction).

### 16. `runCatching` chain swallows plus non-narrow types in `BackendWsClient`

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:124-141` chains `runCatching { ... }.recoverCatching { ex -> ... }.getOrDefault("unknown")`. The block returns `"remote_closed"` on success but uses `.getOrDefault("unknown")` as the last resort, which makes it impossible to tell whether the loop ended cleanly or via an unhandled error. Effective Kotlin item 7. Fix: model the close reason explicitly as a sealed class `data class Clean(val reason: String) : CloseOutcome; data class Failed(val cause: Throwable) : CloseOutcome` and emit one of the two.

### 17. Top-level `private val` colour constants placed at the bottom of the file

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:605-608` declares `TIER_COLOR_NEUTRAL` etc. as file-level `private val` AFTER the classes that use them. Kotlin coding conventions: top-level constants and their kin go in a `companion object` of the primary class or at the top of the file under the imports. Not a bug; just feels backwards on first read.

### 18. `Thread(...) { ... }.apply { isDaemon = true }.start()` × N

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatPanel.kt:574-617` (the inline `Thread(...) { ... }.apply { isDaemon = true }.start()` for chat send), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:633-637` (`runInBackground` helper), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingFlow.kt:150-171` (`Thread({...}, "osrsllm-pairing-poll").apply { isDaemon = true }`). The cloud surface elsewhere uses coroutines. Mixing raw `Thread` for background blocking work with `runBlocking` inside the same coroutine-scoped class signals two different async paradigms living in the same module. Effective Kotlin item 26: pick one. Fix: keep the EDT `SwingWorker` (it is the right primitive for Swing) but route the inline `Thread { ... }.start()` patterns through it too, or expose a single `runInBackground` utility.

### 19. `error()` thrown from a public surface method that the caller has no way to recover from

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatStore.kt:104` (`val chat = read(id) ?: error("chat $id not found")`). `appendMessage` is on the public surface; a caller that holds a stale id (e.g. after a delete fired) gets an unhandleable `IllegalStateException`. Effective Kotlin item 7 (prefer Result-like types for expected failure). Fix: return `Chat?` or a sealed `AppendResult`.

### 20. `onFailure { log.warn(..., it.message) }` loses the stack

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:268, 275, 299`: pattern `runCatching { egress(payload) }.onFailure { log.warn("... egress failed: {}", it.message) }`. detekt's `SwallowedException` flags this exactly: `it.message` may be `null` in which case the log line reads "... egress failed: null" with no stack. Fix: log with the throwable, not its `.message`: `log.warn("egress failed", it)`.

## Nits

### N1. `runCatching { ... }.getOrNull()` chains with no other callback

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:165, 240`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:246, 274, 290, 354`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/integrations/SlayerIntegration.kt:32-45`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/integrations/PartyIntegration.kt:40-57`. When you neither log the failure nor distinguish it from success, a plain `try { ... } catch { null }` is more honest about intent. Or, more idiomatically: define a single `safeCall(block: () -> T?): T?` and pin the surface there.

### N2. Mixed `it.takeIf { ... }?.let { ... }` chains where a single `?.takeIf { ... }` would do

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/HarnessContext.kt:121, 132, 146`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingFlow.kt:124-125`. Not wrong; just longer than it needs to be.

### N3. Local extension `contentOrNull` shadows a stdlib helper

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:171, 327-328` chains `(obj["url"] as? JsonPrimitive)?.contentOrNull()` where `contentOrNull` is the extension defined at the bottom of the file with the same name as a stdlib utility. Risk of confusion. Rename the local helper or use `jsonPrimitive.contentOrNull` directly.

### N4. `String.format("Expires in %d:%02d", ...)` in a Kotlin file

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingModal.kt:108`. Idiomatic Kotlin uses `"Expires in $mins:%02d".format(secs)` or the `formatString` extension. Trivial taste.

### N5. `MutableMap`/`MutableList` exposed as type in private fields

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatPanel.kt:72, 74`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/PairingFlow.kt:41`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ToolGatingTelemetry.kt:46-58`. Kotlin idiom: declare as `val` of a `MutableList<...>` interface only when the local code needs the mutable interface; otherwise declare as `List` and back with a `mutableListOf`. Effective Kotlin item 1.

### N6. `companion object { const val CAPACITY: Int = 200 }` style varies

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AuditLog.kt:44`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/NetworkAuditLogger.kt:73`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DeviceKey.kt:73-76`. Some have `const val` with type, some do not; some are `Int`, some `String`. Minor consistency call. JetBrains conventions allow omitting the type when it is obvious; pick one and apply it.

### N7. `JvmField`, `JvmStatic` decoration consistency

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/byo/ByoProvider.kt:220-235` decorates `all` and `JSON` with `@JvmStatic`. `ChatMode.kt` does too. Neither has a Java caller in this module. Effective Kotlin item 30: drop the annotations until a Java caller actually exists.

### N8. KDoc capitalisation and full-stop usage drifts

Across `cloud/*` some KDoc paragraphs end with a full-stop, others do not; some sentences open lowercase. Minor; consistent style helps the maintainer skim.

### N9. `getOrDefault` on a Result vs explicit fallback

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:141`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountSummaryClient.kt:327-328` (private extension), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/chat/ChatStore.kt:172-177`. `runCatching { ... }.getOrDefault(empty)` reads as a half-step. Replace with explicit `try` / `catch` once you commit to fixing Important 3.

### N10. `@Synchronized` on every public method of a `@Singleton`

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AuditLog.kt`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/NetworkAuditLogger.kt`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendWsClient.kt:79-84, 104-110, 159`. Works, but the JetBrains style guide prefers a private `lock` object plus `synchronized(lock) { ... }` for fine-grained control. Optional.

### N11. `String.startsWith` then `substring` arithmetic

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/EgressGate.kt:303-313` (`toHttpsUrl`) hand-rolls substring arithmetic to swap `wss://` for `https://`. `removePrefix(BackendUrl.WSS_PREFIX)` plus a re-prefix reads more cleanly.

### N12. `runCatching { transport.setListener(null) }` belt-and-braces

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:209-212`, `apps/plugin/src/main/kotlin/co/rowm/osrsllm/plugin/OsrsLlmHelperPlugin.kt:432-489`. The functions cannot throw on their own contract; wrapping defends against a future regression but reads as paranoid. Fix: drop or keep with a comment.

### N13. Trivial single-expression functions should use expression body

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AuditLog.kt:38, 41` (`fun entries(): List<Entry> = buffer.toList()` is fine), `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/NetworkAuditLogger.kt:62-67` (mixed). `BackendUrl.toString()` at `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendUrl.kt:28` is fine. Audit for the few non-expression single-return methods.

### N14. Trailing-comma policy is inconsistent

Compare `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/CloudChatRunner.kt:286-296` (trailing comma) with `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:215-221` (no trailing comma). JetBrains style guide as of 2024 recommends them; pick one and rely on the formatter.

## Effective Kotlin pass matrix

| Item | Topic | Result | Reference |
|---|---|---|---|
| 1 | Limit mutability (val over var, immutable collections) | mostly passes | Nit N5; Important 13 |
| 2 | Minimize scope of variables | passes | Important 4 (lateinit guard noise) |
| 3 | Eliminate platform types ASAP | passes | `client.localPlayer?.name` style is consistent |
| 4 | Do not expose inferred types in public API | passes | DTOs and sealed types are explicit |
| 5 | Specify expectations with require, check, error | mostly passes | Important 8 prefers `check` over `error` |
| 7 | Prefer null or Result-like to exceptions for expected failure | fails | Important 3, 6, 16, 19, 20 |
| 8 | Handle nulls properly; no reckless !! | passes | no `!!` in cloud surface; Important 2 and 4 are async-related |
| 9 | Close resources with `use` | passes | `outputStream.use`, `bufferedReader().use` in EgressGate |
| 19 | Do not repeat knowledge | fails | Important 1 (device key), Important 10 (Json), Important 11 (logger boilerplate) |
| 26 | Single level of abstraction per function | mixed | Important 12 (hex loop), Important 15 (frame loop), Nit N2 |
| 30 | Minimize element visibility | fails | Important 9 (redundant `public`) |

## detekt rule pass matrix (simulated)

| Rule family / rule | Result | Reference |
|---|---|---|
| complexity: LongMethod | passes | longest methods are clearly partitioned (`startUp`, `egressHttp`) |
| complexity: LongParameterList | borderline | `CloudChatRunner` ctor and `DirectChatRunner` ctor have 8+ params; acceptable for DI roots |
| complexity: TooManyFunctions | passes | files stay focused |
| potential-bugs: UnsafeCallOnNullableType | passes | no `!!` use in production sources |
| potential-bugs: UnreachableCode | passes | none observed |
| style: ForbiddenComment | passes | no TODO / FIXME observed in cloud surface |
| style: MagicNumber | passes | constants are named (`CAPACITY`, `LENGTH`, `CONNECT_TIMEOUT_MS`) |
| style: ReturnCount | borderline | `BackendWsClient.connectInternal` and `DirectChatRunner.runRequest` have many early returns; readable in context |
| style: MaxLineLength | borderline | a few KDoc lines and `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt:536` exceed 120 chars |
| style: UnusedPrivateMember | fails | `CloudChatRunner.json` at line 65 declared, never referenced (Important 10) |
| exceptions: TooGenericExceptionCaught | fails | Important 5, 6 |
| exceptions: SwallowedException | fails | Important 3, 20 |
| exceptions: ThrowingExceptionInMain | passes | `throw GradleException` is in `build.gradle.kts` only |
| exceptions: PrintStackTrace | passes | none observed |
| naming: VariableNaming / FunctionNaming / ClassNaming / PackageNaming | passes | Kotlin idiom throughout |
| empty-blocks: EmptyFunctionBlock | passes | default-method bodies in `Callbacks` interfaces are intentional no-op stubs |
| empty-blocks: EmptyCatchBlock | passes | every catch logs or returns |

## Things I checked and was happy with

- Effective Kotlin Safety / Readability: `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/BackendUrl.kt` is a textbook value class with an `init` invariant and a `redact()` helper. `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ConsentState.kt` correctly uses an `AtomicReference` with `@Synchronized` `freeze` and a `check` precondition (item 5). `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/InboundMessage.kt` and `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/OutboundPayload.kt` are exemplary sealed-class hierarchies with `@SerialName` discriminators and per-subtype `data class`. `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/ReconnectStrategy.kt` uses `require` for all three preconditions (item 5). No `!!` operator appears anywhere in the cloud surface (item 8). `apps/plugin/src/main/kotlin/co/rowm/osrsllm/ChatMode.kt` and `apps/plugin/src/main/kotlin/co/rowm/osrsllm/ChatModeChoice.kt` separate domain (`ChatMode` sealed) from UI choice (`ChatModeChoice` enum) cleanly.
- detekt: no `printStackTrace()` call anywhere; no `System.out.println` anywhere; `apps/plugin/build.gradle.kts:237-281` already encodes `checkNoHttpServer`, `checkNoReflection`, `checkNoSubprocess`, `checkNoPlaintextUrls` gates so the `ExitOutsideMain`-style hub blockers are covered by build-time greps. No empty catch blocks observed. No reflection or class-loader escape hatches.
- JetBrains conventions: imports are sorted; no wildcard `import` other than the static `Json` builder pattern. Lambda style is consistent. `data class` is used wherever it should be. `internal` is correctly used to scope `BackendWsClient.connectLoopbackForTest` to the package.
- RuneLite hub style: no `printStackTrace`, no plaintext URL literals (gated), no `ServerSocket`, `Netty`, or `ProcessBuilder` references (gated). Copyright headers are not RuneLite-style consistent across the kotlin tree, but that is a separate doc-comment hat concern. Imports are alphabetised in every file I sampled.

## Recommended fix order

1. Important 1: delete `OsrsLlmHelperPlugin.deviceKeyForAuth()` and inject `DeviceKey`. Single most maintainer-visible "this person knows the codebase" win.
2. Important 3: replace blanket `runCatching` swallows in `cloud/*` with narrowed `try` / `catch` on specific exception types, especially in `AccountSummaryClient`.
3. Important 6: narrow every `catch (t: Throwable)` to `Exception` plus an explicit `is CancellationException -> throw` re-throw on coroutine surfaces.
4. Important 9: strip every redundant `public` keyword across `cloud/*` and `ChatMode.kt`. Tighten `Callbacks`, `HttpTransport`, `SendResult` to `internal` where the call site is in-module.
5. Important 2 plus 7: lift `runBlocking` out of `EgressGate.egress` and `CloudChatRunner.send` by making them `suspend fun`, with a single bridge at the chat-panel / `SwingWorker` boundary.
