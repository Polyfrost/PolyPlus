package org.polyfrost.polyplus.client.pets

import java.util.Arrays
import kotlin.math.floor

internal class PetGrid(private val cell: Double) {
    class IntBag {
        var items = IntArray(8)
            private set
        var size = 0
            internal set

        operator fun get(index: Int): Int = items[index]

        fun add(value: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = value
        }

        fun remove(value: Int) {
            for (i in 0 until size) {
                if (items[i] != value) continue
                items[i] = items[--size]
                return
            }
        }

        fun sort() = Arrays.sort(items, 0, size)
    }

    private val cells = PetLongMap<IntBag>()
    private val spare = ArrayList<IntBag>()
    private var keys = LongArray(16)
    private var placed = BooleanArray(16)

    fun clear() {
        for (slot in 0 until cells.slots) {
            val bag = cells.valueAt(slot) ?: continue
            bag.size = 0
            spare += bag
        }
        cells.clear()
        placed.fill(false)
    }

    fun add(index: Int, x: Double, z: Double) {
        if (index >= placed.size) {
            val capacity = maxOf(index + 1, placed.size * 2)
            keys = keys.copyOf(capacity)
            placed = placed.copyOf(capacity)
        }
        if (placed[index]) bagAt(keys[index])?.remove(index)
        val key = keyOf(cellOf(x), cellOf(z))
        keys[index] = key
        placed[index] = true
        bagFor(key).add(index)
    }

    fun move(index: Int, x: Double, z: Double) {
        if (index !in placed.indices || !placed[index]) return
        val key = keyOf(cellOf(x), cellOf(z))
        val old = keys[index]
        if (key == old) return
        bagAt(old)?.remove(index)
        keys[index] = key
        bagFor(key).add(index)
    }

    fun near(x: Double, z: Double, reach: Double, out: IntBag) {
        out.size = 0
        val minX = cellOf(x - reach)
        val maxX = cellOf(x + reach)
        val minZ = cellOf(z - reach)
        val maxZ = cellOf(z + reach)
        for (cx in minX..maxX) {
            for (cz in minZ..maxZ) {
                val bag = bagAt(keyOf(cx, cz)) ?: continue
                for (i in 0 until bag.size) out.add(bag[i])
            }
        }
        out.sort()
    }

    private fun bagAt(key: Long): IntBag? = cells[key]

    private fun bagFor(key: Long): IntBag {
        cells[key]?.let { return it }
        val bag = if (spare.isEmpty()) IntBag() else spare.removeAt(spare.size - 1)
        cells.put(key, bag)
        return bag
    }

    private fun cellOf(value: Double): Int = floor(value / cell).toInt()

    private fun keyOf(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xffffffffL)
}
