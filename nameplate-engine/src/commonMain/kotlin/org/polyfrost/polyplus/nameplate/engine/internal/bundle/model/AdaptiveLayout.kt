package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal class WidthRange(val min: Int, val max: Int?) {
    fun contains(width: Int): Boolean = width >= min && (max == null || width < max)
}

internal class Breakpoint(val range: WidthRange, val alternative: Int)

internal enum class PathEnd {
    WRAP,
    BOUNCE,
    STOP,
}

internal enum class Edge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    ;

    fun length(rect: PixelRect): Int = span(rect.width, rect.height)

    fun span(width: Int, height: Int): Int = if (this == TOP || this == BOTTOM) width else height

    fun x(rect: PixelRect, along: Int): Int = when (this) {
        TOP, BOTTOM -> rect.x0 + along
        LEFT -> rect.x0
        RIGHT -> rect.x1
    }

    fun y(rect: PixelRect, along: Int): Int = when (this) {
        LEFT, RIGHT -> rect.y0 + along
        TOP -> rect.y0
        BOTTOM -> rect.y1
    }

    companion object {
        fun named(name: String): Edge? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

internal enum class EdgeFollow {
    OUTSIDE,
    LINE,
    INSIDE,
    ;

    companion object {
        fun named(name: String): EdgeFollow? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

internal enum class EdgeSide {
    TOP,
    BOTTOM,
    PERIMETER,
    ;

    companion object {
        fun named(name: String): EdgeSide? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

internal class PathStop(val at: Double, val pause: Int)

internal class SpritePath(
    val anchors: List<Anchor>,
    val offsetsX: IntArray,
    val offsetsY: IntArray,
    val pauses: IntArray,
    val speed: Track,
    val constantSpeed: Int,
    val cycle: Int,
    val end: PathEnd,
    val facings: Array<IntArray?>,
    val walkers: Int,
    val spacing: Int,
    val follow: EdgeFollow? = null,
    val side: EdgeSide = EdgeSide.PERIMETER,
    val laps: Int = 0,
    val reverse: Boolean = false,
    val even: Boolean = false,
    val stops: List<PathStop> = emptyList(),
) {

    val hasFacings: Boolean = facings.any { it != null }

    private val prefix: LongArray = LongArray(cycle + 1).also { sums ->
        for (step in 0 until cycle) {
            sums[step + 1] = sums[step] + speed.at(step)
        }
    }

    fun distance(time: Int): Long = time.floorDiv(cycle).toLong() * prefix[cycle] + prefix[time.mod(cycle)]
}

internal class SpriteRepeat(val edge: Edge, val spacing: Int, val min: Int, val max: Int) {
    val side: EdgeSide
        get() = if (edge == Edge.BOTTOM) EdgeSide.BOTTOM else EdgeSide.TOP

    fun count(rect: PixelRect, width: Int, height: Int, run: Int = edge.length(rect)): Int {
        val art = edge.span(width, height)
        val spaced = edge.length(rect) / spacing
        val copies = if (art < spacing) spaced else maxOf(spaced, (run + art - 1) / art)
        return copies.coerceIn(min, max)
    }
}

internal class SpriteAdaptive(
    val path: SpritePath? = null,
    val repeat: SpriteRepeat? = null,
    val breakpoints: List<Breakpoint> = emptyList(),
    val alternatives: List<List<PixelSprite>> = emptyList(),
) {
    fun alternative(nameWidth: Int): Int {
        if (breakpoints.isEmpty()) return 0

        return breakpoints.firstOrNull { it.range.contains(nameWidth) }?.alternative ?: HIDDEN
    }

    companion object {
        const val HIDDEN = -1
        const val STATE_STRIDE = 128

        val NONE: SpriteAdaptive = SpriteAdaptive()
    }
}
