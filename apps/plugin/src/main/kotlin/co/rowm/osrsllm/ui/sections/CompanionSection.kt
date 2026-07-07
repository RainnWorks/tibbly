package co.rowm.osrsllm.ui.sections

import co.rowm.osrsllm.companion.CompanionConfig
import co.rowm.osrsllm.companion.PersonalityArchetype
import co.rowm.osrsllm.companion.Starter
import co.rowm.osrsllm.ui.CollapsibleSection
import co.rowm.osrsllm.ui.FormBuilder
import co.rowm.osrsllm.ui.TibblySettings
import javax.swing.JPanel

/**
 * Companion section — variant, name, personality, verbosity, proactive
 * lines master toggle. The fine-grained trigger toggles (boss tips,
 * slayer hints, etc.) live in their own Triggers section.
 *
 * Writes back through `TibblySettings` so each interaction immediately
 * persists via RuneLite's ConfigManager and fires its `ConfigChanged`
 * listeners — same as the legacy form would have.
 */
internal object CompanionSection {

    fun build(settings: TibblySettings): CollapsibleSection {
        val body = FormBuilder.body()

        val enabled = FormBuilder.checkBox("Show Tibbly companion", settings.companionEnabled)
        enabled.addActionListener { settings.companionEnabled = enabled.isSelected }
        body.add(
            FormBuilder.row(
                label = "Companion",
                input = enabled,
                help = "Renders Tibbly walking beside your character.",
            ),
        )

        val variant = FormBuilder.combo(Starter.values(), settings.companionStarter)
        variant.addActionListener {
            val sel = variant.selectedItem as? Starter ?: return@addActionListener
            settings.companionStarter = sel
        }
        body.add(FormBuilder.row("Variant", variant, "Visual form. Cosmetic only — personality is separate."))

        val nameField = FormBuilder.textField(settings.companionName)
        nameField.addActionListener { settings.companionName = nameField.text }
        nameField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) {
                if (settings.companionName != nameField.text) {
                    settings.companionName = nameField.text
                }
            }
        })
        body.add(FormBuilder.row("Name", nameField, "Leave blank for the default."))

        val personality = FormBuilder.combo(PersonalityArchetype.values(), settings.companionArchetype)
        personality.addActionListener {
            val sel = personality.selectedItem as? PersonalityArchetype ?: return@addActionListener
            settings.companionArchetype = sel
        }
        body.add(FormBuilder.row("Personality", personality, "Voice style."))

        val verbosity = FormBuilder.slider(
            CompanionConfig.MIN_VERBOSITY,
            CompanionConfig.MAX_VERBOSITY,
            settings.companionSpeechVerbosity,
        )
        verbosity.addChangeListener {
            if (!verbosity.valueIsAdjusting) {
                settings.companionSpeechVerbosity = verbosity.value
            }
        }
        body.add(FormBuilder.row("Verbosity", verbosity, "How often Tibbly speaks unprompted."))

        val proactive = FormBuilder.checkBox(
            "Allow proactive lines",
            settings.companionProactiveTriggersEnabled,
        )
        proactive.addActionListener { settings.companionProactiveTriggersEnabled = proactive.isSelected }
        body.add(
            FormBuilder.row(
                label = "Proactive",
                input = proactive,
                help = "When off Tibbly never speaks without being asked.",
            ),
        )

        return CollapsibleSection("Companion", body as JPanel, expandedByDefault = true)
    }
}
