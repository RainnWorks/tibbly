package co.rowm.osrsllm.companion

import org.slf4j.LoggerFactory
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Atlas of 2D PNG sprite frames for the embodied companion.
 *
 * The atlases are baked offline from a CC0 3D source mesh
 * (Quaternius, see THIRD_PARTY_LICENSES.md and
 * docs/product/COMPANION_3D_SOURCE.md). The Blender baking script
 * lives at apps/plugin/scripts/bake-companion-atlas.py and emits
 * one PNG per frame per cell size under
 * apps/plugin/src/main/resources/companion/<variant>/<size>px/.
 *
 * Frame catalog (58 frames per variant), from
 * docs/product/COMPANION_VISUAL_BIBLE.md section 3 renamed for the
 * floating-robot pivot:
 *
 *   8 directions hover-move x 3 frames = 24
 *   8 directions idle hover x 2 frames = 16
 *   scan x 8
 *   display_on x 2
 *   power_down x 2
 *   power_down_extended x 2
 *   reaction_rise x 2
 *   speak x 2
 *   ---
 *   total = 58
 *
 * The runtime resolves a frame by its pose name + sub-index (e.g.
 * "hover_move_n", 1). If the PNG resource is missing (fresh clone,
 * or running unit tests without the baked assets) the loader falls
 * back to [PlaceholderAtlas], which draws a simple geometric robot
 * silhouette so the renderer never crashes and tests do not require
 * the binary assets to be present.
 */
class CompanionSpriteAtlas private constructor(
    val variant: Variant,
    val cellSize: Int,
    private val frames: Map<String, List<BufferedImage>>,
    val isPlaceholder: Boolean,
) {

    /** Visual variant. Default is "Probe". */
    enum class Variant(val resourceKey: String, val displayName: String) {
        DEFAULT("default", "Probe"),
        COMM_VISOR("comm_visor", "Probe - comm visor"),
        HEAVY_ARMOR("heavy_armor", "Probe - heavy armor"),
        RESEARCH_ARRAY("research_array", "Probe - research array"),
    }

    /**
     * Get frame [subIndex] for [poseName]. Returns null if [poseName] is
     * not in the catalog or [subIndex] is past the pose's frame count.
     */
    fun frame(poseName: String, subIndex: Int): BufferedImage? {
        val pose = frames[poseName] ?: return null
        if (subIndex < 0 || subIndex >= pose.size) return null
        return pose[subIndex]
    }

    /** Number of frames in a given pose, or 0 if unknown. */
    fun frameCount(poseName: String): Int = frames[poseName]?.size ?: 0

    /** Every pose name the atlas can render. */
    fun poseNames(): Set<String> = frames.keys

    companion object {

        private val log = LoggerFactory.getLogger(CompanionSpriteAtlas::class.java)

        /** Frame catalog. (pose name, frame count). 58 frames total. */
        val POSE_CATALOG: List<Pair<String, Int>> = listOf(
            "hover_move_n" to 3,
            "hover_move_ne" to 3,
            "hover_move_e" to 3,
            "hover_move_se" to 3,
            "hover_move_s" to 3,
            "hover_move_sw" to 3,
            "hover_move_w" to 3,
            "hover_move_nw" to 3,
            "idle_hover_n" to 2,
            "idle_hover_ne" to 2,
            "idle_hover_e" to 2,
            "idle_hover_se" to 2,
            "idle_hover_s" to 2,
            "idle_hover_sw" to 2,
            "idle_hover_w" to 2,
            "idle_hover_nw" to 2,
            "scan" to 8,
            "display_on" to 2,
            "power_down" to 2,
            "power_down_extended" to 2,
            "reaction_rise" to 2,
            "speak" to 2,
        )

        const val TOTAL_FRAMES: Int = 58

        init {
            val total = POSE_CATALOG.sumOf { it.second }
            require(total == TOTAL_FRAMES) {
                "atlas spec drift: expected $TOTAL_FRAMES frames, got $total"
            }
        }

        /**
         * Load the atlas for [variant] at [cellSize] pixels per cell.
         * Falls back to [PlaceholderAtlas] when the baked PNGs are
         * absent so calling code (and the test suite) never crashes
         * on a missing-resource path.
         */
        fun load(variant: Variant, cellSize: Int): CompanionSpriteAtlas {
            val resolved = mutableMapOf<String, List<BufferedImage>>()
            var anyMissing = false
            for ((poseName, count) in POSE_CATALOG) {
                val poseFrames = mutableListOf<BufferedImage>()
                for (i in 0 until count) {
                    val path = resourcePath(variant, cellSize, poseName, i)
                    val stream = CompanionSpriteAtlas::class.java.getResourceAsStream(path)
                    if (stream == null) {
                        anyMissing = true
                        break
                    }
                    stream.use {
                        val image = ImageIO.read(it)
                        if (image == null) {
                            anyMissing = true
                        } else {
                            poseFrames.add(image)
                        }
                    }
                }
                if (poseFrames.size == count) {
                    resolved[poseName] = poseFrames
                } else {
                    anyMissing = true
                }
            }

            if (anyMissing || resolved.size != POSE_CATALOG.size) {
                log.info(
                    "companion atlas resources absent for variant={} cellSize={}, " +
                        "falling back to PlaceholderAtlas. " +
                        "Bake with apps/plugin/scripts/bake-companion-atlas.py to enable the real art.",
                    variant.resourceKey,
                    cellSize,
                )
                return PlaceholderAtlas.build(variant, cellSize)
            }

            return CompanionSpriteAtlas(
                variant = variant,
                cellSize = cellSize,
                frames = resolved,
                isPlaceholder = false,
            )
        }

        /**
         * Internal constructor for the placeholder fallback. Public-ish
         * so the test suite can build deterministic fixtures.
         */
        internal fun fromFrames(
            variant: Variant,
            cellSize: Int,
            frames: Map<String, List<BufferedImage>>,
            isPlaceholder: Boolean,
        ): CompanionSpriteAtlas = CompanionSpriteAtlas(
            variant = variant,
            cellSize = cellSize,
            frames = frames,
            isPlaceholder = isPlaceholder,
        )

        private fun resourcePath(
            variant: Variant,
            cellSize: Int,
            poseName: String,
            subIndex: Int,
        ): String {
            val indexStr = subIndex.toString().padStart(2, '0')
            return "/companion/robot-default/${variant.resourceKey}/${cellSize}px/${poseName}_$indexStr.png"
        }
    }
}

/**
 * Deterministic geometric stand-in for [CompanionSpriteAtlas] used when
 * the baked PNGs are not on the classpath. Keeps tests green and lets
 * the renderer ship a recognizable Probe silhouette before the real
 * bake lands.
 *
 * Each pose paints a small floating-robot silhouette (a body circle, a
 * lens circle, a small antenna tick) with per-pose tweaks so the
 * animator code path can still verify pose changes are taking effect.
 */
object PlaceholderAtlas {

    fun build(variant: CompanionSpriteAtlas.Variant, cellSize: Int): CompanionSpriteAtlas {
        val resolved = mutableMapOf<String, List<BufferedImage>>()
        for ((poseName, count) in CompanionSpriteAtlas.POSE_CATALOG) {
            val poseFrames = (0 until count).map { i ->
                paint(variant, cellSize, poseName, i)
            }
            resolved[poseName] = poseFrames
        }
        return CompanionSpriteAtlas.fromFrames(
            variant = variant,
            cellSize = cellSize,
            frames = resolved,
            isPlaceholder = true,
        )
    }

    private fun paint(
        variant: CompanionSpriteAtlas.Variant,
        cellSize: Int,
        poseName: String,
        subIndex: Int,
    ): BufferedImage {
        val image = BufferedImage(cellSize, cellSize, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON,
            )
            g.composite = AlphaComposite.SrcOver
            g.color = Color(0, 0, 0, 0)
            g.fillRect(0, 0, cellSize, cellSize)

            val bodyColor = bodyColor(variant)
            val ledColor = ledColor(variant, poseName, subIndex)

            val cx = cellSize / 2
            val baseCy = cellSize * 6 / 10
            val cy = baseCy + bobOffset(poseName, subIndex, cellSize)
            val bodyRadius = cellSize * 3 / 8

            // Soft ground shadow
            g.color = Color(0, 0, 0, 80)
            g.fillOval(
                cx - bodyRadius,
                cellSize - cellSize / 6,
                bodyRadius * 2,
                cellSize / 12,
            )

            // Body sphere
            g.color = bodyColor
            g.fillOval(
                cx - bodyRadius,
                cy - bodyRadius,
                bodyRadius * 2,
                bodyRadius * 2,
            )

            // Antenna tick on top
            g.color = bodyColor.darker()
            g.fillRect(cx - 1, cy - bodyRadius - cellSize / 8, 2, cellSize / 8)

            // Front-facing lens (the LED)
            g.color = ledColor
            val lensRadius = bodyRadius / 2
            g.fillOval(
                cx - lensRadius,
                cy - lensRadius / 2,
                lensRadius * 2,
                lensRadius,
            )
        } finally {
            g.dispose()
        }
        return image
    }

    private fun bodyColor(variant: CompanionSpriteAtlas.Variant): Color = when (variant) {
        CompanionSpriteAtlas.Variant.DEFAULT -> Color(219, 209, 189)
        CompanionSpriteAtlas.Variant.COMM_VISOR -> Color(209, 214, 219)
        CompanionSpriteAtlas.Variant.HEAVY_ARMOR -> Color(102, 107, 115)
        CompanionSpriteAtlas.Variant.RESEARCH_ARRAY -> Color(189, 219, 199)
    }

    private fun ledColor(
        variant: CompanionSpriteAtlas.Variant,
        poseName: String,
        subIndex: Int,
    ): Color {
        val base = when (variant) {
            CompanionSpriteAtlas.Variant.DEFAULT -> Color(243, 199, 90)
            CompanionSpriteAtlas.Variant.COMM_VISOR -> Color(90, 199, 243)
            CompanionSpriteAtlas.Variant.HEAVY_ARMOR -> Color(243, 90, 76)
            CompanionSpriteAtlas.Variant.RESEARCH_ARRAY -> Color(166, 243, 204)
        }
        return when {
            poseName == "speak" && subIndex % 2 == 1 -> base.darker()
            poseName == "reaction_rise" && subIndex == 0 -> base.brighter()
            poseName == "power_down" || poseName == "power_down_extended" -> Color(
                base.red / 3,
                base.green / 3,
                base.blue / 3,
                base.alpha,
            )
            else -> base
        }
    }

    private fun bobOffset(poseName: String, subIndex: Int, cellSize: Int): Int {
        // Tiny vertical drift so adjacent frames are not pixel-identical.
        // This is what gives the placeholder a hint of life so the
        // renderer's animation tick is visible during dogfood.
        val unit = (cellSize / 32).coerceAtLeast(1)
        return when {
            poseName.startsWith("idle_hover") -> if (subIndex == 0) -unit else unit
            poseName.startsWith("hover_move") -> -subIndex * unit
            poseName == "reaction_rise" -> -(subIndex + 1) * unit * 2
            poseName == "power_down" || poseName == "power_down_extended" -> (subIndex + 1) * unit * 2
            else -> 0
        }
    }
}
