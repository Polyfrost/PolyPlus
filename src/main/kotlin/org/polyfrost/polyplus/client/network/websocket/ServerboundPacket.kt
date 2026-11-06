package org.polyfrost.polyplus.client.network.websocket

import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.http.responses.PlayDestination
import org.polyfrost.polyplus.client.network.http.responses.PlayReason
import org.polyfrost.polyplus.client.network.http.responses.PlayResponse
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
    data class SetParticleColor(val color: Int?) : ServerboundPacket

    @Serializable
    @SerialName("PlayEmote")
    data class PlayEmote(@SerialName("emote_id") val emoteId: Int) : ServerboundPacket

    @Serializable
    @SerialName("StopEmote")
    data object StopEmote : ServerboundPacket

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
}
