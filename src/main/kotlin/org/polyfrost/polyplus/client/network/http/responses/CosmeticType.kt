package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CosmeticType {
    @SerialName("cape")
    Cape,

    @SerialName("backpack")
    Backpack,

    @SerialName("glasses")
    Glasses,

    @SerialName("wings")
    Wings,

    @SerialName("glove")
    Glove,

    @SerialName("emote")
    Emote;

    val serializedName: String
        get() = when (this) {
            Cape -> "cape"
            Backpack -> "backpack"
            Glasses -> "glasses"
            Wings -> "wings"
            Glove -> "glove"
            Emote -> "emote"
        }

    val displayName: String
        get() = when (this) {
            Cape -> "Cape"
            Backpack -> "Backpack"
            Glasses -> "Glasses"
            Wings -> "Wings"
            Glove -> "Glove"
            Emote -> "Emote"
        }

    companion object {
        val equippableSlots: List<CosmeticType> = listOf(Cape, Backpack, Glasses, Wings, Glove)

        fun fromSerializedName(name: String): CosmeticType? =
            entries.firstOrNull { it.serializedName == name }

        fun isEquippableSlot(type: CosmeticType): Boolean = type in equippableSlots
    }
}
