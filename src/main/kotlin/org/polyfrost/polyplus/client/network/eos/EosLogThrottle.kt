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

    @Synchronized
    fun onMessage(message: String, nowMs: Long): Int? {
        val state = states.getOrPut(normalize(message)) { State() }

        if (state.emitted < burst) {
            state.emitted++
            state.lastEmitMs = nowMs
            state.intervalMs = firstIntervalMs
            return 0
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
