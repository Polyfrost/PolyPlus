package org.polyfrost.polyplus.nameplate.engine.api.shader

class TexturePixels(
    val width: Int,
    val height: Int,
    argb: IntArray,
    sequence: IntArray = IntArray(0),
    ticks: IntArray = IntArray(0),
) {
    internal val pixels: IntArray = argb.copyOf()
    private val sequence: IntArray = sequence.copyOf()
    private val ends: IntArray = IntArray(ticks.size)

    val frames: Int
        get() = pixels.size / (width * height)

    init {
        require(width > 0 && height > 0 && pixels.isNotEmpty() && pixels.size % (width * height) == 0) {
            "Texture needs $width x $height pixels per frame"
        }
        require(sequence.size == ticks.size && sequence.all { it in 0 until frames }) { "Texture animation needs one time per frame" }
        var total = 0
        for ((k, time) in ticks.withIndex()) {
            require(time > 0) { "Texture frame times must be positive" }
            total += time
            ends[k] = total
        }
    }

    fun frameArgb(frame: Int = 0): IntArray {
        val size = width * height
        val at = frame.coerceIn(0, frames - 1) * size
        return pixels.copyOfRange(at, at + size)
    }

    internal fun offset(tick: Long): Int {
        if (ends.isEmpty()) return 0
        val at = tick.mod(ends.last().toLong()).toInt()
        var k = 0
        while (ends[k] <= at) {
            k++
        }
        return sequence[k] * width * height
    }

    companion object {
        const val TICKS_PER_SECOND = 20
    }
}
