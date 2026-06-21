package co.rowm.osrsllm.cloud.byo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips Anthropic provider request/response shapes.
 *
 * Pinned against the documented Messages API shape; if Anthropic ships a
 * breaking response change these tests fail loudly and the plugin
 * upgrade story has a single place to look (this test + ByoProvider.kt).
 */
class AnthropicProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request body carries model max_tokens system and messages`() {
        val provider = ByoProvider.Anthropic
        val body = provider.encodeRequest(
            ChatRequest(
                model = "claude-haiku-4-5",
                systemPrompt = "You are helping with OSRS.",
                userMessage = "What's the GE price of a ranarr seed?",
                maxTokens = 256,
            ),
        )
        val parsed = json.parseToJsonElement(body).jsonObject

        assertEquals("claude-haiku-4-5", parsed["model"]!!.jsonPrimitive.contentOrNull)
        assertEquals(256, parsed["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("You are helping with OSRS.", parsed["system"]!!.jsonPrimitive.contentOrNull)
        val messages = parsed["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val firstMsg = messages.first().jsonObject
        assertEquals("user", firstMsg["role"]!!.jsonPrimitive.contentOrNull)
        assertEquals("What's the GE price of a ranarr seed?", firstMsg["content"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `request body omits system when prompt is blank`() {
        val body = ByoProvider.Anthropic.encodeRequest(
            ChatRequest(model = "claude-haiku-4-5", systemPrompt = "  ", userMessage = "hi"),
        )
        val parsed = json.parseToJsonElement(body).jsonObject
        assertNull("blank system prompt must be dropped", parsed["system"])
    }

    @Test
    fun `response parses text content and usage`() {
        val body = """
            {
              "id": "msg_01ABC",
              "type": "message",
              "role": "assistant",
              "content": [
                { "type": "text", "text": "Ranarr seed is currently 50,000 gp." }
              ],
              "stop_reason": "end_turn",
              "usage": { "input_tokens": 100, "output_tokens": 25 }
            }
        """.trimIndent()
        val response = ByoProvider.Anthropic.decodeResponse(body)
        assertEquals("Ranarr seed is currently 50,000 gp.", response.text)
        assertEquals(100L, response.inputTokens)
        assertEquals(25L, response.outputTokens)
        assertEquals("end_turn", response.stopReason)
    }

    @Test
    fun `response concatenates multi-block text content`() {
        val body = """
            {
              "content": [
                { "type": "text", "text": "Part one. " },
                { "type": "text", "text": "Part two." }
              ]
            }
        """.trimIndent()
        val response = ByoProvider.Anthropic.decodeResponse(body)
        assertEquals("Part one. Part two.", response.text)
    }

    @Test
    fun `auth header uses x-api-key and extra headers carry anthropic-version`() {
        val provider = ByoProvider.Anthropic
        val (name, value) = provider.authHeader("sk-LEAK-SENTINEL-anthropic")
        assertEquals("x-api-key", name)
        assertEquals("sk-LEAK-SENTINEL-anthropic", value)
        val extras = provider.extraHeaders().toMap()
        assertTrue("expected anthropic-version", extras.containsKey("anthropic-version"))
    }

    @Test
    fun `host and path match documented endpoint`() {
        assertEquals("api.anthropic.com", ByoProvider.Anthropic.host)
        assertEquals("/v1/messages", ByoProvider.Anthropic.path)
    }
}
