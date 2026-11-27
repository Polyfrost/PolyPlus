package org.polyfrost.polyplus.client.nameplate.material

class NameplateDrawBuffers(private val slots: Int, val width: Int) {
    val buffers = IntArray(slots * width)
    private val framebuffers = IntArray(slots) { NO_FRAMEBUFFER }
    private val lengths = IntArray(slots)
    private val targets = IntArray(slots)
    private var next = 0

    fun reset() {
        framebuffers.fill(NO_FRAMEBUFFER)
        next = 0
    }

    fun find(framebuffer: Int): Int {
        for (slot in 0 until slots) if (framebuffers[slot] == framebuffer) return slot
        return -1
    }

    fun claim(framebuffer: Int): Int {
        val slot = next
        next = (next + 1) % slots
        framebuffers[slot] = framebuffer
        lengths[slot] = 0
        targets[slot] = NONE
        return slot
    }

    fun commit(slot: Int, count: Int) {
        val at = offset(slot)
        val n = count.coerceIn(0, width)
        var length = 0
        var used = 0
        var first = NONE
        for (i in 0 until n) {
            val buffer = buffers[at + i]
            if (buffer == NONE) continue
            if (first == NONE) first = buffer
            used++
            length = i + 1
        }
        lengths[slot] = length
        targets[slot] = if (used > 1) first else NONE
    }

    fun offset(slot: Int): Int = slot * width

    fun target(slot: Int): Int = targets[slot]

    fun length(slot: Int): Int = lengths[slot]

    companion object {
        const val NONE: Int = 0
        private const val NO_FRAMEBUFFER = -1
    }
}
