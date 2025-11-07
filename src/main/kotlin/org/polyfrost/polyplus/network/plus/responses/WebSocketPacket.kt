package org.polyfrost.polyplus.network.plus.responses

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
object WebSocketPacket {
    object ClientBound {
        @Serializable
        @JsonClassDiscriminator("type")
        sealed class FallibleResponse {
            @Serializable
            @SerialName("Error")
            data class Error(
                @SerialName("error_code") val code: String,
                @SerialName("message") val message: String
            ) : FallibleResponse()

            @Serializable
            @SerialName("CosmeticsInfo")
            data class CosmeticsInfo(
                @SerialName("cosmetics") val cosmetics: HashMap<String, List<Int>>
            ) : FallibleResponse()
        }
    }

    object ServerBound {
        @Serializable
        @JsonClassDiscriminator("type")
        sealed class Packet {
            @Serializable
            @SerialName("GetActiveCosmetics")
            data class GetActiveCosmetics(
                @SerialName("players") val players: List<String>
            ) : Packet()
        }
    }
}