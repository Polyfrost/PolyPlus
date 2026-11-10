package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.BodyEdge

internal sealed interface MaterialInput {
    data class Param(val name: String) : MaterialInput

    class Expression(val source: String, internal val expression: MaterialExpression) : MaterialInput

    data class BodyEdgeInput(val lane: Int) : MaterialInput {
        fun value(edge: BodyEdge): Double = when (lane) {
            TOP_LEFT -> edge.topLeft
            TOP_RIGHT -> edge.topRight
            BOTTOM_RIGHT -> edge.bottomRight
            BOTTOM_LEFT -> edge.bottomLeft
            else -> edge.rings
        }.toDouble()

        companion object {
            const val TOP_LEFT = 0
            const val TOP_RIGHT = 1
            const val BOTTOM_RIGHT = 2
            const val BOTTOM_LEFT = 3
            const val RINGS = 4
            const val LANES = 5
        }
    }
}
