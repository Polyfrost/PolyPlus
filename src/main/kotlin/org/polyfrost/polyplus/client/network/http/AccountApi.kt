package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.HttpMethod
import org.polyfrost.polyplus.libs.ktor.http.contentType
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatusBody
import org.polyfrost.polyplus.client.utils.runSuspendCatching

@Serializable
private data class LinkPuidRequest(@SerialName("product_user_id") val productUserId: String)

object AccountApi {
    suspend fun linkPuid(productUserId: String): Result<Unit> = runSuspendCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/account/link-puid") {
            contentType(ContentType.Application.Json)
            setBody(LinkPuidRequest(productUserId))
        }
        Unit
    }

    suspend fun status(): Result<PlayerStatus> =
        PolyPlusClient.HTTP.getBodyAuthorized<PlayerStatusBody>("${PolyPlusConfig.apiUrl}/account/status").map { it.status }

    suspend fun setStatus(status: PlayerStatus): Result<Unit> = runSuspendCatching {
        val response = PolyPlusClient.HTTP.requestAuthorized("${PolyPlusConfig.apiUrl}/account/status", HttpMethod.Put) {
            contentType(ContentType.Application.Json)
            setBody(PlayerStatusBody(status))
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}: ${response.bodyAsText()}" }
    }
}
