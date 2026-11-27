package org.polyfrost.polyplus.client.pets

internal object PetTimers {
    fun due(now: Long, last: Long, every: Long): Boolean =
        last == Long.MIN_VALUE || now < last || now - last >= every

    fun due(now: Int, last: Int, every: Int): Boolean =
        last == Int.MIN_VALUE || now < last || now - last >= every
}
