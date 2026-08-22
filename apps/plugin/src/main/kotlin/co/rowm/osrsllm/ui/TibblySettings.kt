package co.rowm.osrsllm.ui

import co.rowm.osrsllm.ChatModeChoice
import co.rowm.osrsllm.OsrsLlmHelperConfig
import co.rowm.osrsllm.companion.PersonalityArchetype
import co.rowm.osrsllm.companion.Starter
import net.runelite.client.config.ConfigManager

/**
 * Narrow write-side seam over `ConfigManager`. Lives in this file so
 * the settings façade has a one-line abstraction over the only method
 * it actually calls; tests stub this interface instead of having to
 * construct a real `ConfigManager`.
 */
interface SettingsSink {
    fun set(group: String, key: String, value: Any)

    companion object {
        /** Default impl — writes through the supplied `ConfigManager`. */
        fun forConfigManager(cm: ConfigManager): SettingsSink = object : SettingsSink {
            override fun set(group: String, key: String, value: Any) {
                cm.setConfiguration(group, key, value)
            }
        }
    }
}

/**
 * Single read + write surface over `OsrsLlmHelperConfig` for the in-panel
 * settings UI.
 *
 * Read goes through the typed `OsrsLlmHelperConfig` proxy that RuneLite
 * mints from the interface; write goes through `ConfigManager.setConfiguration`
 * so RuneLite still persists the value and fires its `ConfigChanged`
 * listeners. Centralising both sides here means each section just calls
 * `settings.companionEnabled` / `settings.companionEnabled = true` and the
 * persisted key, group, type-coercion rules stay in one place.
 *
 * The `GROUP` constant must match the `@ConfigGroup("osrsllm")` value on
 * `OsrsLlmHelperConfig` — drift there silently bypasses persistence.
 */
class TibblySettings(
    private val cfg: OsrsLlmHelperConfig,
    private val sink: SettingsSink,
) {
    /* -------- Cloud chat / consent ---------------------------------- */

    var consentAccepted: Boolean
        get() = cfg.consentAccepted()
        set(value) = sink.set(GROUP, "consentAccepted", value)

    var cloudChatEnabled: Boolean
        get() = cfg.cloudChatEnabled()
        set(value) = sink.set(GROUP, "cloudChatEnabled", value)

    var backendUrl: String
        get() = cfg.backendUrl()
        set(value) = sink.set(GROUP, "backendUrl", value)

    /* -------- Chat mode + BYOK --------------------------------------- */

    var chatModeChoice: ChatModeChoice
        get() = cfg.chatModeChoice()
        set(value) = sink.set(GROUP, "chatMode", value)

    var byoApiKey: String
        get() = cfg.byoApiKey()
        set(value) = sink.set(GROUP, "byoApiKey", value)

    var byoModel: String
        get() = cfg.byoModel()
        set(value) = sink.set(GROUP, "byoModel", value)

    var byoTelemetryOptIn: Boolean
        get() = cfg.byoTelemetryOptIn()
        set(value) = sink.set(GROUP, "byoTelemetryOptIn", value)

    /* -------- Companion ---------------------------------------------- */

    var companionEnabled: Boolean
        get() = cfg.companionEnabled()
        set(value) = sink.set(GROUP, "companionEnabled", value)

    var companionStarter: Starter
        get() = cfg.companionStarter()
        set(value) = sink.set(GROUP, "companionStarter", value)

    var companionName: String
        get() = cfg.companionName()
        set(value) = sink.set(GROUP, "companionName", value)

    var companionArchetype: PersonalityArchetype
        get() = cfg.companionArchetype()
        set(value) = sink.set(GROUP, "companionArchetype", value)

    var companionSpeechVerbosity: Int
        get() = cfg.companionSpeechVerbosity()
        set(value) = sink.set(GROUP, "companionSpeechVerbosity", value)

    var companionProactiveTriggersEnabled: Boolean
        get() = cfg.companionProactiveTriggersEnabled()
        set(value) = sink.set(GROUP, "companionProactiveTriggersEnabled", value)

    /* -------- Developer ---------------------------------------------- */

    var developerMode: Boolean
        get() = cfg.developerMode()
        set(value) = sink.set(GROUP, "developerMode", value)

    var localMcpEnabled: Boolean
        get() = cfg.localMcpEnabled()
        set(value) = sink.set(GROUP, "localMcpEnabled", value)

    var localMcpHost: String
        get() = cfg.localMcpHost()
        set(value) = sink.set(GROUP, "localMcpHost", value)

    var localMcpPort: Int
        get() = cfg.localMcpPort()
        set(value) = sink.set(GROUP, "localMcpPort", value)

    companion object {
        const val GROUP: String = "osrsllm"
    }
}
