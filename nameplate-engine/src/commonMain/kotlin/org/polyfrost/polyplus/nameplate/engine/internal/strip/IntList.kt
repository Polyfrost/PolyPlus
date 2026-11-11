package org.polyfrost.polyplus.nameplate.engine.internal.strip

internal class IntList(capacity: Int = 16) {
    private var values = IntArray(capacity)

    var size: Int = 0
        private set

    operator fun get(index: Int): Int = values[index]

    operator fun set(index: Int, value: Int) {
        values[index] = value
    }

    fun add(value: Int) {
        if (size == values.size) {
            values = values.copyOf(maxOf(16, size * 2))
        }

        values[size++] = value
    }

    fun clear() {
        size = 0
    }

    fun toIntArray(): IntArray = values.copyOf(size)
}
