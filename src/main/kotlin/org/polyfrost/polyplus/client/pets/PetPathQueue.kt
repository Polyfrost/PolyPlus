package org.polyfrost.polyplus.client.pets

import kotlin.math.min

internal class PetPathQueue(private val pool: Int, private val perPet: Int) {
    class Turn {
        var budget = 0
            internal set
        var headroom = 0
            internal set
        var taken = 0
        var work = 0
    }

    fun interface Stepper {
        fun step(index: Int, turn: Turn): Boolean
    }

    private val turn = Turn()
    private var cursor = 0
    private var lastTick = Long.MIN_VALUE

    fun reset() {
        cursor = 0
        lastTick = Long.MIN_VALUE
    }

    fun serve(tick: Long, count: Int, stepper: Stepper): Boolean {
        if (tick == lastTick) return false
        lastTick = tick
        if (count <= 0) return true
        val start = Math.floorMod(cursor, count)
        var allowance = pool
        var headroom = pool
        var last = -1
        var lastWorker = -1
        for (i in 0 until count) {
            if (allowance <= 0) break
            val index = (start + i) % count
            turn.budget = min(allowance, perPet)
            turn.headroom = headroom
            turn.taken = 0
            turn.work = 0
            if (!stepper.step(index, turn)) continue
            allowance -= turn.taken.coerceIn(0, turn.budget)
            headroom -= turn.work.coerceIn(0, headroom)
            last = index
            if (turn.work > 0) lastWorker = index
        }
        if (allowance <= 0 && last >= 0) {
            cursor = (last + 1) % count
        } else if (lastWorker >= 0) {
            cursor = (lastWorker + 1) % count
        }
        return true
    }
}

internal class PetPathCredit {
    var credit = 0
        private set
    var work = 0
        private set
    private var cost = 1
    private var budget = 0
    private var headroom = 0
    private var available = 0

    fun reset() {
        credit = 0
        work = 0
    }

    fun begin(cost: Int, budget: Int, headroom: Int) {
        this.cost = cost
        this.budget = budget
        this.headroom = headroom
        available = credit + budget
        work = 0
    }

    fun affordable(): Boolean = available >= cost && work + cost <= headroom

    fun charge() {
        available -= cost
        work += cost
    }

    fun end(searching: Boolean): Int {
        credit = if (searching) min(available, cost) else 0
        return budget - min(budget, available - credit)
    }
}
