package co.rowm.osrsllm.overlay

import net.runelite.api.Client
import net.runelite.api.events.GameTick
import net.runelite.api.gameval.InterfaceID
import net.runelite.api.widgets.Widget
import net.runelite.client.callback.ClientThread
import net.runelite.client.eventbus.Subscribe
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hijacks the chatbox's "Trade" tab to act as a dedicated "AI" channel.
 *
 * Why repurpose Trade instead of injecting an 8th tab? The chatbox tab strip is
 * laid out by CS2 scripts at fixed pixel positions for the 7 existing tabs. Adding
 * a new child widget would require shifting every other tab AND re-applying the
 * shift on every CS2 redraw — fragile glue. The Trade tab is the least-used by
 * most players, so we relabel its text widget to "AI" and post AI messages as
 * [net.runelite.api.ChatMessageType.TRADE] so the game's own tab filter routes
 * them into our tab automatically.
 *
 * The actual filtering logic stays inside the OSRS client — clicking other tabs
 * hides AI messages, clicking the "AI" (Trade) tab shows them. Real trade
 * messages, if any, will still appear here. That's the cost of borrowing the slot.
 */
@Singleton
class AiChannelService @Inject constructor(
    private val client: Client,
    private val clientThread: ClientThread,
) {

    private val log = LoggerFactory.getLogger(AiChannelService::class.java)

    /**
     * The chatbox is rebuilt by CS2 scripts on various events (resize, login,
     * resizable-vs-fixed mode swap). Each rebuild resets the tab label, so we
     * re-apply on every GameTick. Idempotent — only writes when the label is
     * actually wrong, so it's cheap.
     */
    @Subscribe
    fun onGameTick(event: GameTick) {
        ensureAiLabel()
    }

    private fun ensureAiLabel() {
        val textWidget: Widget? = runCatching {
            client.getWidget(InterfaceID.Chatbox.CHAT_TRADE_TEXT)
        }.getOrNull()
        if (textWidget != null && textWidget.text != AI_LABEL) {
            textWidget.text = AI_LABEL
            log.debug("Relabeled Trade tab → AI")
        }
        // Also tweak the right-click "Show: Trade" option to read "Show: AI".
        val tabWidget: Widget? = runCatching {
            client.getWidget(InterfaceID.Chatbox.CHAT_TRADE)
        }.getOrNull()
        if (tabWidget != null) {
            val actions = tabWidget.actions
            if (actions != null) {
                for (i in actions.indices) {
                    val a = actions[i] ?: continue
                    if (a.contains("Trade", ignoreCase = true)) {
                        tabWidget.setAction(i, a.replace("Trade", AI_LABEL, ignoreCase = true))
                    }
                }
            }
        }
    }

    /**
     * One-shot restore of the original label on shutdown so the chatbox isn't left
     * mis-labeled if the user disables the plugin.
     */
    fun restoreTradeLabel() {
        clientThread.invoke(Runnable {
            val textWidget: Widget? = runCatching {
                client.getWidget(InterfaceID.Chatbox.CHAT_TRADE_TEXT)
            }.getOrNull()
            if (textWidget != null) textWidget.text = ORIGINAL_LABEL

            val tabWidget: Widget? = runCatching {
                client.getWidget(InterfaceID.Chatbox.CHAT_TRADE)
            }.getOrNull()
            tabWidget?.actions?.let { acts ->
                for (i in acts.indices) {
                    val a = acts[i] ?: continue
                    if (a.contains(AI_LABEL)) tabWidget.setAction(i, a.replace(AI_LABEL, ORIGINAL_LABEL))
                }
            }
        })
    }

    companion object {
        const val AI_LABEL = "AI"
        const val ORIGINAL_LABEL = "Trade"
    }
}
