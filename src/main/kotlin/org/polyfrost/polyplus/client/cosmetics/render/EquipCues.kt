package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.polyplus.client.nameplate.engine.LongObjectMap

internal class EquipCues(
    private val slots: Int,
    private val windowSeconds: Double = WINDOW_SECONDS,
    private val pendingSeconds: Double = PENDING_SECONDS,
) {
    private inner class Wearer {
        var drawnAt = Double.NEGATIVE_INFINITY
        var drawnFrame = NEVER
        var lastFrame = NEVER
        val shown = LongArray(slots)
        val shownFrame = IntArray(slots) { NEVER }
        val target = IntArray(slots) { NONE }
        val since = DoubleArray(slots)
    }

    private val wearers = LongObjectMap<Wearer>()
    private var stale = LongArray(0)
    private var cachedId = 0L
    private var cached: Wearer? = null

    fun changed(wearer: Long, slot: Int, target: Int?, now: Double) {
        if (slot !in 0 until slots) return
        val state = wearers[wearer] ?: return
        if (target == null || now - state.drawnAt > windowSeconds) {
            state.target[slot] = NONE
            if (target == null) state.shownFrame[slot] = NEVER
            return
        }
        if (state.shownFrame[slot] == state.drawnFrame && cosmeticOf(state.shown[slot]) == target) {
            state.target[slot] = NONE
            return
        }
        state.target[slot] = target
        state.since[slot] = now
    }

    fun seen(wearer: Long, frame: Int, now: Double) {
        roll(state(wearer), frame, now)
    }

    fun hidden(wearer: Long) {
        wearers[wearer]?.target?.fill(NONE)
    }

    fun drawn(wearer: Long, frame: Int, slot: Int, cosmetic: Int, colorway: Int, now: Double): Boolean {
        if (slot !in 0 until slots) return false
        val state = state(wearer)
        roll(state, frame, now)
        val identity = identity(cosmetic, colorway)
        val seen = state.shownFrame[slot]
        val hadPrevious = seen == frame || seen == state.lastFrame
        val previous = state.shown[slot]
        state.shown[slot] = identity
        state.shownFrame[slot] = frame
        val target = state.target[slot]
        if (target == NONE) return false
        if (now - state.since[slot] > pendingSeconds) {
            state.target[slot] = NONE
            return false
        }
        if (target != cosmetic) return false
        state.target[slot] = NONE
        return !hadPrevious || previous != identity
    }

    fun forget(wearer: Long) {
        wearers.remove(wearer)
        cached = null
    }

    fun prune(now: Double) {
        if (wearers.size == 0) return
        if (stale.size < wearers.size) stale = LongArray(wearers.size)
        var count = 0
        for (slot in 0 until wearers.capacity) {
            val state = wearers.valueAt(slot) ?: continue
            if (now - state.drawnAt > windowSeconds) stale[count++] = wearers.keyAt(slot)
        }
        if (count == 0) return
        for (n in 0 until count) wearers.remove(stale[n])
        cached = null
    }

    fun clear() {
        wearers.clear()
        cached = null
    }

    private fun roll(state: Wearer, frame: Int, now: Double) {
        if (state.drawnFrame == frame) return
        if (now - state.drawnAt > windowSeconds) {
            state.target.fill(NONE)
            state.shownFrame.fill(NEVER)
        }
        state.lastFrame = state.drawnFrame
        state.drawnFrame = frame
        state.drawnAt = now
    }

    private fun state(wearer: Long): Wearer {
        val hit = cached
        if (hit != null && cachedId == wearer) return hit
        var state = wearers[wearer]
        if (state == null) {
            state = Wearer()
            wearers[wearer] = state
        }
        cachedId = wearer
        cached = state
        return state
    }

    private fun identity(cosmetic: Int, colorway: Int): Long = (cosmetic.toLong() shl 32) or (colorway.toLong() and 0xFFFFFFFFL)

    private fun cosmeticOf(identity: Long): Int = (identity ushr 32).toInt()

    companion object {
        const val WINDOW_SECONDS = 30.0
        const val PENDING_SECONDS = 60.0
        private const val NONE = Int.MIN_VALUE
        private const val NEVER = Int.MIN_VALUE
    }
}
