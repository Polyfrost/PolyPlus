package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.polyplus.client.pets.PetArchetype

internal object WearerJumpRules {
    const val JUMP_BLOCKS = 2.0
    private const val JUMP_BLOCKS_SQ = JUMP_BLOCKS * JUMP_BLOCKS

    fun jumped(dx: Double, dy: Double, dz: Double): Boolean = dx * dx + dy * dy + dz * dz > JUMP_BLOCKS_SQ

    fun offset(relative: Boolean, value: Double, current: Double): Double = if (relative) value else value - current

    fun petJumpsWithOwner(archetype: PetArchetype?): Boolean = archetype == PetArchetype.Shoulder

    fun anchorSwitched(previous: String?, current: String): Boolean = previous != null && previous != current
}

internal class WearerSlides {
    private var ids = IntArray(8)
    private var left = IntArray(8)

    var count = 0
        private set

    fun id(index: Int): Int = ids[index]

    fun start(entityId: Int) {
        var i = 0
        while (i < count) {
            if (ids[i] == entityId) {
                left[i] = SLIDE_TICKS
                return
            }
            i++
        }
        if (count == ids.size) {
            ids = ids.copyOf(count * 2)
            left = left.copyOf(count * 2)
        }
        ids[count] = entityId
        left[count] = SLIDE_TICKS
        count++
    }

    fun advance() {
        var i = 0
        while (i < count) {
            if (--left[i] > 0) {
                i++
                continue
            }
            count--
            ids[i] = ids[count]
            left[i] = left[count]
        }
    }

    fun clear() {
        count = 0
    }

    companion object {
        const val SLIDE_TICKS = 3
    }
}

internal class WearerJumpGate {
    private var tick = Long.MIN_VALUE
    private var ids = LongArray(16)
    private var count = 0

    fun first(wearer: Long, tick: Long): Boolean {
        if (tick != this.tick) {
            this.tick = tick
            count = 0
        }
        var i = 0
        while (i < count) {
            if (ids[i] == wearer) return false
            i++
        }
        if (count == ids.size) ids = ids.copyOf(count * 2)
        ids[count++] = wearer
        return true
    }
}
