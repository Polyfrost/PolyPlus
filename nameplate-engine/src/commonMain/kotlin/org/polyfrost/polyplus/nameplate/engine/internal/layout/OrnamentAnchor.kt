package org.polyfrost.polyplus.nameplate.engine.internal.layout

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import kotlin.math.floor

internal class OrnamentAnchor private constructor(
    val edge: Anchor,
    val side: Side,
    val at: Double?,
    private val fromEnd: Boolean,
) {
    enum class Side {
        EDGE,
        NAME_LEFT,
        NAME_RIGHT,
    }

    val defaultPivot: Anchor
        get() = when (side) {
            Side.EDGE -> edge
            Side.NAME_LEFT -> Anchor.RIGHT
            Side.NAME_RIGHT -> Anchor.LEFT
        }

    private var mirror: OrnamentAnchor? = null

    val mirrored: OrnamentAnchor
        get() = mirror ?: when (side) {
            Side.EDGE -> OrnamentAnchor(edge.mirrored, side, at, if (alongX(edge)) !fromEnd else fromEnd)
            Side.NAME_LEFT -> OrnamentAnchor(Anchor.RIGHT, Side.NAME_RIGHT, null, false)
            Side.NAME_RIGHT -> OrnamentAnchor(Anchor.LEFT, Side.NAME_LEFT, null, false)
        }.also { mirror = it }

    fun x(rect: PixelRect, block: TagBlock): Int = when (side) {
        Side.NAME_LEFT -> block.nameX
        Side.NAME_RIGHT -> block.nameX + block.nameWidth
        Side.EDGE -> edgeX(rect)
    }

    fun y(rect: PixelRect, block: TagBlock): Int = when (side) {
        Side.NAME_LEFT, Side.NAME_RIGHT -> block.nameLineY + TagBlock.GLYPH_HEIGHT / 2
        Side.EDGE -> edgeY(rect)
    }

    fun edgeX(rect: PixelRect): Int = if (at != null && alongX(edge)) along(rect.x0, rect.x1) else edge.x(rect)

    fun edgeY(rect: PixelRect): Int = if (at != null && !alongX(edge)) along(rect.y0, rect.y1) else edge.y(rect)

    private fun along(from: Int, to: Int): Int {
        val offset = floor(checkNotNull(at) * (to - from)).toInt()
        return if (fromEnd) to - offset else from + offset
    }

    companion object {
        const val NAME_LEFT = "name_left"
        const val NAME_RIGHT = "name_right"

        val NAMES: List<String> = Anchor.entries.map { it.name.lowercase() } + listOf(NAME_LEFT, NAME_RIGHT)

        private val ALONG = setOf(Anchor.TOP, Anchor.BOTTOM, Anchor.LEFT, Anchor.RIGHT)

        fun takesAt(name: String): Boolean = Anchor.named(name)?.let { it in ALONG } == true

        fun isName(name: String): Boolean = name == NAME_LEFT || name == NAME_RIGHT

        fun named(name: String, at: Double? = null): OrnamentAnchor? = when (name) {
            NAME_LEFT -> OrnamentAnchor(Anchor.LEFT, Side.NAME_LEFT, null, false)
            NAME_RIGHT -> OrnamentAnchor(Anchor.RIGHT, Side.NAME_RIGHT, null, false)
            else -> Anchor.named(name)?.let { OrnamentAnchor(it, Side.EDGE, at?.takeIf { _ -> it in ALONG }, false) }
        }

        fun of(edge: Anchor): OrnamentAnchor = OrnamentAnchor(edge, Side.EDGE, null, false)

        private fun alongX(edge: Anchor): Boolean = edge == Anchor.TOP || edge == Anchor.BOTTOM
    }
}
