package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.GlobalChatMessage
import org.polyfrost.polyplus.client.network.http.responses.SendGlobalChatMessageRequest

object GlobalChatApi {
    suspend fun history(before: Long? = null, limit: Long? = null): Result<List<GlobalChatMessage>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/chat/global") {
            if (before != null) parameter("before", before)
            if (limit != null) parameter("limit", limit)
        }

    suspend fun send(content: String): Result<GlobalChatMessage> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/chat/global") {
            contentType(ContentType.Application.Json)
            setBody(SendGlobalChatMessageRequest(content))
        }
}
