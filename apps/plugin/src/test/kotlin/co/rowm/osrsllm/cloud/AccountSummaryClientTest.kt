package co.rowm.osrsllm.cloud

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-8: account-panel client + DTO round-trip.
 *
 * These tests assert the wire shape decoded by [AccountSummaryClient] matches
 * what the backend's `/v1/account/summary` + `/v1/account/usage-proxy` emit,
 * AND that nothing in the DTO decoder allows a `tokens` field to slip through
 * onto a player-visible surface. Together with the Gradle gate
 * `:checkAccountPanelNoRawTokens`, this is the second belt of the
 * "no raw tokens in the UI" guarantee.
 */
class AccountSummaryClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "form"
    }

    @Test
    fun `AccountSummary decodes the documented backend shape`() {
        val body = """
            {
              "tier": "pro",
              "subscriptionStatus": "active",
              "renewsAt": "2026-07-14T00:00:00Z",
              "pairedOsrsAccounts": [
                { "id": "acct_1", "displayName": "Zezima", "accountType": "main", "isCurrent": true }
              ],
              "pairedDevices": [
                { "id": "dev_1", "displayName": "Tom's MacBook", "lastSeenAt": null, "isCurrent": false }
              ],
              "consent": { "canDeleteAccount": true }
            }
        """.trimIndent()

        val summary = json.decodeFromString(AccountSummary.serializer(), body)
        assertEquals("pro", summary.tier)
        assertEquals("active", summary.subscriptionStatus)
        assertEquals("2026-07-14T00:00:00Z", summary.renewsAt)
        assertEquals(1, summary.pairedOsrsAccounts.size)
        assertEquals("Zezima", summary.pairedOsrsAccounts[0].displayName)
        assertTrue(summary.pairedOsrsAccounts[0].isCurrent)
        assertEquals(1, summary.pairedDevices.size)
        assertEquals("Tom's MacBook", summary.pairedDevices[0].displayName)
    }

    @Test
    fun `UsageProxy decodes messages-left shape verbatim from backend`() {
        val body = """{ "form": "messages-left", "messagesUsedToday": 23, "messagesPerDay": 30 }"""
        val proxy = json.decodeFromString(UsageProxy.serializer(), body)
        assertTrue(proxy is UsageProxy.MessagesLeft)
        val left = proxy as UsageProxy.MessagesLeft
        assertEquals(23, left.messagesUsedToday)
        assertEquals(30, left.messagesPerDay)
    }

    @Test
    fun `UsageProxy decodes subscription-active shape verbatim from backend`() {
        val body = """{ "form": "subscription-active", "renewsAt": "2026-07-14T00:00:00Z" }"""
        val proxy = json.decodeFromString(UsageProxy.serializer(), body)
        assertTrue(proxy is UsageProxy.SubscriptionActive)
        assertEquals("2026-07-14T00:00:00Z", (proxy as UsageProxy.SubscriptionActive).renewsAt)
    }

    @Test
    fun `UsageProxy decodes unlimited shape verbatim from backend`() {
        val body = """{ "form": "unlimited" }"""
        val proxy = json.decodeFromString(UsageProxy.serializer(), body)
        assertTrue(proxy is UsageProxy.Unlimited)
    }

    /**
     * Guard: even if the backend regressed and tried to send a raw token
     * count, the Kotlin DTO would ignore the unknown field. The panel renders
     * what the DTO exposes, full stop — no `tokens` accessor exists on
     * [UsageProxy], so the player cannot see one.
     */
    @Test
    fun `UsageProxy silently drops unknown raw-token fields`() {
        val body = """
            {
              "form": "messages-left",
              "messagesUsedToday": 5,
              "messagesPerDay": 30,
              "balanceTokens": 999999,
              "promptTokens": 1234,
              "completionTokens": 5678,
              "costMicroUsd": 4200
            }
        """.trimIndent()
        val proxy = json.decodeFromString(UsageProxy.serializer(), body)
        assertTrue(proxy is UsageProxy.MessagesLeft)
        // The DTO surface area, by design, has no token-arithmetic fields
        // for the panel to surface. Re-encoding round-trips only the four
        // documented properties.
        val reencoded = json.encodeToString(UsageProxy.serializer(), proxy)
        assertTrue("re-encode should not contain raw token fields", !reencoded.contains("balance"))
        assertTrue("re-encode should not contain raw token fields", !reencoded.contains("prompt"))
        assertTrue("re-encode should not contain raw token fields", !reencoded.contains("completion"))
        assertTrue("re-encode should not contain cost fields", !reencoded.contains("cost"))
    }

    @Test
    fun `formatRenewDate handles ISO-8601 input and falls back gracefully`() {
        // valid ISO → rendered as a short date
        val rendered = formatRenewDate("2026-07-14T00:00:00Z")
        assertTrue("expected day '14' in '$rendered'", rendered.contains("14"))
        // garbage → returned verbatim, never throws
        val raw = formatRenewDate("not-a-date")
        assertEquals("not-a-date", raw)
    }

    @Test
    fun `AccountSummary tolerates missing optional fields`() {
        val body = """{ }"""
        val summary = json.decodeFromString(AccountSummary.serializer(), body)
        assertNull(summary.tier)
        assertNull(summary.subscriptionStatus)
        assertNull(summary.renewsAt)
        assertTrue(summary.pairedOsrsAccounts.isEmpty())
        assertTrue(summary.pairedDevices.isEmpty())
    }
}
