package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpecialChatTarget(
    val player: String,
    @SerialName("group_id") val groupId: Int? = null,
)

@Serializable
data class SendSpecialChatMessageRequest(
    val content: String,
)

@Serializable
data class SpecialChatSentMessage(
    @SerialName("group_id") val groupId: Int,
    @SerialName("message_id") val messageId: Long,
    @SerialName("sent_at") val sentAt: String,
)
