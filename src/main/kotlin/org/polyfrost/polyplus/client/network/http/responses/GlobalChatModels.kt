package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlobalChatMessage(
    val id: Long,
    val sender: String,
    val content: String,
    @SerialName("sent_at") val sentAt: String,
)

@Serializable
data class SendGlobalChatMessageRequest(
    val content: String,
)
