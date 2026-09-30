package org.polyfrost.polyplus.client.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.PlayersApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PlayerNamesRepository {
    private val LOGGER = LogManager.getLogger()
    private val lock = Mutex()
    private val inFlight = mutableSetOf<String>()

    // Far above what any social screen shows at once
    private const val MAX_NAMES = 2048

    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names = _names.asStateFlow()

    fun resolve(ids: Collection<String>) = PolyPlusClient.SCOPE.launch {
        val toFetch = lock.withLock {
            val missing = ids.filter { it !in _names.value && it !in inFlight }
            inFlight.addAll(missing)
            missing
        }
        if (toFetch.isEmpty()) return@launch

        PlayersApi.resolve(toFetch)
            .onSuccess { resolved ->
                _names.update { current ->
                    val merged = current + resolved
                    // Map.plus keeps insertion order, so this drops the earliest resolved names
                    if (merged.size <= MAX_NAMES) merged else merged.entries.drop(merged.size - MAX_NAMES).associate { it.toPair() }
                }
            }
            .onFailure { LOGGER.error("Failed to resolve {} player name(s)", toFetch.size, it) }

        lock.withLock { inFlight.removeAll(toFetch.toSet()) }
    }

    fun nameOr(uuid: String): String = _names.value[uuid] ?: shortId(uuid)

    @Composable
    fun displayName(uuid: String): String {
        val resolvedNames by names.collectAsState()
        // Keyed on presence too, so a name evicted while still shown is resolved again
        LaunchedEffect(uuid, uuid in resolvedNames) {
            repeat(5) { attempt ->
                if (uuid in _names.value) return@LaunchedEffect
                resolve(listOf(uuid)).join()
                if (uuid in _names.value) return@LaunchedEffect
                if (attempt < 4) delay(1500)
            }
        }
        return resolvedNames[uuid] ?: shortId(uuid)
    }
}

private fun shortId(uuid: String): String = uuid.take(8)
