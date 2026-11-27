package org.polyfrost.polyplus.nameplate.engine.internal.material

internal class MaterialSprite(val width: Int, val height: Int, val frames: Int, val cells: IntArray) {
    fun ref(x: Int, y: Int, frame: Int): Int =
        if (x !in 0 until width || y !in 0 until height) -1 else cells[(frame.mod(frames) * height + y) * width + x]
}
