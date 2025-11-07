package org.polyfrost.polyplus.client.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ServerboundPacket {
    @Serializable
    @SerialName("GetActiveCosmetics")
    data class GetActiveCosmetics(val players: List<String>) : ServerboundPacket {
        constructor(vararg players: String) : this(players.toList())
    }
}
