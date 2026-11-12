package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest
import org.polyfrost.polyplus.client.utils.runSuspendCatching

object SocialApi {
    suspend fun friendsPage(cursor: String?): Result<CursorPage<Friend>> =
        PolyPlusClient.HTTP.pageAuthorized("${PolyPlusConfig.apiUrl}/v1/social/friends", cursor)

    suspend fun removeFriend(player: String): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/friends/$player")
        Unit
    }

    suspend fun incomingRequestsPage(cursor: String?): Result<CursorPage<FriendRequest>> =
        PolyPlusClient.HTTP.pageAuthorized("${PolyPlusConfig.apiUrl}/v1/social/requests/incoming", cursor)

    suspend fun outgoingRequestsPage(cursor: String?): Result<CursorPage<FriendRequest>> =
        PolyPlusClient.HTTP.pageAuthorized("${PolyPlusConfig.apiUrl}/v1/social/requests/outgoing", cursor)

    suspend fun sendRequest(player: String): Result<FriendRequest?> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$player")

    suspend fun acceptRequest(id: Int): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id/accept")
        Unit
    }

    suspend fun declineRequest(id: Int): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id/decline")
        Unit
    }

    suspend fun cancelRequest(id: Int): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/requests/$id")
        Unit
    }

    suspend fun blockedPage(cursor: String?): Result<CursorPage<BlockedPlayer>> =
        PolyPlusClient.HTTP.pageAuthorized("${PolyPlusConfig.apiUrl}/v1/social/blocked", cursor)

    suspend fun block(player: String): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/social/blocked/$player")
        Unit
    }

    suspend fun unblock(player: String): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/social/blocked/$player")
        Unit
    }
}
