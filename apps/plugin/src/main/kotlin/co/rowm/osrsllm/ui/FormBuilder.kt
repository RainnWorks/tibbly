package co.rowm.osrsllm.ui

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JComboBox
import javax.swing.JCheckBox
import javax.swing.JSlider
import javax.swing.SwingConstants

/**
 * Small Swing helpers used by every config section.
 *
 * Keeps section files focused on what they configure rather than on
 * label / input wiring. Every helper returns a `JPanel` styled to match
 * the RuneLite plugin sidebar (dark background, light text, mono caption
 * for "help" text under each row).
 */
internal object FormBuilder {

    private val ROW_BG: Color = ColorScheme.DARK_GRAY_COLOR
    private val LABEL_COLOR: Color = Color(220, 220, 220)
    private val HELP_COLOR: Color = Color(150, 150, 150)
    private val GRID_INSETS: Insets = Insets(4, 0, 4, 0)

    /**
     * Vertical-stack body container. Every section's body should pass
     * its rows into one of these so the spacing stays consistent.
     */
    fun body(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ROW_BG
    }

    /**
     * Label + input row with optional caption under the input. Returns
     * a panel sized to its content; the surrounding body container does
     * the vertical stacking.
     */
    fun row(label: String, input: Component, help: String? = null): JPanel {
        val container = JPanel(GridBagLayout()).apply {
            background = ROW_BG
            border = BorderFactory.createEmptyBorder(2, 0, 6, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 0.45
            fill = GridBagConstraints.HORIZONTAL
            insets = GRID_INSETS
        }
        val lbl = JLabel(label).apply {
            foreground = LABEL_COLOR
            font = FontManager.getRunescapeSmallFont()
            horizontalAlignment = SwingConstants.LEFT
        }
        container.add(lbl, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.55
        container.add(input, gbc)

        if (help != null) {
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.gridwidth = 2
            gbc.weightx = 1.0
            val helpLabel = JLabel("<html><body style='width:170px'>$help</body></html>").apply {
                foreground = HELP_COLOR
                font = FontManager.getRunescapeSmallFont()
            }
            container.add(helpLabel, gbc)
        }
        return container
    }

    /**
     * Edge-to-edge horizontal divider, drawn the same colour as the
     * section header underline so rows visually group.
     */
    fun divider(): Component = Box.createRigidArea(Dimension(0, 8))

    /**
     * Single line of caption text inside a body. Use for "tip", "fyi",
     * "this won't take effect until restart" notes.
     */
    fun caption(text: String): JLabel = JLabel("<html><body style='width:230px'>$text</body></html>").apply {
        foreground = HELP_COLOR
        font = FontManager.getRunescapeSmallFont()
        border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
    }

    /** Convenience: a wide text field sized for the sidebar. */
    fun textField(initial: String): JTextField = JTextField(initial).apply {
        preferredSize = Dimension(140, 22)
        background = ColorScheme.DARKER_GRAY_COLOR
        foreground = LABEL_COLOR
        caretColor = Color.WHITE
        border = BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter(), 1)
    }

    fun <T> combo(values: Array<T>, selected: T): JComboBox<T> = JComboBox(values).apply {
        this.selectedItem = selected
        background = ColorScheme.DARKER_GRAY_COLOR
        foreground = LABEL_COLOR
    }

    fun checkBox(label: String, initial: Boolean): JCheckBox = JCheckBox(label, initial).apply {
        background = ROW_BG
        foreground = LABEL_COLOR
        font = FontManager.getRunescapeSmallFont()
        isFocusPainted = false
    }

    fun slider(min: Int, max: Int, initial: Int): JSlider = JSlider(min, max, initial).apply {
        background = ROW_BG
        foreground = LABEL_COLOR
        paintTicks = true
        paintLabels = true
        majorTickSpacing = (max - min)
        snapToTicks = true
    }
}
