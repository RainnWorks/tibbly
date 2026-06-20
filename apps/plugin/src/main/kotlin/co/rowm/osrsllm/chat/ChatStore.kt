package co.rowm.osrsllm.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.runelite.client.RuneLite
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
data class ToolCall(
    val name: String,
    val input: String,
    val result: String,
)

data class ChatMessage(
    val role: Role,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
) {
    enum class Role { USER, ASSISTANT }
}

private val toolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val messages: List<ChatMessage>,
)

/**
 * Chats are stored as markdown files under ~/.runelite/osrs-llm-helper/chats/.
 * Format:
 * ---
 * id: <id>
 * title: <title>
 * created: <iso>
 * ---
 * ## You
 * message text
 *
 * ## Assistant
 * response text
 */
class ChatStore {

    private val log = LoggerFactory.getLogger(ChatStore::class.java)
    private val dir: File =
        File(RuneLite.RUNELITE_DIR, "osrs-llm-helper/chats").apply { mkdirs() }
        .also { LoggerFactory.getLogger(ChatStore::class.java).info("Chat store at {}", it.absolutePath) }

    /**
     * Lightweight observer pattern. Listeners get called on every mutation (create,
     * appendMessage, delete) so UI surfaces (sidebar, overlay) can refresh themselves
     * when chats change from another code path.
     */
    private val listeners = mutableListOf<(ChangeKind, String) -> Unit>()

    enum class ChangeKind { CREATED, UPDATED, DELETED }

    fun addListener(listener: (ChangeKind, String) -> Unit) {
        synchronized(listeners) { listeners += listener }
    }

    fun removeListener(listener: (ChangeKind, String) -> Unit) {
        synchronized(listeners) { listeners -= listener }
    }

    private fun fire(kind: ChangeKind, id: String) {
        val snapshot = synchronized(listeners) { listeners.toList() }
        for (l in snapshot) runCatching { l(kind, id) }
            .onFailure { log.warn("ChatStore listener threw: {}", it.message) }
    }

    fun list(): List<Chat> =
        (dir.listFiles { f -> f.extension == "md" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull { read(it.nameWithoutExtension) }

    fun create(title: String = "New chat"): Chat {
        val id = UUID.randomUUID().toString().take(8)
        val chat = Chat(id, title, Instant.now(), emptyList())
        write(chat)
        log.info("Created chat {} ('{}')", id, title)
        fire(ChangeKind.CREATED, id)
        return chat
    }

    fun read(id: String): Chat? {
        val f = file(id)
        if (!f.exists()) return null
        return parse(f)
    }

    fun appendMessage(id: String, message: ChatMessage): Chat {
        val chat = read(id) ?: error("chat $id not found")
        val isFirstUserMsg = chat.messages.isEmpty() && message.role == ChatMessage.Role.USER
        val updated = chat.copy(
            messages = chat.messages + message,
            title = if (isFirstUserMsg) deriveTitle(message.text) else chat.title,
        )
        write(updated)
        log.debug("Appended {} message to {} ({} chars, {} tool calls)",
            message.role, id, message.text.length, message.toolCalls.size)
        fire(ChangeKind.UPDATED, id)
        return updated
    }

    /** History to feed back to claude — only role/text, no tool metadata. */
    fun textOnlyHistory(id: String): List<ChatMessage> =
        read(id)?.messages?.map { it.copy(toolCalls = emptyList()) } ?: emptyList()

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(dir, "$id.md")

    private fun write(chat: Chat) {
        val out = buildString {
            appendLine("---")
            appendLine("id: ${chat.id}")
            appendLine("title: ${chat.title.replace("\n", " ")}")
            appendLine("created: ${chat.createdAt}")
            appendLine("---")
            appendLine()
            for (m in chat.messages) {
                appendLine(if (m.role == ChatMessage.Role.USER) "## You" else "## Assistant")
                appendLine()
                if (m.toolCalls.isNotEmpty()) {
                    appendLine("<!-- osrsllm-tools")
                    appendLine(toolJson.encodeToString(ListSerializer(ToolCall.serializer()), m.toolCalls))
                    appendLine("-->")
                    appendLine()
                }
                appendLine(m.text)
                appendLine()
            }
        }
        file(chat.id).writeText(out)
    }

    private fun parse(f: File): Chat? {
        val lines = f.readLines()
        if (lines.firstOrNull() != "---") return null

        var i = 1
        val meta = mutableMapOf<String, String>()
        while (i < lines.size && lines[i] != "---") {
            val parts = lines[i].split(":", limit = 2)
            if (parts.size == 2) meta[parts[0].trim()] = parts[1].trim()
            i++
        }
        i++ // skip closing ---

        val messages = mutableListOf<ChatMessage>()
        var currentRole: ChatMessage.Role? = null
        val buf = StringBuilder()
        val toolRegex = Regex("<!-- osrsllm-tools\\s*\\n(.+?)\\n-->", RegexOption.DOT_MATCHES_ALL)

        fun flush() {
            val role = currentRole ?: return
            val raw = buf.toString()
            val match = toolRegex.find(raw)
            val toolCalls = match?.groupValues?.get(1)?.let { encoded ->
                runCatching {
                    toolJson.decodeFromString(ListSerializer(ToolCall.serializer()), encoded.trim())
                }.getOrDefault(emptyList())
            } ?: emptyList()
            val cleaned = raw.replace(toolRegex, "").trim()
            if (cleaned.isNotEmpty() || toolCalls.isNotEmpty()) {
                messages.add(ChatMessage(role, cleaned, toolCalls))
            }
            buf.clear()
        }

        while (i < lines.size) {
            when (val line = lines[i]) {
                "## You" -> { flush(); currentRole = ChatMessage.Role.USER }
                "## Assistant" -> { flush(); currentRole = ChatMessage.Role.ASSISTANT }
                else -> if (currentRole != null) buf.appendLine(line)
            }
            i++
        }
        flush()

        return Chat(
            id = meta["id"] ?: f.nameWithoutExtension,
            title = meta["title"] ?: "(untitled)",
            createdAt = runCatching { Instant.parse(meta["created"]) }.getOrDefault(Instant.now()),
            messages = messages.toList(),
        )
    }

    private fun deriveTitle(firstMessage: String): String {
        val firstLine = firstMessage.lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine.take(40).ifBlank { "New chat" }
    }
}
