package org.polyfrost.polyplus.client.emotes.playback

internal class EmoteSceneClock {
    private var ids = LongArray(INITIAL_WEARERS)
    private var seenAt = DoubleArray(INITIAL_WEARERS)
    private var count = 0
    private var shift = 0.0
    private var last = Double.NaN
    private var lastReal = Double.NaN

    fun at(wearer: Long, wanted: Double, shift: Double, real: Double): Double {
        val gap = real - lastReal
        if (gap > IDLE_SECONDS && indexOf(wearer) >= 0) {
            for (i in 0 until count) seenAt[i] += gap
        }
        lastReal = real
        expire(real)
        val fresh = count == 0
        mark(wearer, real)
        val base = wanted - shift
        this.shift = when {
            last.isNaN() -> shift
            fresh -> maxOf(shift, last - base)
            else -> this.shift
        }
        var time = wanted + (this.shift - shift)
        if (time < last && last - time <= REWIND_SECONDS) time = last
        last = time
        return time
    }

    fun forget(wearer: Long) {
        val at = indexOf(wearer)
        if (at >= 0) removeAt(at)
    }

    private fun expire(real: Double) {
        var i = 0
        while (i < count) {
            if (real - seenAt[i] > IDLE_SECONDS) removeAt(i) else i++
        }
    }

    private fun mark(wearer: Long, real: Double) {
        val at = indexOf(wearer)
        if (at >= 0) {
            seenAt[at] = real
            return
        }
        if (count == ids.size) {
            ids = ids.copyOf(count * 2)
            seenAt = seenAt.copyOf(count * 2)
        }
        ids[count] = wearer
        seenAt[count] = real
        count++
    }

    private fun indexOf(wearer: Long): Int {
        for (i in 0 until count) {
            if (ids[i] == wearer) return i
        }
        return -1
    }

    private fun removeAt(at: Int) {
        count--
        ids[at] = ids[count]
        seenAt[at] = seenAt[count]
    }

    private companion object {
        const val INITIAL_WEARERS = 4
        const val IDLE_SECONDS = 1.0
        const val REWIND_SECONDS = 1.0
    }
}
