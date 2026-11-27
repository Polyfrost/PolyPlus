package org.polyfrost.polyplus.client.network.websocket

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.http.responses.PlayDestination
import org.polyfrost.polyplus.client.network.http.responses.PlayReason
import org.polyfrost.polyplus.client.network.http.responses.PlayResponse
import org.polyfrost.polyplus.client.pets.PetPlay
import org.polyfrost.polyplus.client.pets.PetPlaySerializer
import org.polyfrost.polyplus.client.ping.PingKind
import org.polyfrost.polyplus.client.ping.PingReply
import org.polyfrost.polyplus.client.ping.PingTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ServerboundPacket {
    @Serializable
    @SerialName("GetActiveCosmetics")
    data class GetActiveCosmetics(val players: List<String>) : ServerboundPacket {
        constructor(vararg players: String) : this(players.toList())
    }

    @Serializable
    @SerialName("SubscribePlayers")
    data class SubscribePlayers(
        val players: List<String>,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ServerboundPacket {
        constructor(vararg players: String) : this(players.toList())
    }

    @Serializable
    @SerialName("UnsubscribePlayers")
    data class UnsubscribePlayers(val players: List<String>) : ServerboundPacket {
        constructor(vararg players: String) : this(players.toList())
    }

    @Serializable
    @SerialName("SetEquippedCosmetic")
    data class SetEquippedCosmetic(
        val slot: BodySlot,
        @SerialName("cosmetic_id") val cosmeticId: Int?,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SetParticleColor")
    data class SetParticleColor(
        val color: Int?,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SetCosmeticColors")
    data class SetCosmeticColors(
        @SerialName("cosmetic_id") val cosmeticId: Int,
        val colors: Map<String, Int>? = null,
        val active: Boolean = true,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SetPetPreferences")
    data class SetPetPreferences(
        @Serializable(with = PetPlaySerializer::class) val interactions: PetPlay,
    ) : ServerboundPacket

    @Serializable
    @SerialName("PlayEmote")
    data class PlayEmote(
        @SerialName("emote_id") val emoteId: Int,
        val anchor: EmoteAnchor? = null,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SharedEmoteRequest")
    data class SharedEmoteRequest(
        @SerialName("emote_id") val emoteId: Int,
        val target: String,
        @SerialName("request_id") val requestId: Long? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SharedEmoteResponse")
    data class SharedEmoteResponse(
        @SerialName("request_id") val requestId: String,
        val accept: Boolean,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SharedEmoteCancel")
    data class SharedEmoteCancel(
        @SerialName("request_id") val requestId: String,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SharedEmoteStart")
    data class SharedEmoteStart(
        @SerialName("request_id") val requestId: String,
        val anchor: EmoteAnchor,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SharedEmoteStop")
    data object SharedEmoteStop : ServerboundPacket

    @Serializable
    @SerialName("StopEmote")
    data class StopEmote(
        @SerialName("emote_id") val emoteId: Int? = null,
        @SerialName("start_time") val startTime: Long? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SetPartyState")
    data class SetPartyState(val state: MemberState) : ServerboundPacket

    @Serializable
    @SerialName("StartPlayTogether")
    data class StartPlayTogether(val destination: PlayDestination) : ServerboundPacket

    @Serializable
    @SerialName("CancelPlayTogether")
    data class CancelPlayTogether(val id: String) : ServerboundPacket

    @Serializable
    @SerialName("PlayTogetherResponse")
    data class PlayTogetherResponse(
        val id: String,
        val response: PlayResponse,
        val reason: PlayReason? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("SendPing")
    data class SendPing(
        val id: String,
        val server: String,
        val dimension: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val kind: PingKind,
        val target: PingTarget? = null,
        val label: String? = null,
    ) : ServerboundPacket

    @Serializable
    @SerialName("ClearPing")
    data class ClearPing(val id: String) : ServerboundPacket

    @Serializable
    @SerialName("ReplyPing")
    data class ReplyPing(
        val id: String,
        val owner: String,
        val reply: PingReply,
    ) : ServerboundPacket
}
