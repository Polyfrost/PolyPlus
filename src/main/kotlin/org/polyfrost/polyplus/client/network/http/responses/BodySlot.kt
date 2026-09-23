package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = BodySlot.Serializer::class)
enum class BodySlot(val serializedName: String, val displayName: String) {
    Cape("cape", "Cape"),
    Backpack("backpack", "Back"),
    Glasses("glasses", "Glasses"),
    Wings("wings", "Wings"),
    LeftHand("left_hand", "Left Hand"),
    RightHand("right_hand", "Right Hand"),
    Hat("hat", "Hat"),
    Aura("aura", "Aura"),
    Boots("boots", "Boots"),
    Shoulder("shoulder", "Shoulder"),
    Pet("pet", "Pet"),
    Unknown("unknown", "Unknown");

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
        Pet -> CosmeticType.Pet
        Unknown -> CosmeticType.Unknown
    }

    companion object {
        val equippableSlots: List<BodySlot> = entries - Unknown

        fun isEquippableSlot(slot: BodySlot): Boolean = slot in equippableSlots
    }

    internal object Serializer : KSerializer<BodySlot> by ApiEnumSerializer(
        "BodySlot",
        BodySlot.entries,
        Unknown,
        BodySlot::serializedName,
    )
}
