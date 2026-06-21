package co.rowm.osrsllm.cloud.byo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Tier-2 BYO chat provider abstraction.
 *
 * Each provider tells the [co.rowm.osrsllm.cloud.DirectChatRunner] how to
 * talk to a specific third-party LLM endpoint:
 *
 *  - `host` + `path` — the exact-match URL the runner will hit. The host
 *    is also asserted against the plugin-wide BYO host allow-list inside
 *    [co.rowm.osrsllm.cloud.EgressGate.egressHttp] so a refactor that
 *    accidentally widens the surface can't sneak by review.
 *  - `defaultModel` — used when the player leaves the BYO-model field
 *    blank. Picked to be the cheapest-but-capable per provider.
 *  - `encodeRequest` — builds the provider-specific JSON body from the
 *    normalized [ChatRequest].
 *  - `decodeResponse` — parses the provider's reply into the normalized
 *    [ChatResponse].
 *  - `authHeader` — names the HTTP header the API key is attached to,
 *    and how (raw vs `Bearer <key>`). Held here so each call site uses
 *    the right header for the right host.
 *
 * Adding a new provider means a new sealed subtype, an entry in the
 * runner's `pickProvider` switch, AND a row in `DATA_DISCLOSURE.md`
 * §D-quater. The host must also be added to
 * `EgressGate.BYO_ALLOWED_HOSTS`.
 */
public sealed class ByoProvider {

    /** Exact-match HTTPS host. NEVER a URL — only a host. */
    public abstract val host: String

    /** Request path with leading slash. */
    public abstract val path: String

    /** Model id used when the player leaves the config blank. */
    public abstract val defaultModel: String

    /** Stable name used in audit rows + telemetry pings. */
    public abstract val displayName: String

    /**
     * Build the provider-specific request body from a normalized
     * [ChatRequest]. The runner already resolved the model id.
     */
    public abstract fun encodeRequest(request: ChatRequest): String

    /**
     * Parse a successful HTTP 2xx response body into a normalized
     * [ChatResponse]. Throws on a malformed body so the runner can
     * surface the failure to the chat panel.
     */
    public abstract fun decodeResponse(responseBody: String): ChatResponse

    /**
     * Header name + value used to attach the API key. Held as a pair
     * rather than a raw header map so the runner can guarantee the
     * value is built consistently for the right provider.
     */
    public abstract fun authHeader(apiKey: String): Pair<String, String>

    /**
     * Optional extra headers the provider needs (e.g. Anthropic's
     * `anthropic-version`). Headers here MUST NOT contain the API key.
     */
    public open fun extraHeaders(): List<Pair<String, String>> = emptyList()

    /**
     * Anthropic Messages API. See https://docs.anthropic.com/en/api/messages.
     *
     * Anthropic distinguishes the system prompt from the message array. We
     * pack the system prompt the plugin built (via `HarnessContext`) into
     * the top-level `system` field and put the player's text in a single
     * user message.
     *
     * Auth header: `x-api-key: <key>`. Also requires `anthropic-version`.
     */
    public object Anthropic : ByoProvider() {

        public override val host: String = "api.anthropic.com"
        public override val path: String = "/v1/messages"
        public override val defaultModel: String = "claude-haiku-4-5"
        public override val displayName: String = "anthropic"

        /**
         * Pinned to the latest GA Messages API revision. Bump only after
         * a manual round-trip against the live endpoint.
         */
        private const val API_VERSION: String = "2023-06-01"
        private const val DEFAULT_MAX_TOKENS: Int = 1024

        public override fun encodeRequest(request: ChatRequest): String {
            val body = buildJsonObject {
                put("model", request.model)
                put("max_tokens", request.maxTokens ?: DEFAULT_MAX_TOKENS)
                if (request.systemPrompt != null && request.systemPrompt.isNotBlank()) {
                    put("system", request.systemPrompt)
                }
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", request.userMessage)
                            },
                        )
                    },
                )
            }
            return JSON.encodeToString(JsonObject.serializer(), body)
        }

        public override fun decodeResponse(responseBody: String): ChatResponse {
            val root = JSON.parseToJsonElement(responseBody).jsonObject
            // Anthropic shape: { content: [ { type: "text", text: "..." }, ... ], usage: { input_tokens, output_tokens } }
            val contentText = root["content"]?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("") ?: ""
            val usage = root["usage"]?.jsonObject
            val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.long
            val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.long
            val stopReason = root["stop_reason"]?.jsonPrimitive?.contentOrNull
            return ChatResponse(
                text = contentText,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                stopReason = stopReason,
            )
        }

        public override fun authHeader(apiKey: String): Pair<String, String> = "x-api-key" to apiKey

        public override fun extraHeaders(): List<Pair<String, String>> = listOf(
            "anthropic-version" to API_VERSION,
        )
    }

    /**
     * OpenAI Chat Completions API. See
     * https://platform.openai.com/docs/api-reference/chat.
     *
     * We use the legacy chat-completions path rather than the Responses
     * API because most BYO players already have keys provisioned for it
     * and the wire shape is easier to keep aligned with OpenRouter.
     *
     * Auth header: `Authorization: Bearer <key>`.
     */
    public object OpenAi : ByoProvider() {

        public override val host: String = "api.openai.com"
        public override val path: String = "/v1/chat/completions"
        public override val defaultModel: String = "gpt-4o-mini"
        public override val displayName: String = "openai"

        public override fun encodeRequest(request: ChatRequest): String =
            encodeOpenAiCompatRequest(request)

        public override fun decodeResponse(responseBody: String): ChatResponse =
            decodeOpenAiCompatResponse(responseBody)

        public override fun authHeader(apiKey: String): Pair<String, String> =
            "Authorization" to "Bearer $apiKey"
    }

    /**
     * OpenRouter (OpenAI-compatible) Chat Completions. See
     * https://openrouter.ai/docs.
     *
     * OpenRouter is a pass-through aggregator — same request/response
     * shape as OpenAI Chat Completions but with the model id including
     * a provider prefix (`anthropic/claude-sonnet-4-5`,
     * `openai/gpt-4o-mini`, etc.).
     *
     * Auth header: `Authorization: Bearer <key>`. We also send the
     * `HTTP-Referer` + `X-Title` headers OpenRouter asks for so usage
     * shows up correctly in their dashboard — neither carries player
     * data.
     */
    public object OpenRouter : ByoProvider() {

        public override val host: String = "openrouter.ai"
        public override val path: String = "/api/v1/chat/completions"
        public override val defaultModel: String = "anthropic/claude-haiku-4-5"
        public override val displayName: String = "openrouter"

        public override fun encodeRequest(request: ChatRequest): String =
            encodeOpenAiCompatRequest(request)

        public override fun decodeResponse(responseBody: String): ChatResponse =
            decodeOpenAiCompatResponse(responseBody)

        public override fun authHeader(apiKey: String): Pair<String, String> =
            "Authorization" to "Bearer $apiKey"

        public override fun extraHeaders(): List<Pair<String, String>> = listOf(
            "HTTP-Referer" to "https://tibbly.app",
            "X-Title" to "Tibbly RuneLite plugin",
        )
    }

    public companion object {

        /** Stable ordering for the BYO provider list. */
        @JvmStatic
        public val all: List<ByoProvider> = listOf(Anthropic, OpenAi, OpenRouter)

        /**
         * Shared Json instance for provider encode/decode. Lenient parsing so
         * a provider that adds a new field doesn't break the runner — the
         * fields we care about (`content[].text`, `choices[0].message.content`,
         * `usage.*`) are stable.
         */
        @JvmStatic
        internal val JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            isLenient = true
        }

        private const val DEFAULT_MAX_TOKENS_COMPAT: Int = 1024

        /**
         * OpenAI Chat Completions request body. Used by both [OpenAi] and
         * [OpenRouter] — the wire shape is identical, only the model id
         * convention differs and that's resolved upstream.
         */
        private fun encodeOpenAiCompatRequest(request: ChatRequest): String {
            val body = buildJsonObject {
                put("model", request.model)
                put("max_tokens", request.maxTokens ?: DEFAULT_MAX_TOKENS_COMPAT)
                put(
                    "messages",
                    buildJsonArray {
                        if (request.systemPrompt != null && request.systemPrompt.isNotBlank()) {
                            add(
                                buildJsonObject {
                                    put("role", "system")
                                    put("content", request.systemPrompt)
                                },
                            )
                        }
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", request.userMessage)
                            },
                        )
                    },
                )
            }
            return JSON.encodeToString(JsonObject.serializer(), body)
        }

        /**
         * OpenAI Chat Completions response shape:
         *   { choices: [ { message: { role, content }, finish_reason } ],
         *     usage: { prompt_tokens, completion_tokens } }
         */
        private fun decodeOpenAiCompatResponse(responseBody: String): ChatResponse {
            val root = JSON.parseToJsonElement(responseBody).jsonObject
            val firstChoice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val message = firstChoice?.get("message")?.jsonObject
            val text = message?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
            val finishReason = firstChoice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
            val usage = root["usage"]?.jsonObject
            val promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.long
            val completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.long
            return ChatResponse(
                text = text,
                inputTokens = promptTokens,
                outputTokens = completionTokens,
                stopReason = finishReason,
            )
        }
    }
}
