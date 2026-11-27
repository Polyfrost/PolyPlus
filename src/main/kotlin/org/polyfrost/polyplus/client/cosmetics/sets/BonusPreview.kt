package org.polyfrost.polyplus.client.cosmetics.sets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.libs.ktor.client.plugins.ClientRequestException
import org.polyfrost.polyplus.libs.ktor.http.HttpHeaders
import org.polyfrost.polyplus.libs.ktor.http.HttpStatusCode
import org.polyfrost.polyplus.client.network.http.BonusApi
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

object BonusPreview {
    private const val CACHE_SIZE = 32
    private const val DEFAULT_BACKOFF_MS = 5_000L
    private const val ENTRY_LIFETIME_MS = 60_000L

    private class Entry(val bonuses: List<BonusRef>, val storedAtMs: Long)

    private val cache = object : LinkedHashMap<List<Int>, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Int>, Entry>?): Boolean = size > CACHE_SIZE
    }

    private var account: UUID? = null
    private var generation = 0

    @Volatile
    private var blockedUntilMs = 0L

    var revision by mutableIntStateOf(0)
        private set

    fun cached(cosmeticIds: Collection<Int>): List<BonusRef>? = synchronized(cache) { lookup(key(cosmeticIds)) }

    fun remainingMs(cosmeticIds: Collection<Int>): Long = synchronized(cache) {
        val entry = cache[key(cosmeticIds)] ?: return 0L
        (ENTRY_LIFETIME_MS - (System.currentTimeMillis() - entry.storedAtMs)).coerceAtLeast(0L)
    }

    suspend fun preview(cosmeticIds: Collection<Int>): Result<List<BonusRef>> {
        val key = key(cosmeticIds)
        if (key.isEmpty()) return Result.success(emptyList())
        val stamp = synchronized(cache) {
            lookup(key)?.let { return Result.success(it) }
            generation
        }
        val wait = blockedUntilMs - System.currentTimeMillis()
        if (wait > 0) return Result.failure(IllegalStateException("Bonus previews are cooling down for ${wait}ms"))
        return BonusApi.preview(key)
            .recoverCatching { error ->
                if (!rejected(error)) throw error
                emptyList()
            }
            .onSuccess { bonuses ->
                synchronized(cache) {
                    if (generation == stamp) cache[key] = Entry(bonuses, System.currentTimeMillis())
                }
            }
            .onFailure(::noteRateLimit)
    }

    fun shouldRetry(error: Throwable): Boolean {
        if (error is IllegalArgumentException) return false
        val status = statusOf(error) ?: return true
        return status == HttpStatusCode.TooManyRequests.value || status >= 500
    }

    private fun rejected(error: Throwable): Boolean {
        val status = statusOf(error) ?: return false
        return status in 400..499 && status != HttpStatusCode.TooManyRequests.value
    }

    private fun statusOf(error: Throwable): Int? = (error as? ClientRequestException)?.response?.status?.value

    fun clear() {
        synchronized(cache) {
            cache.clear()
            generation++
        }
        ClientPlatform.runOnMain { revision++ }
    }

    private fun lookup(key: List<Int>): List<BonusRef>? {
        val current = ClientPlatform.localPlayerUuid()
        if (account != current) {
            account = current
            cache.clear()
            generation++
        }
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMs > ENTRY_LIFETIME_MS) {
            cache.remove(key)
            return null
        }
        return entry.bonuses
    }

    private fun key(cosmeticIds: Collection<Int>): List<Int> = cosmeticIds.distinct().sorted()

    private fun noteRateLimit(error: Throwable) {
        val response = (error as? ClientRequestException)?.response ?: return
        if (response.status != HttpStatusCode.TooManyRequests) return
        val seconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
        blockedUntilMs = System.currentTimeMillis() + (seconds?.times(1_000L) ?: DEFAULT_BACKOFF_MS)
    }
}
