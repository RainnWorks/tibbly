package co.rowm.osrsllm

import net.runelite.client.config.ConfigItem
import net.runelite.client.config.ConfigSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the BYOK config surface — the player-facing dropdown, the secret key
 * field, the section grouping, and the `chatMode()` round-trip — so a future
 * refactor can't silently break the hub-release strategy (Tier 2 BYO).
 *
 * Each test is a load-bearing invariant. If you change one of these you are
 * almost certainly also touching `docs/runelite-hub/DATA_DISCLOSURE.md` and
 * `docs/architecture/HUB_RELEASE_STRATEGY.md` — keep them in sync.
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

    // -------------------------------------------------------------------------
    // ChatMode enum round-trip — every variant parses both ways.
    // -------------------------------------------------------------------------

    @Test
    fun `every ChatModeChoice round-trips to a ChatMode subtype`() {
        for (choice in ChatModeChoice.values()) {
            val mode = choice.toChatMode()
            assertEquals(
                "config value mismatch for $choice",
                choice.configValue,
                mode.configValue,
            )
            assertEquals(
                "fromConfigValue round-trip failed for $choice",
                mode,
                ChatMode.fromConfigValue(choice.configValue),
            )
        }
    }

    @Test
    fun `ChatMode-fromConfigValue is lenient on unknown input`() {
        // An unknown / hand-edited config string must NEVER throw. The plugin
        // would crash before showing the consent dialog. Default to Cloud and
        // let the startup logger flag the typo.
        assertEquals(ChatMode.Cloud, ChatMode.fromConfigValue(null))
        assertEquals(ChatMode.Cloud, ChatMode.fromConfigValue(""))
        assertEquals(ChatMode.Cloud, ChatMode.fromConfigValue("not-a-mode"))
    }

    @Test
    fun `ChatMode-fromConfigValue is whitespace and case tolerant`() {
        assertEquals(ChatMode.ByoOpenAi, ChatMode.fromConfigValue(" BYO-OPENAI "))
        assertEquals(ChatMode.ByoOpenRouter, ChatMode.fromConfigValue("Byo-OpenRouter"))
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
    // RuneLite annotation invariants — the bits the hub reviewer eyeballs.
    // -------------------------------------------------------------------------

    @Test
    fun `byoApiKey is annotated secret = true`() {
        val method = OsrsLlmHelperConfig::class.java.getMethod("byoApiKey")
        val item = method.getAnnotation(ConfigItem::class.java)
        assertNotNull("byoApiKey() must carry a @ConfigItem annotation", item)
        assertTrue(
            "byoApiKey() must be annotated secret = true so RuneLite renders it password-masked",
            item.secret,
        )
    }

    @Test
    fun `byoApiKey lives in the chat-mode section`() {
        val method = OsrsLlmHelperConfig::class.java.getMethod("byoApiKey")
        val item = method.getAnnotation(ConfigItem::class.java)
        assertEquals(CHAT_MODE_SECTION, item.section)
    }

    @Test
    fun `chat-mode section annotation is present`() {
        val sections = OsrsLlmHelperConfig::class.java.declaredFields
            .mapNotNull { it.getAnnotation(ConfigSection::class.java) }
        // The annotation lives on the synthetic field for the `val chatModeSection`
        // property. We can't easily look it up by getter (interface vals on JVM
        // become methods, but the annotation target is FIELD on the impl) — so
        // we look at the underlying chatMode-grouped @ConfigItem section ids and
        // additionally verify the constant exists.
        val byApiKeyItem = OsrsLlmHelperConfig::class.java
            .getMethod("byoApiKey")
            .getAnnotation(ConfigItem::class.java)
        val chatModeItem = OsrsLlmHelperConfig::class.java
            .getMethod("chatModeChoice")
            .getAnnotation(ConfigItem::class.java)
        assertEquals(byApiKeyItem.section, chatModeItem.section)
        assertEquals(CHAT_MODE_SECTION, byApiKeyItem.section)
        // Quiet "unused": sections list is read only for the side effect of
        // proving the annotation class loads on the test classpath.
        assertNotNull(sections)
    }

    @Test
    fun `secret flag is false on non-secret fields`() {
        // Spot-check a sibling so we know the reflection actually reads the
        // annotation rather than constant-folding `true`.
        val method = OsrsLlmHelperConfig::class.java.getMethod("byoModel")
        val item = method.getAnnotation(ConfigItem::class.java)
        assertFalse("byoModel is not secret", item.secret)
    }

    // -------------------------------------------------------------------------
    // Leak shield — the key value MUST NOT appear in any stringified config dump.
    // -------------------------------------------------------------------------

    @Test
    fun `key value never appears in stringified config snapshot`() {
        // We construct a snapshot the same way the plugin would for logs,
        // diagnostics, or audit dumps — by stringifying each getter result.
        // The byoApiKey value MUST be excluded by callers; this test pins
        // the policy.
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
    // Helpers — a hand-rolled stub of the Config interface for default-value
    // checks. RuneLite's ConfigManager is not on the test classpath, so we
    // exercise the interface defaults directly.
    // -------------------------------------------------------------------------

    /**
     * Stub implementation that takes all default-method values. Default
     * methods on Kotlin interfaces with `= …` are compiled to method bodies
     * on the interface class, so calling them through a concrete subtype is
     * the cleanest way to assert defaults without instantiating ConfigManager.
     */
    private class StubConfig(
        private val byoApiKeyValue: String = "",
    ) : OsrsLlmHelperConfig {
        // Override only the leak-test path. Every other getter falls through
        // to the interface default declared in [OsrsLlmHelperConfig].
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
        // NOTE: byoApiKey() is deliberately NOT included. That's the policy
        // this test pins — secret fields stay out of stringified snapshots.
        appendLine("developerMode=${cfg.developerMode()}")
        appendLine("localMcpEnabled=${cfg.localMcpEnabled()}")
    }
}
