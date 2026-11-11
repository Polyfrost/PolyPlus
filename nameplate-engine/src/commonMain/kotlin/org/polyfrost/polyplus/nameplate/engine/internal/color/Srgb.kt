package org.polyfrost.polyplus.nameplate.engine.internal.color

import kotlin.math.pow
import kotlin.math.roundToInt

internal object Srgb {
    private const val THRESHOLD = 0.04045
    private const val LINEAR_SLOPE = 12.92

    private val LINEAR = DoubleArray(256) { i ->
        val c = i / 255.0
        if (c <= THRESHOLD) c / LINEAR_SLOPE else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun linear(channel8: Int): Double = LINEAR[channel8]

    fun encode8(linear: Double): Int {
        val encoded = if (linear <= THRESHOLD / LINEAR_SLOPE) {
            linear * LINEAR_SLOPE
        } else {
            1.055 * linear.pow(1.0 / 2.4) - 0.055
        }
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }
}
