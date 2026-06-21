package co.rowm.osrsllm.cloud

import co.rowm.osrsllm.ChatMode
import co.rowm.osrsllm.cloud.byo.ByoProvider
import co.rowm.osrsllm.cloud.byo.ChatRequest
import co.rowm.osrsllm.cloud.byo.ChatResponse
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tier-2 BYO chat runner — drives a chat turn DIRECTLY against the
 * player's choice of Anthropic / OpenAI / OpenRouter using the player's
 * own API key. Sibling to [CloudChatRunner].
 *
 * Lifecycle
 * ─────────
 *   1. [start] flips the runner into the started state. It performs NO
 *      network IO — there is no persistent connection in the BYO path,
 *      every [send] opens a fresh HTTPS request.
 *   2. [send] reads the (key, mode, model, consent) suppliers, picks
 *      the [ByoProvider] from the mode, builds a [ChatRequest], encodes
 *      to provider JSON, posts via [EgressGate.egressHttp], parses the
 *      reply, and fires [Callbacks.onAssistantMessage].
 *   3. [stop] flips the runner back. There is nothing to tear down.
 *
 * Security posture
 * ────────────────
 *   - The API key is read from [keySupplier] ONCE per [send] call, kept
 *     in a local val, attached as the provider-specific auth header by
 *     [ByoProvider.authHeader], and then dropped. It is never stored on
 *     the runner instance, never serialized, never logged, never
 *     surfaced in callbacks.
 *   - The audit row written by [EgressGate.egressHttp] records ONLY
 *     `Http:POST <host><path> size=<bytes>`. The key value never reaches
 *     [AuditLog].
 *   - Any [Throwable] that escapes is re-thrown WITHOUT a `.cause` chain
 *     constructed from the call site — the underlying HttpURLConnection
 *     exception message includes only the URL + cause, never headers.
 *   - On a missing key the runner short-circuits to `byok_no_key` BEFORE
 *     any network call. There is no "trial the API and see if it fails"
 *     path.
 *
 * Wire-in
 * ───────
 *   The plugin's `startUp()` constructs ONE DirectChatRunner when
 *   `consentAccepted = on` AND `chatMode.isByo`. The runner is held in
 *   the same lifecycle slot as [CloudChatRunner] and stopped from the
 *   same `shutDown()` symmetric tear-down.
 *
 * See `apps/plugin/docs/CONFIG.md` and `DATA_DISCLOSURE.md` §D-quater
 * for the player-facing description of what bytes leave the plugin in
 * BYO mode.
 */
public class DirectChatRunner(
    private val egressGate: EgressGate,
    private val keySupplier: () -> String,
    private val modeSupplier: () -> ChatMode,
    private val modelSupplier: () -> String,
    private val consentSupplier: () -> ConsentState,
    private val telemetryOptInSupplier: () -> Boolean,
    private val callbacks: Callbacks,
    /**
     * Test seam: clock used for the latency telemetry ping. Tests inject
     * a fake to make the ping payload deterministic.
     */
    private val clock: () -> Long = { System.currentTimeMillis() },
    /**
     * Test seam: HTTP transport. Defaults to [egressGate]. Production must
     * always use the default — the test override exists so unit tests can
     * verify the request shape WITHOUT touching the real allow-list path,
     * and to assert that the allow-list path IS used in the EgressGate test.
     */
    private val transport: HttpTransport = HttpTransport.ofEgressGate(egressGate),
) {

    private val log = LoggerFactory.getLogger(DirectChatRunner::class.java)
    private val running = AtomicBoolean(false)

    /**
     * Hooks fired from [send]. Implementers must marshal back to the EDT
     * before touching Swing components.
     */
    public interface Callbacks {

        /** A complete assistant reply has arrived. */
        public fun onAssistantMessage(response: ChatResponse) {}

        /**
         * The runner refused or failed a turn. `code` is one of the stable
         * machine-readable strings below; `message` is human-readable and
         * SAFE TO RENDER — it never includes the API key.
         *
         * Codes:
         *  - `byok_no_key` — no API key configured for the active BYO mode.
         *  - `byok_not_byo_mode` — runner asked to send in a non-BYO mode.
         *  - `byok_consent_off` — consent toggle is off.
         *  - `byok_runner_stopped` — [start] was not called or [stop] ran.
         *  - `byok_host_blocked` — host fell outside the egress allow-list.
         *  - `byok_http_<status>` — provider responded non-2xx.
         *  - `byok_provider_error` — provider response failed to parse.
         *  - `byok_network_error` — IO or unknown transport failure.
         */
        public fun onError(code: String, message: String) {}

        /** Optional progress hook — fires before the HTTP call. */
        public fun onTurnStarted(provider: String, model: String) {}
    }

    /** Result of [send] — mirrors [CloudChatRunner.SendResult] for caller parity. */
    public data class SendResult(
        val ok: Boolean,
        val text: String,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
    )

    /**
     * Tiny indirection over [EgressGate.egressHttp] so the unit test can
     * pre-populate a canned response without constructing a real
     * [EgressGate] (which depends on an [AuditLog] in tests where the
     * audit-log assertion is the whole point).
     */
    public interface HttpTransport {
        public fun post(
            host: String,
            path: String,
            headers: List<Pair<String, String>>,
            body: String,
        ): EgressGate.HttpEgressResponse

        public companion object {
            @JvmStatic
            public fun ofEgressGate(gate: EgressGate): HttpTransport = object : HttpTransport {
                override fun post(
                    host: String,
                    path: String,
                    headers: List<Pair<String, String>>,
                    body: String,
                ): EgressGate.HttpEgressResponse =
                    gate.egressHttp(host = host, path = path, method = "POST", headers = headers, body = body)
            }
        }
    }

    /** Start the runner. Idempotent. Performs no network IO. */
    public fun start() {
        if (!running.compareAndSet(false, true)) {
            log.debug("DirectChatRunner already running")
            return
        }
        log.info("DirectChatRunner started")
    }

    /** Stop the runner. Idempotent. */
    public fun stop() {
        if (!running.compareAndSet(true, false)) return
        log.info("DirectChatRunner stopped")
    }

    /** Convenience for callers that want to know whether [start] has been called. */
    public fun isRunning(): Boolean = running.get()

    /**
     * Send a single user turn through the configured BYO provider. Blocking
     * — runs the HTTP request on the calling thread. The chat panel
     * already invokes its backends on a worker thread.
     */
    public fun send(systemPrompt: String?, userMessage: String): SendResult {
        if (!running.get()) {
            return error("byok_runner_stopped", "Runner not started.")
        }
        val consent = consentSupplier()
        if (!consent.accepted) {
            return error("byok_consent_off", "Consent toggle is off — refusing BYO chat.")
        }
        val mode = modeSupplier()
        val provider = pickProvider(mode)
            ?: return error(
                "byok_not_byo_mode",
                "Active chat mode (${mode::class.simpleName}) is not a BYO mode.",
            )

        // Read the key into a LOCAL val so it cannot leak via the field set.
        // Trim because a paste from a provider dashboard often grabs a trailing
        // newline — that would otherwise fail Authorization-header validation.
        val key = keySupplier().trim()
        if (key.isBlank()) {
            return error(
                "byok_no_key",
                "No API key configured for BYO mode. Set it in the RuneLite config.",
            )
        }

        val model = modelSupplier().trim().ifBlank { provider.defaultModel }
        val request = ChatRequest(
            model = model,
            systemPrompt = systemPrompt,
            userMessage = userMessage,
        )

        runCatching { callbacks.onTurnStarted(provider.displayName, model) }

        val body = provider.encodeRequest(request)
        val (authName, authValue) = provider.authHeader(key)
        val headers = mutableListOf<Pair<String, String>>()
        headers.add(authName to authValue)
        headers.addAll(provider.extraHeaders())

        val startedAtMs = clock()
        val result = runRequest(provider = provider, headers = headers, body = body, key = key)

        // Fire-and-forget telemetry — opt-in, content-free. We do it AFTER
        // building the result so a telemetry failure can't poison the
        // assistant reply.
        val latencyMs = clock() - startedAtMs
        if (telemetryOptInSupplier()) {
            runCatching {
                sendTelemetry(provider = provider, model = model, ok = result.ok, latencyMs = latencyMs)
            }.onFailure { log.debug("BYO telemetry ping failed: {}", it.message) }
        }
        return result
    }

    /**
     * Inner driver — split out of [send] so the request/response branches
     * can use plain `return`s instead of `try`-labelled blocks. The [key]
     * is passed in for redaction only; it never lands on the result.
     */
    private fun runRequest(
        provider: ByoProvider,
        headers: List<Pair<String, String>>,
        body: String,
        key: String,
    ): SendResult {
        val response = try {
            transport.post(
                host = provider.host,
                path = provider.path,
                headers = headers,
                body = body,
            )
        } catch (e: EgressGate.EgressBlockedException) {
            // Belt-and-braces: a refactor that swaps providers but forgets to
            // widen the allow-list lands here. The key is NOT in the
            // exception message — EgressGate constructs it with only the host.
            val message = redactKey(raw = e.message ?: "host blocked", key = key)
            runCatching { callbacks.onError("byok_host_blocked", message) }
            return SendResult(
                ok = false, text = "",
                errorCode = "byok_host_blocked", errorMessage = message,
            )
        } catch (t: Throwable) {
            val message = redactKey(
                raw = "Network error: ${t.message ?: t::class.simpleName ?: "unknown"}",
                key = key,
            )
            runCatching { callbacks.onError("byok_network_error", message) }
            return SendResult(
                ok = false, text = "",
                errorCode = "byok_network_error", errorMessage = message,
            )
        }

        if (response.status !in 200..299) {
            val code = "byok_http_${response.status}"
            val message = redactKey(
                raw = "Provider returned HTTP ${response.status}.",
                key = key,
            )
            runCatching { callbacks.onError(code, message) }
            return SendResult(
                ok = false, text = "",
                errorCode = code, errorMessage = message,
            )
        }

        val parsed = try {
            provider.decodeResponse(response.body)
        } catch (t: Throwable) {
            val message = redactKey(
                raw = "Provider response failed to parse: ${t.message ?: "unknown"}",
                key = key,
            )
            runCatching { callbacks.onError("byok_provider_error", message) }
            return SendResult(
                ok = false, text = "",
                errorCode = "byok_provider_error", errorMessage = message,
            )
        }
        runCatching { callbacks.onAssistantMessage(parsed) }
        return SendResult(
            ok = true,
            text = parsed.text,
            inputTokens = parsed.inputTokens,
            outputTokens = parsed.outputTokens,
        )
    }

    /**
     * Fire-and-forget anonymous usage ping. Per `apps/plugin/docs/CONFIG.md`
     * §"Share anonymous BYO usage telemetry": no message content, no key,
     * no model id, no game state.
     *
     * NOTE: the telemetry endpoint isn't part of the BYO host allow-list
     * because it's a Tibbly-side service. We post via a separate
     * (no-op-in-tests) helper so the failure mode is silent and the BYO
     * audit-shield test doesn't care.
     */
    private fun sendTelemetry(provider: ByoProvider, model: String, ok: Boolean, latencyMs: Long) {
        // The body INTENTIONALLY does not include `model` or any user input.
        // We forward provider + ok + latency only — exactly what the docs
        // promise the player. The unused params are retained on the
        // signature so a future "send model name" change must touch this
        // doc comment as well.
        @Suppress("UNUSED_PARAMETER")
        val unused = model

        // We don't have a stable BYO-telemetry endpoint yet; logging at
        // debug serves as a placeholder so the user-visible behaviour is
        // off-by-default-AND-quiet. A future PR wires this to a Tibbly
        // host added to the cloud allow-list.
        log.debug(
            "BYO telemetry: provider={} ok={} latencyMs={}",
            provider.displayName, ok, latencyMs,
        )
    }

    private fun pickProvider(mode: ChatMode): ByoProvider? = when (mode) {
        is ChatMode.ByoAnthropic -> ByoProvider.Anthropic
        is ChatMode.ByoOpenAi -> ByoProvider.OpenAi
        is ChatMode.ByoOpenRouter -> ByoProvider.OpenRouter
        is ChatMode.Cloud, is ChatMode.ToolsOnly -> null
    }

    private fun error(code: String, message: String): SendResult {
        runCatching { callbacks.onError(code, message) }
        return SendResult(ok = false, text = "", errorCode = code, errorMessage = message)
    }

    /**
     * Final-mile redaction: replace any occurrence of the literal key
     * value with `<redacted>` in a string we're about to surface. Belt
     * and braces — the provider response shouldn't echo the key, but a
     * misconfigured server could, and we don't want it landing in the
     * chat bubble.
     */
    private fun redactKey(raw: String, key: String): String {
        if (key.length < 4) return raw
        return raw.replace(key, "<redacted>")
    }
}
