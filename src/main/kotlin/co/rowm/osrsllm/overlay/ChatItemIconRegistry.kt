package co.rowm.osrsllm.overlay

import net.runelite.api.Client
import net.runelite.api.IndexedSprite
import net.runelite.client.callback.ClientThread
import net.runelite.client.game.ItemManager
import net.runelite.client.util.AsyncBufferedImage
import net.runelite.client.util.ImageUtil
import org.slf4j.LoggerFactory
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers OSRS item icons as RuneLite mod-icons so we can reference them inline
 * in chat output via `<img=N>` tags.
 *
 * How it works:
 *   1. Item icons are async — `ItemManager.getImage(id)` returns an [AsyncBufferedImage]
 *      that fills in once the cache has fetched the sprite. We wait on a CountDownLatch.
 *   2. Once loaded, we convert each image to an [IndexedSprite] via
 *      [ImageUtil.getImageIndexedSprite] and append it to `client.getModIcons()`.
 *      That array mutation must happen on the client thread.
 *   3. The new index becomes the value chat strings reference as `<img=<index>>`.
 *
 * Cached per-process; each new itemId costs one slot in modIcons forever, so we cap
 * the cache size and reject further registrations once full.
 */
@Singleton
class ChatItemIconRegistry @Inject constructor(
    private val client: Client,
    private val itemManager: ItemManager,
    private val clientThread: ClientThread,
) {

    private val log = LoggerFactory.getLogger(ChatItemIconRegistry::class.java)

    /** itemId → modIcons index, lazily populated. */
    private val cache = ConcurrentHashMap<Int, Int>()

    /**
     * Block until every id in [itemIds] has a mod-icon index (or we hit [timeoutMs]).
     * Returns itemId → index for every successfully registered icon; unsuccessful ones
     * are omitted so callers can fall back to text-only rendering.
     */
    fun ensure(itemIds: Collection<Int>, timeoutMs: Long = 4000): Map<Int, Int> {
        if (itemIds.isEmpty()) return emptyMap()
        val unique = itemIds.toSet()
        val missing = unique.filter { !cache.containsKey(it) }

        if (missing.isNotEmpty() && cache.size + missing.size <= MAX_CACHED) {
            registerMissing(missing, timeoutMs)
        } else if (missing.isNotEmpty()) {
            log.warn("Icon cache full ({}). Skipping {} new item icons.", cache.size, missing.size)
        }

        return unique.mapNotNull { id ->
            val idx = cache[id] ?: return@mapNotNull null
            if (idx >= 0) id to idx else null
        }.toMap()
    }

    private fun registerMissing(missing: List<Int>, timeoutMs: Long) {
        val loaded = ConcurrentHashMap<Int, IndexedSprite>()
        val latch = CountDownLatch(missing.size)

        // Step 1: request each item image. The conversion to IndexedSprite happens once
        // the AsyncBufferedImage is filled (onLoaded fires immediately if it's already
        // cached). Converting outside the client thread is fine — ImageUtil just reads
        // BufferedImage pixels.
        for (id in missing) {
            val image = itemManager.getImage(id, 1, false)
            image.onLoaded {
                try {
                    // Scale to chat-line height BEFORE converting to IndexedSprite so the
                    // `<img=N>` tag renders inline-sized instead of bursting into the line
                    // above. The native item sprite is 36×32 — way too big for chat.
                    val scaled = ImageUtil.resizeImage(image, ICON_SIZE, ICON_SIZE, true)
                    val sprite = ImageUtil.getImageIndexedSprite(scaled, client)
                    loaded[id] = sprite
                } catch (t: Throwable) {
                    log.warn("Failed to convert item {} icon to sprite: {}", id, t.message)
                } finally {
                    latch.countDown()
                }
            }
        }

        val ok = try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
        if (!ok) log.warn("Icon load timed out — {}/{} ready", loaded.size, missing.size)

        if (loaded.isEmpty()) return

        // Step 2: batch-extend mod icons on the client thread.
        val regFuture = CompletableFuture<Map<Int, Int>>()
        clientThread.invoke(Runnable {
            try {
                val current = client.modIcons
                val newArr = Arrays.copyOf(current, current.size + loaded.size)
                val outMap = HashMap<Int, Int>()
                var offset = current.size
                for ((id, sprite) in loaded) {
                    newArr[offset] = sprite
                    outMap[id] = offset
                    offset++
                }
                client.modIcons = newArr
                regFuture.complete(outMap)
            } catch (t: Throwable) {
                regFuture.completeExceptionally(t)
            }
        })

        val registered = try {
            regFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            log.warn("Mod-icon registration failed: {}", t.message)
            return
        }
        cache.putAll(registered)
        log.info("Registered {} new item icons in modIcons (cache size now {})",
            registered.size, cache.size)
    }

    companion object {
        /** Hard cap on cached entries to keep modIcons from ballooning. */
        private const val MAX_CACHED = 256
        /** Inline icons in chat target the line height — ~14px works for both modes. */
        private const val ICON_SIZE = 14
    }
}
