package co.rowm.osrsllm.companion

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory

/**
 * Eight-compass direction the companion can face. Encoded so the loader
 * can index into atlas rows / file names without string fiddling on the
 * hot path.
 *
 * The vector represents the "front" of the sprite in tile-space. We use
 * RuneScape's screen-space convention: north is +y, east is +x. That keeps
 * the math aligned with [net.runelite.api.coords.WorldPoint].
 */
enum class Direction(val dx: Int, val dy: Int) {
    SOUTH(0, -1),
    SOUTH_WEST(-1, -1),
    WEST(-1, 0),
    NORTH_WEST(-1, 1),
    NORTH(0, 1),
    NORTH_EAST(1, 1),
    EAST(1, 0),
    SOUTH_EAST(1, -1);

    companion object {
        /**
         * Nearest of the eight directions to the supplied tile delta. Used by
         * the renderer to decide which sprite row to draw based on the
         * direction of travel.
         */
        fun fromDelta(dx: Int, dy: Int): Direction {
            if (dx == 0 && dy == 0) return SOUTH
            // atan2 gives angle in radians; map it to one of eight buckets.
            val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
            // Normalise to [0, 360).
            val normalised = ((angle % 360.0) + 360.0) % 360.0
            // 0 deg points east; each direction owns a 45 deg slice centred
            // on its compass heading. Offset by 22.5 deg so the buckets land
            // symmetrically around the cardinal directions.
            val bucket = ((normalised + 22.5) / 45.0).toInt() % 8
            return when (bucket) {
                0 -> EAST
                1 -> NORTH_EAST
                2 -> NORTH
                3 -> NORTH_WEST
                4 -> WEST
                5 -> SOUTH_WEST
                6 -> SOUTH
                7 -> SOUTH_EAST
                else -> SOUTH
            }
        }
    }
}

/**
 * The animation the companion is currently playing. Sealed so the renderer
 * can `when`-exhaust the cases and the state machine can drive transitions
 * without ambiguity.
 *
 * Naming follows the spec in `docs/product/EMBODIED_COMPANION.md` §4
 * (idle + reactive animations). Two-frame placeholders cover every state
 * before the real artwork lands.
 */
sealed class AnimationState(val id: String, val loops: Boolean) {
    /** Standing still, occasionally looking around. */
    object Idle : AnimationState("idle", loops = true)

    /** Trotting between tiles to catch up to the player. */
    object Walking : AnimationState("walking", loops = true)

    /** Head-snap toward something specific (NPC dialog target, examined object). */
    object LookAt : AnimationState("look_at", loops = false)

    /** Sat cross-legged with a book open. Triggered by short idle. */
    object Read : AnimationState("read", loops = true)

    /** Sat down, hands folded. Triggered by long AFK. */
    object Sit : AnimationState("sit", loops = true)

    /** Hop + small puff of smoke. Pet drops, level-ups, collection log entries. */
    object Surprise : AnimationState("surprise", loops = false)

    /** Yawn. Mild idle indicator after about a minute of inactivity. */
    object Yawn : AnimationState("yawn", loops = false)

    /** Mouth moves while a speech bubble is rendered above the head. */
    object Speak : AnimationState("speak", loops = true)

    companion object {
        /**
         * Every state, useful for atlas iteration in tests. Built lazily
         * because the static init of the companion object can run before
         * the sealed-class child `object`s are themselves initialised,
         * which would publish a list of nulls.
         */
        val ALL: List<AnimationState> by lazy {
            listOf(Idle, Walking, LookAt, Read, Sit, Surprise, Yawn, Speak)
        }
    }
}

/**
 * One frame from an animation atlas. The `index` is the frame's column
 * inside the atlas row; `durationMs` is how long the renderer should show
 * it before advancing.
 */
data class Frame(val index: Int, val durationMs: Long) {
    init {
        require(index >= 0) { "Frame index must be non-negative (got $index)" }
        require(durationMs > 0) { "Frame durationMs must be positive (got $durationMs)" }
    }
}

/**
 * Ordered list of frames the renderer plays in sequence. Total duration is
 * pre-computed so the renderer can wrap-around without iterating every
 * paint.
 */
data class Animation(val frames: List<Frame>) {
    val totalDurationMs: Long = frames.sumOf { it.durationMs }

    init {
        require(frames.isNotEmpty()) { "Animation must have at least one frame" }
    }

    /**
     * Resolve which frame should be visible at [elapsedMs] into the
     * animation. Caller decides whether to wrap or clamp. We wrap by
     * default because the renderer is the only caller and it always
     * wraps for looping states.
     */
    fun frameAt(elapsedMs: Long): Frame {
        if (elapsedMs <= 0) return frames.first()
        val wrap = elapsedMs % totalDurationMs
        var acc = 0L
        for (f in frames) {
            acc += f.durationMs
            if (wrap < acc) return f
        }
        return frames.last()
    }
}

/**
 * One starter form, e.g. `veteran`, `fox`, `wisp`, `golem`. The atlas
 * loader uses the id as the resource subdirectory name.
 */
enum class Starter(val id: String, val placeholderTint: Color) {
    VETERAN("veteran", Color(140, 180, 220)),
    FOX("fox", Color(220, 130, 60)),
    WISP("wisp", Color(180, 220, 140)),
    GOLEM("golem", Color(180, 140, 90));

    companion object {
        fun fromIdOrDefault(id: String?): Starter =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: VETERAN
    }
}

/**
 * Read-only view of one starter's sprite atlas. The atlas resolves a
 * sprite by (`Direction`, `AnimationState`, `Frame.index`) so the
 * renderer never has to know how the underlying PNGs are packed.
 *
 * Implementations:
 *  - [PlaceholderAtlas] ships with the plugin and is what the renderer
 *    falls back to when no commissioned art is on the classpath. It draws
 *    a flat-shaded 32x32 silhouette per direction so every state machine
 *    transition is observable end-to-end without art.
 *  - [ResourceAtlas] (lazy) loads `companion/<starter>/<state>_<dir>_<n>.png`
 *    sequences from the classpath when they exist. The renderer is
 *    written so that real art "drops in" without code changes; this is
 *    the seam.
 *
 * The renderer must NOT block on disk I/O during paint. All loads happen
 * once, eagerly, when the atlas is first asked for the starter. Worst
 * case is a few small PNG decodes during plugin startup.
 */
interface CompanionSpriteAtlas {
    val starter: Starter
    val tileSize: Int
    fun animation(state: AnimationState, direction: Direction): Animation
    fun sprite(state: AnimationState, direction: Direction, frameIndex: Int): BufferedImage
}

/**
 * Loader for sprite atlases. The single static entry-point [load] picks
 * the best available atlas for a starter, falling back to
 * [PlaceholderAtlas] when no commissioned PNGs are on the classpath.
 *
 * The lookup pattern for real art is intentionally simple so a contractor
 * can drop a folder of PNGs in without touching code:
 *
 * ```
 * src/main/resources/companion/<starter>/<state>_<direction>_<index>.png
 * ```
 *
 * For example, `veteran/walking_north_0.png` is frame 0 of the walking
 * animation facing north, on the Veteran starter.
 */
object CompanionAtlasLoader {
    private val log = LoggerFactory.getLogger(CompanionAtlasLoader::class.java)
    private val cache = mutableMapOf<Starter, CompanionSpriteAtlas>()

    /**
     * Return the atlas for [starter], loading and caching it on first call.
     * Always returns SOMETHING - the placeholder is the floor.
     */
    @Synchronized
    fun load(starter: Starter): CompanionSpriteAtlas {
        cache[starter]?.let { return it }
        val resolved = tryLoadResources(starter) ?: PlaceholderAtlas(starter)
        cache[starter] = resolved
        log.info("Companion atlas resolved: starter={} kind={}", starter.id, resolved::class.simpleName)
        return resolved
    }

    /** Drop the cache; visible for tests that want to swap atlases. */
    @Synchronized
    internal fun reset() {
        cache.clear()
    }

    /** Try to load real PNGs from the classpath. Returns null if any state is missing. */
    private fun tryLoadResources(starter: Starter): CompanionSpriteAtlas? {
        // The shipped resources directory contains a `.gitkeep` so it
        // exists, but no PNGs land until the asset commission completes
        // (separate PR). We do a single existence probe for the south
        // idle frame 0 - if that's missing, we fall back wholesale.
        val probe = "companion/${starter.id}/idle_south_0.png"
        val url = CompanionAtlasLoader::class.java.classLoader.getResource(probe)
        if (url == null) {
            log.debug("Companion atlas probe '{}' missing; using placeholder for starter={}", probe, starter.id)
            return null
        }
        return runCatching { ResourceAtlas(starter) }
            .onFailure { log.warn("Failed to load companion atlas resources for {}: {}", starter.id, it.message) }
            .getOrNull()
    }
}

/**
 * Placeholder atlas with a single flat-colour silhouette per direction.
 *
 * The placeholder shows clearly that the companion subsystem is alive
 * without leaning on any commissioned art. Each state is drawn the same
 * way (a coloured 32x32 with a small directional indicator) so the
 * rendering loop, the state machine transitions, the speech bubble
 * anchoring, and the path-follow interpolation can all be exercised in
 * dogfood and in tests.
 */
class PlaceholderAtlas(override val starter: Starter) : CompanionSpriteAtlas {
    override val tileSize: Int = TILE_PX

    private val cache = HashMap<Triple<String, Direction, Int>, BufferedImage>()
    private val animationCache = HashMap<Pair<String, Direction>, Animation>()

    override fun animation(state: AnimationState, direction: Direction): Animation =
        animationCache.getOrPut(state.id to direction) {
            // Two-frame placeholder. Walking flips a tiny "foot" pixel
            // between frames so the renderer's frame advance is visible.
            Animation(
                listOf(
                    Frame(0, defaultDurationMs(state)),
                    Frame(1, defaultDurationMs(state)),
                ),
            )
        }

    override fun sprite(state: AnimationState, direction: Direction, frameIndex: Int): BufferedImage {
        val key = Triple(state.id, direction, frameIndex.coerceIn(0, 1))
        return cache.getOrPut(key) { renderPlaceholder(state, direction, key.third) }
    }

    /** Per-state default frame timing for the placeholder. */
    private fun defaultDurationMs(state: AnimationState): Long = when (state) {
        AnimationState.Walking -> 150L
        AnimationState.Speak -> 120L
        AnimationState.Surprise -> 200L
        AnimationState.Yawn -> 300L
        AnimationState.LookAt -> 250L
        AnimationState.Read, AnimationState.Sit -> 400L
        AnimationState.Idle -> 500L
    }

    private fun renderPlaceholder(state: AnimationState, direction: Direction, frame: Int): BufferedImage {
        val img = BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.composite = AlphaComposite.Src
            // Body: rounded square tinted by starter.
            val tint = starter.placeholderTint
            g.color = Color(tint.red, tint.green, tint.blue, 220)
            g.fillRoundRect(4, 8, TILE_PX - 8, TILE_PX - 12, 8, 8)
            // Direction indicator: small dark dot toward the facing side.
            g.color = Color(20, 20, 20, 220)
            val cx = TILE_PX / 2
            val cy = TILE_PX / 2
            val dirX = cx + direction.dx * 8
            val dirY = cy - direction.dy * 8
            g.fillOval(dirX - 3, dirY - 3, 6, 6)
            // State badge: tiny coloured square in the corner so each state
            // is visually distinct in the placeholder.
            g.color = stateBadgeColor(state, frame)
            g.fillRect(TILE_PX - 8, 2, 6, 6)
        } finally {
            g.dispose()
        }
        return img
    }

    private fun stateBadgeColor(state: AnimationState, frame: Int): Color = when (state) {
        AnimationState.Idle -> if (frame == 0) Color(180, 180, 180) else Color(210, 210, 210)
        AnimationState.Walking -> if (frame == 0) Color(80, 200, 80) else Color(120, 240, 120)
        AnimationState.LookAt -> Color(220, 180, 100)
        AnimationState.Read -> Color(140, 100, 220)
        AnimationState.Sit -> Color(120, 120, 200)
        AnimationState.Surprise -> Color(255, 120, 80)
        AnimationState.Yawn -> Color(160, 160, 220)
        AnimationState.Speak -> if (frame == 0) Color(240, 240, 100) else Color(255, 255, 160)
    }

    companion object {
        const val TILE_PX: Int = 32
    }
}

/**
 * Eager-load atlas backed by classpath PNGs at the layout described in
 * [CompanionAtlasLoader] KDoc. Construction throws if any state is
 * missing for the default direction, so the loader can fall back to the
 * placeholder cleanly.
 *
 * We do NOT support partial atlases; the rationale is that mixing
 * commissioned and placeholder frames looks worse than either alone.
 * The asset-drop PR will replace this whole tree at once.
 */
internal class ResourceAtlas(override val starter: Starter) : CompanionSpriteAtlas {
    override val tileSize: Int = PlaceholderAtlas.TILE_PX

    private val sprites: Map<Triple<String, Direction, Int>, BufferedImage>
    private val animations: Map<Pair<String, Direction>, Animation>

    init {
        val loaded = HashMap<Triple<String, Direction, Int>, BufferedImage>()
        val anims = HashMap<Pair<String, Direction>, Animation>()
        val loader = CompanionAtlasLoader::class.java.classLoader
        for (state in AnimationState.ALL) {
            for (dir in Direction.values()) {
                val frames = ArrayList<Frame>()
                var idx = 0
                while (true) {
                    val resourcePath = "companion/${starter.id}/${state.id}_${dir.name.lowercase()}_$idx.png"
                    val url = loader.getResource(resourcePath) ?: break
                    val img = ImageIO.read(url)
                        ?: error("ImageIO failed to read $resourcePath")
                    loaded[Triple(state.id, dir, idx)] = img
                    frames += Frame(idx, defaultDurationMs(state))
                    idx++
                    if (idx > MAX_FRAMES_PER_ANIM) break
                }
                if (frames.isEmpty()) {
                    error("ResourceAtlas missing frames for starter=${starter.id} state=${state.id} dir=$dir")
                }
                anims[state.id to dir] = Animation(frames)
            }
        }
        sprites = loaded
        animations = anims
    }

    override fun animation(state: AnimationState, direction: Direction): Animation =
        animations[state.id to direction]
            ?: error("ResourceAtlas missing animation for ${state.id}/$direction")

    override fun sprite(state: AnimationState, direction: Direction, frameIndex: Int): BufferedImage =
        sprites[Triple(state.id, direction, frameIndex)]
            ?: sprites[Triple(state.id, direction, 0)]
            ?: error("ResourceAtlas missing sprite for ${state.id}/$direction/$frameIndex")

    private fun defaultDurationMs(state: AnimationState): Long = when (state) {
        AnimationState.Walking -> 150L
        AnimationState.Speak -> 120L
        AnimationState.Surprise -> 200L
        AnimationState.Yawn -> 300L
        AnimationState.LookAt -> 250L
        AnimationState.Read, AnimationState.Sit -> 400L
        AnimationState.Idle -> 500L
    }

    /**
     * Draw [sprite] into [g] at canvas (`x`, `y`). Centralised so the
     * renderer never has to know about scaling.
     */
    fun draw(g: Graphics2D, image: BufferedImage, x: Int, y: Int) {
        g.drawImage(image, x - tileSize / 2, y - tileSize, null)
    }

    companion object {
        private const val MAX_FRAMES_PER_ANIM = 16
    }
}
