package org.polyfrost.polyplus.client.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

@Serializable
sealed interface ClientboundPacket {
    @Serializable
    @SerialName("Error")
    data class Error(
        @SerialName("error_code") val code: String,
        val message: String,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("CosmeticsInfo")
    data class CosmeticsInfo(@SerialName("cosmetics") val all: HashMap<String, List<Int>>) : ClientboundPacket

    @Serializable
    @SerialName("SubscriptionSnapshot")
    data class SubscriptionSnapshot(
        val equipped: Map<String, Map<BodySlot, Int>>,
        @SerialName("active_emotes") val activeEmotes: Map<String, Int>,
        @SerialName("particle_colors") val particleColors: Map<String, Int> = emptyMap(),
        val users: List<String> = emptyList(),
        val rejected: List<String> = emptyList(),
        @SerialName("request_id") val requestId: Long? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerPresence")
    data class PlayerPresence(
        val player: String,
        val online: Boolean,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerCosmeticEquipped")
    data class PlayerCosmeticEquipped(
        val player: String,
        val slot: BodySlot,
        @SerialName("cosmetic_id") val cosmeticId: Int?,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerParticleColorChanged")
    data class PlayerParticleColorChanged(
        val player: String,
        val color: Int?,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerEmoteStarted")
    data class PlayerEmoteStarted(
        val player: String,
        @SerialName("emote_id") val emoteId: Int,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerEmoteStopped")
    data class PlayerEmoteStopped(
        val player: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("OwnershipUpdated")
    data class OwnershipUpdated(
        val player: String,
        @SerialName("cosmetic_ids") val cosmeticIds: List<Int>,
        @SerialName("emote_ids") val emoteIds: List<Int>,
    ) : ClientboundPacket

    @Serializable
    @SerialName("EmotePlay")
    data class EmotePlay(
        val player: String,
        @SerialName("emote_id") val emoteId: Int,
        @SerialName("start_time") val startTime: Long,
    ) : ClientboundPacket

    @Serializable
    @SerialName("EmoteStop")
    data class EmoteStop(val player: String) : ClientboundPacket

    @Serializable
    @SerialName("FriendRequestReceived")
    data class FriendRequestReceived(
        @SerialName("request_id") val requestId: Int,
        val sender: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("FriendRequestUpdated")
    data class FriendRequestUpdated(
        @SerialName("request_id") val requestId: Int,
        val status: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("FriendRemoved")
    data class FriendRemoved(val player: String) : ClientboundPacket

    @Serializable
    @SerialName("GroupMessageReceived")
    data class GroupMessageReceived(
        @SerialName("group_id") val groupId: Int,
        @SerialName("message_id") val messageId: Long,
        val sender: String,
        val content: String,
        @SerialName("session_invite_id") val sessionInviteId: Int? = null,
        @SerialName("session_invite_status") val sessionInviteStatus: String? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("GroupMessageEdited")
    data class GroupMessageEdited(
        @SerialName("group_id") val groupId: Int,
        @SerialName("message_id") val messageId: Long,
        val content: String,
        @SerialName("session_invite_status") val sessionInviteStatus: String? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("GroupMessageDeleted")
    data class GroupMessageDeleted(
        @SerialName("group_id") val groupId: Int,
        @SerialName("message_id") val messageId: Long,
    ) : ClientboundPacket

    @Serializable
    @SerialName("GlobalChatMessageReceived")
    data class GlobalChatMessageReceived(
        @SerialName("message_id") val messageId: Long,
        val sender: String,
        val content: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SessionInviteReceived")
    data class SessionInviteReceived(
        @SerialName("invite_id") val inviteId: Int,
        @SerialName("session_id") val sessionId: String,
        val sender: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SessionInviteUpdated")
    data class SessionInviteUpdated(
        @SerialName("invite_id") val inviteId: Int,
        val status: String,
    ) : ClientboundPacket
}
