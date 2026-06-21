package co.rowm.osrsllm.chat

import net.runelite.client.ui.ColorScheme
import net.runelite.client.ui.FontManager
import net.runelite.client.ui.PluginPanel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class ChatPanel(
    private val store: ChatStore,
    private val runner: ChatBackend,
    // Pass `false` to disable PluginPanel's auto-scroll wrapper. We own the layout
    // (BorderLayout) and have our own JScrollPane around the messages list, so the
    // outer auto-wrap would just nest scrollbars.
) : PluginPanel(false) {

    private val log = LoggerFactory.getLogger(ChatPanel::class.java)

    // Card layout for list / chat views.
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout).apply { background = ColorScheme.DARK_GRAY_COLOR }
    private val listCard: JPanel
    private val chatCard: JPanel

    // List view
    private val listContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = ColorScheme.DARK_GRAY_COLOR
    }
    private val listScroll = JScrollPane(listContainer)
    private val newButton = JButton("+ New chat")

    // Chat view
    private val backButton = JButton("←")
    private val titleLabel = JLabel(" ")
    private val messagesContainer = JPanel()
    private val messagesScroll = JScrollPane(messagesContainer)
    private val inputArea = JTextArea(2, 20)
    private val sendButton = JButton("Send")

    private var currentChat: Chat? = null
    private var pendingChatId: String? = null
    private val pendingToolCalls: MutableList<ToolCall> = mutableListOf()
    /** id (from claude's tool_use_id) → index in pendingToolCalls so we can fill in results. */
    private val pendingToolCallIndex: MutableMap<String, Int> = HashMap()
    private var thinkingLabel: JLabel? = null
    private var thinkingDots = 0
    private val thinkingTimer: Timer = Timer(350) {
        thinkingDots = (thinkingDots + 1) % 4
        val dots = ".".repeat(thinkingDots).padEnd(3, ' ')
        thinkingLabel?.text = "Claude is thinking$dots"
    }.apply { isRepeats = true }

    private val htmlKit: HTMLEditorKit = buildEditorKit()
    // Direct scrollbar manipulation. Attached recursively to every component in the
    // messages tree (via attachWheelTree) so cursor-over-bubble / gap / tool-call all
    // scroll the outer pane. Consuming the event prevents JScrollPane's built-in
    // listener from also firing and double-stepping.
    private val wheelToScroll = MouseWheelListener { e ->
        val bar = messagesScroll.verticalScrollBar
        val rows = if (e.scrollType == java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL) e.unitsToScroll else e.wheelRotation * 3
        bar.value = (bar.value + rows * bar.unitIncrement).coerceIn(0, (bar.maximum - bar.visibleAmount).coerceAtLeast(0))
        e.consume()
    }

    private fun attachWheelTree(root: java.awt.Component) {
        if (root.mouseWheelListeners.none { it === wheelToScroll }) {
            root.addMouseWheelListener(wheelToScroll)
        }
        if (root is java.awt.Container) {
            for (c in root.components) attachWheelTree(c)
        }
    }

    private val resizeDebounce: Timer = Timer(150) {
        currentChat?.let { renderMessages(it) }
    }.apply { isRepeats = false }
    private var lastRenderedWidth = -1

    /**
     * Reflects external chat-store changes (overlay slash commands, etc.) into the
     * sidebar UI. Marshals to EDT and re-renders whichever card is visible.
     */
    private val storeListener: (ChatStore.ChangeKind, String) -> Unit = { _, changedId ->
        SwingUtilities.invokeLater {
            refreshListView()
            val open = currentChat
            if (open != null && open.id == changedId) {
                store.read(changedId)?.let { fresh ->
                    currentChat = fresh
                    renderMessages(fresh)
                }
            }
        }
    }

    init {
        layout = BorderLayout()
        background = ColorScheme.DARK_GRAY_COLOR
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

        listCard = buildListCard()
        chatCard = buildChatCard()
        cards.add(listCard, CARD_LIST)
        cards.add(chatCard, CARD_CHAT)
        add(cards, BorderLayout.CENTER)

        showList()
        store.addListener(storeListener)
    }

    /** Called from the plugin on shutdown so we don't leak a listener. */
    fun detach() {
        store.removeListener(storeListener)
        thinkingTimer.stop()
    }

    // ---------- List view ----------

    private fun buildListCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.background = ColorScheme.DARK_GRAY_COLOR
        card.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Header
        val header = JPanel(BorderLayout(4, 0))
        header.background = ColorScheme.DARK_GRAY_COLOR
        header.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        val title = JLabel("Chats")
        title.font = FontManager.getRunescapeBoldFont()
        title.foreground = Color.WHITE
        newButton.font = FontManager.getRunescapeSmallFont()
        newButton.addActionListener { createNewChat() }
        header.add(title, BorderLayout.WEST)
        header.add(newButton, BorderLayout.EAST)
        card.add(header, BorderLayout.NORTH)

        // List
        listContainer.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        listScroll.viewport.background = ColorScheme.DARK_GRAY_COLOR
        listScroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        listScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        listScroll.border = BorderFactory.createEmptyBorder()
        listScroll.verticalScrollBar.unitIncrement = 16
        card.add(listScroll, BorderLayout.CENTER)
        return card
    }

    private fun refreshListView() {
        listContainer.removeAll()
        val chats = store.list()
        if (chats.isEmpty()) {
            val empty = JLabel("No chats yet. Click + New chat to begin.")
            empty.foreground = Color(120, 120, 120)
            empty.alignmentX = Component.LEFT_ALIGNMENT
            empty.border = BorderFactory.createEmptyBorder(20, 6, 20, 6)
            listContainer.add(empty)
        } else {
            for (chat in chats) {
                listContainer.add(chatListRow(chat))
                listContainer.add(Box.createRigidArea(Dimension(0, 4)))
            }
        }
        listContainer.add(Box.createVerticalGlue())
        listContainer.revalidate()
        listContainer.repaint()
    }

    private fun chatListRow(chat: Chat): JComponent {
        val row = JPanel(BorderLayout())
        row.background = ColorScheme.DARKER_GRAY_COLOR
        row.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        row.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        row.alignmentX = Component.LEFT_ALIGNMENT
        row.maximumSize = Dimension(Int.MAX_VALUE, 60)

        val title = JLabel(truncate(chat.title, 60))
        title.foreground = Color(230, 230, 230)
        title.font = FontManager.getRunescapeSmallFont()
        row.add(title, BorderLayout.CENTER)

        val rightLabel = if (pendingChatId == chat.id) {
            JLabel("…").apply { foreground = Color(140, 214, 168) }
        } else {
            val msgCount = chat.messages.size
            JLabel(if (msgCount > 0) "$msgCount" else "").apply {
                foreground = Color(140, 140, 140)
                font = FontManager.getRunescapeSmallFont()
            }
        }
        row.add(rightLabel, BorderLayout.EAST)

        row.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showChat(chat.id)
            override fun mouseEntered(e: MouseEvent) {
                row.background = Color(60, 60, 60)
            }
            override fun mouseExited(e: MouseEvent) {
                row.background = ColorScheme.DARKER_GRAY_COLOR
            }
        })
        return row
    }

    // ---------- Chat view ----------

    private fun buildChatCard(): JPanel {
        val card = JPanel(BorderLayout(0, 6))
        card.background = ColorScheme.DARK_GRAY_COLOR
        card.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Header
        val header = JPanel(BorderLayout(6, 0))
        header.background = ColorScheme.DARK_GRAY_COLOR
        backButton.font = FontManager.getRunescapeSmallFont()
        backButton.margin = java.awt.Insets(2, 6, 2, 6)
        backButton.addActionListener { showList() }
        titleLabel.foreground = Color.WHITE
        titleLabel.font = FontManager.getRunescapeBoldFont()
        header.add(backButton, BorderLayout.WEST)
        header.add(titleLabel, BorderLayout.CENTER)
        card.add(header, BorderLayout.NORTH)

        // Messages
        messagesContainer.layout = BoxLayout(messagesContainer, BoxLayout.Y_AXIS)
        messagesContainer.background = ColorScheme.DARKER_GRAY_COLOR
        messagesContainer.border = BorderFactory.createEmptyBorder(6, 4, 6, 4)
        messagesScroll.viewport.background = ColorScheme.DARKER_GRAY_COLOR
        messagesScroll.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        messagesScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        messagesScroll.border = BorderFactory.createEmptyBorder()
        messagesScroll.verticalScrollBar.unitIncrement = 16
        messagesScroll.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val w = messagesScroll.viewport.width
                if (w > 0 && w != lastRenderedWidth) resizeDebounce.restart()
            }
        })
        card.add(messagesScroll, BorderLayout.CENTER)

        // Input
        val bottom = JPanel(BorderLayout(4, 4))
        bottom.background = ColorScheme.DARK_GRAY_COLOR
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.background = ColorScheme.DARKER_GRAY_COLOR
        inputArea.foreground = Color.WHITE
        inputArea.caretColor = Color.WHITE
        inputArea.font = FontManager.getRunescapeSmallFont()
        inputArea.margin = java.awt.Insets(4, 4, 4, 4)
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    onSendOrStop()
                }
            }
        })
        val inputScroll = JScrollPane(inputArea)
        inputScroll.preferredSize = Dimension(0, 64)
        inputScroll.border = BorderFactory.createEmptyBorder()
        bottom.add(inputScroll, BorderLayout.CENTER)
        sendButton.addActionListener { onSendOrStop() }
        bottom.add(sendButton, BorderLayout.SOUTH)
        card.add(bottom, BorderLayout.SOUTH)
        return card
    }

    // ---------- Navigation ----------

    private fun showList() {
        refreshListView()
        cardLayout.show(cards, CARD_LIST)
    }

    private fun showChat(id: String) {
        val chat = store.read(id) ?: return
        currentChat = chat
        titleLabel.text = truncate(chat.title, 30)
        renderMessages(chat)
        cardLayout.show(cards, CARD_CHAT)
        SwingUtilities.invokeLater { inputArea.requestFocusInWindow() }
    }

    private fun createNewChat() {
        val chat = store.create()
        currentChat = chat
        titleLabel.text = chat.title
        renderMessages(chat)
        cardLayout.show(cards, CARD_CHAT)
        SwingUtilities.invokeLater { inputArea.requestFocusInWindow() }
    }

    // ---------- Messages ----------

    private fun renderMessages(chat: Chat) {
        lastRenderedWidth = messagesScroll.viewport.width
        messagesContainer.removeAll()
        val showPending = pendingChatId == chat.id

        if (chat.messages.isEmpty() && !showPending) {
            val empty = JLabel("Send a message below to start.")
            empty.foreground = Color(120, 120, 120)
            empty.alignmentX = Component.CENTER_ALIGNMENT
            empty.border = BorderFactory.createEmptyBorder(20, 0, 0, 0)
            messagesContainer.add(empty)
        } else {
            for (m in chat.messages) {
                if (m.role == ChatMessage.Role.USER) {
                    messagesContainer.add(userRow(m.text))
                } else {
                    for (call in m.toolCalls) {
                        messagesContainer.add(toolCallRow(call))
                    }
                    if (m.text.isNotBlank()) {
                        messagesContainer.add(claudeRow(m.text))
                    }
                }
                messagesContainer.add(Box.createRigidArea(Dimension(0, 4)))
            }
            if (showPending) {
                for (call in pendingToolCalls) {
                    messagesContainer.add(toolCallRow(call))
                }
                messagesContainer.add(pendingRow())
            }
        }
        messagesContainer.add(Box.createVerticalGlue())
        // Blanket-attach the wheel listener to every child so cursor-over-gap still scrolls.
        attachWheelTree(messagesContainer)
        messagesContainer.revalidate()
        messagesContainer.repaint()

        SwingUtilities.invokeLater {
            val sb = messagesScroll.verticalScrollBar
            sb.value = sb.maximum
        }
    }

    private fun bubbleMaxWidth(): Int {
        val viewportW = messagesScroll.viewport.width.takeIf { it > 0 } ?: 230
        return (viewportW - 44).coerceIn(120, 480)
    }

    private fun bubbleContentWidth(): Int =
        (bubbleMaxWidth() - 20).coerceAtLeast(80)

    private fun userRow(text: String): JComponent {
        val bubble = BubblePanel(USER_BG)
        bubble.layout = BorderLayout()
        bubble.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
        val area = JTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            foreground = Color.WHITE
            font = FontManager.getRunescapeSmallFont()
            addMouseWheelListener(wheelToScroll)
        }
        bubble.add(area, BorderLayout.CENTER)
        sizeContent(area, bubbleContentWidth())
        bubble.maximumSize = Dimension(bubbleMaxWidth(), Int.MAX_VALUE)
        return rightAlignedRow(bubble)
    }

    private fun claudeRow(text: String): JComponent {
        val bubble = BubblePanel(CLAUDE_BG)
        bubble.layout = BorderLayout()
        bubble.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
        val pane = newHtmlPane()
        pane.text = "<html><body>${MarkdownRenderer.toHtml(text)}</body></html>"
        bubble.add(pane, BorderLayout.CENTER)
        sizeContent(pane, bubbleContentWidth())
        bubble.maximumSize = Dimension(bubbleMaxWidth(), Int.MAX_VALUE)
        return leftAlignedRow(bubble)
    }

    private fun toolCallRow(call: ToolCall): JComponent {
        val view = ToolCallView(
            call = call,
            contentWidth = bubbleContentWidth(),
            wheelListener = wheelToScroll,
            onToggled = {
                // Re-attach wheel listener to newly-spawned detail components.
                attachWheelTree(messagesContainer)
                messagesContainer.revalidate()
                messagesContainer.repaint()
            },
        )
        return leftAlignedRow(view, leftMargin = 4, rightMargin = 30)
    }

    private fun pendingRow(): JComponent {
        val bubble = BubblePanel(CLAUDE_BG)
        bubble.layout = BorderLayout()
        bubble.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
        val label = JLabel("Claude is thinking").apply {
            foreground = Color(170, 170, 170)
            font = FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC)
            addMouseWheelListener(wheelToScroll)
        }
        thinkingLabel = label
        bubble.add(label, BorderLayout.CENTER)
        return leftAlignedRow(bubble)
    }

    private fun newHtmlPane(): JEditorPane = JEditorPane().apply {
        editorKit = htmlKit
        contentType = "text/html"
        isEditable = false
        isOpaque = false
        background = Color(0, 0, 0, 0)
        addMouseWheelListener(wheelToScroll)
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED && e.url != null) {
                runCatching { Desktop.getDesktop().browse(e.url.toURI()) }
            }
        }
    }

    private fun sizeContent(c: JComponent, width: Int) {
        c.setSize(width, Short.MAX_VALUE.toInt())
        val h = c.preferredSize.height
        c.preferredSize = Dimension(width, h)
        c.maximumSize = Dimension(width, h + 200)
    }

    private fun leftAlignedRow(c: JComponent, leftMargin: Int = 4, rightMargin: Int = 30): JComponent {
        val row = capHeightRow()
        row.layout = BoxLayout(row, BoxLayout.X_AXIS)
        row.isOpaque = false
        row.alignmentX = Component.LEFT_ALIGNMENT
        row.add(Box.createHorizontalStrut(leftMargin))
        row.add(c)
        row.add(Box.createHorizontalGlue())
        row.add(Box.createHorizontalStrut(rightMargin))
        return row
    }

    private fun rightAlignedRow(c: JComponent, leftMargin: Int = 30, rightMargin: Int = 4): JComponent {
        val row = capHeightRow()
        row.layout = BoxLayout(row, BoxLayout.X_AXIS)
        row.isOpaque = false
        row.alignmentX = Component.LEFT_ALIGNMENT
        row.add(Box.createHorizontalStrut(leftMargin))
        row.add(Box.createHorizontalGlue())
        row.add(c)
        row.add(Box.createHorizontalStrut(rightMargin))
        return row
    }

    /**
     * Row whose maximum height equals its preferred height — otherwise JPanel reports
     * Int.MAX_VALUE vertically and the parent BoxLayout.Y_AXIS gives it a slice of the
     * slack space instead of giving it all to the trailing vertical glue. Without this
     * cap, a chat with one or two bubbles stretches them to fill the viewport.
     */
    private fun capHeightRow(): JPanel = object : JPanel() {
        override fun getMaximumSize(): Dimension {
            val pref = preferredSize
            return Dimension(Int.MAX_VALUE, pref.height)
        }
    }

    private fun buildEditorKit(): HTMLEditorKit {
        val kit = HTMLEditorKit()
        val ss = kit.styleSheet
        ss.addRule("body { color: #e0e0e0; font-family: sans-serif; font-size: 11px; margin: 0; padding: 0; }")
        ss.addRule("p { margin: 2px 0; }")
        ss.addRule("h1, h2, h3, h4 { color: #ffffff; margin: 4px 0 2px 0; font-weight: bold; }")
        ss.addRule("h1 { font-size: 14px; }")
        ss.addRule("h2 { font-size: 13px; }")
        ss.addRule("h3 { font-size: 12px; }")
        ss.addRule("h4 { font-size: 11px; }")
        ss.addRule("ul, ol { margin: 2px 0 4px 18px; padding: 0; }")
        ss.addRule("li { margin: 1px 0; }")
        ss.addRule("strong, b { color: #ffffff; }")
        ss.addRule("em, i { color: #e8e8e8; }")
        ss.addRule("a { color: #9bbcff; }")
        ss.addRule("code { background: #1a1a1a; color: #f0f0f0; font-family: monospace; font-size: 10px; padding: 1px 3px; }")
        ss.addRule("pre { background: #1a1a1a; color: #f0f0f0; font-family: monospace; font-size: 10px; padding: 4px 6px; margin: 4px 0; }")
        ss.addRule("blockquote { color: #b0b0b0; border-left: 2px solid #555; padding: 0 0 0 6px; margin: 4px 0; }")
        return kit
    }

    // ---------- Send/Stop ----------

    private fun onSendOrStop() {
        if (pendingChatId != null) {
            log.info("Stop pressed — cancelling chat={}", pendingChatId)
            runner.cancel()
        } else {
            onSend()
        }
    }

    private fun onSend() {
        val text = inputArea.text.trim()
        if (text.isBlank()) return
        log.info("Send pressed ({} chars)", text.length)

        val chatId = currentChat?.id ?: store.create().also {
            currentChat = it
            titleLabel.text = it.title
        }.id

        val withUser = store.appendMessage(chatId, ChatMessage(ChatMessage.Role.USER, text))
        currentChat = withUser
        titleLabel.text = truncate(withUser.title, 30)
        inputArea.text = ""
        pendingChatId = chatId
        pendingToolCalls.clear()
        pendingToolCallIndex.clear()
        thinkingDots = 0
        thinkingTimer.start()
        sendButton.text = "Stop"
        inputArea.isEnabled = false
        renderMessages(withUser)

        // Reader-thread events. Marshal to EDT, append/update the pending tool-call
        // list, and trigger a re-render so the user sees tool activity live.
        val liveListener = object : ToolCallListener {
            override fun onToolCallStarted(id: String, name: String, input: String) {
                SwingUtilities.invokeLater {
                    if (pendingChatId != chatId) return@invokeLater
                    pendingToolCallIndex[id] = pendingToolCalls.size
                    pendingToolCalls.add(ToolCall(name = name, input = input, result = "(running…)"))
                    currentChat?.let { renderMessages(it) }
                }
            }
            override fun onToolCallCompleted(id: String, result: String) {
                SwingUtilities.invokeLater {
                    if (pendingChatId != chatId) return@invokeLater
                    val idx = pendingToolCallIndex[id] ?: return@invokeLater
                    if (idx in pendingToolCalls.indices) {
                        val existing = pendingToolCalls[idx]
                        pendingToolCalls[idx] = existing.copy(result = result)
                        currentChat?.let { renderMessages(it) }
                    }
                }
            }
        }

        Thread({
            val result = runner.send(withUser, liveListener)
            SwingUtilities.invokeLater {
                pendingChatId = null
                pendingToolCalls.clear()
                pendingToolCallIndex.clear()
                thinkingTimer.stop()
                thinkingLabel = null
                sendButton.text = "Send"
                inputArea.isEnabled = true
                inputArea.requestFocusInWindow()

                if (result.success) {
                    val withResponse = store.appendMessage(
                        chatId,
                        ChatMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            text = result.text,
                            toolCalls = result.toolCalls,
                        ),
                    )
                    if (currentChat?.id == chatId) {
                        currentChat = withResponse
                        titleLabel.text = truncate(withResponse.title, 30)
                        renderMessages(withResponse)
                    }
                } else {
                    val errored = store.appendMessage(
                        chatId,
                        ChatMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            text = "**Error** (exit ${result.exitCode}):\n\n```\n${result.text}\n```",
                        ),
                    )
                    if (currentChat?.id == chatId) {
                        currentChat = errored
                        renderMessages(errored)
                    }
                }
                // Always refresh the list view so the … indicator clears and message counts
                // update, regardless of which card the user is currently on.
                refreshListView()
            }
        }, "osrsllm-chat-send").apply { isDaemon = true }.start()
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max) + "…"

    companion object {
        private const val CARD_LIST = "list"
        private const val CARD_CHAT = "chat"
        private val USER_BG = Color(70, 110, 170)
        private val CLAUDE_BG = Color(50, 50, 50)
    }
}
