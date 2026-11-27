package org.polyfrost.polyplus.client.nameplate.engine

class LongObjectMap<V : Any>(initialCapacity: Int = 16) {
    private var keys: LongArray
    private var values: Array<Any?>
    private var mask: Int

    var size: Int = 0
        private set

    val capacity: Int
        get() = keys.size

    init {
        var cap = 8
        while (cap < initialCapacity * 2) cap = cap shl 1
        keys = LongArray(cap)
        values = arrayOfNulls(cap)
        mask = cap - 1
    }

    operator fun get(key: Long): V? {
        var i = slot(key)
        while (true) {
            val value = values[i] ?: return null
            if (keys[i] == key) {
                @Suppress("UNCHECKED_CAST")
                return value as V
            }
            i = (i + 1) and mask
        }
    }

    operator fun set(key: Long, value: V) {
        var i = slot(key)
        while (true) {
            if (values[i] == null) break
            if (keys[i] == key) {
                values[i] = value
                return
            }
            i = (i + 1) and mask
        }
        keys[i] = key
        values[i] = value
        size++
        if (size * 2 > keys.size) grow()
    }

    fun remove(key: Long): V? {
        var i = slot(key)
        while (true) {
            if (values[i] == null) return null
            if (keys[i] == key) break
            i = (i + 1) and mask
        }
        @Suppress("UNCHECKED_CAST")
        val removed = values[i] as V
        var hole = i
        var j = (i + 1) and mask
        while (values[j] != null) {
            val ideal = slot(keys[j])
            if (((j - ideal) and mask) >= ((j - hole) and mask)) {
                keys[hole] = keys[j]
                values[hole] = values[j]
                hole = j
            }
            j = (j + 1) and mask
        }
        values[hole] = null
        size--
        return removed
    }

    fun clear() {
        if (size == 0) return
        values.fill(null)
        size = 0
    }

    fun keyAt(index: Int): Long = keys[index]

    fun valueAt(index: Int): V? {
        @Suppress("UNCHECKED_CAST")
        return values[index] as V?
    }

    private fun slot(key: Long): Int = ((key * MIX) ushr 32).toInt() and mask

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val cap = oldKeys.size shl 1
        keys = LongArray(cap)
        values = arrayOfNulls(cap)
        mask = cap - 1
        for (n in oldKeys.indices) {
            val value = oldValues[n] ?: continue
            var i = slot(oldKeys[n])
            while (values[i] != null) i = (i + 1) and mask
            keys[i] = oldKeys[n]
            values[i] = value
        }
    }

    private companion object {
        const val MIX: Long = -7046029254386353131L
    }
}
