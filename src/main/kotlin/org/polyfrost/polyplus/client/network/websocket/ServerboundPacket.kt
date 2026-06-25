package org.polyfrost.polyplus.client.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

@Serializable
sealed interface ServerboundPacket {
    @Serializable
    @SerialName("GetActiveCosmetics")
    data class GetActiveCosmetics(val players: List<String>) : ServerboundPacket {
        constructor(vararg players: String) : this(players.toList())
    }

    @Serializable
    @SerialName("SubscribePlayers")
    data class SubscribePlayers(val players: List<String>) : ServerboundPacket {
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
    @SerialName("PlayEmote")
    data class PlayEmote(@SerialName("emote_id") val emoteId: Int) : ServerboundPacket

    @Serializable
    @SerialName("StopEmote")
    data object StopEmote : ServerboundPacket
}
