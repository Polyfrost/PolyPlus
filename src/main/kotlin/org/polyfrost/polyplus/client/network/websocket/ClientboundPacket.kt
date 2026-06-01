package org.polyfrost.polyplus.client.network.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

@Serializable
sealed interface ClientboundPacket {
    @Serializable
    @SerialName("Error")
    data class Error(@SerialName("error_code") val code: String, val message: String) : ClientboundPacket

    @Serializable
    @SerialName("CosmeticsInfo")
    data class CosmeticsInfo(@SerialName("cosmetics") val all: HashMap<String, List<Int>>) : ClientboundPacket

    @Serializable
    @SerialName("SubscriptionSnapshot")
    data class SubscriptionSnapshot(
        val equipped: Map<String, Map<CosmeticType, Int>>,
        @SerialName("active_emotes") val activeEmotes: Map<String, Int>,
    ) : ClientboundPacket

    @Serializable
    @SerialName("PlayerCosmeticEquipped")
    data class PlayerCosmeticEquipped(
        val player: String,
        val slot: CosmeticType,
        @SerialName("cosmetic_id") val cosmeticId: Int?,
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
}
