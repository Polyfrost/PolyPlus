package org.polyfrost.polyplus.client.network.http

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.PlayerLookupResponse
import org.polyfrost.polyplus.client.network.http.responses.ResolvePlayersRequest
import org.polyfrost.polyplus.client.network.http.responses.ResolvePlayersResponse

object PlayersApi {
    suspend fun resolve(ids: List<String>): Result<Map<String, String>> =
        PolyPlusClient.HTTP.postBodyAuthorized<ResolvePlayersResponse>("${PolyPlusConfig.apiUrl}/players/resolve") {
            contentType(ContentType.Application.Json)
            setBody(ResolvePlayersRequest(ids))
        }.map { response -> response.players.associate { it.id to it.username } }

    suspend fun lookupByUsername(username: String): Result<String?> =
        PolyPlusClient.HTTP.getBodyAuthorized<PlayerLookupResponse>("${PolyPlusConfig.apiUrl}/players/by-username/$username")
            .map<String?, PlayerLookupResponse> { it.id }
            .recoverCatching { error ->
                if (error is ClientRequestException && error.response.status == HttpStatusCode.NotFound) {
                    null
                } else {
                    throw error
                }
            }
}
