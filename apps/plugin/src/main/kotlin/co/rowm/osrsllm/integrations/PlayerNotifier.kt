package co.rowm.osrsllm.integrations

import net.runelite.client.Notifier
import net.runelite.client.config.Notification
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tray / OS-level notifications the agent can fire when something needs the
 * player's attention while they're AFK (herbs grown, kill streak hit, low HP
 * threshold tripped, etc.). Thin wrapper over RuneLite's [Notifier].
 */
@Singleton
class PlayerNotifier @Inject constructor(
    private val notifier: Notifier,
) {

    private val log = LoggerFactory.getLogger(PlayerNotifier::class.java)

    /**
     * Fire a notification. [forceTray] forces system-tray output even if the
     * user has it globally disabled in RuneLite settings — only use when the
     * agent thinks the user really needs to see this (default: respect user
     * settings via [Notification.ON]).
     */
    fun notify(message: String, forceTray: Boolean = false) {
        val text = message.take(MAX_LEN)
        try {
            val notification = if (forceTray) {
                Notification.ON.withOverride(true).withTray(true)
            } else {
                Notification.ON
            }
            notifier.notify(notification, text)
        } catch (t: Throwable) {
            log.warn("notify failed: {}", t.message)
        }
    }

    companion object {
        private const val MAX_LEN = 240
    }
}
