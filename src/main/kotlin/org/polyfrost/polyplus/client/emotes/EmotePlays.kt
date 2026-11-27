package org.polyfrost.polyplus.client.emotes

import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import java.util.UUID

internal fun endsDevPlayback(active: Int, dev: Boolean, ended: Int?): Boolean =
    dev && (ended == null || ended == active)

internal fun snapshotSkips(player: String, devPlayer: UUID?): Boolean =
    devPlayer != null && runCatching { UUID.fromString(player) }.getOrNull() == devPlayer

internal fun snapshotSessions(sessions: Map<String, SharedEmote>, devPlayer: UUID?): Map<String, SharedEmote> {
    if (devPlayer == null) return sessions
    return sessions.filter { (player, session) ->
        !snapshotSkips(player, devPlayer) && session.players.none { snapshotSkips(it, devPlayer) }
    }
}

internal fun snapshotActive(
    active: Map<String, Int>,
    starts: Map<String, Long>,
    sessions: Map<String, SharedEmote>,
    devPlayer: UUID?,
): Map<String, Int> {
    if (devPlayer == null) return active
    val dropped = sessions.values.filter { session -> session.players.any { snapshotSkips(it, devPlayer) } }.toSet()
    return active.filter { (player, emoteId) ->
        !snapshotSkips(player, devPlayer) && dropped.none { session ->
            session.emoteId == emoteId &&
                starts[player].let { it == null || it == session.startTime } &&
                session.players.any { samePlayer(it, player) }
        }
    }
}

private fun samePlayer(a: String, b: String): Boolean {
    if (a.equals(b, ignoreCase = true)) return true
    val left = runCatching { UUID.fromString(a) }.getOrNull() ?: return false
    return left == runCatching { UUID.fromString(b) }.getOrNull()
}

internal class EmotePlays(
    private val stop: (emoteId: Int, startTime: Long?) -> Boolean,
    private val drop: (emoteId: Int, startTime: Long) -> Unit,
) {
    private class Sent(val requestId: Long, val emoteId: Int) {
        var cancelled = false
    }

    private data class Start(val emoteId: Int, val startTime: Long)

    private val sent = ArrayList<Sent>()
    private var acked: Start? = null
    private var applied: Start? = null
    private val skipped = ArrayList<Start>()

    @Synchronized
    fun sent(requestId: Long, emoteId: Int) {
        sent += Sent(requestId, emoteId)
        trim(sent)
    }

    fun send(requestId: Long, emoteId: Int, send: () -> Result<Unit>): Result<Unit> {
        sent(requestId, emoteId)
        return runCatching { send().getOrThrow() }.onFailure { failed(requestId) }
    }

    @Synchronized
    fun cancel(): Boolean {
        var cancelled = false
        for (play in sent) {
            if (play.cancelled) continue
            play.cancelled = true
            cancelled = true
        }
        val waiting = acked ?: return cancelled
        if (!stop(waiting.emoteId, waiting.startTime)) return cancelled
        acked = null
        withdraw(waiting)
        return true
    }

    @Synchronized
    fun failed(requestId: Long) {
        sent.removeIf { it.requestId == requestId }
    }

    @Synchronized
    fun skips(emoteId: Int, startTime: Long?, requestId: Long?): Boolean {
        if (requestId == null) {
            if (startTime == null) return false
            return skipped.remove(Start(emoteId, startTime))
        }
        val play = sent.firstOrNull { it.requestId == requestId } ?: return false
        sent.remove(play)
        if (!play.cancelled) {
            if (startTime != null) started(Start(emoteId, startTime))
            return false
        }
        if (!stop(emoteId, startTime)) {
            if (startTime != null) started(Start(emoteId, startTime))
            return false
        }
        if (startTime != null) withdraw(Start(emoteId, startTime))
        return true
    }

    @Synchronized
    fun applied(emoteId: Int, startTime: Long) {
        val start = Start(emoteId, startTime)
        if (acked == start) acked = null
        applied = start
    }

    @Synchronized
    fun clear() {
        sent.clear()
        acked = null
        applied = null
        skipped.clear()
    }

    private fun started(start: Start) {
        acked = start.takeIf { it != applied }
    }

    private fun withdraw(start: Start) {
        drop(start.emoteId, start.startTime)
        skipped += start
        trim(skipped)
    }

    private fun trim(list: MutableList<*>) {
        while (list.size > MAX_TRACKED) list.removeAt(0)
    }

    private companion object {
        const val MAX_TRACKED = 32
    }
}
