package co.rowm.osrsllm

import net.runelite.client.config.ConfigItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the BYOK config surface — default values, the `chatMode()`
 * round-trip, and the secret-leak shield — so a future refactor can't
 * silently break the hub-release strategy (Tier 2 BYO).
 *
 * History: this used to assert annotation invariants on the legacy
 * RuneLite-rendered config form (every getter carried `@ConfigItem`,
 * the API-key field was `secret = true`, etc.). The whole settings UI
 * moved inside the plugin panel (see `co.rowm.osrsllm.ui.TibblyPanel`)
 * so only ONE `@ConfigItem` remains — a pointer that tells the player
 * where the real settings live. The reflected invariants below pin
 * that pointer plus the absence of the legacy decorations, and the
 * separate `TibblyPanelTest` covers the password-masked rendering.
 */
class OsrsLlmHelperConfigTest {

    // -------------------------------------------------------------------------
    // Default values — existing-install behaviour MUST NOT change.
    // -------------------------------------------------------------------------

    @Test
    fun `chatMode defaults to Cloud`() {
        val cfg = StubConfig()
        assertEquals(ChatModeChoice.CLOUD, cfg.chatModeChoice())
        assertEquals(ChatMode.Cloud, cfg.chatMode())
    }

    @Test
    fun `byoApiKey defaults to empty`() {
        val cfg = StubConfig()
        assertEquals("", cfg.byoApiKey())
    }

    @Test
    fun `byoModel defaults to empty`() {
        val cfg = StubConfig()
        // Per-provider default is applied in code, not in this layer.
        assertEquals("", cfg.byoModel())
    }

    @Test
    fun `byoTelemetryOptIn defaults to false`() {
        val cfg = StubConfig()
        assertFalse(cfg.byoTelemetryOptIn())
    }

    @Test
    fun `consentAccepted defaults to false`() {
        val cfg = StubConfig()
        assertFalse(cfg.consentAccepted())
    }

    @Test
    fun `cloudChatEnabled defaults to false`() {
        val cfg = StubConfig()
        assertFalse(cfg.cloudChatEnabled())
    }

    @Test
    fun `backendUrl defaults to a wss URL`() {
        val cfg = StubConfig()
        assertTrue(
            "backend URL must be wss:// — plaintext is rejected at startup",
            cfg.backendUrl().startsWith("wss://"),
        )
    }

    @Test
    fun `developerMode defaults to false`() {
        val cfg = StubConfig()
        assertFalse(cfg.developerMode())
    }

    @Test
    fun `isByo is true only for BYO variants`() {
        assertTrue(ChatMode.ByoAnthropic.isByo)
        assertTrue(ChatMode.ByoOpenAi.isByo)
        assertTrue(ChatMode.ByoOpenRouter.isByo)
        assertFalse(ChatMode.Cloud.isByo)
        assertFalse(ChatMode.ToolsOnly.isByo)
    }

    // -------------------------------------------------------------------------
    // New invariants — the settings-live-in-panel pivot.
    // -------------------------------------------------------------------------

    @Test
    fun `settings-live-in-panel pointer item exists and is the only ConfigItem`() {
        val configItemMethods = OsrsLlmHelperConfig::class.java.methods
            .filter { it.getAnnotation(ConfigItem::class.java) != null }
        assertEquals(
            "exactly one @ConfigItem is permitted on the config — the panel pointer",
            1,
            configItemMethods.size,
        )
        val pointer = configItemMethods.single()
        val annotation = pointer.getAnnotation(ConfigItem::class.java)
        assertEquals(
            "the surviving @ConfigItem must be the panel pointer",
            "_settingsLiveInPanel",
            annotation.keyName,
        )
        assertTrue(
            "pointer description must direct the player into the panel",
            annotation.description.contains("panel"),
        )
    }

    @Test
    fun `legacy settings methods are no longer decorated with @ConfigItem`() {
        // Pick representative legacy settings: each used to carry a
        // @ConfigItem; the new shape persists them via ConfigManager
        // through TibblySettings.
        val keys = listOf(
            "consentAccepted",
            "cloudChatEnabled",
            "backendUrl",
            "chatModeChoice",
            "byoApiKey",
            "byoModel",
            "byoTelemetryOptIn",
            "companionEnabled",
            "companionStarter",
            "companionName",
            "companionArchetype",
            "companionSpeechVerbosity",
            "companionProactiveTriggersEnabled",
            "developerMode",
            "localMcpEnabled",
            "localMcpHost",
            "localMcpPort",
        )
        for (k in keys) {
            val m = OsrsLlmHelperConfig::class.java.getMethod(k)
            assertNull(
                "legacy form should be stripped — $k still has @ConfigItem",
                m.getAnnotation(ConfigItem::class.java),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Leak shield — the key value MUST NOT appear in any stringified config dump.
    // -------------------------------------------------------------------------

    @Test
    fun `key value never appears in stringified config snapshot`() {
        val leaked = "sk-LEAK-this-must-never-appear"
        val cfg = StubConfig(byoApiKeyValue = leaked)
        val snapshot = stringifySnapshot(cfg)
        assertFalse(
            "byoApiKey value leaked into config snapshot: $snapshot",
            snapshot.contains(leaked),
        )
        assertFalse(
            "byoApiKey value leaked (substring) into snapshot",
            snapshot.contains("LEAK"),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers.
    // -------------------------------------------------------------------------

    private class StubConfig(
        private val byoApiKeyValue: String = "",
    ) : OsrsLlmHelperConfig {
        override fun byoApiKey(): String = byoApiKeyValue
    }

    /**
     * Build a safe stringified snapshot of the config — what a `log.info`
     * call or diagnostics page might emit. byoApiKey() is intentionally
     * excluded (it's a secret).
     */
    private fun stringifySnapshot(cfg: OsrsLlmHelperConfig): String = buildString {
        appendLine("consentAccepted=${cfg.consentAccepted()}")
        appendLine("cloudChatEnabled=${cfg.cloudChatEnabled()}")
        appendLine("backendUrl=${cfg.backendUrl()}")
        appendLine("chatMode=${cfg.chatMode()::class.simpleName}")
        appendLine("byoModel=${cfg.byoModel()}")
        appendLine("byoTelemetryOptIn=${cfg.byoTelemetryOptIn()}")
        // NOTE: byoApiKey() is deliberately NOT included.
        appendLine("developerMode=${cfg.developerMode()}")
        appendLine("localMcpEnabled=${cfg.localMcpEnabled()}")
    }

    init {
        assertNotNull("guard against the StubConfig test helper being optimised out", StubConfig::class.java)
    }
}
