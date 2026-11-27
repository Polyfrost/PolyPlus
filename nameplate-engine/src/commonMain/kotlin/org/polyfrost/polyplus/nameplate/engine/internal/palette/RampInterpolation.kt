package org.polyfrost.polyplus.nameplate.engine.internal.palette

import org.polyfrost.polyplus.nameplate.engine.internal.color.ColorLerp

internal object RampInterpolation {
    fun expand(stops: IntArray, tones: Int): IntArray {
        if (stops.size < 2 || tones <= stops.size) return stops.copyOf()

        return IntArray(tones) { ColorLerp.along(stops, it, tones) }
    }
}
