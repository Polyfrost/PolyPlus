package org.polyfrost.polyplus.client.emotes

object EmoteServerClock {
    private const val SAMPLES = 9

    private val offsets = LongArray(SAMPLES)
    @Volatile
    private var count = 0
    private var next = 0

    @Volatile
    private var offsetMs = 0L

    val synced: Boolean
        get() = count > 0

    fun offset(): Long = offsetMs

    @Synchronized
    fun sample(serverMs: Long, sentMs: Long, serverReceivedMs: Long? = null) {
        val receivedMs = System.currentTimeMillis()
        if (receivedMs < sentMs) return
        offsets[next] = offsetFor(serverMs, sentMs, serverReceivedMs, receivedMs)
        next = (next + 1) % SAMPLES
        if (count < SAMPLES) count++
        val sorted = offsets.copyOf(count)
        sorted.sort()
        offsetMs = sorted[count / 2]
    }

    internal fun offsetFor(serverMs: Long, sentMs: Long, serverReceivedMs: Long?, receivedMs: Long): Long =
        if (serverReceivedMs == null) serverMs - (sentMs + receivedMs) / 2
        else ((serverReceivedMs - sentMs) + (serverMs - receivedMs)) / 2

    fun now(): Long = System.currentTimeMillis() + offsetMs

    fun elapsedSince(serverMs: Long): Long = now() - serverMs
}
