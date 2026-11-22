package org.polyfrost.polyplus.nameplate.engine.internal.palette

import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette

internal class Palette(slots: List<Slot>) {
    val slots: List<Slot> = slots.toList()

    init {
        val ids = HashSet<String>()
        for (slot in this.slots) {
            require(ids.add(slot.id)) { "Duplicate palette slot '${slot.id}'" }
        }
    }

    fun ref(id: String, tone: Int = 0): Int {
        val index = indexOfSlot(id)
        require(tone >= 0 && tone < slots[index].size) { "Slot '$id' has no tone $tone" }
        return index * MAX_TONES + tone
    }

    fun with(id: String, ramp: IntArray, stops: IntArray): Palette {
        val index = indexOfSlot(id)
        val copy = slots.toMutableList()
        val slot = copy[index]
        copy[index] = Slot(slot.id, slot.name, ramp, stops)
        return Palette(copy)
    }

    fun resolve(): ResolvedPalette {
        val colors = IntArray(slots.size * MAX_TONES)
        for ((i, slot) in slots.withIndex()) {
            for (tone in 0 until MAX_TONES) {
                colors[i * MAX_TONES + tone] = slot.tone(minOf(tone, slot.size - 1))
            }
        }

        return ResolvedPalette(colors, Array(slots.size) { slots[it].stops() })
    }

    fun indexOfSlot(id: String): Int {
        val index = slots.indexOfFirst { it.id == id }
        require(index >= 0) { "No palette slot '$id'" }
        return index
    }

    class Slot(val id: String, val name: String, vararg ramp: Int) {
        private val ramp: IntArray = ramp.copyOf()
        private var stops: IntArray = this.ramp

        init {
            require(ramp.isNotEmpty() && ramp.size <= MAX_TONES) { "Slot '$id' needs 1 to $MAX_TONES tones" }
        }

        constructor(id: String, name: String, ramp: IntArray, stops: IntArray) : this(id, name, *ramp) {
            require(stops.isNotEmpty() && stops.size <= MAX_TONES) { "Slot '$id' needs 1 to $MAX_TONES stops" }
            this.stops = stops.copyOf()
        }

        fun stops(): IntArray = stops.copyOf()

        val size: Int
            get() = ramp.size

        fun tone(index: Int): Int = ramp[index]
    }

    companion object {
        const val MAX_TONES = 16
    }
}
