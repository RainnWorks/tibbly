package co.rowm.osrsllm.widgets

import net.runelite.api.Client
import net.runelite.api.widgets.Widget
import net.runelite.client.callback.ClientThread
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walks a widget tree on the client thread and emits a compact JSON snapshot:
 * visible text, item id/name/qty for item widgets, and click actions.
 *
 * The agent calls this via the `read_interface` MCP tool with a group id from
 * the harness state block (or from WidgetTracker output). Output is depth-limited
 * and length-capped so big interfaces (collection log, GE) don't blow context.
 */
@Singleton
class WidgetReader @Inject constructor(
    private val client: Client,
    private val clientThread: ClientThread,
    private val itemManager: ItemManager,
) {

    private val log = LoggerFactory.getLogger(WidgetReader::class.java)

    /**
     * Returns a JSON object describing the widget group. If the group is not
     * loaded or every child is hidden, returns `{"groupId":N,"open":false}`.
     */
    fun readGroup(groupId: Int, maxNodes: Int = 200, maxCharsPerText: Int = 200): String =
        onClientThread {
            val root = client.getWidget(groupId, 0)
            val sb = StringBuilder()
            sb.append('{')
            sb.append("\"groupId\":").append(groupId)
            if (root == null) {
                sb.append(",\"open\":false}")
                return@onClientThread sb.toString()
            }
            sb.append(",\"open\":").append(!root.isHidden)
            sb.append(",\"nodes\":[")
            val state = WalkState(maxNodes, maxCharsPerText)
            walk(root, 0, sb, state)
            sb.append("]")
            if (state.truncated) sb.append(",\"truncated\":true")
            sb.append('}')
            sb.toString()
        }

    private fun walk(w: Widget?, depth: Int, sb: StringBuilder, state: WalkState) {
        if (w == null || state.emitted >= state.maxNodes) {
            if (state.emitted >= state.maxNodes) state.truncated = true
            return
        }
        if (w.isHidden) return

        val rawText = (w.text ?: "").trim()
        val name = (w.name ?: "").trim()
        val itemId = w.itemId
        val itemQty = w.itemQuantity
        val actions = w.actions?.filter { !it.isNullOrBlank() }?.takeIf { it.isNotEmpty() }

        // Only emit interesting nodes (skip pure containers with nothing to show)
        val hasText = rawText.isNotEmpty()
        val hasName = name.isNotEmpty() && name != rawText
        val hasItem = itemId > 0
        val hasActions = actions != null
        val interesting = hasText || hasName || hasItem || hasActions

        if (interesting) {
            if (state.emitted > 0) sb.append(',')
            sb.append('{')
            sb.append("\"d\":").append(depth)
            if (hasText) {
                sb.append(",\"text\":")
                sb.append(jsonString(stripTags(rawText).take(state.maxCharsPerText)))
            }
            if (hasName) {
                sb.append(",\"name\":")
                sb.append(jsonString(stripTags(name).take(state.maxCharsPerText)))
            }
            if (hasItem) {
                sb.append(",\"itemId\":").append(itemId)
                sb.append(",\"qty\":").append(itemQty)
                val itemName = runCatching { itemManager.getItemComposition(itemId).name }.getOrNull()
                if (!itemName.isNullOrBlank()) {
                    sb.append(",\"itemName\":").append(jsonString(itemName))
                }
            }
            actions?.let { acts ->
                sb.append(",\"actions\":[")
                acts.forEachIndexed { i, a ->
                    if (i > 0) sb.append(',')
                    sb.append(jsonString(a))
                }
                sb.append(']')
            }
            sb.append('}')
            state.emitted++
        }

        if (state.emitted >= state.maxNodes) {
            state.truncated = true
            return
        }

        val dynamic = runCatching { w.dynamicChildren }.getOrNull()
        dynamic?.forEach { walk(it, depth + 1, sb, state) }
        val staticChildren = runCatching { w.staticChildren }.getOrNull()
        staticChildren?.forEach { walk(it, depth + 1, sb, state) }
        val nested = runCatching { w.nestedChildren }.getOrNull()
        nested?.forEach { walk(it, depth + 1, sb, state) }
    }

    private class WalkState(val maxNodes: Int, val maxCharsPerText: Int) {
        var emitted = 0
        var truncated = false
    }

    /** Strip Jagex-style color/font tags like <col=ffff00> </col> <br>. */
    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]*>"), "").replace(" ", " ").trim()

    private fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun <T> onClientThread(body: () -> T): T {
        val future = CompletableFuture<T>()
        clientThread.invoke(Runnable {
            try { future.complete(body()) } catch (t: Throwable) { future.completeExceptionally(t) }
        })
        return try {
            future.get(8, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.warn("read_interface call failed: {}", t.message)
            throw t
        }
    }
}
