package org.polyfrost.polyplus.client.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.PlayersApi

object PlayerNamesRepository {
    private val LOGGER = LogManager.getLogger()
    private val lock = Mutex()
    private val inFlight = mutableSetOf<String>()

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
            .onSuccess { resolved -> _names.value += resolved }
            .onFailure { LOGGER.error("Failed to resolve {} player name(s)", toFetch.size, it) }

        lock.withLock { inFlight.removeAll(toFetch.toSet()) }
    }

    @Composable
    fun displayName(uuid: String): String {
        val names by names.collectAsState()
        LaunchedEffect(uuid) { resolve(listOf(uuid)) }
        return names[uuid] ?: shortId(uuid)
    }
}

private fun shortId(uuid: String): String = uuid.take(8)
