package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.SendSpecialChatMessageRequest
import org.polyfrost.polyplus.client.network.http.responses.SpecialChatSentMessage
import org.polyfrost.polyplus.client.network.http.responses.SpecialChatTarget

object SpecialChatApi {
    suspend fun targets(): Result<List<SpecialChatTarget>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/special-chat/")

    suspend fun send(player: String, content: String): Result<SpecialChatSentMessage> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/special-chat/$player/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendSpecialChatMessageRequest(content))
        }
}
