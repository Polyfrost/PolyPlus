package org.polyfrost.polyplus.client.pets

internal class PetLongMap<V : Any>(initialCapacity: Int = 16) {
    private var keys = LongArray(initialCapacity)
    private var values = arrayOfNulls<Any>(initialCapacity)
    private var mask = initialCapacity - 1

    var size = 0
        private set

    val slots: Int get() = keys.size

    @Suppress("UNCHECKED_CAST")
    fun valueAt(slot: Int): V? = values[slot] as V?

    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Long): V? {
        var slot = hash(key) and mask
        while (true) {
            val value = values[slot] ?: return null
            if (keys[slot] == key) return value as V
            slot = (slot + 1) and mask
        }
    }

    fun put(key: Long, value: V) {
        if ((size + 1) * 4 > keys.size * 3) grow()
        var slot = hash(key) and mask
        while (true) {
            if (values[slot] == null) {
                keys[slot] = key
                values[slot] = value
                size++
                return
            }
            if (keys[slot] == key) {
                values[slot] = value
                return
            }
            slot = (slot + 1) and mask
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun remove(key: Long): V? {
        var slot = hash(key) and mask
        while (true) {
            val value = values[slot] ?: return null
            if (keys[slot] == key) {
                shiftBack(slot)
                size--
                return value as V
            }
            slot = (slot + 1) and mask
        }
    }

    fun removeStale(keep: (V) -> Boolean) {
        var slot = 0
        while (slot < keys.size) {
            @Suppress("UNCHECKED_CAST")
            val value = values[slot] as V?
            if (value != null && !keep(value)) {
                shiftBack(slot)
                size--
            } else {
                slot++
            }
        }
    }

    fun clear() {
        if (size == 0) return
        java.util.Arrays.fill(values, null)
        size = 0
    }

    private fun shiftBack(start: Int) {
        var hole = start
        var slot = start
        while (true) {
            slot = (slot + 1) and mask
            if (values[slot] == null) break
            val home = hash(keys[slot]) and mask
            if (((slot - home) and mask) >= ((slot - hole) and mask)) {
                keys[hole] = keys[slot]
                values[hole] = values[slot]
                hole = slot
            }
        }
        values[hole] = null
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val capacity = oldKeys.size * 2
        keys = LongArray(capacity)
        values = arrayOfNulls(capacity)
        mask = capacity - 1
        for (i in oldKeys.indices) {
            val value = oldValues[i] ?: continue
            var slot = hash(oldKeys[i]) and mask
            while (values[slot] != null) slot = (slot + 1) and mask
            keys[slot] = oldKeys[i]
            values[slot] = value
        }
    }

    private fun hash(key: Long): Int {
        val mixed = key * -0x61c8864680b583ebL
        return (mixed ushr 32).toInt() xor mixed.toInt()
    }
}
