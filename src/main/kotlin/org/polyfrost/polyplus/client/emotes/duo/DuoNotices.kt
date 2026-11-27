package org.polyfrost.polyplus.client.emotes.duo

import org.polyfrost.polyplus.client.network.http.responses.SharedEmoteResult

internal class NoticeQueue(private val showMs: Long, private val limit: Int = LIMIT) {
    private val pending = ArrayDeque<String>()
    private var current: String? = null
    private var shownMs = 0L
    private var lastShown = 0L

    @Synchronized
    fun add(text: String) {
        if (pending.isEmpty() && text == current) {
            shownMs = 0L
            return
        }
        if (pending.lastOrNull() == text) return
        pending.addLast(text)
        while (pending.size > limit) pending.removeFirst()
    }

    @Synchronized
    fun show(now: Long): String? {
        current?.let { text ->
            shownMs += (now - lastShown).coerceIn(0L, MAX_STEP_MS)
            lastShown = now
            if (shownMs < showMs) return text
        }
        current = pending.removeFirstOrNull()
        shownMs = 0L
        lastShown = now
        return current
    }

    @Synchronized
    fun clear() {
        pending.clear()
        current = null
        shownMs = 0L
    }

    private companion object {
        const val LIMIT = 4
        const val MAX_STEP_MS = 100L
    }
}

internal fun duoNotice(request: DuoEmoteState.Request, result: SharedEmoteResult?, name: String, shown: Boolean): String? =
    when (result) {
        SharedEmoteResult.Declined -> if (request.incoming) null else "$name said no"
        SharedEmoteResult.Expired -> when {
            request.accepted && request.incoming -> "You didn't reach the marker in time"
            request.accepted -> "$name didn't reach the marker in time"
            request.incoming -> null
            else -> "$name didn't answer"
        }
        SharedEmoteResult.Cancelled ->
            if (request.incoming && !request.accepted && !shown) null else "The duo emote was cancelled"
        else -> null
    }

internal fun othersWaiting(names: List<String>, secondsLeft: Int): String? = when (names.size) {
    0 -> null
    1 -> "${names[0]} also asked you (${secondsLeft}s)"
    2 -> "${names[0]} and 1 other also asked you (${secondsLeft}s)"
    else -> "${names[0]} and ${names.size - 1} others also asked you (${secondsLeft}s)"
}
