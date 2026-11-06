package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.AccountApi
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus

object PlayerStatusRepository {
    private val LOGGER = LogManager.getLogger()

    private val lock = Mutex()

    private val _status = MutableStateFlow(PlayerStatus.Online)
    val status = _status.asStateFlow()

    fun refresh() = PolyPlusClient.SCOPE.launch {
        lock.withLock { load() }
    }

    fun set(status: PlayerStatus) {
        if (status == _status.value) return
        _status.value = status
        PolyPlusClient.SCOPE.launch {
            lock.withLock {
                AccountApi.setStatus(status)
                    .onSuccess { _status.value = status }
                    .onFailure {
                        LOGGER.error("Failed to set status to $status", it)
                        SocialErrors.emit("Couldn't change your status", it)
                        load()
                    }
            }
        }
    }

    private suspend fun load() {
        AccountApi.status()
            .onSuccess { if (it != PlayerStatus.Unknown) _status.value = it }
            .onFailure { LOGGER.warn("Failed to load account status", it) }
    }
}
