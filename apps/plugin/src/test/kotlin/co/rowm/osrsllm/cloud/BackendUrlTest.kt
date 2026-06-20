package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * RAI-38 — invariants on BackendUrl's value-class constructor. These are the
 * static guarantees that make the "no plaintext leaves this plugin" claim
 * provable rather than just plausible.
 */
class BackendUrlTest {

    @Test
    fun `accepts a wss url`() {
        val url = BackendUrl("wss://api.example.com/plugin")
        assertEquals("wss://api.example.com/plugin", url.value)
    }

    @Test
    fun `rejects plaintext http`() {
        assertRejects("http://api.example.com/plugin")
    }

    @Test
    fun `rejects plaintext https`() {
        // We require wss specifically; an https URL is not a WebSocket URL.
        assertRejects("https://api.example.com/plugin")
    }

    @Test
    fun `rejects plaintext ws`() {
        assertRejects("ws://api.example.com/plugin")
    }

    @Test
    fun `rejects empty host`() {
        assertRejects(BackendUrl.WSS_PREFIX)
    }

    @Test
    fun `rejects blank`() {
        assertRejects("")
    }

    @Test
    fun `rejects spoof prefix`() {
        // Defensive: leading whitespace shouldn't be parsed-out.
        assertRejects("  wss://api.example.com/plugin")
    }

    private fun assertRejects(raw: String) {
        try {
            BackendUrl(raw)
            fail("Expected BackendUrl('$raw') to throw, but it did not")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "exception message should mention the prefix or empty host, was: ${e.message}",
                (e.message ?: "").contains("wss") || (e.message ?: "").contains("empty"),
            )
        }
    }
}
