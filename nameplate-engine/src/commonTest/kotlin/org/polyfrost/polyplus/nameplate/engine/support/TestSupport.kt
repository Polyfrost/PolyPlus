package org.polyfrost.polyplus.nameplate.engine.support

import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal fun Palette.Companion.of(vararg slots: Palette.Slot): Palette = Palette(slots.toList())

internal fun Palette.with(id: String, vararg ramp: Int): Palette = with(id, ramp, ramp)

internal fun pixelKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

internal fun pixelKeyX(key: Long): Int = (key shr 32).toInt()

internal fun pixelKeyY(key: Long): Int = key.toInt()

internal fun contrastRatio(a: Int, b: Int): Double {
    val la = Contrast.luminance(a)
    val lb = Contrast.luminance(b)
    return if (la >= lb) (la + 0.05) / (lb + 0.05) else (lb + 0.05) / (la + 0.05)
}
