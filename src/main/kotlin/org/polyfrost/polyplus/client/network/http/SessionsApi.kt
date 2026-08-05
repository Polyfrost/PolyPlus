package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.CreateSessionRequest
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.http.responses.SessionResponse

object SessionsApi {
    suspend fun create(eosSessionId: String? = null): Result<SessionResponse> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(eosSessionId))
        }

    suspend fun close(sessionId: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/sessions/$sessionId")
        Unit
    }

    suspend fun invite(sessionId: String, player: String): Result<SessionInvite> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/sessions/$sessionId/invites/$player")

    suspend fun incomingInvites(): Result<List<SessionInvite>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/sessions/invites/incoming")

    suspend fun acceptInvite(inviteId: Int): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/sessions/invites/$inviteId/accept")
        Unit
    }

    suspend fun declineInvite(inviteId: Int): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/sessions/invites/$inviteId/decline")
        Unit
    }
}
