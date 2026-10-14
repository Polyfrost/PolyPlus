package org.polyfrost.polyplus.client.social

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.GroupsApi

object ChatIdentity {
    private val LOGGER = LogManager.getLogger()

    private val _actAs = MutableStateFlow<String?>(null)
    val actAs = _actAs.asStateFlow()

    private val _delegations = MutableStateFlow<List<String>>(emptyList())
    val delegations = _delegations.asStateFlow()

    fun localId(): String = runCatching { Minecraft.getInstance().user.profileId.toString() }.getOrDefault("")

    fun selfId(): String = _actAs.value ?: localId()

    fun refreshDelegations() = PolyPlusClient.SCOPE.launch {
        GroupsApi.delegations()
            .onSuccess { owners ->
                _delegations.value = owners
                PlayerNamesRepository.resolve(owners)
                if (_actAs.value !in owners) switchTo(null)
            }
            .onFailure { LOGGER.error("Failed to load chat delegations", it) }
    }

    fun switchTo(owner: String?) {
        if (_actAs.value == owner) return
        _actAs.value = owner
        GroupsRepository.reset()
    }

    fun HttpRequestBuilder.applyActAs() {
        _actAs.value?.let { header("x-polyplus-act-as", it) }
    }
}
