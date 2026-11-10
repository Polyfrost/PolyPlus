package org.polyfrost.polyplus.nameplate.engine.internal.track

internal class Track(
    private val steps: IntArray,
    private val values: IntArray,
    private val modes: IntArray = IntArray(steps.size),
    private val molang: IntArray? = null,
    private val kind: Int = ChannelValue.FRAME,
) {
    val usesMolang: Boolean
        get() = molang != null

    fun at(step: Int, frame: MolangFrame? = null): Int {
        val i = keyAt(step)
        val mode = if (i + 1 < steps.size) modes[i + 1] else Interpolation.STEP
        return if (mode == Interpolation.STEP) {
            value(i, frame)
        } else {
            Interpolation.blend(mode, value(i, frame), value(i + 1, frame), maxOf(step - steps[i], 0), steps[i + 1] - steps[i])
        }
    }

    fun values(): IntArray = values.copyOf()

    fun range(bounds: MolangBounds?, into: IntArray) {
        for (i in values.indices) {
            val slot = molang?.get(i) ?: NO_SLOT
            val low = if (slot != NO_SLOT && bounds != null) bounds.min(slot) else values[i]
            val high = if (slot != NO_SLOT && bounds != null) bounds.max(slot) else values[i]
            into[0] = minOf(into[0], low)
            into[1] = maxOf(into[1], high)
        }
    }

    fun positionReach(frame: MolangFrame, step: Int? = null): Double {
        val slots = molang ?: return 0.0
        val first = step?.let(::keyAt) ?: 0
        val last = when {
            step == null -> slots.lastIndex
            first < slots.lastIndex && modes[first + 1] != Interpolation.STEP -> first + 1
            else -> first
        }
        var reach = 0.0
        for (i in first..last) {
            if (slots[i] != NO_SLOT) {
                reach = maxOf(reach, ChannelValue.positionReach(frame.channel(slots[i])))
            }
        }
        return reach
    }

    private fun keyAt(step: Int): Int {
        var i = 0
        while (i + 1 < steps.size && steps[i + 1] <= step) {
            i++
        }
        return i
    }

    private fun value(index: Int, frame: MolangFrame?): Int {
        val slots = molang
        if (slots == null || frame == null) return values[index]
        val slot = slots[index]
        return if (slot == NO_SLOT) values[index] else ChannelValue.convert(kind, frame.channel(slot))
    }

    companion object {
        const val NO_SLOT = -1
    }
}
