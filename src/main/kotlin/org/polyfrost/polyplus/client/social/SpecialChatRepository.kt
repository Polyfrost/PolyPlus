package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.SpecialChatApi
import org.polyfrost.polyplus.client.network.http.responses.SpecialChatStatus

object SpecialChatRepository {
    private val LOGGER = LogManager.getLogger()

    private val _status = MutableStateFlow<SpecialChatStatus?>(null)
    val status = _status.asStateFlow()

    fun refreshTargets() = PolyPlusClient.SCOPE.launch {
        SpecialChatApi.status()
            .onSuccess { result ->
                _status.value = result
                GroupsRepository.refreshGroups()
            }
            .onFailure { LOGGER.error("Failed to refresh Special Chat status", it) }
    }
}
