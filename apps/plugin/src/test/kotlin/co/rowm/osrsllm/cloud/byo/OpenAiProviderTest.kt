package co.rowm.osrsllm.cloud.byo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips OpenAI Chat Completions request/response shapes.
 */
class OpenAiProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request body carries model max_tokens and messages with system as first role`() {
        val body = ByoProvider.OpenAi.encodeRequest(
            ChatRequest(
                model = "gpt-4o-mini",
                systemPrompt = "You are an OSRS assistant.",
                userMessage = "Where do I get a rune scimitar?",
                maxTokens = 128,
            ),
        )
        val parsed = json.parseToJsonElement(body).jsonObject
        assertEquals("gpt-4o-mini", parsed["model"]!!.jsonPrimitive.contentOrNull)
        assertEquals(128, parsed["max_tokens"]!!.jsonPrimitive.int)
        val messages = parsed["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        val systemMsg = messages[0].jsonObject
        assertEquals("system", systemMsg["role"]!!.jsonPrimitive.contentOrNull)
        assertEquals("You are an OSRS assistant.", systemMsg["content"]!!.jsonPrimitive.contentOrNull)
        val userMsg = messages[1].jsonObject
        assertEquals("user", userMsg["role"]!!.jsonPrimitive.contentOrNull)
        assertEquals("Where do I get a rune scimitar?", userMsg["content"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `request body omits system message when prompt is null or blank`() {
        val body = ByoProvider.OpenAi.encodeRequest(
            ChatRequest(model = "gpt-4o-mini", systemPrompt = null, userMessage = "hi"),
        )
        val parsed = json.parseToJsonElement(body).jsonObject
        val messages = parsed["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `response parses first choice message content and usage`() {
        val body = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": { "role": "assistant", "content": "Try the Varrock sword shop." },
                  "finish_reason": "stop"
                }
              ],
              "usage": { "prompt_tokens": 42, "completion_tokens": 7 }
            }
        """.trimIndent()
        val response = ByoProvider.OpenAi.decodeResponse(body)
        assertEquals("Try the Varrock sword shop.", response.text)
        assertEquals(42L, response.inputTokens)
        assertEquals(7L, response.outputTokens)
        assertEquals("stop", response.stopReason)
    }

    @Test
    fun `auth header uses bearer scheme`() {
        val (name, value) = ByoProvider.OpenAi.authHeader("sk-LEAK-SENTINEL-openai")
        assertEquals("Authorization", name)
        assertEquals("Bearer sk-LEAK-SENTINEL-openai", value)
        assertTrue("openai has no extra headers", ByoProvider.OpenAi.extraHeaders().isEmpty())
    }

    @Test
    fun `host and path match documented endpoint`() {
        assertEquals("api.openai.com", ByoProvider.OpenAi.host)
        assertEquals("/v1/chat/completions", ByoProvider.OpenAi.path)
    }
}
