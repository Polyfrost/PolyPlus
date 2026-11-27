package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.libs.ktor.client.request.setBody
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsText
import org.polyfrost.polyplus.libs.ktor.http.ContentType
import org.polyfrost.polyplus.libs.ktor.http.HttpMethod
import org.polyfrost.polyplus.libs.ktor.http.contentType
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacy
import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacyBody
import org.polyfrost.polyplus.client.utils.runSuspendCatching

object SharedEmoteApi {
    suspend fun privacy(): Result<SharedEmotePrivacy> =
        PolyPlusClient.HTTP.getBodyAuthorized<SharedEmotePrivacyBody>("${PolyPlusConfig.apiUrl}/account/shared-emote-privacy").map { it.privacy }

    suspend fun setPrivacy(privacy: SharedEmotePrivacy): Result<Unit> = runSuspendCatching {
        require(privacy != SharedEmotePrivacy.Unknown) { "Pick everyone, friends or nobody" }
        val response = PolyPlusClient.HTTP.requestAuthorized("${PolyPlusConfig.apiUrl}/account/shared-emote-privacy", HttpMethod.Put) {
            contentType(ContentType.Application.Json)
            setBody(SharedEmotePrivacyBody(privacy))
        }
        check(response.status.isSuccess()) { "HTTP ${response.status.value}: ${response.bodyAsText()}" }
    }
}
