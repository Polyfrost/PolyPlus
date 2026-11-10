package org.polyfrost.polyplus.nameplate.engine.internal.material

internal object MaterialClock {
    fun state(frame: Int, step: Int, loop: Int): Int = (frame.floorDiv(step)).mod(loop / step)

    fun drift(frame: Int, step: Int): Int = frame.floorDiv(step)
}
