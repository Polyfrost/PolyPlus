package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpecialChatStatus(
    @SerialName("group_id") val groupId: Int? = null,
    @SerialName("cooldown_until") val cooldownUntil: String? = null,
    @SerialName("is_special_chat_target") val isSpecialChatTarget: Boolean = false,
)
