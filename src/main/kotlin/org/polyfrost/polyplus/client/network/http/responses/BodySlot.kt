package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BodySlot.Serializer::class)
enum class BodySlot {
    Cape,
    Backpack,
    Glasses,
    Wings,
    LeftHand,
    RightHand,
    Hat,
    Aura,
    Boots,
    Shoulder,
    Unknown;

    val serializedName: String
        get() = when (this) {
            Cape -> "cape"
            Backpack -> "backpack"
            Glasses -> "glasses"
            Wings -> "wings"
            LeftHand -> "left_hand"
            RightHand -> "right_hand"
            Hat -> "hat"
            Aura -> "aura"
            Boots -> "boots"
            Shoulder -> "shoulder"
            Unknown -> "unknown"
        }

    val displayName: String
        get() = when (this) {
            Cape -> "Cape"
            Backpack -> "Backpack"
            Glasses -> "Glasses"
            Wings -> "Wings"
            LeftHand -> "Left Hand"
            RightHand -> "Right Hand"
            Hat -> "Hat"
            Aura -> "Aura"
            Boots -> "Boots"
            Shoulder -> "Shoulder"
            Unknown -> "Unknown"
        }

    fun cosmeticType(): CosmeticType = when (this) {
        Cape -> CosmeticType.Cape
        Backpack -> CosmeticType.Backpack
        Glasses -> CosmeticType.Glasses
        Wings -> CosmeticType.Wings
        LeftHand, RightHand -> CosmeticType.Glove
        Hat -> CosmeticType.Hat
        Aura -> CosmeticType.Aura
        Boots -> CosmeticType.Boots
        Shoulder -> CosmeticType.Shoulder
        Unknown -> CosmeticType.Unknown
    }

    companion object {
        val equippableSlots: List<BodySlot> =
            listOf(Cape, Backpack, Glasses, Wings, LeftHand, RightHand, Hat, Aura, Boots, Shoulder)

        fun fromSerializedName(name: String): BodySlot? =
            entries.firstOrNull { it != Unknown && it.serializedName == name }

        fun isEquippableSlot(slot: BodySlot): Boolean = slot in equippableSlots
    }

    internal object Serializer : KSerializer<BodySlot> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("BodySlot", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BodySlot) {
            encoder.encodeString(value.serializedName)
        }

        override fun deserialize(decoder: Decoder): BodySlot =
            fromSerializedName(decoder.decodeString()) ?: Unknown
    }
}
