package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.internal.flag.FlagFitting

internal class Flag(
    val id: String,
    private val bands: IntArray,
    private val weights: IntArray,
    val overlay: Overlay?,
    private val overrides: Map<Int, IntArray>,
    val symmetric: Boolean,
    val columns: Boolean = false,
) {
    val bandCount: Int
        get() = bands.size

    fun band(index: Int): Int = bands[index]

    fun rows(rows: Int): IntArray? = overrides[rows]?.copyOf()
        ?: if (symmetric) FlagFitting.fitSymmetric(weights, rows) else FlagFitting.fit(weights, rows)
}

internal class Overlay(val kind: OverlayKind, private val colors: IntArray, val width: Int) {
    val colorCount: Int
        get() = colors.size

    fun color(index: Int): Int = colors[index]
}

internal enum class OverlayKind {
    CHEVRON,
    TRIANGLE,
    CIRCLE,
}
