package org.polyfrost.polyplus.nameplate.engine.internal.track

internal object Interpolation {
    const val STEP = 0
    const val LINEAR = 1
    const val EASE = 2

    private const val EASE_UNITS = 1024L

    fun parse(name: String): Int? = when (name) {
        "step" -> STEP
        "linear" -> LINEAR
        "ease" -> EASE
        else -> null
    }

    fun blend(mode: Int, from: Int, to: Int, elapsed: Int, span: Int): Int {
        val delta = (to - from).toLong()
        val t = elapsed.toLong()
        val s = span.toLong()
        return when (mode) {
            LINEAR -> from + roundedDivide(delta * t, s)
            EASE -> from + roundedDivide(delta * smoothstep(t, s), EASE_UNITS)
            else -> from
        }
    }

    private fun smoothstep(t: Long, span: Long): Long = t * t * (3 * span - 2 * t) * EASE_UNITS / (span * span * span)

    private fun roundedDivide(numerator: Long, denominator: Long): Int =
        (2 * numerator + denominator).floorDiv(2 * denominator).toInt()
}
