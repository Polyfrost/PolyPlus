package org.polyfrost.polyplus.client.network.websocket

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition
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
import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import org.polyfrost.polyplus.client.network.http.responses.SharedEmoteResult
import org.polyfrost.polyplus.client.pets.PetPlay
import org.polyfrost.polyplus.client.pets.PetPlaySerializer
import org.polyfrost.polyplus.client.ping.PingKind
import org.polyfrost.polyplus.client.ping.PingReply
import org.polyfrost.polyplus.client.ping.PingTarget
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
        @SerialName("active_emote_starts") val activeEmoteStarts: Map<String, Long> = emptyMap(),
        @SerialName("active_emote_anchors") val activeEmoteAnchors: Map<String, EmoteAnchor> = emptyMap(),
        @SerialName("shared_emotes") val sharedEmotes: Map<String, SharedEmote> = emptyMap(),
        @SerialName("emote_definitions") val emoteDefinitions: Map<String, EmoteDefinition> = emptyMap(),
        @SerialName("active_emote_definitions") val activeEmoteDefinitions: Map<String, EmoteDefinition> = emptyMap(),
        @SerialName("particle_colors") val particleColors: Map<String, Int> = emptyMap(),
        val colors: Map<String, Map<String, Map<String, Int>>> = emptyMap(),
        @SerialName("saved_colors") val savedColors: Map<String, Map<String, Int>>? = null,
        @SerialName("saved_colors_active") val savedColorsActive: List<Int>? = null,
        val bonuses: Map<String, List<BonusRef>> = emptyMap(),
        @SerialName("pet_interactions_off") val petInteractionsOff: List<String> = emptyList(),
        @SerialName("pet_interactions_friends") val petInteractionsFriends: Map<String, List<String>> = emptyMap(),
        val users: List<String> = emptyList(),
        val rejected: List<String> = emptyList(),
        @SerialName("request_id") val requestId: Long? = null,
        @SerialName("server_time") val serverTime: Long? = null,
        @SerialName("server_received_time") val serverReceivedTime: Long? = null,
    ) : ClientboundPacket {
        fun emoteDefinitionFor(players: Collection<String>, emoteId: Int): EmoteDefinition? =
            players.firstNotNullOfOrNull { activeEmoteDefinitions[it]?.takeIf { definition -> definition.id == emoteId } }
                ?: emoteDefinitions[emoteId.toString()]
    }

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
    @SerialName("PlayerColorsChanged")
    data class PlayerColorsChanged(
        val player: String,
        @SerialName("cosmetic_id") val cosmeticId: Int,
        val colors: Map<String, Int>? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("CosmeticColorsSaved")
    data class CosmeticColorsSaved(
        @SerialName("request_id") val requestId: Long,
        @SerialName("cosmetic_id") val cosmeticId: Int,
        val colors: Map<String, Int>? = null,
        val active: Boolean = true,
    ) : ClientboundPacket

    @Serializable
    @SerialName("ParticleColorSaved")
    data class ParticleColorSaved(
        @SerialName("request_id") val requestId: Long,
        val color: Int? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerBonusesChanged")
    data class PlayerBonusesChanged(
        val player: String,
        val bonuses: List<BonusRef> = emptyList(),
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerPetPreferencesChanged")
    data class PlayerPetPreferencesChanged(
        val player: String,
        val interactions: Boolean = true,
        @Serializable(with = PetPlaySerializer::class) @SerialName("pet_play") val petPlay: PetPlay? = null,
        val friends: List<String> = emptyList(),
    ) : ClientboundPacket {
        val play: PetPlay get() = petPlay ?: PetPlay.ofSwitch(interactions)
    }

    @Serializable
    @SerialName("BonusDiscovered")
    data class BonusDiscovered(
        @SerialName("bonus_id") val bonusId: Int,
        val name: String,
        @SerialName("cosmetic_ids") val cosmeticIds: List<Int> = emptyList(),
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerEmoteStarted")
    data class PlayerEmoteStarted(
        val player: String,
        @SerialName("emote_id") val emoteId: Int,
        @SerialName("start_time") val startTime: Long? = null,
        val anchor: EmoteAnchor? = null,
        val emote: EmoteDefinition? = null,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerEmoteStopped")
    data class PlayerEmoteStopped(
        val player: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SharedEmoteRequested")
    data class SharedEmoteRequested(
        @SerialName("request_id") val requestId: String,
        val from: String,
        @SerialName("emote_id") val emoteId: Int,
        @SerialName("expires_at") val expiresAt: Long,
        val emote: EmoteDefinition? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SharedEmoteRequestSent")
    data class SharedEmoteRequestSent(
        @SerialName("request_id") val requestId: String,
        val target: String,
        @SerialName("emote_id") val emoteId: Int,
        @SerialName("expires_at") val expiresAt: Long,
        val emote: EmoteDefinition? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SharedEmoteResolved")
    data class SharedEmoteResolved(
        @SerialName("request_id") val requestId: String,
        val result: SharedEmoteResult = SharedEmoteResult.Unknown,
    ) : ClientboundPacket

    @Serializable
    @SerialName("SharedEmoteStart")
    data class SharedEmoteStart(
        @SerialName("emote_id") val emoteId: Int,
        val players: List<String>,
        val anchor: EmoteAnchor,
        @SerialName("start_time") val startTime: Long,
        val emote: EmoteDefinition? = null,
    ) : ClientboundPacket {
        fun toSharedEmote(): SharedEmote = SharedEmote(emoteId, players, anchor, startTime)
    }

    @Serializable
    @SerialName("SharedEmoteStop")
    data class SharedEmoteStop(val players: List<String> = emptyList()) : ClientboundPacket

    @Serializable
    @SerialName("OwnershipUpdated")
    data class OwnershipUpdated(
        val player: String,
        @SerialName("cosmetic_ids") val cosmeticIds: List<Int>,
        @SerialName("emote_ids") val emoteIds: List<Int>,
    ) : ClientboundPacket

    @Serializable
    @SerialName("CatalogUpdated")
    data class CatalogUpdated(val version: Long) : ClientboundPacket

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

    @Serializable
    @SerialName("PartyPing")
    data class PartyPing(
        val from: String,
        @SerialName("sent_at") val sentAt: Long,
        val id: String,
        val server: String,
        val dimension: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val kind: PingKind,
        val target: PingTarget? = null,
        val label: String? = null,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PartyPingCleared")
    data class PartyPingCleared(
        val id: String,
        val from: String,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PartyPingReply")
    data class PartyPingReply(
        val id: String,
        val owner: String,
        val from: String,
        val reply: PingReply,
        @SerialName("sent_at") val sentAt: Long,
    ) : ClientboundPacket
}
