package org.polyfrost.polyplus.client

import java.io.File
import java.security.MessageDigest

/**
 * Suppresses an error that was already reported within [windowMs].
 */
internal class SentryEventRateLimiter(
    private val stateFile: File,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()

    private val reportedAt = HashMap<String, Long>()

    private var loaded = false

    fun allow(key: String): Boolean {
        synchronized(lock) {
            val now = clock()
            if (!loaded) {
                loaded = true
                load()
            }
            prune(now)

            val digest = digest(key)
            if (reportedAt.containsKey(digest)) return false

            reportedAt[digest] = now
            trim()
            persist()
            return true
        }
    }

    private fun load() {
        runCatching {
            if (!stateFile.isFile) return
            for (line in stateFile.readLines()) {
                val at = line.substringBefore('\t').trim().toLongOrNull() ?: continue
                val digest = line.substringAfter('\t', "").trim()
                if (digest.isEmpty()) continue
                reportedAt[digest] = maxOf(reportedAt[digest] ?: Long.MIN_VALUE, at)
            }
        }
    }

    private fun persist() {
        runCatching {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(reportedAt.entries.joinToString("\n") { "${it.value}\t${it.key}" })
        }
    }

    private fun prune(now: Long) {
        reportedAt.entries.removeAll { now - it.value !in 0..windowMs }
    }

    private fun trim() {
        val excess = reportedAt.size - MAX_ENTRIES
        if (excess <= 0) return
        reportedAt.entries.sortedBy { it.value }.take(excess).forEach { reportedAt.remove(it.key) }
    }

    private fun digest(key: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return hash.take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DEFAULT_WINDOW_MS = 60L * 60 * 1000

        const val MAX_ENTRIES = 200

        const val DIGEST_BYTES = 8
    }
}
