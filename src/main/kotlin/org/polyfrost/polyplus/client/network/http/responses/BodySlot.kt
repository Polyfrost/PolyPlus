package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BodySlot {
    @SerialName("cape")
    Cape,

    @SerialName("backpack")
    Backpack,

    @SerialName("glasses")
    Glasses,

    @SerialName("wings")
    Wings,

    @SerialName("left_hand")
    LeftHand,

    @SerialName("right_hand")
    RightHand;

    val serializedName: String
        get() = when (this) {
            Cape -> "cape"
            Backpack -> "backpack"
            Glasses -> "glasses"
            Wings -> "wings"
            LeftHand -> "left_hand"
            RightHand -> "right_hand"
        }

    val displayName: String
        get() = when (this) {
            Cape -> "Cape"
            Backpack -> "Backpack"
            Glasses -> "Glasses"
            Wings -> "Wings"
            LeftHand -> "Left hand"
            RightHand -> "Right hand"
        }

    companion object {
        fun fromSerializedName(name: String): BodySlot? =
            entries.firstOrNull { it.serializedName == name }

        fun defaultFor(type: CosmeticType): BodySlot? = when (type) {
            CosmeticType.Cape -> Cape
            CosmeticType.Backpack -> Backpack
            CosmeticType.Glasses -> Glasses
            CosmeticType.Wings -> Wings
            CosmeticType.Glove -> RightHand
            CosmeticType.Emote -> null
        }
    }
}
