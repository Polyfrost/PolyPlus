package org.polyfrost.polyplus.client.emotes.duo

import java.util.UUID

internal class CancelledStarts(private val windowMs: Long = 10_000L, private val limit: Int = 8) {
    private class Entry(val emoteId: Int, val partner: UUID, val until: Long)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(emoteId: Int, partner: UUID, now: Long) {
        prune(now)
        entries.removeAll { it.emoteId == emoteId && it.partner == partner }
        entries.addLast(Entry(emoteId, partner, now + windowMs))
        while (entries.size > limit) entries.removeFirst()
    }

    @Synchronized
    fun forget(partner: UUID) {
        entries.removeAll { it.partner == partner }
    }

    @Synchronized
    fun matches(emoteId: Int, partner: UUID?, now: Long): Boolean {
        prune(now)
        return entries.any { it.emoteId == emoteId && (partner == null || it.partner == partner) }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun prune(now: Long) {
        entries.removeAll { it.until <= now }
    }
}
