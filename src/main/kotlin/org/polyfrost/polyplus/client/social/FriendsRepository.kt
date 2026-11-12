package org.polyfrost.polyplus.client.social

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.network.http.SocialApi
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

object FriendsRepository {
    private val LOGGER = LogManager.getLogger()

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends = _friends.asStateFlow()

    @Volatile
    private var friendUuids: Set<UUID> = emptySet()

    fun isFriend(uuid: UUID): Boolean = uuid in friendUuids

    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests = _incomingRequests.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingRequests = _outgoingRequests.asStateFlow()

    private val _blocked = MutableStateFlow<List<BlockedPlayer>>(emptyList())
    val blocked = _blocked.asStateFlow()

    @Volatile
    private var fetchedIncomingOnce = false

    fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.FriendRequestReceived -> {
                    PolyPlusClient.SCOPE.launch { refreshRequests() }
                    notifyFriendRequest(packet.requestId, packet.sender)
                }
                is ClientboundPacket.FriendRequestUpdated -> {
                    PolyPlusClient.SCOPE.launch {
                        refreshRequests()
                        refreshFriends()
                    }
                }
                is ClientboundPacket.FriendRemoved -> {
                    PolyPlusClient.SCOPE.launch { refreshFriends() }
                }
                is ClientboundPacket.PlayerPresence -> onPresence(packet)
                else -> {}
            }
            Unit
        }
    }

    fun presence(player: String, friends: List<Friend>, party: Party?): Boolean? =
        party?.member(player)?.online ?: friends.firstOrNull { it.player.equals(player, ignoreCase = true) }?.online

    private fun onPresence(packet: ClientboundPacket.PlayerPresence) {
        _friends.update { list ->
            list.map { if (it.player.equals(packet.player, ignoreCase = true)) it.copy(online = packet.online, status = packet.status) else it }
        }
    }

    fun refreshAll() {
        PlayerStatusRepository.refresh()
        PolyPlusClient.SCOPE.launch {
            refreshFriends()
            refreshRequests()
            refreshBlocked()
        }
    }

    private suspend fun refreshFriends() {
        SocialApi.friends()
            .onSuccess {
                _friends.value = it
                friendUuids = it.mapNotNullTo(HashSet()) { friend ->
                    runCatching { UUID.fromString(friend.player) }.getOrNull()
                }
                CosmeticSync.watchFriends(it.map { friend -> friend.player })
            }
            .onFailure { LOGGER.error("Failed to refresh friends list", it) }
    }

    private suspend fun refreshRequests() {
        SocialApi.incomingRequests()
            .onSuccess { fresh ->
                val known = _incomingRequests.value.mapTo(HashSet()) { it.id }
                _incomingRequests.value = fresh
                if (fetchedIncomingOnce) {
                    fresh.filter { it.id !in known }.forEach { notifyFriendRequest(it.id, it.player) }
                }
                fetchedIncomingOnce = true
            }
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

    fun sendRequest(player: String, onSent: () -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        SocialApi.sendRequest(player)
            .onSuccess {
                refreshRequests()
                refreshFriends()
                onSent()
            }
            .onFailure {
                LOGGER.error("Failed to send friend request to $player", it)
                SocialErrors.emit("Couldn't send friend request", it)
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

    private fun notifyFriendRequest(requestId: Int, sender: String) {
        if (SocialOverlay.isShowing()) return
        if (!NotificationDedup.shouldNotify("friend_request:$requestId")) return
        PlayerNamesRepository.resolve(listOf(sender))
        val name = PlayerNamesRepository.nameOr(sender)
        Notifications.info("PolyPlus", "$name sent you a friend request")
    }
}
