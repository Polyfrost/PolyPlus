package org.polyfrost.polyplus.nameplate.engine.internal.color

internal object ColorLerp {
    fun srgb(from: Int, to: Int, numerator: Int, denominator: Int): Int {
        var result = 0
        for (shift in 24 downTo 0 step 8) {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            result = result or ((a + ((b - a) * numerator).floorDiv(denominator)) shl shift)
        }
        return result
    }

    fun along(stops: IntArray, position: Int, positions: Int): Int {
        if (stops.size == 1 || positions <= 1) return stops[0]

        val segments = stops.size - 1
        val scaled = position * segments
        val segment = minOf(scaled / (positions - 1), segments - 1)
        return srgb(stops[segment], stops[segment + 1], scaled - segment * (positions - 1), positions - 1)
    }
}
