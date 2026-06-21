package co.rowm.osrsllm.cloud

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Tier-2 BYO host allow-list. The gate must refuse anything outside
 * [EgressGate.BYO_ALLOWED_HOSTS] BEFORE any socket is opened — the test
 * relies on the throw happening before connect().
 *
 * We do NOT make a real network call to the three allowed hosts in this
 * test (CI shouldn't talk to the public internet). Instead we assert
 * that the validation passes — the call only fails at the
 * HttpURLConnection.connect() stage, which we accept as "the allow-list
 * let it through".
 */
class EgressGateHostAllowListTest {

    private val auditLog = AuditLog()
    private lateinit var gate: EgressGate

    @Before
    fun setUp() {
        gate = EgressGate(transport = BackendWsClient(), auditLog = auditLog)
    }

    @After
    fun tearDown() {
        auditLog.clear()
    }

    @Test
    fun `allow-list contains exactly the three documented hosts`() {
        assertEquals(
            "BYO_ALLOWED_HOSTS must match DATA_DISCLOSURE.md §D-quater exactly",
            setOf("api.anthropic.com", "api.openai.com", "openrouter.ai"),
            EgressGate.BYO_ALLOWED_HOSTS,
        )
    }

    @Test
    fun `blocked host throws EgressBlockedException without audit row`() {
        try {
            gate.egressHttp(
                host = "evil.example.com",
                path = "/v1/messages",
                method = "POST",
                headers = emptyList(),
                body = "{}",
            )
            fail("expected EgressBlockedException")
        } catch (e: EgressGate.EgressBlockedException) {
            assertTrue(
                "exception message should mention the blocked host",
                (e.message ?: "").contains("evil.example.com"),
            )
        }
        assertEquals(
            "blocked host must not leave an audit row",
            0, auditLog.entries().size,
        )
    }

    @Test
    fun `every-non-allowlisted-host is blocked`() {
        val blocked = listOf(
            "api.anthropic.com.evil.com",     // suffix attack
            "evil.com.api.anthropic.com",     // prefix attack
            "api.OpenAi.com",                 // case mismatch
            "openrouter.ai/extra-path",       // hostname injection
            "anthropic.com",                  // dropped subdomain
            "127.0.0.1",                      // loopback
            "localhost",                      // loopback alias
            "169.254.169.254",                // cloud-metadata IMDS
        )
        for (host in blocked) {
            try {
                gate.egressHttp(
                    host = host, path = "/x", method = "POST",
                    headers = emptyList(), body = "{}",
                )
                fail("expected EgressBlockedException for host=$host")
            } catch (e: EgressGate.EgressBlockedException) {
                assertTrue(
                    "exception message should reference $host",
                    (e.message ?: "").contains(host),
                )
            } catch (e: Exception) {
                fail("expected EgressBlockedException for host=$host, got ${e::class.simpleName}: ${e.message}")
            }
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `bad method is rejected even for allowed host`() {
        try {
            gate.egressHttp(
                host = "api.anthropic.com",
                path = "/v1/messages",
                method = "DELETE",
                headers = emptyList(),
                body = null,
            )
            fail("expected IllegalArgumentException for DELETE")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("unsupported method"))
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `bad path is rejected`() {
        try {
            gate.egressHttp(
                host = "api.anthropic.com",
                path = "v1/messages",
                method = "POST",
                headers = emptyList(),
                body = "{}",
            )
            fail("expected IllegalArgumentException for path without leading slash")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("path must start with"))
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `CRLF in header name is rejected`() {
        try {
            gate.egressHttp(
                host = "api.anthropic.com",
                path = "/v1/messages",
                method = "POST",
                headers = listOf("X-Evil\r\nX-Inject" to "yes"),
                body = "{}",
            )
            fail("expected IllegalArgumentException for header name with CRLF")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("header name"))
        }
        assertEquals(0, auditLog.entries().size)
    }

    @Test
    fun `CRLF in header value is rejected`() {
        try {
            gate.egressHttp(
                host = "api.anthropic.com",
                path = "/v1/messages",
                method = "POST",
                headers = listOf("X-Foo" to "v1\r\nHost: evil.com"),
                body = "{}",
            )
            fail("expected IllegalArgumentException for header value with CRLF")
        } catch (e: IllegalArgumentException) {
            assertTrue((e.message ?: "").contains("header value"))
        }
        assertEquals(0, auditLog.entries().size)
    }
}
