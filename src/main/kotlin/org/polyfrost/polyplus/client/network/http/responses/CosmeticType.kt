package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CosmeticType.Serializer::class)
enum class CosmeticType {
    Cape,
    Backpack,
    Glasses,
    Wings,
    Glove,
    Hat,
    Aura,
    Boots,
    Shoulder,
    Emote,
    Unknown;

    val serializedName: String
        get() = when (this) {
            Cape -> "cape"
            Backpack -> "backpack"
            Glasses -> "glasses"
            Wings -> "wings"
            Glove -> "glove"
            Hat -> "hat"
            Aura -> "aura"
            Boots -> "boots"
            Shoulder -> "shoulder"
            Emote -> "emote"
            Unknown -> "unknown"
        }

    val displayName: String
        get() = when (this) {
            Cape -> "Cape"
            Backpack -> "Back"
            Glasses -> "Glasses"
            Wings -> "Wings"
            Glove -> "Glove"
            Hat -> "Hat"
            Aura -> "Aura"
            Boots -> "Boots"
            Shoulder -> "Shoulder"
            Emote -> "Emote"
            Unknown -> "Unknown"
        }

    fun defaultSlot(): BodySlot? = when (this) {
        Cape -> BodySlot.Cape
        Backpack -> BodySlot.Backpack
        Glasses -> BodySlot.Glasses
        Wings -> BodySlot.Wings
        Glove -> BodySlot.RightHand
        Hat -> BodySlot.Hat
        Aura -> BodySlot.Aura
        Boots -> BodySlot.Boots
        Shoulder -> BodySlot.Shoulder
        Emote, Unknown -> null
    }

    companion object {
        fun fromSerializedName(name: String): CosmeticType? =
            entries.firstOrNull { it != Unknown && it.serializedName == name }
    }

    internal object Serializer : KSerializer<CosmeticType> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CosmeticType", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: CosmeticType) {
            encoder.encodeString(value.serializedName)
        }

        override fun deserialize(decoder: Decoder): CosmeticType =
            fromSerializedName(decoder.decodeString()) ?: Unknown
    }
}
