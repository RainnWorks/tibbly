package co.rowm.osrsllm.integrations

import net.runelite.api.ChatMessageType
import net.runelite.client.callback.ClientThread
import net.runelite.client.chat.ChatMessageBuilder
import net.runelite.client.chat.ChatMessageManager
import net.runelite.client.chat.QueuedMessage
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends RuneLite-formatted text into the player's actual in-game chatbox via
 * [ChatMessageManager]. The agent's primary voice is still the overlay /
 * sidebar chat, but for hands-free flows (player is mid-fight and can't look
 * at the panel) speaking in chat is the right channel.
 *
 * Default channel is GAMEMESSAGE (yellow, neutral text). Override for
 * BROADCAST etc. when calling [say] directly. All messages are prefixed with
 * a colored `[AI]` tag so the player can tell apart agent output from game text.
 */
@Singleton
class ChatOutput @Inject constructor(
    private val chat: ChatMessageManager,
    private val clientThread: ClientThread,
) {

    private val log = LoggerFactory.getLogger(ChatOutput::class.java)

    fun say(message: String, type: ChatMessageType = ChatMessageType.GAMEMESSAGE) {
        val trimmed = message.take(MAX_LEN).replace(Regex("[\\r\\n]+"), " ")
        clientThread.invoke(Runnable {
            try {
                val body = ChatMessageBuilder()
                    .append(net.runelite.client.ui.JagexColors.MENU_TARGET, "[AI] ")
                    .append(trimmed)
                    .build()
                chat.queue(
                    QueuedMessage.builder()
                        .type(type)
                        .runeLiteFormattedMessage(body)
                        .build(),
                )
            } catch (t: Throwable) {
                log.warn("chat say failed: {}", t.message)
            }
        })
    }

    companion object {
        private const val MAX_LEN = 250
    }
}
