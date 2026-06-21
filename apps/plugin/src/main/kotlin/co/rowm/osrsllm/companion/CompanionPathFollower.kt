package co.rowm.osrsllm.companion

import kotlin.math.abs

/**
 * Drives the companion's position between the integer tile coordinates
 * produced by [CompanionPathfinder]. The renderer wants smooth sub-tile
 * motion at the OSRS tick rate (one tile per 600ms); this class handles
 * the interpolation and the fade-respawn fallback for long-distance
 * teleports.
 *
 * The follower is intentionally a pure state machine over tiles and
 * elapsed-ms inputs. The wire-in layer feeds it the player tile each
 * tick and the follower returns a [CompanionRenderer.Snapshot] for the
 * renderer to pick up.
 */
class CompanionPathFollower(
    private val pathfinder: CompanionPathfinder,
    private val walkable: WalkableTest,
    /** OSRS server tick is 600ms; we ease the sprite across the same window. */
    private val tickDurationMs: Long = DEFAULT_TICK_DURATION_MS,
    private val fadeDurationMs: Long = DEFAULT_FADE_MS,
    /** Tiles beyond which the follower fades+respawns instead of pathing. */
    private val teleportThreshold: Int = DEFAULT_TELEPORT_THRESHOLD,
) {

    private var currentTile: CompanionTile = CompanionTile(0, 0, 0)
    private var targetTile: CompanionTile = CompanionTile(0, 0, 0)
    private var stepFromTile: CompanionTile = CompanionTile(0, 0, 0)
    private var stepToTile: CompanionTile = CompanionTile(0, 0, 0)
    private var stepStartedAtMs: Long = 0L
    private var fadeStartedAtMs: Long = 0L
    private var fadingOut: Boolean = false
    private var fadingIn: Boolean = false
    private var facing: Direction = Direction.SOUTH
    @Volatile private var initialized: Boolean = false

    /** Place the companion at [tile] with no animation. Used on first spawn. */
    fun teleport(tile: CompanionTile, facing: Direction = Direction.SOUTH) {
        currentTile = tile
        targetTile = tile
        stepFromTile = tile
        stepToTile = tile
        stepStartedAtMs = 0L
        fadingOut = false
        fadingIn = false
        this.facing = facing
        initialized = true
    }

    /**
     * Tell the follower where the player wants the companion. The
     * follower decides whether to walk, fade-respawn, or stand still.
     *
     * @return true if a new path was computed; false if no change.
     */
    fun retarget(newTarget: CompanionTile, nowMs: Long): Boolean {
        if (!initialized) {
            teleport(newTarget)
            return true
        }
        if (newTarget == targetTile) return false
        targetTile = newTarget
        // Out-of-range / off-plane targets trigger fade-respawn.
        if (newTarget.plane != currentTile.plane ||
            chebyshev(newTarget, currentTile) > teleportThreshold
        ) {
            beginFade(nowMs)
            return true
        }
        // Otherwise compute next step. If pathfinder fails, fall back to fade.
        val next = pathfinder.nextStep(currentTile, newTarget)
        if (next == null) {
            beginFade(nowMs)
            return true
        }
        beginStep(currentTile, next, nowMs)
        return true
    }

    /**
     * Advance internal time. Returns a snapshot the renderer should
     * publish. Caller (the wire-in) calls this once per render tick.
     */
    fun advance(nowMs: Long, state: AnimationState): CompanionRenderer.Snapshot {
        if (!initialized) return CompanionRenderer.Snapshot.HIDDEN

        // Fade-out / fade-in cycle wins over walking.
        if (fadingOut) {
            val progress = ((nowMs - fadeStartedAtMs).toFloat() / fadeDurationMs).coerceIn(0f, 1f)
            if (progress >= 1f) {
                // Snap to target plane / tile, start fading back in.
                currentTile = targetTile
                stepFromTile = targetTile
                stepToTile = targetTile
                fadingOut = false
                fadingIn = true
                fadeStartedAtMs = nowMs
                return CompanionRenderer.Snapshot(
                    visible = true,
                    tile = targetTile,
                    subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                    direction = facing,
                    state = state,
                    animationElapsedMs = nowMs - stepStartedAtMs,
                    opacity = 0f,
                )
            }
            return CompanionRenderer.Snapshot(
                visible = true,
                tile = currentTile,
                subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                direction = facing,
                state = state,
                animationElapsedMs = nowMs - stepStartedAtMs,
                opacity = 1f - progress,
            )
        }
        if (fadingIn) {
            val progress = ((nowMs - fadeStartedAtMs).toFloat() / fadeDurationMs).coerceIn(0f, 1f)
            if (progress >= 1f) {
                fadingIn = false
            }
            return CompanionRenderer.Snapshot(
                visible = true,
                tile = currentTile,
                subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                direction = facing,
                state = state,
                animationElapsedMs = nowMs - stepStartedAtMs,
                opacity = progress,
            )
        }
        // Walking interpolation.
        val stepProgress = if (stepFromTile == stepToTile) {
            1f
        } else {
            ((nowMs - stepStartedAtMs).toFloat() / tickDurationMs).coerceIn(0f, 1f)
        }
        if (stepProgress >= 1f) {
            // Finished this tile; consume the step.
            currentTile = stepToTile
            if (currentTile != targetTile) {
                val next = pathfinder.nextStep(currentTile, targetTile)
                if (next != null && walkable.isWalkable(next.x, next.y, next.plane)) {
                    beginStep(currentTile, next, nowMs)
                }
            }
            return CompanionRenderer.Snapshot(
                visible = true,
                tile = currentTile,
                subTileOffset = CompanionRenderer.SubTileOffset.ZERO,
                direction = facing,
                state = state,
                animationElapsedMs = nowMs - stepStartedAtMs,
                opacity = 1f,
            )
        }
        // Interpolate between stepFromTile and stepToTile.
        val dx = (stepToTile.x - stepFromTile.x).toFloat() * stepProgress
        val dy = (stepToTile.y - stepFromTile.y).toFloat() * stepProgress
        return CompanionRenderer.Snapshot(
            visible = true,
            tile = stepFromTile,
            subTileOffset = CompanionRenderer.SubTileOffset(dx, dy),
            direction = facing,
            state = state,
            animationElapsedMs = nowMs - stepStartedAtMs,
            opacity = 1f,
        )
    }

    /** Companion's current tile (may be in the middle of a step). */
    fun tile(): CompanionTile = currentTile

    private fun beginStep(from: CompanionTile, to: CompanionTile, nowMs: Long) {
        stepFromTile = from
        stepToTile = to
        stepStartedAtMs = nowMs
        facing = Direction.fromDelta(to.x - from.x, to.y - from.y)
    }

    private fun beginFade(nowMs: Long) {
        fadingOut = true
        fadingIn = false
        fadeStartedAtMs = nowMs
        stepFromTile = currentTile
        stepToTile = currentTile
    }

    private fun chebyshev(a: CompanionTile, b: CompanionTile): Int =
        kotlin.math.max(abs(a.x - b.x), abs(a.y - b.y))

    companion object {
        const val DEFAULT_TICK_DURATION_MS: Long = 600L
        const val DEFAULT_FADE_MS: Long = 350L
        const val DEFAULT_TELEPORT_THRESHOLD: Int = 12
    }
}
