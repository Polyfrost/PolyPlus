package org.polyfrost.polyplus.client.network.http

import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.SpecialChatStatus

object SpecialChatApi {
    suspend fun status(): Result<SpecialChatStatus> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/special-chat")
}
