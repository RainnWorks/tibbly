package co.rowm.osrsllm.managed

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.PluginPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Sidebar pane that lists every visual the agent has added to the player's
 * game/config and offers one-click removal. Lives next to the existing
 * chat / stats panels in the RuneLite sidebar.
 *
 * Rebuild strategy: the registry fires a [ManagedVisualsRegistry.Listener]
 * after every mutation; we marshal back onto the Swing EDT and rebuild the
 * list pane from scratch. Cheap because the list is rarely more than dozens
 * of entries.
 */
class ManagedVisualsPanel(
    private val registry: ManagedVisualsRegistry,
) : PluginPanel() {

    private val timestampFormat = SimpleDateFormat("MMM d HH:mm")
    private val listContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
    }
    private val emptyLabel = JLabel("No agent-added visuals.", SwingConstants.CENTER).apply {
        foreground = ColorScheme.LIGHT_GRAY_COLOR
        font = font.deriveFont(Font.ITALIC)
        border = BorderFactory.createEmptyBorder(20, 8, 20, 8)
    }
    private val scrollPane = JScrollPane(listContainer).apply {
        border = BorderFactory.createEmptyBorder()
        background = ColorScheme.DARK_GRAY_COLOR
        verticalScrollBar.unitIncrement = 12
    }
    private val countLabel = JLabel().apply { foreground = ColorScheme.LIGHT_GRAY_COLOR }
    private val clearAllButton = JButton("Remove all").apply {
        background = ColorScheme.DARKER_GRAY_COLOR
        foreground = Color(232, 100, 100, 240)
        isFocusPainted = false
        addActionListener {
            val confirm = JOptionPane.showConfirmDialog(
                this@ManagedVisualsPanel,
                "Remove ALL agent-added visuals? This will un-mark tiles, delete bank tabs, " +
                    "and clear every highlight the agent placed.",
                "Remove all", JOptionPane.OK_CANCEL_OPTION,
            )
            if (confirm == JOptionPane.OK_OPTION) registry.clearAll()
        }
    }

    private val listener = ManagedVisualsRegistry.Listener {
        SwingUtilities.invokeLater { rebuild() }
    }

    init {
        layout = BorderLayout()
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val header = JPanel(BorderLayout()).apply {
            background = ColorScheme.DARK_GRAY_COLOR
            add(JLabel("Agent visuals").apply {
                foreground = Color.WHITE
                font = font.deriveFont(Font.BOLD, 14f)
            }, BorderLayout.WEST)
            add(countLabel, BorderLayout.EAST)
        }

        val footer = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            background = ColorScheme.DARK_GRAY_COLOR
            add(clearAllButton)
        }

        add(header, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)

        registry.addListener(listener)
        rebuild()
    }

    fun detach() {
        registry.removeListener(listener)
    }

    private fun rebuild() {
        listContainer.removeAll()
        val entries = registry.list().sortedByDescending { it.createdAt }
        countLabel.text = if (entries.isEmpty()) "" else "${entries.size}"
        clearAllButton.isEnabled = entries.isNotEmpty()
        if (entries.isEmpty()) {
            listContainer.add(emptyLabel)
        } else {
            for (entry in entries) listContainer.add(buildRow(entry))
        }
        listContainer.revalidate()
        listContainer.repaint()
    }

    private fun buildRow(entry: ManagedVisualsRegistry.Entry): Component {
        val row = JPanel(GridBagLayout()).apply {
            background = ColorScheme.DARKER_GRAY_COLOR
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(8, 8, 8, 8),
            )
            maximumSize = Dimension(Int.MAX_VALUE, 80)
        }

        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 2, 8)
            gridx = 0; gridy = 0
        }

        // Color swatch
        val swatch = JPanel().apply {
            preferredSize = Dimension(14, 14)
            background = parseColor(entry.colorHex) ?: typeColor(entry.type)
            border = BorderFactory.createLineBorder(Color.BLACK, 1)
        }
        row.add(swatch, gbc.apply { gridheight = 2; weightx = 0.0 })

        // Type chip
        gbc.gridx = 1; gbc.gridheight = 1; gbc.weightx = 0.0
        row.add(JLabel(typeChip(entry.type)).apply {
            foreground = ColorScheme.LIGHT_GRAY_COLOR
            font = font.deriveFont(Font.PLAIN, 10f)
        }, gbc)

        // Main label
        gbc.gridx = 2; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(JLabel(entry.label).apply {
            foreground = Color.WHITE
            font = font.deriveFont(Font.PLAIN, 12f)
            toolTipText = entry.label
        }, gbc)

        // Remove button (top right, spans rows)
        val removeButton = JButton("✕").apply {
            background = ColorScheme.DARK_GRAY_COLOR
            foreground = Color(232, 100, 100, 240)
            isFocusPainted = false
            border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
            toolTipText = "Remove this visual"
            addActionListener { registry.remove(entry.type, entry.id) }
        }
        val rbGbc = GridBagConstraints().apply {
            gridx = 3; gridy = 0; gridheight = 2
            anchor = GridBagConstraints.NORTHEAST
            weightx = 0.0
        }
        row.add(removeButton, rbGbc)

        // Subtitle (created time + type-specific notes)
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        val subtitleParts = buildList {
            entry.subtitle?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(timestampFormat.format(Date(entry.createdAt)))
            entry.source?.takeIf { it.isNotBlank() }?.let { add("from $it") }
        }
        row.add(JLabel(subtitleParts.joinToString(" · ")).apply {
            foreground = ColorScheme.LIGHT_GRAY_COLOR
            font = font.deriveFont(Font.PLAIN, 10f)
        }, gbc)

        return Box.createVerticalBox().apply {
            background = ColorScheme.DARK_GRAY_COLOR
            add(row)
        }
    }

    private fun parseColor(hex: String?): Color? = hex?.let {
        runCatching {
            val trimmed = it.trim().removePrefix("#")
            when (trimmed.length) {
                6 -> {
                    val rgb = Integer.parseUnsignedInt(trimmed, 16)
                    Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                }
                8 -> {
                    val argb = Integer.parseUnsignedInt(trimmed, 16)
                    Color(
                        (argb shr 16) and 0xFF,
                        (argb shr 8) and 0xFF,
                        argb and 0xFF,
                        (argb ushr 24) and 0xFF,
                    )
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun typeColor(type: String): Color = when (type) {
        ManagedVisualsRegistry.Type.TILE_MARKER -> Color(140, 214, 168)
        ManagedVisualsRegistry.Type.BANK_TAB -> Color(232, 174, 78)
        ManagedVisualsRegistry.Type.NPC_HIGHLIGHT -> Color(120, 200, 220)
        ManagedVisualsRegistry.Type.OBJECT_HIGHLIGHT -> Color(180, 140, 220)
        ManagedVisualsRegistry.Type.GROUND_ITEM_HIGHLIGHT -> Color(232, 100, 100)
        ManagedVisualsRegistry.Type.INVENTORY_ITEM_HIGHLIGHT -> Color(220, 200, 100)
        ManagedVisualsRegistry.Type.HINT_ARROW -> Color(255, 255, 255)
        else -> ColorScheme.LIGHT_GRAY_COLOR
    }

    private fun typeChip(type: String): String = when (type) {
        ManagedVisualsRegistry.Type.TILE_MARKER -> "TILE"
        ManagedVisualsRegistry.Type.BANK_TAB -> "BANK"
        ManagedVisualsRegistry.Type.NPC_HIGHLIGHT -> "NPC"
        ManagedVisualsRegistry.Type.OBJECT_HIGHLIGHT -> "OBJ"
        ManagedVisualsRegistry.Type.GROUND_ITEM_HIGHLIGHT -> "GND"
        ManagedVisualsRegistry.Type.INVENTORY_ITEM_HIGHLIGHT -> "INV"
        ManagedVisualsRegistry.Type.HINT_ARROW -> "ARROW"
        else -> type.uppercase().take(4)
    }
}
