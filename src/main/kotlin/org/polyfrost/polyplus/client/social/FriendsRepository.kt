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
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.events.WebSocketMessage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

object FriendsRepository {
    private val LOGGER = LogManager.getLogger()
    private const val ACCEPTED = "accepted"

    @Volatile
    private var fetchedIncomingOnce = false

    val friendsPaging = PagedList<Friend, String>("friends list", { it.player }, SocialApi::friendsPage)
    val friends = friendsPaging.items

    @Volatile
    private var friendUuids: Set<UUID> = emptySet()

    fun isFriend(uuid: UUID): Boolean = uuid in friendUuids

    val incomingPaging = PagedList<FriendRequest, Int>("incoming friend requests", { it.id }) { cursor ->
        SocialApi.incomingRequestsPage(cursor).onSuccess { page -> if (cursor == null) notifyNewRequests(page.items) }
    }
    val incomingRequests = incomingPaging.items

    val outgoingPaging = PagedList<FriendRequest, Int>("outgoing friend requests", { it.id }, SocialApi::outgoingRequestsPage)
    val outgoingRequests = outgoingPaging.items

    val blockedPaging = PagedList<BlockedPlayer, String>("blocked players", { it.player }, SocialApi::blockedPage)
    val blocked = blockedPaging.items

    fun earlyInitialize() {
        PolyPlusClient.SCOPE.launch {
            friends.map { list -> list.map { it.player } }.distinctUntilChanged().collect { players ->
                friendUuids = players.mapNotNullTo(HashSet()) { runCatching { UUID.fromString(it) }.getOrNull() }
                CosmeticSync.watchFriends(players)
            }
        }
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.FriendRequestReceived -> {
                    incomingPaging.putFirst(FriendRequest(packet.requestId, packet.sender, Instant.now().toString()))
                    notifyFriendRequest(packet.requestId, packet.sender)
                }
                is ClientboundPacket.FriendRequestUpdated -> onRequestUpdated(packet)
                is ClientboundPacket.FriendRemoved -> friendsPaging.removeIf { PartyRepository.samePlayer(it.player, packet.player) }
                is ClientboundPacket.PlayerPresence -> onPresence(packet)
                else -> {}
            }
            Unit
        }
    }

    fun presence(player: String, friends: List<Friend>, party: Party?): Boolean? =
        party?.member(player)?.online ?: friends.firstOrNull { it.player.equals(player, ignoreCase = true) }?.online

    private fun onPresence(packet: ClientboundPacket.PlayerPresence) {
        friendsPaging.update { list ->
            list.map { if (it.player.equals(packet.player, ignoreCase = true)) it.copy(online = packet.online, status = packet.status) else it }
        }
    }

    private fun onRequestUpdated(packet: ClientboundPacket.FriendRequestUpdated) {
        incomingPaging.removeIf { it.id == packet.requestId }
        outgoingPaging.removeIf { it.id == packet.requestId }
        if (packet.status == ACCEPTED) friendsPaging.syncHead()
    }

    fun refreshAll() {
        PlayerStatusRepository.refresh()
        PolyPlusClient.SCOPE.launch {
            friendsPaging.refresh().join()
            incomingPaging.refresh().join()
            outgoingPaging.refresh().join()
            blockedPaging.refresh().join()
        }
    }

    private fun notifyNewRequests(fresh: List<FriendRequest>) {
        val known = incomingRequests.value.mapTo(HashSet()) { it.id }
        if (fetchedIncomingOnce) {
            fresh.filter { it.id !in known }.forEach { notifyFriendRequest(it.id, it.player) }
        }
        fetchedIncomingOnce = true
    }

    private fun samePlayer(player: String): (String) -> Boolean = { PartyRepository.samePlayer(it, player) }

    fun acceptRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.acceptRequest(id)
            .onSuccess {
                incomingPaging.removeIf { it.id == id }
                friendsPaging.syncHead()
            }
            .onFailure {
                LOGGER.error("Failed to accept friend request $id", it)
                SocialErrors.emit("Couldn't accept friend request", it)
            }
    }

    fun declineRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.declineRequest(id).onSuccess { incomingPaging.removeIf { it.id == id } }
            .onFailure {
                LOGGER.error("Failed to decline friend request $id", it)
                SocialErrors.emit("Couldn't decline friend request", it)
            }
    }

    fun cancelRequest(id: Int) = PolyPlusClient.SCOPE.launch {
        SocialApi.cancelRequest(id).onSuccess { outgoingPaging.removeIf { it.id == id } }
            .onFailure {
                LOGGER.error("Failed to cancel friend request $id", it)
                SocialErrors.emit("Couldn't cancel friend request", it)
            }
    }

    fun sendRequest(player: String, onSent: () -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        SocialApi.sendRequest(player)
            .onSuccess { request ->
                if (request != null) {
                    outgoingPaging.putFirst(request)
                } else {
                    val same = samePlayer(player)
                    incomingPaging.removeIf { same(it.player) }
                    friendsPaging.syncHead()
                }
                onSent()
            }
            .onFailure {
                LOGGER.error("Failed to send friend request to $player", it)
                SocialErrors.emit("Couldn't send friend request", it)
            }
    }

    fun removeFriend(player: String) = PolyPlusClient.SCOPE.launch {
        val same = samePlayer(player)
        SocialApi.removeFriend(player).onSuccess { friendsPaging.removeIf { same(it.player) } }
            .onFailure {
                LOGGER.error("Failed to remove friend $player", it)
                SocialErrors.emit("Couldn't remove friend", it)
            }
    }

    fun block(player: String) = PolyPlusClient.SCOPE.launch {
        val same = samePlayer(player)
        SocialApi.block(player)
            .onSuccess {
                friendsPaging.removeIf { same(it.player) }
                incomingPaging.removeIf { same(it.player) }
                outgoingPaging.removeIf { same(it.player) }
                blockedPaging.syncHead()
            }
            .onFailure {
                LOGGER.error("Failed to block $player", it)
                SocialErrors.emit("Couldn't block player", it)
            }
    }

    fun unblock(player: String) = PolyPlusClient.SCOPE.launch {
        val same = samePlayer(player)
        SocialApi.unblock(player).onSuccess { blockedPaging.removeIf { same(it.player) } }
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
