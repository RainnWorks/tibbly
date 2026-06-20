package co.rowm.osrsllm.items

import net.runelite.client.callback.ClientThread
import net.runelite.client.game.ItemManager
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe resolver for OSRS item display names.
 *
 * Tools that run on RuneLite's MCP Ktor coroutines (TransportService, EquipmentLoadoutService,
 * etc.) can't call [ItemManager.getItemComposition] directly — it throws because internal
 * client state isn't safe to touch off the client thread, which is why item names were
 * coming back as `"Unknown"` / `"item 1712"` everywhere.
 *
 * This resolver bridges the gap: callers hand off a collection of item IDs from any thread,
 * we batch one [ClientThread.invoke] hop that resolves every missing name on the client
 * thread, cache results so subsequent lookups for the same IDs are instant, and return a
 * `Map<itemId, name>` keyed for the caller.
 *
 * Cache survives the plugin lifetime — item names don't change. Fallback name on resolve
 * failure is `"item <id>"` so the data shape stays useful even if the cache is cold and
 * the round-trip times out.
 */
@Singleton
class ItemNameResolver @Inject constructor(
    private val itemManager: ItemManager,
    private val clientThread: ClientThread,
) {

    private val log = LoggerFactory.getLogger(ItemNameResolver::class.java)
    private val cache = ConcurrentHashMap<Int, String>()

    /** Resolve a single id. Blocks up to ~5s on a client-thread round-trip if not cached. */
    fun resolve(id: Int): String {
        cache[id]?.let { return it }
        return resolveAll(listOf(id))[id] ?: fallback(id)
    }

    /**
     * Batch-resolve. Any cached ids return instantly; the remainder go through a single
     * `ClientThread.invoke` so we pay one tick of latency per batch, not per item.
     */
    fun resolveAll(ids: Collection<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        val unique = ids.toSet().filter { it > 0 }
        if (unique.isEmpty()) return emptyMap()

        val cached = HashMap<Int, String>(unique.size)
        val missing = mutableListOf<Int>()
        for (id in unique) {
            val hit = cache[id]
            if (hit != null) cached[id] = hit else missing += id
        }
        if (missing.isEmpty()) return cached

        val future = CompletableFuture<Map<Int, String>>()
        clientThread.invoke(Runnable {
            try {
                val resolved = HashMap<Int, String>(missing.size)
                for (id in missing) {
                    resolved[id] = runCatching { itemManager.getItemComposition(id).name }
                        .getOrDefault(fallback(id))
                }
                future.complete(resolved)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })

        val fresh: Map<Int, String> = try {
            future.get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.warn("Item name resolve failed for {} ids: {}", missing.size, t.message)
            missing.associateWith { fallback(it) }
        }
        cache.putAll(fresh)
        return cached + fresh
    }

    private fun fallback(id: Int): String = "item $id"
}
