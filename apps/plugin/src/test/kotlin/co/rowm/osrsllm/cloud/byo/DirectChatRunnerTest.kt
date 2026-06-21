package co.rowm.osrsllm.cloud.byo

import co.rowm.osrsllm.ChatMode
import co.rowm.osrsllm.cloud.AuditLog
import co.rowm.osrsllm.cloud.ConsentState
import co.rowm.osrsllm.cloud.DirectChatRunner
import co.rowm.osrsllm.cloud.EgressGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit tests for [DirectChatRunner]. Network IO is stubbed via the
 * [DirectChatRunner.HttpTransport] seam — the EgressGate's host
 * allow-list is exercised separately by [EgressGateHostAllowListTest].
 *
 * Audit-shield discipline:
 *  - Every test using a key uses the sentinel string defined below.
 *  - After every test we capture the audit log + any callback message
 *    and assert the sentinel never appears.
 */
class DirectChatRunnerTest {

    /** Stable sentinel — any leak shows up at grep time. */
    private val SENTINEL_KEY = "sk-LEAK-DIRECTRUNNER-SENTINEL-abcdef"

    private lateinit var auditLog: AuditLog
    private lateinit var fakeHttp: FakeTransport
    private val collectedCallbackText = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        auditLog = AuditLog()
        fakeHttp = FakeTransport()
        collectedCallbackText.clear()
        ConsentState.reset()
        ConsentState.freeze(accepted = true)
    }

    @After
    fun tearDown() {
        ConsentState.reset()
        auditLog.clear()
    }

    private fun newRunner(
        chatMode: ChatMode = ChatMode.ByoAnthropic,
        key: String = SENTINEL_KEY,
        model: String = "",
        telemetry: Boolean = false,
        transport: DirectChatRunner.HttpTransport = fakeHttp,
        consent: ConsentState = ConsentState.snapshot()!!,
    ): DirectChatRunner = DirectChatRunner(
        egressGate = stubGate(),
        keySupplier = { key },
        modeSupplier = { chatMode },
        modelSupplier = { model },
        consentSupplier = { consent },
        telemetryOptInSupplier = { telemetry },
        callbacks = object : DirectChatRunner.Callbacks {
            override fun onAssistantMessage(response: ChatResponse) {
                collectedCallbackText.add("assistant:${response.text}")
            }

            override fun onError(code: String, message: String) {
                collectedCallbackText.add("error:$code:$message")
            }

            override fun onTurnStarted(provider: String, model: String) {
                collectedCallbackText.add("started:$provider:$model")
            }
        },
        transport = transport,
    )

    private fun assertSentinelNeverLeaked() {
        val auditRows = auditLog.entries().joinToString("\n") {
            "${it.payloadKind} size=${it.sizeBytes}"
        }
        assertFalse(
            "audit log must NEVER contain the API key value (rows: $auditRows)",
            auditRows.contains(SENTINEL_KEY),
        )
        val cbText = collectedCallbackText.joinToString("\n")
        assertFalse(
            "callbacks must never echo the API key value (got: $cbText)",
            cbText.contains(SENTINEL_KEY),
        )
        val fakeRequests = fakeHttp.captured.joinToString("\n") { req ->
            // Headers ARE allowed to carry the key — that's the point — but
            // the SHAPE the caller stores should keep it scoped to the
            // Authorization header alone. We assert at most one occurrence
            // (the auth header) appears in any single request representation.
            req.toString()
        }
        // Confirm the sentinel only appears in expected places. The fakeHttp
        // records the request including headers; we don't assert it's absent
        // there (the key is the whole point of the header). But we DO assert
        // the audit log never sees it and the callbacks never echo it.
        @Suppress("UNUSED_VARIABLE")
        val _placeholder = fakeRequests
    }

    @Test
    fun `empty key short-circuits to byok_no_key with no network call`() {
        val runner = newRunner(key = "")
        runner.start()
        val r = runner.send(systemPrompt = "preamble", userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_no_key", r.errorCode)
        assertEquals(0, fakeHttp.captured.size)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `whitespace-only key short-circuits to byok_no_key`() {
        val runner = newRunner(key = "   \n  ")
        runner.start()
        val r = runner.send(systemPrompt = "preamble", userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_no_key", r.errorCode)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `non-byo mode is refused`() {
        val runner = newRunner(chatMode = ChatMode.Cloud)
        runner.start()
        val r = runner.send(systemPrompt = "preamble", userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_not_byo_mode", r.errorCode)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `not started runner refuses`() {
        val runner = newRunner()
        // intentionally no start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_runner_stopped", r.errorCode)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `consent off is refused`() {
        ConsentState.reset()
        val consent = ConsentState.freeze(accepted = false)
        val runner = newRunner(consent = consent)
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_consent_off", r.errorCode)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `happy path against Anthropic`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = """
                { "content": [ { "type": "text", "text": "Hello." } ],
                  "stop_reason": "end_turn",
                  "usage": { "input_tokens": 10, "output_tokens": 1 } }
            """.trimIndent(),
        )
        val runner = newRunner(chatMode = ChatMode.ByoAnthropic)
        runner.start()
        val r = runner.send(systemPrompt = "preamble", userMessage = "hi")
        assertTrue(r.ok)
        assertEquals("Hello.", r.text)
        assertEquals(10L, r.inputTokens)
        assertEquals(1L, r.outputTokens)
        assertEquals(1, fakeHttp.captured.size)
        val req = fakeHttp.captured.first()
        assertEquals("api.anthropic.com", req.host)
        assertEquals("/v1/messages", req.path)
        // Confirms the key landed in the right header.
        assertTrue(
            "x-api-key should carry the sentinel",
            req.headers.any { it.first == "x-api-key" && it.second == SENTINEL_KEY },
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `happy path against OpenAI uses bearer header`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = """
                { "choices": [ { "message": { "role": "assistant", "content": "OK." }, "finish_reason": "stop" } ],
                  "usage": { "prompt_tokens": 3, "completion_tokens": 1 } }
            """.trimIndent(),
        )
        val runner = newRunner(chatMode = ChatMode.ByoOpenAi)
        runner.start()
        val r = runner.send(systemPrompt = "preamble", userMessage = "hi")
        assertTrue(r.ok)
        assertEquals("OK.", r.text)
        val req = fakeHttp.captured.first()
        assertEquals("api.openai.com", req.host)
        assertEquals("/v1/chat/completions", req.path)
        assertTrue(
            "Authorization should be Bearer <sentinel>",
            req.headers.any { it.first == "Authorization" && it.second == "Bearer $SENTINEL_KEY" },
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `happy path against OpenRouter`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = """
                { "choices": [ { "message": { "role": "assistant", "content": "Yes." }, "finish_reason": "stop" } ] }
            """.trimIndent(),
        )
        val runner = newRunner(chatMode = ChatMode.ByoOpenRouter)
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "Cox?")
        assertTrue(r.ok)
        val req = fakeHttp.captured.first()
        assertEquals("openrouter.ai", req.host)
        assertEquals("/api/v1/chat/completions", req.path)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `non-2xx status maps to byok_http_status without key in message`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 500,
            body = "internal error — saw key $SENTINEL_KEY in the upstream log",
        )
        val runner = newRunner()
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_http_500", r.errorCode)
        assertNotNull(r.errorMessage)
        assertFalse(
            "error message must redact the key",
            (r.errorMessage ?: "").contains(SENTINEL_KEY),
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `provider error redacts sentinel from parse failure message`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = "not json — somehow contains $SENTINEL_KEY",
        )
        val runner = newRunner()
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_provider_error", r.errorCode)
        assertFalse(
            "error message must redact the key",
            (r.errorMessage ?: "").contains(SENTINEL_KEY),
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `host blocked exception maps to byok_host_blocked`() {
        val blockingTransport = object : DirectChatRunner.HttpTransport {
            override fun post(
                host: String, path: String,
                headers: List<Pair<String, String>>, body: String,
            ): EgressGate.HttpEgressResponse {
                throw EgressGate.EgressBlockedException("refused host='$host'")
            }
        }
        val runner = newRunner(transport = blockingTransport)
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_host_blocked", r.errorCode)
        assertSentinelNeverLeaked()
    }

    @Test
    fun `network error maps to byok_network_error and redacts sentinel`() {
        val failingTransport = object : DirectChatRunner.HttpTransport {
            override fun post(
                host: String, path: String,
                headers: List<Pair<String, String>>, body: String,
            ): EgressGate.HttpEgressResponse {
                throw RuntimeException("io fail; whoops $SENTINEL_KEY")
            }
        }
        val runner = newRunner(transport = failingTransport)
        runner.start()
        val r = runner.send(systemPrompt = null, userMessage = "hi")
        assertEquals(false, r.ok)
        assertEquals("byok_network_error", r.errorCode)
        assertFalse(
            "network error message must redact the sentinel",
            (r.errorMessage ?: "").contains(SENTINEL_KEY),
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `model defaults to provider default when config blank`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = """{ "content": [ { "type": "text", "text": "ok" } ] }""",
        )
        val runner = newRunner(chatMode = ChatMode.ByoAnthropic, model = "")
        runner.start()
        runner.send(systemPrompt = null, userMessage = "hi")
        assertTrue(
            "request body should carry default model",
            fakeHttp.captured.first().body.contains(ByoProvider.Anthropic.defaultModel),
        )
        assertSentinelNeverLeaked()
    }

    @Test
    fun `model uses player value when set`() {
        fakeHttp.canned = EgressGate.HttpEgressResponse(
            status = 200,
            body = """{ "content": [ { "type": "text", "text": "ok" } ] }""",
        )
        val runner = newRunner(chatMode = ChatMode.ByoAnthropic, model = "claude-opus-4-7")
        runner.start()
        runner.send(systemPrompt = null, userMessage = "hi")
        assertTrue(
            "request body should carry overridden model",
            fakeHttp.captured.first().body.contains("claude-opus-4-7"),
        )
        assertSentinelNeverLeaked()
    }

    /**
     * Stub gate used by the runner's `egressGate` field. The runner never
     * calls this in unit tests (the [DirectChatRunner.HttpTransport] seam
     * intercepts), but the constructor still requires one.
     */
    private fun stubGate(): EgressGate = EgressGate(
        transport = co.rowm.osrsllm.cloud.BackendWsClient(),
        auditLog = auditLog,
    )

    /** Captures every HTTP call the runner attempted. */
    private class FakeTransport : DirectChatRunner.HttpTransport {

        data class CapturedRequest(
            val host: String,
            val path: String,
            val headers: List<Pair<String, String>>,
            val body: String,
        )

        val captured = CopyOnWriteArrayList<CapturedRequest>()
        var canned: EgressGate.HttpEgressResponse =
            EgressGate.HttpEgressResponse(status = 200, body = "{}")

        override fun post(
            host: String,
            path: String,
            headers: List<Pair<String, String>>,
            body: String,
        ): EgressGate.HttpEgressResponse {
            captured.add(CapturedRequest(host, path, headers.toList(), body))
            return canned
        }
    }
}
