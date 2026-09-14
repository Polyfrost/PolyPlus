package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest

object SocialApi {
    suspend fun friends(): Result<List<Friend>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/social/friends")

    suspend fun removeFriend(player: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/friends/$player")
        Unit
    }

    suspend fun incomingRequests(): Result<List<FriendRequest>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/social/requests/incoming")

    suspend fun outgoingRequests(): Result<List<FriendRequest>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/social/requests/outgoing")

    suspend fun sendRequest(player: String): Result<FriendRequest?> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$player")

    suspend fun acceptRequest(id: Int): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id/accept")
        Unit
    }

    suspend fun declineRequest(id: Int): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id/decline")
        Unit
    }

    suspend fun cancelRequest(id: Int): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id")
        Unit
    }

    suspend fun blocked(): Result<List<BlockedPlayer>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/social/blocked")

    suspend fun block(player: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/blocked/$player")
        Unit
    }

    suspend fun unblock(player: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/blocked/$player")
        Unit
    }
}
