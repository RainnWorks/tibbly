package co.rowm.osrsllm.ui.sections

import co.rowm.osrsllm.ui.CollapsibleSection
import co.rowm.osrsllm.ui.FormBuilder
import javax.swing.JPanel

/**
 * Sections we've designed but not yet wired up. They show up in the
 * config view so the structure is visible end-to-end (and so Tom can
 * point at the empty section and tell us what to put in it), but the
 * body is a one-liner caption for now.
 *
 * Each section will graduate into its own file once we wire the
 * actual controls.
 */
internal object StubSections {

    fun triggers(): CollapsibleSection = stub(
        title = "Proactive triggers",
        body = "Per-category toggles for boss tips, slayer hints, quest nudges, level-ups, GE alerts.",
    )

    fun privacy(): CollapsibleSection = stub(
        title = "Privacy & data",
        body = "Cloud-chat consent, what Tibbly can see, export / delete (GDPR Art. 15 / 17).",
    )

    fun notifications(): CollapsibleSection = stub(
        title = "Notifications & hotkeys",
        body = "Open-panel hotkey, mute proactive lines, sound on speak.",
    )

    fun about(): CollapsibleSection = stub(
        title = "About",
        body = "Plugin version, changelog, wiki, Discord, report a bug.",
    )

    private fun stub(title: String, body: String): CollapsibleSection {
        val pane = FormBuilder.body() as JPanel
        pane.add(FormBuilder.caption(body))
        return CollapsibleSection(title, pane, expandedByDefault = false)
    }
}
