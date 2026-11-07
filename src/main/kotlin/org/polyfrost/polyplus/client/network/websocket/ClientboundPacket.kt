package org.polyfrost.polyplus.client.network.websocket

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.HostedJoinResult
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.http.responses.PlayDestination
import org.polyfrost.polyplus.client.network.http.responses.PlayEndReason
import org.polyfrost.polyplus.client.network.http.responses.PlayReason
import org.polyfrost.polyplus.client.network.http.responses.PlayResponse
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyInvite
import org.polyfrost.polyplus.client.network.http.responses.PartyInviteStatus
import org.polyfrost.polyplus.client.network.http.responses.PartyLeftReason
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        val status: PlayerStatus = PlayerStatus.Unknown,
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

    @Serializable
    @SerialName("PartyUpdated")
    data class PartyUpdated(val party: Party) : ClientboundPacket

    @Serializable
    @SerialName("PartyMemberStateChanged")
    data class PartyMemberStateChanged(
        @SerialName("party_id") val partyId: String,
        val player: String,
        val state: MemberState,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PartyLeft")
    data class PartyLeft(
        @SerialName("party_id") val partyId: String,
        val reason: PartyLeftReason,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PartyInviteReceived")
    data class PartyInviteReceived(val invite: PartyInvite) : ClientboundPacket

    @Serializable
    @SerialName("PartyInviteUpdated")
    data class PartyInviteUpdated(
        @SerialName("invite_id") val inviteId: String,
        val status: PartyInviteStatus,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayTogetherStarted")
    data class PlayTogetherStarted(
        val id: String,
        @SerialName("party_id") val partyId: String,
        val leader: String,
        val destination: PlayDestination,
        @SerialName("starts_at") val startsAt: String,
        @SerialName("session_id") val sessionId: String? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayTogetherUpdated")
    data class PlayTogetherUpdated(
        val id: String,
        val player: String,
        val response: PlayResponse,
        val reason: PlayReason? = null,
        @SerialName("session_id") val sessionId: String? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayTogetherEnded")
    data class PlayTogetherEnded(
        val id: String,
        val reason: PlayEndReason,
    ) : ClientboundPacket

    @Serializable
    @SerialName("HostedJoinRequested")
    data class HostedJoinRequested(
        @SerialName("request_id") val requestId: String,
        @SerialName("session_id") val sessionId: String,
        val player: String,
        @SerialName("expires_at") val expiresAt: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("HostedJoinRequestSent")
    data class HostedJoinRequestSent(
        @SerialName("request_id") val requestId: String,
        @SerialName("session_id") val sessionId: String,
        val host: String,
        @SerialName("expires_at") val expiresAt: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("HostedJoinResolved")
    data class HostedJoinResolved(
        @SerialName("request_id") val requestId: String,
        @SerialName("session_id") val sessionId: String,
        val result: HostedJoinResult,
        @SerialName("invite_id") val inviteId: Int? = null,
    ) : ClientboundPacket
}
