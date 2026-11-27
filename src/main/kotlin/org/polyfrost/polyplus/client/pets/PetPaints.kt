package org.polyfrost.polyplus.client.pets

import kotlin.math.min

internal object PetFrames {
    @Volatile
    var frame = 0L
        private set

    fun advance() {
        frame++
    }
}

internal class PetPaintSlot<A : Any> {
    var asset: A? = null
        private set
    var generation = -1
        private set
    var colorway = Int.MIN_VALUE
        private set
    var colors: Map<String, Int>? = null
        private set
    var paint: PetPaint? = null
        private set

    fun matches(asset: A, generation: Int, colorway: Int, colors: Map<String, Int>?, now: Long): Boolean {
        val held = paint ?: return false
        return !held.expired(now) && this.asset === asset && this.generation == generation &&
            this.colorway == colorway && this.colors === colors
    }

    fun hold(asset: A, generation: Int, colorway: Int, colors: Map<String, Int>?, paint: PetPaint?) {
        this.asset = asset
        this.generation = generation
        this.colorway = colorway
        this.colors = colors
        this.paint = paint
    }

    fun drop(asset: A): Boolean {
        if (this.asset !== asset) return false
        this.asset = null
        generation = -1
        colorway = Int.MIN_VALUE
        colors = null
        paint = null
        return true
    }
}

internal interface PetPaintEntry {
    var drawnFrame: Long
}

internal class PetPaintCache<P : PetPaintEntry>(bytesPerPaint: Long) {
    private val painted = LinkedHashMap<Long, P>()

    val budget: Int = if (bytesPerPaint <= 0L) {
        MAX_KEPT
    } else {
        (MAX_BYTES / bytesPerPaint).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    val kept: Int = min(MAX_KEPT, budget)

    val size: Int get() = painted.size

    operator fun get(key: Long): P? = painted[key]

    fun admit(frame: Long, release: (P) -> Unit): Boolean {
        while (painted.size >= kept) {
            var oldestKey = 0L
            var oldest: P? = null
            for ((key, paint) in painted) {
                if (paint.drawnFrame >= frame - 1) continue
                if (oldest == null || paint.drawnFrame < oldest.drawnFrame) {
                    oldestKey = key
                    oldest = paint
                }
            }
            if (oldest == null) break
            painted.remove(oldestKey)
            release(oldest)
        }
        return painted.size < budget
    }

    fun put(key: Long, paint: P, frame: Long) {
        paint.drawnFrame = frame
        painted[key] = paint
    }

    fun clear(release: (P) -> Unit) {
        for (paint in painted.values) release(paint)
        painted.clear()
    }

    companion object {
        const val MAX_KEPT = 16
        const val MAX_BYTES = 16L * 1024 * 1024
    }
}
