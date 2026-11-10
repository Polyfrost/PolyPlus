package org.polyfrost.polyplus.nameplate.engine.api.text

fun interface TextBackdrop {
    fun sample(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int, sink: (Int) -> Unit)
}
