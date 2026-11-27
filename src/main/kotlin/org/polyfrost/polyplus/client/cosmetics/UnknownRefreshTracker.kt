package org.polyfrost.polyplus.client.cosmetics

internal class UnknownRefreshTracker(
    private val capacity: Int = 512,
    private val retryBaseMs: Long = 60_000L,
    private val retryMaxMs: Long = 600_000L,
    private val staleMs: Long = 300_000L,
) {
    private sealed interface State {
        val lastSeenMs: Long

        data class Pending(val failures: Int, override val lastSeenMs: Long) : State
        data class InFlight(val failures: Int, override val lastSeenMs: Long) : State
        data class Failed(val failures: Int, val retryAtMs: Long, override val lastSeenMs: Long) : State
    }

    private val states = LinkedHashMap<Int, State>()
    private var globalFailures = 0
    private var nextRefreshAtMs = Long.MIN_VALUE

    @Synchronized
    fun request(id: Int, now: Long): Boolean {
        val failures = when (val state = states[id]) {
            null -> if (states.size >= capacity && !reclaimStale(now)) return false else 0
            is State.Failed -> if (now >= state.retryAtMs) state.failures else return false.also { touch(id, state, now) }
            else -> return false.also { touch(id, state, now) }
        }
        states.remove(id)
        states[id] = State.Pending(failures, now)
        return true
    }

    private fun touch(id: Int, state: State, now: Long) {
        if (now <= state.lastSeenMs) return
        states[id] = when (state) {
            is State.Pending -> state.copy(lastSeenMs = now)
            is State.InFlight -> state.copy(lastSeenMs = now)
            is State.Failed -> state.copy(lastSeenMs = now)
        }
    }

    private fun reclaimStale(now: Long): Boolean {
        val before = states.size
        states.values.removeIf { it is State.Failed && now - it.lastSeenMs >= staleMs }
        return states.size < before
    }

    @Synchronized
    fun hasPending(): Boolean = states.values.any { it is State.Pending }

    @Synchronized
    fun takePending(): List<Int> {
        val taken = ArrayList<Int>()
        for (entry in states.entries) {
            val state = entry.value as? State.Pending ?: continue
            entry.setValue(State.InFlight(state.failures, state.lastSeenMs))
            taken += entry.key
        }
        return taken
    }

    @Synchronized
    fun completed(ids: Collection<Int>, success: Boolean, now: Long, known: (Int) -> Boolean = { false }) {
        var resolved = false
        for (id in ids) {
            val state = states[id] as? State.InFlight ?: continue
            if (success && known(id)) {
                states.remove(id)
                resolved = true
            } else {
                val failures = state.failures + 1
                states[id] = State.Failed(failures, now + backoff(failures), state.lastSeenMs)
            }
        }
        if (ids.isEmpty()) return
        if (resolved) {
            globalFailures = 0
            nextRefreshAtMs = Long.MIN_VALUE
        } else {
            globalFailures++
            nextRefreshAtMs = now + backoff(globalFailures)
        }
    }

    @Synchronized
    fun drop(ids: Collection<Int>) {
        for (id in ids) if (states[id] is State.InFlight) states.remove(id)
    }

    @Synchronized
    fun forgetKnown(known: (Int) -> Boolean) {
        val before = states.size
        states.entries.removeIf { it.value !is State.InFlight && known(it.key) }
        if (states.size < before) {
            globalFailures = 0
            nextRefreshAtMs = Long.MIN_VALUE
        }
    }

    @Synchronized
    fun nextRefreshAtMs(): Long = nextRefreshAtMs

    private fun backoff(failures: Int): Long = minOf(retryBaseMs shl minOf(failures - 1, 20), retryMaxMs)

    @Synchronized
    fun clear() {
        states.clear()
        globalFailures = 0
        nextRefreshAtMs = Long.MIN_VALUE
    }

    @Synchronized
    fun size(): Int = states.size
}
