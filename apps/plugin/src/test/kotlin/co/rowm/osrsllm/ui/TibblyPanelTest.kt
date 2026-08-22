package co.rowm.osrsllm.ui

import co.rowm.osrsllm.ChatModeChoice
import co.rowm.osrsllm.OsrsLlmHelperConfig
import co.rowm.osrsllm.chat.Chat
import co.rowm.osrsllm.chat.ChatBackend
import co.rowm.osrsllm.chat.ChatPanel
import co.rowm.osrsllm.chat.ChatStore
import co.rowm.osrsllm.chat.ToolCallListener
import co.rowm.osrsllm.companion.PersonalityArchetype
import co.rowm.osrsllm.companion.Starter
import co.rowm.osrsllm.ui.sections.AccountSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.awt.GraphicsEnvironment

/**
 * Behavioural tests for the unified Tibbly panel.
 *
 * Covers:
 *  - The cog toggles cards (chat ↔ config) and never destroys the chat.
 *  - The config view stacks every section in the documented order.
 *  - Settings writes go through the persistence sink under `osrsllm`.
 *  - The BYOK key field is rendered password-masked (parity with the
 *    `secret = true` annotation we used to ship in the legacy form).
 *
 * Swing tests are skipped in headless CI environments —
 * `GraphicsEnvironment.isHeadless()` is the canonical guard.
 */
class TibblyPanelTest {

    @Before
    fun skipIfHeadless() {
        assumeFalse(
            "Swing-based panel test requires a display",
            GraphicsEnvironment.isHeadless(),
        )
    }

    @Test
    fun `cog toggles between chat and config cards`() {
        val (panel, _) = newPanel()
        assertEquals(TibblyPanel.View.CHAT, panel.currentViewForTest())
        panel.toggleView()
        assertEquals(TibblyPanel.View.CONFIG, panel.currentViewForTest())
        panel.toggleView()
        assertEquals(TibblyPanel.View.CHAT, panel.currentViewForTest())
    }

    @Test
    fun `setView is a no-op when already on that view`() {
        val (panel, _) = newPanel()
        panel.setView(TibblyPanel.View.CHAT)
        assertEquals(TibblyPanel.View.CHAT, panel.currentViewForTest())
        panel.setView(TibblyPanel.View.CONFIG)
        panel.setView(TibblyPanel.View.CONFIG)
        assertEquals(TibblyPanel.View.CONFIG, panel.currentViewForTest())
    }

    @Test
    fun `config view stacks every documented section in order`() {
        val (panel, _) = newPanel()
        val titles = panel.configViewForTest().sectionsForTest().map { it.titleForTest() }
        assertEquals(
            listOf(
                "Account",
                "Companion",
                "Proactive triggers",
                "Privacy & data",
                "Notifications & hotkeys",
                "Developer",
                "About",
            ),
            titles,
        )
    }

    @Test
    fun `BYOK key field is rendered password-masked`() {
        val (panel, _) = newPanel()
        val account: AccountSection = panel.configViewForTest().accountSectionForTest()
        val field = account.byokKeyFieldForTest()
        assertNotNull("BYOK key field must be present", field)
        // JPasswordField's echoChar is non-zero when masking is on. A
        // plain JTextField has no echoChar — this catches a regression
        // to JTextField even if the type assertion drifted.
        assertTrue(
            "BYOK key field must mask input (echoChar non-zero)",
            field.echoChar.code != 0,
        )
    }

    @Test
    fun `settings writes go through the sink under the osrsllm group`() {
        val sink = RecordingSink()
        val settings = TibblySettings(StubConfig(), sink)
        settings.companionEnabled = false
        settings.companionSpeechVerbosity = 3
        settings.backendUrl = "wss://staging.tibbly.io/plugin"
        settings.byoApiKey = "sk-test"
        assertEquals(
            listOf(
                Triple("osrsllm", "companionEnabled", false as Any),
                Triple("osrsllm", "companionSpeechVerbosity", 3 as Any),
                Triple("osrsllm", "backendUrl", "wss://staging.tibbly.io/plugin" as Any),
                Triple("osrsllm", "byoApiKey", "sk-test" as Any),
            ),
            sink.writes,
        )
    }

    @Test
    fun `companion section round-trips a Starter selection`() {
        val sink = RecordingSink()
        val settings = TibblySettings(StubConfig(), sink)
        settings.companionStarter = Starter.VETERAN
        val (_, _, value) = sink.writes.single()
        assertSame(Starter.VETERAN, value)
    }

    @Test
    fun `chat panel instance is preserved across toggles — never disposed`() {
        val (panel, _) = newPanel()
        val before = chatPanelChild(panel)
        panel.toggleView()
        panel.toggleView()
        val after = chatPanelChild(panel)
        assertSame("chat panel must survive the round-trip — state lives there", before, after)
    }

    @Test
    fun `chat panel is the default view`() {
        val (panel, _) = newPanel()
        assertEquals(TibblyPanel.View.CHAT, panel.currentViewForTest())
    }

    @Test
    fun `pointer @ConfigItem stays — settings live in panel`() {
        // Belt-and-braces alongside OsrsLlmHelperConfigTest: even with the
        // panel in play, the player must still see a row in RuneLite's
        // standard plugin settings tab telling them where the settings
        // moved.
        val annotation = OsrsLlmHelperConfig::class.java
            .getMethod("settingsLiveInPanel")
            .getAnnotation(net.runelite.client.config.ConfigItem::class.java)
        assertNotNull(annotation)
        assertTrue(annotation.description.contains("panel"))
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun newPanel(): Pair<TibblyPanel, RecordingSink> {
        val sink = RecordingSink()
        val settings = TibblySettings(StubConfig(), sink)
        val chat = newChatPanel()
        val panel = TibblyPanel(chat, settings, pairingFlow = null)
        return panel to sink
    }

    private fun newChatPanel(): ChatPanel {
        val store = ChatStore()
        val backend = object : ChatBackend {
            override fun send(chat: Chat, listener: ToolCallListener?): ChatBackend.Result =
                ChatBackend.Result(success = false, text = "test stub", exitCode = -1)
            override fun cancel() { /* no-op */ }
        }
        return ChatPanel(store, backend)
    }

    private fun chatPanelChild(panel: TibblyPanel): ChatPanel? = panel.components
        .filterIsInstance<javax.swing.JPanel>()
        .flatMap { it.components.toList() }
        .filterIsInstance<ChatPanel>()
        .firstOrNull()

    private class StubConfig : OsrsLlmHelperConfig {
        override fun chatModeChoice(): ChatModeChoice = ChatModeChoice.CLOUD
        override fun companionArchetype(): PersonalityArchetype = PersonalityArchetype.DRY_WIKI_VETERAN
        override fun companionStarter(): Starter = Starter.VETERAN
    }

    private class RecordingSink : SettingsSink {
        val writes: MutableList<Triple<String, String, Any>> = mutableListOf()
        override fun set(group: String, key: String, value: Any) {
            writes.add(Triple(group, key, value))
        }
    }
}
