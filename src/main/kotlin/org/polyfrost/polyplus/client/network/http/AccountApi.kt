package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
}
