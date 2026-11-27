package org.polyfrost.polyplus.client.cosmetics.moments

import java.util.UUID

internal class MomentCooldowns(private val moments: Int, private val windowMs: Long) {
    private val fired = HashMap<UUID, LongArray>()

    fun coolingDown(wearer: UUID, moment: Int, now: Long): Boolean {
        val last = fired[wearer]?.get(moment) ?: return false
        return last != NEVER && now - last < windowMs
    }

    fun record(wearer: UUID, moment: Int, now: Long) {
        fired.getOrPut(wearer) { LongArray(moments) { NEVER } }[moment] = now
    }

    fun prune(now: Long) {
        if (fired.isEmpty()) return
        fired.values.removeIf { times -> times.all { it == NEVER || now - it >= windowMs } }
    }

    fun size(): Int = fired.size

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
