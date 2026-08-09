package org.polyfrost.polyplus.client.social

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.SocialApi
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.EarlyInitializable

object FriendsRepository : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests = _incomingRequests.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingRequests = _outgoingRequests.asStateFlow()

    private val _blocked = MutableStateFlow<List<BlockedPlayer>>(emptyList())
    val blocked = _blocked.asStateFlow()

    override fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (event.packet) {
                is ClientboundPacket.FriendRequestReceived,
                is ClientboundPacket.FriendRequestUpdated,
                -> {
                    PolyPlusClient.SCOPE.launch { refreshRequests() }
                }
                is ClientboundPacket.FriendRemoved -> {
                    PolyPlusClient.SCOPE.launch { refreshFriends() }
                }
                else -> {}
            }
            Unit
        }.register()
    }

    fun refreshAll() {
        PolyPlusClient.SCOPE.launch {
            refreshFriends()
            refreshRequests()
            refreshBlocked()
        }
    }

    private suspend fun refreshFriends() {
        SocialApi.friends()
            .onSuccess { _friends.value = it }
            .onFailure { LOGGER.error("Failed to refresh friends list", it) }
    }

    private suspend fun refreshRequests() {
        SocialApi.incomingRequests()
            .onSuccess { _incomingRequests.value = it }
            .onFailure { LOGGER.error("Failed to refresh incoming friend requests", it) }
        SocialApi.outgoingRequests()
            .onSuccess { _outgoingRequests.value = it }
            .onFailure { LOGGER.error("Failed to refresh outgoing friend requests", it) }
    }

    private suspend fun refreshBlocked() {
        SocialApi.blocked()
            .onSuccess { _blocked.value = it }
            .onFailure { LOGGER.error("Failed to refresh blocked players", it) }
    }

    fun sendRequest(player: String) = PolyPlusClient.SCOPE.launch {
        SocialApi.sendRequest(player)
            .onSuccess { refreshRequests(); if (it == null) refreshFriends() }
            .onFailure {
                LOGGER.error("Failed to send friend request to $player", it)
                SocialErrors.emit("Couldn't send friend request", it)
            }
    }

    fun acceptRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.acceptRequest(id)
            .onSuccess { refreshRequests(); refreshFriends() }
            .onFailure {
                LOGGER.error("Failed to accept friend request $id", it)
                SocialErrors.emit("Couldn't accept friend request", it)
            }
    }

    fun declineRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.declineRequest(id).onSuccess { refreshRequests() }
            .onFailure {
                LOGGER.error("Failed to decline friend request $id", it)
                SocialErrors.emit("Couldn't decline friend request", it)
            }
    }

    fun cancelRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.cancelRequest(id).onSuccess { refreshRequests() }
            .onFailure {
                LOGGER.error("Failed to cancel friend request $id", it)
                SocialErrors.emit("Couldn't cancel friend request", it)
            }
    }

    fun removeFriend(player: String) = PolyPlusClient.SCOPE.launch {
        SocialApi.removeFriend(player).onSuccess { refreshFriends() }
            .onFailure {
                LOGGER.error("Failed to remove friend $player", it)
                SocialErrors.emit("Couldn't remove friend", it)
            }
    }

    fun block(player: String) = PolyPlusClient.SCOPE.launch {
        SocialApi.block(player)
            .onSuccess { refreshBlocked(); refreshFriends(); refreshRequests() }
            .onFailure {
                LOGGER.error("Failed to block $player", it)
                SocialErrors.emit("Couldn't block player", it)
            }
    }

    fun unblock(player: String) = PolyPlusClient.SCOPE.launch {
        SocialApi.unblock(player).onSuccess { refreshBlocked() }
            .onFailure {
                LOGGER.error("Failed to unblock $player", it)
                SocialErrors.emit("Couldn't unblock player", it)
            }
    }
}
