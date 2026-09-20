package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(with = GroupKind.Serializer::class)
enum class GroupKind(val serializedName: String) {
    Dm("dm"),
    Group("group"),
    Unknown("unknown");

    internal object Serializer : KSerializer<GroupKind> by ApiEnumSerializer(
        "GroupKind",
        GroupKind.entries,
        Unknown,
        GroupKind::serializedName,
    )
}

@Serializable
data class GroupLastMessage(
    val content: String,
    val sender: String,
    @SerialName("sent_at") val sentAt: String,
)

@Serializable
data class GroupSummary(
    val id: Int,
    val kind: GroupKind,
    val name: String? = null,
    val members: List<String> = emptyList(),
    @SerialName("last_message") val lastMessage: GroupLastMessage? = null,
    val unread: Boolean = false,
    val special: Boolean = false,
)

@Serializable
data class GroupMessageSessionInvite(
    val id: Int,
    @SerialName("session_id") val sessionId: String,
    val status: String,
)

@Serializable
data class GroupMessage(
    val id: Long,
    val sender: String,
    val content: String,
    @SerialName("sent_at") val sentAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("session_invite") val sessionInvite: GroupMessageSessionInvite? = null,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val members: List<String> = emptyList(),
)

@Serializable
data class SendGroupMessageRequest(
    val content: String,
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
)

@Serializable
data class EditGroupMessageRequest(
    val content: String,
)
