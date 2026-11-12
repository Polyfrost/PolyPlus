package org.polyfrost.polyplus.client.social

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.CursorPage
import org.polyfrost.polyplus.client.network.http.respectingRateLimit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PagedList<T, K>(
    private val label: String,
    private val keyOf: (T) -> K,
    private val fetch: suspend (cursor: String?) -> Result<CursorPage<T>>,
) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val lock = Any()
    private val fetching = Mutex()
    private var cursor: String? = null
    private var loaded = false
    private var generation = 0L
    private var job: Job? = null
    private var retryAt = 0L

    fun refresh(): Job = synchronized(lock) {
        job?.cancel()
        val ticket = ++generation
        enqueue { loadFirst(ticket) }
    }

    fun syncHead(): Job = synchronized(lock) {
        if (!loaded) return job?.takeIf { it.isActive } ?: refresh()
        val ticket = generation
        enqueue { mergeFirst(ticket) }
    }

    fun loadMore() {
        synchronized(lock) {
            val next = cursor
            if (!loaded || next == null || job?.isActive == true || System.currentTimeMillis() < retryAt) return
            val ticket = generation
            enqueue { appendPage(ticket, next) }
        }
    }

    fun clear() {
        synchronized(lock) {
            job?.cancel()
            generation++
            cursor = null
            loaded = false
            retryAt = 0L
            _items.value = emptyList()
            _hasMore.value = false
        }
    }

    fun update(transform: (List<T>) -> List<T>) = _items.update(transform)

    fun putFirst(item: T) {
        val key = keyOf(item)
        _items.update { list -> listOf(item) + list.filterNot { keyOf(it) == key } }
    }

    fun removeIf(predicate: (T) -> Boolean) = _items.update { list -> list.filterNot(predicate) }

    private fun enqueue(block: suspend () -> Unit): Job =
        PolyPlusClient.SCOPE.launch { fetching.withLock { block() } }.also { job = it }

    private suspend fun loadFirst(ticket: Long) {
        val result = respectingRateLimit { fetch(null) }
        synchronized(lock) {
            if (ticket != generation) return
            result.onSuccess { page ->
                _items.value = page.items.distinctBy(keyOf)
                cursor = page.cursor
                _hasMore.value = page.cursor != null
                loaded = true
                retryAt = 0L
            }
        }
        result.onFailure { LOGGER.error("Failed to load $label", it) }
    }

    private suspend fun mergeFirst(ticket: Long) {
        val result = respectingRateLimit { fetch(null) }
        synchronized(lock) {
            if (ticket != generation) return
            result.onSuccess { page ->
                val fresh = page.items.distinctBy(keyOf)
                val keys = fresh.mapTo(HashSet(), keyOf)
                _items.update { list -> fresh + list.filterNot { keyOf(it) in keys } }
            }
        }
        result.onFailure { LOGGER.error("Failed to update $label", it) }
    }

    private suspend fun appendPage(ticket: Long, next: String) {
        try {
            _loading.value = true
            val result = respectingRateLimit { fetch(next) }
            synchronized(lock) {
                if (ticket != generation || cursor != next) return
                result.onSuccess { page ->
                    _items.update { list ->
                        val keys = list.mapTo(HashSet(), keyOf)
                        list + page.items.distinctBy(keyOf).filterNot { keyOf(it) in keys }
                    }
                    cursor = page.cursor
                    _hasMore.value = page.cursor != null
                }.onFailure {
                    retryAt = System.currentTimeMillis() + RETRY_DELAY_MILLIS
                }
            }
            result.onFailure { LOGGER.error("Failed to load more $label", it) }
        } finally {
            _loading.value = false
        }
    }

    private companion object {
        private val LOGGER = LogManager.getLogger()
        private const val RETRY_DELAY_MILLIS = 5_000L
    }
}
