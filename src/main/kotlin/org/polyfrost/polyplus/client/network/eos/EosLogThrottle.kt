package org.polyfrost.polyplus.client.network.eos

class EosLogThrottle(
    private val burst: Int = BURST,
    private val firstIntervalMs: Long = FIRST_REPEAT_INTERVAL_MS,
    private val maxIntervalMs: Long = MAX_REPEAT_INTERVAL_MS,
) {
    private class State {
        var emitted = 0
        var suppressed = 0
        var lastEmitMs = 0L
        var intervalMs = 0L
    }

    private val states = object : LinkedHashMap<String, State>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, State>) = size > MAX_KEYS
    }

    private var lastMessage: String? = null
    private var lastKey: String = ""

    private fun keyFor(message: String): String {
        if (message == lastMessage) return lastKey
        lastKey = normalize(message)
        lastMessage = message
        return lastKey
    }

    @Synchronized
    fun onMessage(message: String, nowMs: Long): Int? {
        val state = states.getOrPut(keyFor(message)) { State() }

        if (state.emitted >= burst && nowMs - state.lastEmitMs > maxIntervalMs * QUIET_RESET_FACTOR) {
            state.emitted = 0
        }

        if (state.emitted < burst) {
            val suppressed = state.suppressed
            state.suppressed = 0
            state.emitted++
            state.lastEmitMs = nowMs
            state.intervalMs = firstIntervalMs
            return suppressed
        }

        if (nowMs - state.lastEmitMs < state.intervalMs) {
            state.suppressed++
            return null
        }

        val suppressed = state.suppressed
        state.suppressed = 0
        state.emitted++
        state.lastEmitMs = nowMs
        state.intervalMs = (state.intervalMs * 2).coerceAtMost(maxIntervalMs)
        return suppressed
    }

    companion object {
        const val BURST = 3
        const val FIRST_REPEAT_INTERVAL_MS = 30_000L
        const val MAX_REPEAT_INTERVAL_MS = 10L * 60 * 1000
        const val MAX_KEYS = 256

        const val QUIET_RESET_FACTOR = 2

        private val VARYING = listOf(
            Regex("0x[0-9a-fA-F]+"),
            Regex("\\d+\\.\\d+\\.\\d+\\.\\d+(:\\d+)?"),
            Regex("\\d+(\\.\\d+)?\\s*(ns|us|ms|s|seconds|milliseconds)\\b"),
            Regex("\\d{4,}"),
        )

        fun normalize(message: String): String =
            VARYING.fold(message) { text, pattern -> pattern.replace(text, "#") }
    }
}
