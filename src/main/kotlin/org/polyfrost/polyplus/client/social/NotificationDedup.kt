package org.polyfrost.polyplus.client.social

import java.util.concurrent.ConcurrentHashMap

internal object NotificationDedup {
    private const val WINDOW_MS = 3000L

    private val lastSeen = ConcurrentHashMap<String, Long>()

    fun shouldNotify(key: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastSeen.put(key, now)
        if (lastSeen.size > 256) lastSeen.entries.removeIf { now - it.value > WINDOW_MS }
        return previous == null || now - previous > WINDOW_MS
    }
}
