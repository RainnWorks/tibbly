package co.rowm.osrsllm.integrations

import co.rowm.osrsllm.overlay.OverlayChatController
import net.runelite.client.chat.ChatCommandManager
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the chat command `::ai <prompt>` so the user can talk to the agent
 * by typing in their actual OSRS chatbox — no alt-tab, no overlay focus, no
 * sidebar click. The handler delegates to [OverlayChatController.runQuery],
 * which is the same path the in-game `!ai` overlay command uses.
 *
 * Lifecycle: [register] is called from the plugin's startUp(), [unregister]
 * from shutDown(). Safe to call register twice — second call no-ops.
 */
@Singleton
class AiChatCommand @Inject constructor(
    private val chatCommandManager: ChatCommandManager,
    private val controller: OverlayChatController,
) {

    private val log = LoggerFactory.getLogger(AiChatCommand::class.java)

    @Volatile private var registered = false

    fun register() {
        if (registered) return
        try {
            chatCommandManager.registerCommandAsync(COMMAND) { chatMessage, fullText ->
                val prompt = fullText.substringAfter(COMMAND, "").trim()
                if (prompt.isBlank()) {
                    log.info("::ai with no prompt — ignoring")
                    return@registerCommandAsync
                }
                log.info("::ai invoked: '{}'", prompt.take(80))
                controller.runQuery(prompt)
            }
            registered = true
            log.info("Registered chat command {}", COMMAND)
        } catch (t: Throwable) {
            log.warn("Failed to register {} command: {}", COMMAND, t.message)
        }
    }

    fun unregister() {
        if (!registered) return
        runCatching { chatCommandManager.unregisterCommand(COMMAND) }
        registered = false
    }

    companion object {
        const val COMMAND = "::ai"
    }
}
