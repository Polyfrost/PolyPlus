package org.polyfrost.polyplus.nameplate.engine.internal.layout

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect

internal enum class Anchor(private val column: Edge, private val row: Edge) {
    TOP_LEFT(Edge.START, Edge.START),
    TOP(Edge.MIDDLE, Edge.START),
    TOP_RIGHT(Edge.END, Edge.START),
    LEFT(Edge.START, Edge.MIDDLE),
    RIGHT(Edge.END, Edge.MIDDLE),
    BOTTOM_LEFT(Edge.START, Edge.END),
    BOTTOM(Edge.MIDDLE, Edge.END),
    BOTTOM_RIGHT(Edge.END, Edge.END),
    ;

    val mirrored: Anchor
        get() = entries.first { it.row == row && it.column == column.mirrored }

    fun x(rect: PixelRect): Int = column.at(rect.x0, rect.x1)

    fun y(rect: PixelRect): Int = row.at(rect.y0, rect.y1)

    fun pivotX(width: Int): Int = column.at(0, width)

    fun pivotY(height: Int): Int = row.at(0, height)

    private enum class Edge {
        START,
        MIDDLE,
        END,
        ;

        val mirrored: Edge
            get() = when (this) {
                START -> END
                MIDDLE -> MIDDLE
                END -> START
            }

        fun at(from: Int, to: Int): Int = when (this) {
            START -> from
            MIDDLE -> (from + to).floorDiv(2)
            END -> to
        }
    }

    companion object {
        fun named(name: String): Anchor? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
