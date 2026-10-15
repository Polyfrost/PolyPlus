package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.SpecialChatStatus
import org.polyfrost.polyplus.client.social.ChatIdentity.applyActAs

object SpecialChatApi {
    suspend fun status(): Result<SpecialChatStatus> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/special-chat") { applyActAs() }
}
