package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame

internal sealed interface MaterialInput {
    fun value(frame: MaterialFrame): Double

    data class Param(val name: String) : MaterialInput {
        override fun value(frame: MaterialFrame): Double = frame.params[name] ?: 0.0
    }

    class Expression(val source: String, val expression: MolangMaterialExpression) : MaterialInput {
        override fun value(frame: MaterialFrame): Double = expression.evaluate(frame)
    }

    data class BodyEdgeInput(val lane: Int) : MaterialInput {
        override fun value(frame: MaterialFrame): Double {
            val edge = frame.edge
            return when (lane) {
                TOP_LEFT -> edge.topLeft
                TOP_RIGHT -> edge.topRight
                BOTTOM_RIGHT -> edge.bottomRight
                BOTTOM_LEFT -> edge.bottomLeft
                else -> edge.rings
            }.toDouble()
        }

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
