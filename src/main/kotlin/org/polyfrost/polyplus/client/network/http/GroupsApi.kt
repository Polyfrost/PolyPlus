package org.polyfrost.polyplus.client.network.http

import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.http.responses.CreateGroupRequest
import org.polyfrost.polyplus.client.network.http.responses.EditGroupMessageRequest
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.SendGroupMessageRequest

object GroupsApi {
    suspend fun list(): Result<List<GroupSummary>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/groups")

    suspend fun openDirectMessage(player: String): Result<GroupSummary> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/groups/dm/$player")

    suspend fun createGroup(name: String, members: List<String>): Result<GroupSummary> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/groups") {
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name, members))
        }

    suspend fun addMember(groupId: Int, player: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/members/$player")
        Unit
    }

    suspend fun removeMember(groupId: Int, player: String): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/members/$player")
        Unit
    }

    suspend fun messages(groupId: Int, before: Long? = null, limit: Long? = null): Result<List<GroupMessage>> =
        PolyPlusClient.HTTP.getBodyAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/messages") {
            if (before != null) parameter("before", before)
            if (limit != null) parameter("limit", limit)
        }

    suspend fun sendMessage(groupId: Int, content: String, idempotencyKey: String? = null): Result<GroupMessage> =
        PolyPlusClient.HTTP.postBodyAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendGroupMessageRequest(content, idempotencyKey))
        }

    suspend fun editMessage(groupId: Int, messageId: Long, content: String): Result<GroupMessage> =
        PolyPlusClient.HTTP.patchBodyAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/messages/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(EditGroupMessageRequest(content))
        }

    suspend fun deleteMessage(groupId: Int, messageId: Long): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.deleteAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/messages/$messageId")
        Unit
    }

    suspend fun markRead(groupId: Int, messageId: Long): Result<Unit> = runCatching {
        PolyPlusClient.HTTP.postAuthorized("${PolyPlusConfig.apiUrl}/groups/$groupId/read/$messageId")
        Unit
    }
}
