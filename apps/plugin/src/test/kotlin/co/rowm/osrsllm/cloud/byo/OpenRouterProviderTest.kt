package co.rowm.osrsllm.cloud.byo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips OpenRouter request/response shapes. Since the wire shape is
 * OpenAI-compatible the focus here is on the host/path + the Tibbly
 * branding headers OpenRouter asks for.
 */
class OpenRouterProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `host and path match documented endpoint`() {
        assertEquals("openrouter.ai", ByoProvider.OpenRouter.host)
        assertEquals("/api/v1/chat/completions", ByoProvider.OpenRouter.path)
    }

    @Test
    fun `auth header uses bearer scheme and extras carry tibbly branding`() {
        val (name, value) = ByoProvider.OpenRouter.authHeader("sk-LEAK-SENTINEL-router")
        assertEquals("Authorization", name)
        assertEquals("Bearer sk-LEAK-SENTINEL-router", value)
        val extras = ByoProvider.OpenRouter.extraHeaders().toMap()
        assertTrue("expected HTTP-Referer", extras.containsKey("HTTP-Referer"))
        assertTrue("expected X-Title", extras.containsKey("X-Title"))
    }

    @Test
    fun `request body shape matches openai compat`() {
        val body = ByoProvider.OpenRouter.encodeRequest(
            ChatRequest(
                model = "anthropic/claude-haiku-4-5",
                systemPrompt = "OSRS helper.",
                userMessage = "Cox or ToB?",
            ),
        )
        val parsed = json.parseToJsonElement(body).jsonObject
        assertEquals("anthropic/claude-haiku-4-5", parsed["model"]!!.jsonPrimitive.contentOrNull)
        assertTrue(parsed.containsKey("messages"))
        assertTrue(parsed.containsKey("max_tokens"))
    }

    @Test
    fun `response parses openai compat shape`() {
        val body = """
            {
              "choices": [
                { "message": { "role": "assistant", "content": "Depends on team size." }, "finish_reason": "stop" }
              ],
              "usage": { "prompt_tokens": 5, "completion_tokens": 4 }
            }
        """.trimIndent()
        val response = ByoProvider.OpenRouter.decodeResponse(body)
        assertEquals("Depends on team size.", response.text)
        assertEquals(5L, response.inputTokens)
        assertEquals(4L, response.outputTokens)
    }
}
